/**
 * AI 回复的 Markdown 渲染：marked 解析 + DOMPurify 清洗。
 *
 * ```mermaid 代码块会转换为占位容器，由 renderMermaidBlocks 在 DOM 更新后异步渲染为 SVG；
 * 渲染完成前或语法错误时展示源码，不影响其它内容的展示。
 */
import { marked, type TokenizerAndRendererExtension } from 'marked'
import DOMPurify from 'dompurify'

function escapeHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

const mermaidFence: TokenizerAndRendererExtension = {
  name: 'mermaid',
  level: 'block',
  start(src: string) {
    return src.match(/^[ \t]*(?:```|~~~)[ \t]*mermaid\b/m)?.index
  },
  tokenizer(src: string) {
    const match = /^[ \t]*(```|~~~)[ \t]*mermaid[^\n]*\n([\s\S]*?)\n[ \t]*\1[ \t]*(?:\n+|$)/.exec(src)
    if (!match) return undefined
    return { type: 'mermaid', raw: match[0], text: match[2] }
  },
  renderer(token) {
    const code = String(token.text || '')
    return `<div class="mermaid-block" data-mermaid="${encodeURIComponent(code)}">`
      + `<pre class="mermaid-source">${escapeHtml(code)}</pre></div>`
  }
}

marked.use({ extensions: [mermaidFence] })

/** 正文按 Markdown 渲染；HTML 经 DOMPurify 清洗后再插入。 */
export function renderMarkdown(text: string): string {
  if (!text) return ''
  const html = marked.parse(text, { gfm: true, breaks: true, async: false }) as string
  return DOMPurify.sanitize(html, { USE_PROFILES: { html: true } })
}

let mermaidLoader: Promise<import('mermaid').Mermaid> | null = null
let mermaidSeq = 0

function loadMermaid() {
  if (!mermaidLoader) {
    mermaidLoader = import('mermaid').then(({ default: mermaid }) => {
      mermaid.initialize({
        startOnLoad: false,
        securityLevel: 'strict',
        suppressErrorRendering: true,
        fontFamily: 'inherit',
        themeVariables: { fontSize: '13px' }
      })
      return mermaid
    })
  }
  return mermaidLoader
}

/** 将容器内尚未渲染的 mermaid 占位块渲染为 SVG。 */
export async function renderMermaidBlocks(container: HTMLElement) {
  const blocks = Array.from(container.querySelectorAll<HTMLElement>('.mermaid-block'))
  if (!blocks.length) return
  const mermaid = await loadMermaid()
  for (const block of blocks) {
    const encoded = block.dataset.mermaid
    if (!encoded || block.dataset.mermaidRendered === encoded) continue
    try {
      const { svg } = await mermaid.render(`ai-mermaid-${++mermaidSeq}`, decodeURIComponent(encoded))
      // 渲染是异步的，期间消息可能已更新或元素已被替换
      if (!block.isConnected || block.dataset.mermaid !== encoded) continue
      block.innerHTML = svg
      block.dataset.mermaidRendered = encoded
      block.classList.remove('mermaid-error')
      block.classList.add('mermaid-rendered')
    } catch (e) {
      // 语法不完整或非法时保留源码展示，等待下次更新重试
      block.classList.add('mermaid-error')
    }
  }
}
