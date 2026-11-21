<template>
  <Teleport to="body">
    <div v-if="modelValue" class="nway-file-preview" @click.self="close" @wheel="onWheel">
      <div
        class="nway-file-preview__dialog"
        :class="{ 'is-dragging': draggingDialog, 'is-fullscreen': isFullscreen }"
        :style="dialogStyle"
      >
        <header class="nway-file-preview__header" @pointerdown="startDrag('dialog', $event)">
          <span class="nway-file-preview__title" :title="current?.fileName">{{ current?.fileName }}</span>
          <span class="nway-file-preview__meta">{{ formatFileSize(current?.fileSize) }}</span>
          <button type="button" class="nway-file-preview__action" @click="toggleFullscreen">
            {{ isFullscreen ? '退出全屏' : '全屏' }}
          </button>
          <button type="button" class="nway-file-preview__close" @click="close">×</button>
        </header>

        <main class="nway-file-preview__body" :class="{ 'is-image': kind === 'image', 'is-pdf': kind === 'pdf' }">
          <div v-if="loading" class="nway-file-preview__tip">加载中…</div>
          <div v-else-if="errorMessage" class="nway-file-preview__tip nway-file-preview__tip--error">
            <p>{{ errorMessage }}</p>
            <button type="button" class="nway-file-preview__btn" @click="openTicket(true)">重新加载</button>
          </div>
          <div
            v-else-if="kind === 'image'"
            class="nway-file-preview__image"
            :class="{ 'is-dragging': draggingImage }"
            @pointerdown="startDrag('image', $event)"
          >
            <img :src="ticketUrl" :style="imageStyle" alt="" draggable="false" @error="onMediaError" />
          </div>
          <video
            v-else-if="kind === 'video'"
            ref="videoRef"
            class="nway-file-preview__video"
            :src="ticketUrl"
            controls
            playsinline
            preload="auto"
            @loadedmetadata="onVideoMetadata"
            @error="onMediaError"
          ></video>
          <div v-else-if="kind === 'pdf'" class="nway-file-preview__pdf">
            <!-- 浏览器内置阅读器：直接内联打开（跨源时后端 X-Frame-Options 会拦截，走 PDF.js） -->
            <iframe
              v-if="useNativePdfViewer"
              class="nway-file-preview__pdf-frame"
              :src="ticketUrl"
              title="PDF 预览"
            ></iframe>
            <template v-else>
              <!-- PDF.js 不渲染书签，按 getOutline() 的结果自行维护目录侧栏 -->
              <aside v-if="pdfOutlineVisible && pdfOutline.length" class="nway-file-preview__outline">
                <div class="nway-file-preview__outline-title">书签</div>
                <ul class="nway-file-preview__outline-list">
                  <li v-for="(item, i) in pdfOutline" :key="i">
                    <button
                      type="button"
                      class="nway-file-preview__outline-item"
                      :class="{ 'is-active': item.page === pdfPageNumber }"
                      :style="{ paddingLeft: `${10 + item.depth * 14}px` }"
                      :title="item.title"
                      @click="jumpToOutline(item)"
                    >
                      {{ item.title }}
                    </button>
                  </li>
                </ul>
              </aside>
              <!-- PDF.js 兜底：按页连续渲染，滚动容器天然支持滚轮/触控板滑动 -->
              <div ref="pdfViewerRef" class="nway-file-preview__pdfjs" @scroll.passive="onPdfScroll">
                <div v-if="pdfLoading" class="nway-file-preview__tip">加载中…</div>
                <div v-else-if="pdfError" class="nway-file-preview__tip nway-file-preview__tip--error">
                  <p>{{ pdfError }}</p>
                  <button type="button" class="nway-file-preview__btn" @click="openTicket(true)">重新加载</button>
                  <button type="button" class="nway-file-preview__btn" @click="download">下载文件</button>
                </div>
                <div v-show="!pdfError" class="nway-file-preview__pages">
                  <div
                    v-for="page in pdfPages"
                    :key="page.number"
                    class="nway-file-preview__page"
                    :data-page="page.number"
                    :style="{ width: `${page.width}px`, height: `${page.height}px` }"
                  >
                    <canvas></canvas>
                  </div>
                </div>
              </div>
            </template>
          </div>
          <div v-else class="nway-file-preview__tip">
            <p>该文件类型不支持在线预览</p>
            <button type="button" class="nway-file-preview__btn" @click="download">下载文件</button>
          </div>
        </main>

        <footer class="nway-file-preview__footer">
          <div v-if="kind === 'image'" class="nway-file-preview__toolbar">
            <button type="button" class="nway-file-preview__btn" @click="zoomBy(0.25)">放大</button>
            <button type="button" class="nway-file-preview__btn" @click="zoomBy(-0.25)">缩小</button>
            <button type="button" class="nway-file-preview__btn" @click="rotateBy(90)">旋转</button>
            <button type="button" class="nway-file-preview__btn" @click="reset">重置</button>
          </div>
          <div v-else-if="kind === 'pdf'" class="nway-file-preview__toolbar">
            <template v-if="useNativePdfViewer">
              <button type="button" class="nway-file-preview__btn" @click="openPdfInNewTab">新窗口打开</button>
            </template>
            <template v-else>
              <button
                v-if="pdfOutline.length"
                type="button"
                class="nway-file-preview__btn"
                @click="toggleOutline"
              >
                {{ pdfOutlineVisible ? '隐藏书签' : '书签' }}
              </button>
              <button
                type="button"
                class="nway-file-preview__btn"
                :disabled="pdfLoading || pdfPageNumber <= 1"
                @click="changePdfPage(-1)"
              >
                上一页
              </button>
              <span>{{ pdfPageNumber }} / {{ pdfPageCount || '-' }}</span>
              <button
                type="button"
                class="nway-file-preview__btn"
                :disabled="pdfLoading || pdfPageNumber >= pdfPageCount"
                @click="changePdfPage(1)"
              >
                下一页
              </button>
            </template>
            <button type="button" class="nway-file-preview__btn" @click="download">下载文件</button>
          </div>
          <div v-if="navFiles.length > 1" class="nway-file-preview__nav">
            <button type="button" class="nway-file-preview__btn" :disabled="index <= 0" @click="prev">上一张</button>
            <span>{{ index + 1 }} / {{ navFiles.length }}</span>
            <button
              type="button"
              class="nway-file-preview__btn"
              :disabled="index >= navFiles.length - 1"
              @click="next"
            >
              下一张
            </button>
          </div>
        </footer>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, shallowRef, watch } from 'vue'
import type { FileClient, FileMeta } from '../types'
import { canPreviewPdfNatively, formatFileSize, resolvePreviewKind } from './fileType'

export interface Props {
  modelValue: boolean
  file?: FileMeta | null
  client: FileClient
  files?: FileMeta[]
}

const props = withDefaults(defineProps<Props>(), {
  file: null,
  files: () => []
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'change', value: FileMeta): void
}>()

const index = ref(0)
const ticketUrl = ref('')
const loading = ref(false)
const errorMessage = ref('')
const refreshCount = ref(0)
const scale = ref(1)
const rotate = ref(0)
const offsetX = ref(0)
const offsetY = ref(0)
const dialogOffset = reactive({ x: 0, y: 0 })
const draggingDialog = ref(false)
const draggingImage = ref(false)
const isFullscreen = ref(false)
const videoRef = ref<HTMLVideoElement | null>(null)
const pdfViewerRef = ref<HTMLDivElement | null>(null)
const pdfDoc = shallowRef<PdfDocumentProxy | null>(null)
const pdfPages = ref<PdfPageState[]>([])
const pdfPageNumber = ref(1)
const pdfPageCount = ref(0)
const pdfLoading = ref(false)
const pdfError = ref('')
const pdfOutline = ref<PdfOutlineItem[]>([])
const pdfOutlineVisible = ref(true)
let pdfLoadTask: PdfLoadingTask | null = null
let pdfRequestId = 0
let pdfObserver: IntersectionObserver | null = null
let pdfScrollFrame = 0
let pdfRelayoutTimer = 0
const pdfRenderTasks = new Map<number, PdfRenderTask>()

const navFiles = computed<FileMeta[]>(() => {
  const list = props.files && props.files.length ? props.files : []
  if (!list.length) {
    return props.file ? [props.file] : []
  }
  // 当前文件不在列表内时以当前文件为准，避免导航到错误的图片
  if (props.file && !list.some((item) => item.id === props.file?.id)) {
    return [props.file]
  }
  return list
})

const current = computed<FileMeta | null>(() => navFiles.value[index.value] || props.file || null)

const kind = computed(() => resolvePreviewKind(current.value))

/**
 * PDF 预览模式：浏览器内置阅读器 + iframe。
 * 跨源时后端响应带 X-Frame-Options: SAMEORIGIN，iframe 会被拦截，因此改用 PDF.js 渲染。
 */
const useNativePdfViewer = computed(
  () => kind.value === 'pdf' && canPreviewPdfNatively() && isSameOrigin(ticketUrl.value)
)

const imageStyle = computed(() => ({
  transform: `translate(${offsetX.value}px, ${offsetY.value}px) scale(${scale.value}) rotate(${rotate.value}deg)`
}))

const dialogStyle = computed(() => ({
  transform: `translate(${dialogOffset.x}px, ${dialogOffset.y}px)`
}))

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      syncIndex()
      resetTransform()
      resetDialogPosition()
      openTicket()
    } else {
      destroyPdf()
      isFullscreen.value = false
    }
  }
)

watch(
  () => props.file?.id,
  () => {
    if (!props.modelValue || current.value?.id === props.file?.id) {
      return
    }
    syncIndex()
    resetTransform()
    openTicket()
  }
)

// 票据地址就绪后加载 PDF.js 兜底渲染；原生阅读器模式下无需额外处理
watch([ticketUrl, kind], () => {
  destroyPdf()
  if (!props.modelValue || kind.value !== 'pdf' || useNativePdfViewer.value || !ticketUrl.value) {
    return
  }
  void loadPdf(ticketUrl.value)
})

function syncIndex() {
  refreshCount.value = 0
  if (!navFiles.value.length) {
    index.value = 0
    return
  }
  const found = props.file ? navFiles.value.findIndex((item) => item.id === props.file?.id) : 0
  index.value = found >= 0 ? found : 0
}

async function openTicket(force = false) {
  const file = current.value
  if (!file) {
    errorMessage.value = '文件信息缺失'
    return
  }
  if (kind.value === 'other') {
    errorMessage.value = ''
    ticketUrl.value = ''
    loading.value = false
    return
  }
  if (!force) {
    refreshCount.value = 0
  }
  loading.value = true
  errorMessage.value = ''
  ticketUrl.value = ''
  try {
    const ticket = await props.client.createTicket(file.id, 'preview')
    ticketUrl.value = props.client.resolveUrl(ticket.url)
  } catch (e) {
    errorMessage.value = (e as { message?: string })?.message || '预览加载失败'
  } finally {
    loading.value = false
  }
}

/**
 * 自动播放：浏览器允许时直接播放；被自动播放策略拦截（未静音 + 无用户手势）时 play() 会拒绝，
 * 此时保留 preload="auto" 已解码的首帧和原生控件，避免出现纯黑画面，由用户手动点击播放。
 */
function onVideoMetadata() {
  void videoRef.value?.play()?.catch(() => {})
}

function onMediaError() {
  if (refreshCount.value < 1) {
    refreshCount.value += 1
    openTicket(true)
    return
  }
  errorMessage.value = '文件加载失败，请稍后重试'
}

interface PdfRenderTask {
  promise: Promise<void>
  cancel(): void
}

interface PdfPageState {
  number: number
  width: number
  height: number
  rendered: boolean
}

interface PdfViewport {
  width: number
  height: number
}

interface PdfPageProxy {
  getViewport(params: { scale: number }): PdfViewport
  render(params: { canvas: HTMLCanvasElement; viewport: PdfViewport }): PdfRenderTask
}

interface PdfDocumentProxy {
  numPages: number
  getPage(pageNumber: number): Promise<PdfPageProxy>
  /** PDF 书签目录（Outline），无书签时返回 null */
  getOutline(): Promise<PdfOutlineNode[] | null>
  /** 具名跳转目标：书签的 dest 为字符串时需要通过它解析成显式目标数组 */
  getDestination(name: string): Promise<unknown>
  /** 由目标数组中的页面引用换算出 0 起的页序号 */
  getPageIndex(ref: { num: number; gen: number }): Promise<number>
}

interface PdfOutlineNode {
  title?: string
  dest?: unknown
  url?: string | null
  items?: PdfOutlineNode[]
}

/** 拍平后的书签项：depth 用于缩进，page 为 1 起页码（外部链接为 null） */
interface PdfOutlineItem {
  title: string
  depth: number
  page: number | null
  url: string
}

interface PdfLoadingTask {
  promise: Promise<PdfDocumentProxy>
  destroy(): Promise<void>
}

interface PdfJsModule {
  GlobalWorkerOptions: { workerSrc: string }
  getDocument(params: {
    url: string
    cMapPacked?: boolean
    BinaryDataFactory?: PdfBinaryDataFactory
  }): PdfLoadingTask
}

type PdfAssetKind = 'cMapUrl' | 'standardFontDataUrl' | 'wasmUrl'

interface PdfAssetRequest {
  kind: PdfAssetKind
  filename: string
}

interface PdfBinaryDataFactory {
  new (params: Record<string, unknown>): { fetch(request: PdfAssetRequest): Promise<Uint8Array> }
}

/** 包内静态资源目录：CMap / 标准字体 / WASM 不再依赖 CDN */
const pdfAssetDirs: Record<PdfAssetKind, string> = {
  cMapUrl: 'cmaps',
  standardFontDataUrl: 'standard_fonts',
  wasmUrl: 'wasm'
}

// 按需加载随包发布的资源；exhaustive 允许匹配 node_modules 下的文件
const pdfAssetLoaders = import.meta.glob(
  [
    '/node_modules/pdfjs-dist/cmaps/*.bcmap',
    '/node_modules/pdfjs-dist/standard_fonts/*.pfb',
    '/node_modules/pdfjs-dist/standard_fonts/*.ttf',
    '/node_modules/pdfjs-dist/wasm/*.wasm'
  ],
  { query: '?url', import: 'default', exhaustive: true }
) as Record<string, () => Promise<string>>

/** 替代默认的 DOM 取数逻辑：从打包产物中定位 PDF.js 资源并读取字节 */
class BundledPdfAssetFactory {
  async fetch({ kind, filename }: PdfAssetRequest): Promise<Uint8Array> {
    const loader = pdfAssetLoaders[`/node_modules/pdfjs-dist/${pdfAssetDirs[kind]}/${filename}`]
    if (!loader) {
      throw new Error(`PDF.js 资源缺失: ${kind}/${filename}`)
    }
    const response = await fetch(await loader())
    if (!response.ok) {
      throw new Error(`PDF.js 资源加载失败: ${kind}/${filename}`)
    }
    return new Uint8Array(await response.arrayBuffer())
  }
}

let pdfJsModulePromise: Promise<PdfJsModule> | null = null

/** 按需加载随包引入的 PDF.js（legacy 构建兼容旧内核），并设置同版本的 worker；失败时允许下次重试 */
function loadPdfJsModule(): Promise<PdfJsModule> {
  if (!pdfJsModulePromise) {
    pdfJsModulePromise = Promise.all([
      import('pdfjs-dist/legacy/build/pdf.mjs'),
      import('pdfjs-dist/legacy/build/pdf.worker.min.mjs?url')
    ])
      .then(([mod, worker]) => {
        const pdfjs = (mod as { default?: PdfJsModule }).default || (mod as unknown as PdfJsModule)
        pdfjs.GlobalWorkerOptions.workerSrc = worker.default
        return pdfjs
      })
      .catch((error) => {
        pdfJsModulePromise = null
        throw error
      })
  }
  return pdfJsModulePromise
}

function isSameOrigin(url: string): boolean {
  if (!url || typeof window === 'undefined') {
    return false
  }
  try {
    return new URL(url, window.location.href).origin === window.location.origin
  } catch (e) {
    return false
  }
}

async function loadPdf(url: string) {
  const requestId = ++pdfRequestId
  pdfLoading.value = true
  pdfError.value = ''
  pdfPageCount.value = 0
  pdfPageNumber.value = 1
  pdfPages.value = []
  // 等待可视化容器渲染完成，渲染尺寸按实际宽度计算
  await nextTick()
  try {
    const pdfjs = await loadPdfJsModule()
    if (requestId !== pdfRequestId) {
      return
    }
    const task = pdfjs.getDocument({
      url,
      cMapPacked: true,
      // CMap、标准字体与 WASM 解码器均取自随包资源，无需外网 CDN
      BinaryDataFactory: BundledPdfAssetFactory
    })
    pdfLoadTask = task
    const doc = await task.promise
    if (requestId !== pdfRequestId) {
      void task.destroy()
      return
    }
    pdfDoc.value = doc
    pdfPageCount.value = doc.numPages
    await loadPdfOutline(doc)
    if (requestId !== pdfRequestId) {
      return
    }
    // 等侧栏渲染完成，页面宽度按扣掉侧栏后的实际容器宽度计算
    await nextTick()
    await buildPdfPages()
  } catch (e) {
    if (requestId !== pdfRequestId) {
      return
    }
    pdfError.value = 'PDF 加载失败，请下载后查看'
  } finally {
    if (requestId === pdfRequestId) {
      pdfLoading.value = false
    }
  }
}

/**
 * 生成连续滚动的页面列表：先按首页比例铺出全部占位，滚动到可视区域附近再实际渲染，
 * 这样滚轮/触控板可以像原生阅读器一样自然翻页，也避免一次性渲染几百页。
 */
async function buildPdfPages() {
  const doc = pdfDoc.value
  if (!doc) {
    return
  }
  const width = pdfPageCssWidth()
  const firstPage = await doc.getPage(1)
  // 等待期间预览可能已关闭或重新加载
  if (pdfDoc.value !== doc) {
    return
  }
  const baseViewport = firstPage.getViewport({ scale: 1 })
  const height = Math.round((width * baseViewport.height) / baseViewport.width)
  pdfViewerRef.value?.querySelectorAll<HTMLCanvasElement>('.nway-file-preview__page canvas').forEach((canvas) => {
    canvas.width = 0
    canvas.height = 0
    canvas.style.width = ''
    canvas.style.height = ''
  })
  pdfPages.value = Array.from({ length: doc.numPages }, (_, i) => ({
    number: i + 1,
    width,
    height,
    rendered: false
  }))
  await nextTick()
  observePdfPages()
  syncPdfCurrentPage()
}

/**
 * 读取书签目录并拍平：PDF.js 只解析页面内容，书签需要单独取 Outline 再换算成页码。
 * 单个节点解析失败（目标缺失、命名目标已损坏）时只影响该项，不阻断整体预览。
 */
async function loadPdfOutline(doc: PdfDocumentProxy) {
  let nodes: PdfOutlineNode[] | null = null
  try {
    nodes = await doc.getOutline()
  } catch (e) {
    nodes = null
  }
  const items: PdfOutlineItem[] = []
  if (nodes?.length) {
    const walk = async (list: PdfOutlineNode[], depth: number) => {
      for (const node of list) {
        items.push({
          title: (node.title || '未命名书签').trim(),
          depth,
          page: await resolveOutlinePage(doc, node.dest),
          url: node.url || ''
        })
        if (node.items?.length) {
          await walk(node.items, depth + 1)
        }
      }
    }
    await walk(nodes, 0)
  }
  if (pdfDoc.value === doc) {
    pdfOutline.value = items
  }
}

/** 把书签的 dest（具名目标字符串 / 显式目标数组）解析成 1 起页码 */
async function resolveOutlinePage(doc: PdfDocumentProxy, dest: unknown): Promise<number | null> {
  try {
    const target = typeof dest === 'string' ? await doc.getDestination(dest) : dest
    if (!Array.isArray(target) || !target.length) {
      return null
    }
    const ref = target[0] as number | { num?: number; gen?: number } | null
    // 显式目标的第一项可能是页码（1 起），也可能是页面引用 { num, gen }（0 起）
    if (typeof ref === 'number') {
      return ref >= 1 ? ref : null
    }
    if (ref && typeof ref.num === 'number') {
      return (await doc.getPageIndex({ num: ref.num, gen: ref.gen ?? 0 })) + 1
    }
    return null
  } catch (e) {
    return null
  }
}

function jumpToOutline(item: PdfOutlineItem) {
  if (item.page && item.page >= 1 && item.page <= pdfPageCount.value) {
    scrollToPdfPage(item.page)
    return
  }
  if (item.url) {
    window.open(item.url, '_blank', 'noopener')
  }
}

function toggleOutline() {
  pdfOutlineVisible.value = !pdfOutlineVisible.value
  // 侧栏显隐会改变页面可用宽度，需要按新宽度重排
  schedulePdfRelayout()
}

function pdfPageCssWidth(): number {
  const viewer = pdfViewerRef.value
  return Math.max((viewer?.clientWidth || 960) - 24, 320)
}

/** 只渲染进入可视区（含前后 400px 预取）的页面 */
function observePdfPages() {
  pdfObserver?.disconnect()
  pdfObserver = null
  const viewer = pdfViewerRef.value
  if (!viewer || typeof IntersectionObserver === 'undefined') {
    return
  }
  pdfObserver = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) {
          return
        }
        void renderPdfPage(Number((entry.target as HTMLElement).dataset.page))
      })
    },
    { root: viewer, rootMargin: '400px 0px' }
  )
  viewer.querySelectorAll<HTMLElement>('.nway-file-preview__page').forEach((el) => pdfObserver?.observe(el))
}

/** 按容器宽度与设备像素比渲染指定页 */
async function renderPdfPage(pageNumber: number) {
  const doc = pdfDoc.value
  const viewer = pdfViewerRef.value
  const state = pdfPages.value[pageNumber - 1]
  if (!doc || !viewer || !state || state.rendered || pdfRenderTasks.has(pageNumber)) {
    return
  }
  const host = viewer.querySelector<HTMLElement>(`.nway-file-preview__page[data-page="${pageNumber}"]`)
  const canvas = host?.querySelector('canvas')
  if (!canvas) {
    return
  }
  const requestId = pdfRequestId
  try {
    const page = await doc.getPage(pageNumber)
    if (requestId !== pdfRequestId) {
      return
    }
    const baseViewport = page.getViewport({ scale: 1 })
    const width = pdfPageCssWidth()
    const ratio = Math.min(window.devicePixelRatio || 1, 2)
    const viewport = page.getViewport({ scale: (width / baseViewport.width) * ratio })
    const cssWidth = Math.round(viewport.width / ratio)
    const cssHeight = Math.round(viewport.height / ratio)
    // 占位框与实际渲染尺寸对齐，避免滚动位置跳动
    state.width = cssWidth
    state.height = cssHeight
    canvas.width = Math.floor(viewport.width)
    canvas.height = Math.floor(viewport.height)
    canvas.style.width = `${cssWidth}px`
    canvas.style.height = `${cssHeight}px`
    // v6 起渲染参数使用 canvas 本身，不再传 canvasContext
    const task = page.render({ canvas, viewport })
    pdfRenderTasks.set(pageNumber, task)
    try {
      await task.promise
      if (requestId === pdfRequestId) {
        state.rendered = true
      }
    } finally {
      pdfRenderTasks.delete(pageNumber)
    }
  } catch (e) {
    // 关闭或重排时取消渲染会抛 RenderingCancelledException，属于正常流程
    if ((e as { name?: string })?.name !== 'RenderingCancelledException' && requestId === pdfRequestId) {
      pdfError.value = 'PDF 渲染失败，请下载后查看'
    }
  }
}

function onPdfScroll() {
  if (pdfScrollFrame) {
    return
  }
  pdfScrollFrame = window.requestAnimationFrame(() => {
    pdfScrollFrame = 0
    syncPdfCurrentPage()
  })
}

/** 以容器上方 30% 处为基准线，取已滚动到的最后一页作为当前页 */
function syncPdfCurrentPage() {
  const viewer = pdfViewerRef.value
  if (!viewer || !pdfPages.value.length) {
    return
  }
  const anchor = viewer.getBoundingClientRect().top + viewer.clientHeight * 0.3
  const hosts = viewer.querySelectorAll<HTMLElement>('.nway-file-preview__page')
  let current = 1
  for (let i = 0; i < hosts.length; i += 1) {
    if (hosts[i].getBoundingClientRect().top > anchor) {
      break
    }
    current = Number(hosts[i].dataset.page)
  }
  pdfPageNumber.value = current
}

/** 上一页/下一页按钮：滚动定位到目标页，滚轮本身由容器原生滚动接管 */
function changePdfPage(delta: number) {
  const target = pdfPageNumber.value + delta
  if (!pdfPages.value.length || target < 1 || target > pdfPageCount.value) {
    return
  }
  scrollToPdfPage(target)
}

function scrollToPdfPage(pageNumber: number) {
  const viewer = pdfViewerRef.value
  const host = viewer?.querySelector<HTMLElement>(`.nway-file-preview__page[data-page="${pageNumber}"]`)
  if (!viewer || !host) {
    return
  }
  const top = host.getBoundingClientRect().top - viewer.getBoundingClientRect().top + viewer.scrollTop - 12
  viewer.scrollTo({ top, behavior: 'smooth' })
  pdfPageNumber.value = pageNumber
}

/** 全屏切换后容器宽度变化，按新宽度重排页面 */
function schedulePdfRelayout() {
  if (kind.value !== 'pdf' || useNativePdfViewer.value) {
    return
  }
  window.clearTimeout(pdfRelayoutTimer)
  pdfRelayoutTimer = window.setTimeout(() => {
    void relayoutPdf()
  }, 150)
}

async function relayoutPdf() {
  const doc = pdfDoc.value
  const viewer = pdfViewerRef.value
  if (!doc || !viewer || pdfLoading.value) {
    return
  }
  // 让在途的按页渲染结果失效，避免用旧宽度回写画布
  pdfRequestId += 1
  const scrollTop = viewer.scrollTop
  pdfRenderTasks.forEach((task) => task.cancel())
  pdfRenderTasks.clear()
  pdfObserver?.disconnect()
  pdfObserver = null
  await buildPdfPages()
  if (pdfViewerRef.value) {
    pdfViewerRef.value.scrollTop = scrollTop
  }
}

function openPdfInNewTab() {
  if (ticketUrl.value) {
    window.open(ticketUrl.value, '_blank', 'noopener')
  }
}

/** 释放 PDF.js 资源；递增请求号让仍在途的加载/渲染结果失效 */
function destroyPdf() {
  pdfRequestId += 1
  pdfObserver?.disconnect()
  pdfObserver = null
  if (pdfScrollFrame) {
    window.cancelAnimationFrame(pdfScrollFrame)
    pdfScrollFrame = 0
  }
  window.clearTimeout(pdfRelayoutTimer)
  pdfRenderTasks.forEach((task) => task.cancel())
  pdfRenderTasks.clear()
  // v6 移除 PDFDocumentProxy.destroy()，文档与 worker 统一通过 loadingTask 释放
  if (pdfLoadTask) {
    void pdfLoadTask.destroy().catch(() => {})
    pdfLoadTask = null
  }
  pdfDoc.value = null
  pdfPages.value = []
  pdfPageCount.value = 0
  pdfPageNumber.value = 1
  pdfOutline.value = []
  pdfOutlineVisible.value = true
  pdfLoading.value = false
  pdfError.value = ''
}

function resetTransform() {
  scale.value = 1
  rotate.value = 0
  offsetX.value = 0
  offsetY.value = 0
}

function resetDialogPosition() {
  dialogOffset.x = 0
  dialogOffset.y = 0
}

/**
 * 预览窗口全屏：仅在浏览器内把弹窗铺满视口（无边框、无阴影），
 * 不调用浏览器原生全屏 API，避免接管显示器屏幕。
 */
function toggleFullscreen() {
  isFullscreen.value = !isFullscreen.value
  if (isFullscreen.value) {
    resetDialogPosition()
  }
  schedulePdfRelayout()
}

function zoomBy(delta: number) {
  scale.value = Math.min(4, Math.max(0.25, scale.value + delta))
}

function rotateBy(degree: number) {
  rotate.value = (rotate.value + degree) % 360
}

function reset() {
  resetTransform()
}

type DragType = 'dialog' | 'image'

interface DragState {
  type: DragType
  startX: number
  startY: number
  originX: number
  originY: number
}

let dragState: DragState | null = null

function startDrag(type: DragType, event: PointerEvent) {
  if (event.button !== 0) {
    return
  }
  if (type === 'image' && kind.value !== 'image') {
    return
  }
  // 全屏时拖动弹窗会让内容整体偏移，禁用标题栏拖动
  if (type === 'dialog' && (isFullscreen.value || (event.target as HTMLElement | null)?.closest('button'))) {
    return
  }
  dragState = {
    type,
    startX: event.clientX,
    startY: event.clientY,
    originX: type === 'dialog' ? dialogOffset.x : offsetX.value,
    originY: type === 'dialog' ? dialogOffset.y : offsetY.value
  }
  if (type === 'dialog') {
    draggingDialog.value = true
  } else {
    draggingImage.value = true
  }
  window.addEventListener('pointermove', onDragMove)
  window.addEventListener('pointerup', endDrag)
  window.addEventListener('pointercancel', endDrag)
  event.preventDefault()
}

function onDragMove(event: PointerEvent) {
  if (!dragState) {
    return
  }
  const deltaX = event.clientX - dragState.startX
  const deltaY = event.clientY - dragState.startY
  if (dragState.type === 'dialog') {
    dialogOffset.x = dragState.originX + deltaX
    dialogOffset.y = dragState.originY + deltaY
    return
  }
  offsetX.value = dragState.originX + deltaX
  offsetY.value = dragState.originY + deltaY
}

function endDrag() {
  dragState = null
  draggingDialog.value = false
  draggingImage.value = false
  window.removeEventListener('pointermove', onDragMove)
  window.removeEventListener('pointerup', endDrag)
  window.removeEventListener('pointercancel', endDrag)
}

function onWheel(event: WheelEvent) {
  if (kind.value !== 'image') {
    return
  }
  // 仅图片预览需要拦截滚轮缩放，PDF/视频区域保留原生滚动
  event.preventDefault()
  zoomBy(event.deltaY < 0 ? 0.1 : -0.1)
}

function prev() {
  if (index.value <= 0) {
    return
  }
  index.value -= 1
  afterNavigate()
}

function next() {
  if (index.value >= navFiles.value.length - 1) {
    return
  }
  index.value += 1
  afterNavigate()
}

function afterNavigate() {
  resetTransform()
  refreshCount.value = 0
  openTicket()
  const file = current.value
  if (file) {
    emit('change', file)
  }
}

async function download() {
  const file = current.value
  if (!file) {
    return
  }
  try {
    await props.client.download(file.id, file.fileName)
  } catch (e) {
    errorMessage.value = (e as { message?: string })?.message || '下载失败'
  }
}

function close() {
  isFullscreen.value = false
  emit('update:modelValue', false)
}

function onKeydown(event: KeyboardEvent) {
  if (!props.modelValue) {
    return
  }
  if (event.key === 'Escape') {
    // 全屏状态下 Esc 先退出全屏，非全屏时才关闭预览
    if (isFullscreen.value) {
      toggleFullscreen()
    } else {
      close()
    }
  } else if (event.key === 'ArrowLeft') {
    prev()
  } else if (event.key === 'ArrowRight') {
    next()
  }
}

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  endDrag()
  destroyPdf()
})
</script>

<script lang="ts">
export default {
  name: 'NwayFilePreview'
}
</script>

<style scoped>
.nway-file-preview {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.72);
}

.nway-file-preview__dialog {
  display: flex;
  flex-direction: column;
  width: min(1080px, 92vw);
  max-height: 92vh;
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.35);
}

.nway-file-preview__header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid #ebeef5;
  cursor: move;
  user-select: none;
}

.nway-file-preview__title {
  flex: 1;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.nway-file-preview__meta {
  color: #909399;
  font-size: 12px;
}

.nway-file-preview__action {
  flex: none;
  height: 28px;
  padding: 0 10px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #fff;
  color: #606266;
  font-size: 12px;
  cursor: pointer;
}

.nway-file-preview__action:hover {
  border-color: #409eff;
  color: #409eff;
}

.nway-file-preview__close {
  flex: none;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: #606266;
  font-size: 20px;
  line-height: 1;
  cursor: pointer;
}

.nway-file-preview__close:hover {
  background: #f2f3f5;
}

/* 全屏：弹窗在浏览器视口内铺满（无边框、无阴影），不进入显示器级原生全屏 */
.nway-file-preview__dialog.is-fullscreen {
  width: 100%;
  height: 100%;
  max-height: none;
  border-radius: 0;
  box-shadow: none;
  transform: none !important;
}

.nway-file-preview__dialog.is-fullscreen .nway-file-preview__header {
  cursor: default;
}

.nway-file-preview__dialog.is-fullscreen .nway-file-preview__pdf {
  height: 100%;
}

.nway-file-preview__dialog.is-fullscreen .nway-file-preview__image img,
.nway-file-preview__dialog.is-fullscreen .nway-file-preview__video {
  max-height: 100%;
}

.nway-file-preview__body {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 320px;
  padding: 12px;
  background: #f5f7fa;
  overflow: auto;
}

/* 图片支持拖拽平移，隐藏滚动条避免拖动时出现滚动 */
.nway-file-preview__body.is-image {
  overflow: hidden;
}

/* PDF 由内嵌阅读器 / PDF.js 自行管理滚动与留白 */
.nway-file-preview__body.is-pdf {
  padding: 0;
  overflow: hidden;
}

.nway-file-preview__pdf {
  display: flex;
  width: 100%;
  height: 72vh;
  min-height: 0;
}

.nway-file-preview__pdf-frame {
  width: 100%;
  height: 100%;
  border: none;
  background: #fff;
}

/* 书签侧栏：层级用左内边距缩进，长标题省略并保留 title 提示 */
.nway-file-preview__outline {
  display: flex;
  flex: none;
  flex-direction: column;
  width: 220px;
  height: 100%;
  border-right: 1px solid #ebeef5;
  background: #fff;
  box-sizing: border-box;
}

.nway-file-preview__outline-title {
  flex: none;
  padding: 10px 12px;
  border-bottom: 1px solid #f2f3f5;
  color: #303133;
  font-size: 12px;
  font-weight: 600;
}

.nway-file-preview__outline-list {
  flex: 1;
  margin: 0;
  padding: 4px 0;
  overflow: auto;
  list-style: none;
}

.nway-file-preview__outline-item {
  display: block;
  width: 100%;
  padding: 6px 10px;
  border: none;
  background: transparent;
  color: #606266;
  font-size: 12px;
  line-height: 18px;
  text-align: left;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  cursor: pointer;
}

.nway-file-preview__outline-item:hover {
  background: #f5f7fa;
  color: #409eff;
}

.nway-file-preview__outline-item.is-active {
  background: #ecf5ff;
  color: #409eff;
  font-weight: 600;
}

/* 连续滚动容器：滚轮/触控板直接滚动，页面按可视区懒渲染 */
.nway-file-preview__pdfjs {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  min-width: 0;
  height: 100%;
  padding: 12px;
  box-sizing: border-box;
  overflow: auto;
  /* 预留滚动条宽度：页面按容器宽度渲染，出现纵向滚动条后不应再横向溢出 */
  scrollbar-gutter: stable;
  overscroll-behavior: contain;
}

.nway-file-preview__pages {
  display: flex;
  flex: none;
  flex-direction: column;
  align-items: center;
  width: 100%;
}

.nway-file-preview__page {
  flex: none;
  background: #fff;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.15);
}

.nway-file-preview__page + .nway-file-preview__page {
  margin-top: 12px;
}

.nway-file-preview__page canvas {
  display: block;
  width: 100%;
  height: 100%;
}

.nway-file-preview__pdfjs .nway-file-preview__tip {
  margin: auto;
}

.nway-file-preview__image {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  cursor: grab;
  touch-action: none;
}

.nway-file-preview__image.is-dragging {
  cursor: grabbing;
}

.nway-file-preview__image img {
  max-width: 100%;
  max-height: 72vh;
  transition: transform 0.2s;
  user-select: none;
  -webkit-user-drag: none;
}

.nway-file-preview__image.is-dragging img {
  transition: none;
}

.nway-file-preview__video {
  max-width: 100%;
  max-height: 72vh;
  background: #000;
}

.nway-file-preview__tip {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  color: #606266;
}

.nway-file-preview__tip--error {
  color: #f56c6c;
}

.nway-file-preview__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 16px;
  border-top: 1px solid #ebeef5;
}

.nway-file-preview__toolbar,
.nway-file-preview__nav {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #606266;
  font-size: 12px;
}

.nway-file-preview__btn {
  padding: 4px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #fff;
  color: #606266;
  font-size: 12px;
  cursor: pointer;
}

.nway-file-preview__btn:hover:not(:disabled) {
  border-color: #409eff;
  color: #409eff;
}

.nway-file-preview__btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
