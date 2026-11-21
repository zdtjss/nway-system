/**
 * 当前页面上下文：文本快照与 DOM 截图。
 *
 * 页面容器随渲染方式不同：
 * - native：current.vue 中 #currentMainComp 内的本地组件
 * - microApp / microApp-iframe / wujie：内容都在宿主文档，#currentMainApp 内；
 *   wujie 使用 open shadowRoot（attachShadow mode=open），需要递归读取
 * - wujie-iframe（degrade）：#currentMainApp 内的真实 iframe，继承宿主源，可读
 * - iFrame：#currentMainIframe 内的真实 iframe，同源可读
 * 跨域 iframe 受同源策略限制读不到，调用方按空结果降级。
 *
 * 截图基于 modern-screenshot（SVG foreignObject），支持 open shadowRoot；
 * iframe 页面直接截取其 body，避免 foreignObject 不渲染 iframe 内容的问题。
 */

const MAX_TEXT_CHARS = 12_000
const MAX_TEXT_NODES = 20_000
const MAX_TABLE_ROWS = 60
const MAX_IMAGE_SIDE = 2400
const MAX_IMAGE_PIXELS = 6_000_000
const MAX_IMAGE_BYTES = 4.8 * 1024 * 1024

const SKIP_TAGS = new Set([
    'SCRIPT', 'STYLE', 'NOSCRIPT', 'TEMPLATE', 'LINK', 'META', 'HEAD', 'SOURCE', 'TRACK', 'CANVAS'
])

export interface PageMeta {
    title: string
    url: string
    renderType: string
}

export interface PageTextSnapshot extends PageMeta {
    text: string
    truncated: boolean
}

interface WalkState {
    lines: string[]
    chars: number
    nodes: number
    tableRows: number
    truncated: boolean
}

type CheckVisibilityElement = Element & {
    checkVisibility?: (options?: { checkOpacity?: boolean; checkVisibilityCSS?: boolean }) => boolean
}

function frameBody(frame: HTMLIFrameElement | null): HTMLElement | null {
    if (!frame) return null
    try {
        return frame.contentDocument?.body ?? null
    } catch (e) {
        // 跨域 iframe：同源策略拒绝访问
        return null
    }
}

function isVisible(el: Element): boolean {
    const check = el as CheckVisibilityElement
    if (typeof check.checkVisibility === 'function') {
        return check.checkVisibility({ checkOpacity: false, checkVisibilityCSS: true })
    }
    return el.getClientRects().length > 0
}

function visibleFrameIn(selector: string): HTMLElement | null {
    const frames = document.querySelectorAll<HTMLIFrameElement>(selector)
    for (const frame of frames) {
        if (!isVisible(frame)) continue
        const body = frameBody(frame)
        if (body) return body
    }
    return null
}

/**
 * 定位当前页面的内容节点：iframe 页面返回其 body，其余返回宿主侧容器。
 */
export function resolvePageNode(renderType = ''): HTMLElement | null {
    if (renderType === 'iFrame') {
        return visibleFrameIn('#currentMainIframe iframe')
    }

    const appHost = document.getElementById('currentMainApp')
    if (appHost) {
        if (renderType.startsWith('wujie') && renderType.includes('-iframe')) {
            const body = visibleFrameIn('#currentMainApp iframe[data-wujie-id]')
            if (body) return body
        }
        const shell = appHost.querySelector<HTMLElement>('wujie-app, micro-app')
        if (shell && isVisible(shell)) return shell
    }

    const compHost = document.getElementById('currentMainComp')
    if (compHost && compHost.firstElementChild && isVisible(compHost)) return compHost

    return document.querySelector<HTMLElement>('.content-wrap')
}

function normalize(value: string | null | undefined): string {
    return (value || '').replace(/\u00a0/g, ' ').replace(/[ \t]+/g, ' ').trim()
}

function valueOfInput(el: HTMLInputElement): string {
    const type = (el.type || 'text').toLowerCase()
    if (type === 'password') return el.value ? '【密码已隐藏】' : ''
    if (type === 'checkbox' || type === 'radio') {
        if (!el.checked) return ''
        const label = normalize(el.closest('label')?.textContent) || normalize(el.getAttribute('aria-label'))
        return label ? `【已选中】${label}` : '【已选中】'
    }
    if (type === 'hidden' || type === 'file') return ''
    return normalize(el.value)
}

function valueOfSelect(el: HTMLSelectElement): string {
    return normalize(Array.from(el.selectedOptions).map((option) => option.textContent).join(' '))
}

function labelOf(el: Element): string {
    return normalize(el.textContent) || normalize(el.getAttribute('aria-label')) || normalize(el.getAttribute('title'))
}

function push(state: WalkState, text: string) {
    if (!text || state.truncated) return
    // 表格单元格结束符并到同一行，读起来接近表格结构
    if (text === '|') {
        if (state.lines.length) state.lines[state.lines.length - 1] += ' |'
        return
    }
    state.lines.push(text)
    state.chars += text.length + 1
    if (state.chars >= MAX_TEXT_CHARS + 2_000) state.truncated = true
}

function walk(node: Node, state: WalkState) {
    if (state.truncated) return

    if (node.nodeType === Node.TEXT_NODE) {
        push(state, normalize(node.nodeValue))
        return
    }
    if (node.nodeType !== Node.ELEMENT_NODE) return

    if (state.nodes >= MAX_TEXT_NODES) {
        state.truncated = true
        return
    }

    const el = node as HTMLElement
    const tag = el.tagName
    if (SKIP_TAGS.has(tag)) return
    if (el.classList.contains('gs-root')) return
    if (el.getAttribute('aria-hidden') === 'true') return
    if (!isVisible(el)) return

    state.nodes += 1

    if (tag === 'INPUT') {
        push(state, valueOfInput(el as HTMLInputElement))
        return
    }
    if (tag === 'TEXTAREA') {
        push(state, normalize((el as HTMLTextAreaElement).value))
        return
    }
    if (tag === 'SELECT') {
        push(state, valueOfSelect(el as HTMLSelectElement))
        return
    }
    if (tag === 'IMG') {
        push(state, normalize(el.getAttribute('alt')))
        return
    }
    if (tag === 'BUTTON' || tag === 'A') {
        push(state, labelOf(el))
        return
    }
    if (tag === 'TR') {
        state.tableRows += 1
        if (state.tableRows > MAX_TABLE_ROWS) return
    }
    if (tag === 'IFRAME') {
        const body = frameBody(el as HTMLIFrameElement)
        if (body) {
            push(state, '【内嵌页面】')
            walk(body, state)
            push(state, '【内嵌页面结束】')
        }
        return
    }

    if (el.shadowRoot) {
        for (const child of Array.from(el.shadowRoot.childNodes)) walk(child, state)
    }
    for (const child of Array.from(el.childNodes)) walk(child, state)
    if (tag === 'TD' || tag === 'TH') push(state, '|')
}

function tidy(lines: string[]): string {
    const result: string[] = []
    for (const raw of lines) {
        const line = normalize(raw)
        if (!line) {
            if (result.length && result[result.length - 1] !== '') result.push('')
            continue
        }
        if (result[result.length - 1] === line) continue
        result.push(line)
    }
    return result.join('\n').replace(/\n{3,}/g, '\n\n').trim()
}

/**
 * 生成当前页面的文本快照。页面不可读时返回 null。
 */
export function buildPageTextSnapshot(meta: PageMeta): PageTextSnapshot | null {
    const node = resolvePageNode(meta.renderType)
    if (!node) return null

    const state: WalkState = { lines: [], chars: 0, nodes: 0, tableRows: 0, truncated: false }
    walk(node, state)

    let text = tidy(state.lines)
    let truncated = state.truncated
    if (text.length > MAX_TEXT_CHARS) {
        const cut = text.lastIndexOf('\n', MAX_TEXT_CHARS)
        text = text.slice(0, cut > MAX_TEXT_CHARS / 2 ? cut : MAX_TEXT_CHARS)
        truncated = true
    }
    if (!text) return null

    return { ...meta, text, truncated }
}

function captureScale(node: HTMLElement): number {
    const rect = node.getBoundingClientRect()
    const width = node.clientWidth || rect.width
    const height = node.clientHeight || rect.height
    const side = Math.max(width, height, 1)
    return Math.max(1, Math.min(2, MAX_IMAGE_SIDE / side))
}

function canvasToBlob(canvas: HTMLCanvasElement, quality: number): Promise<Blob | null> {
    return new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', quality))
}

function fitCanvas(canvas: HTMLCanvasElement): HTMLCanvasElement {
    const side = Math.max(canvas.width, canvas.height)
    const pixels = canvas.width * canvas.height
    const ratio = Math.min(1, MAX_IMAGE_SIDE / side, Math.sqrt(MAX_IMAGE_PIXELS / Math.max(pixels, 1)))
    if (ratio >= 1) return canvas

    const out = document.createElement('canvas')
    out.width = Math.max(1, Math.round(canvas.width * ratio))
    out.height = Math.max(1, Math.round(canvas.height * ratio))
    const ctx = out.getContext('2d')
    if (!ctx) return canvas
    ctx.fillStyle = '#ffffff'
    ctx.fillRect(0, 0, out.width, out.height)
    ctx.drawImage(canvas, 0, 0, out.width, out.height)
    return out
}

function timestamp(): string {
    const now = new Date()
    const pad = (value: number) => String(value).padStart(2, '0')
    return `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}-${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`
}

/**
 * 对当前页面做 DOM 截图，返回可直接走附件上传接口的 File。
 * 页面不可读或库加载失败时抛出异常，由调用方降级为只发文本。
 */
export async function capturePageScreenshot(meta: PageMeta): Promise<File | null> {
    const node = resolvePageNode(meta.renderType)
    if (!node) return null

    const { domToCanvas } = await import('modern-screenshot')
    const canvas = await domToCanvas(node, {
        scale: captureScale(node),
        backgroundColor: '#ffffff',
        maximumCanvasSize: MAX_IMAGE_PIXELS,
        // iframe 内节点与宿主不是同一 realm，不能用 instanceof Element 判断
        filter: (target: Node) => {
            const classList = (target as Element)?.classList
            return !classList?.contains?.('gs-root')
        },
        features: { restoreScrollPosition: true }
    })

    const sized = fitCanvas(canvas)
    let blob = await canvasToBlob(sized, 0.92)
    if (blob && blob.size > MAX_IMAGE_BYTES) {
        blob = await canvasToBlob(sized, 0.78)
    }
    if (!blob) throw new Error('页面截图生成失败')

    return new File([blob], `页面截图-${timestamp()}.jpg`, { type: 'image/jpeg' })
}
