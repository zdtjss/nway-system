import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import {
    AI_PANEL_DEFAULT_HEIGHT,
    AI_PANEL_MAX_HEIGHT,
    AI_PANEL_MAX_WIDTH,
    AI_PANEL_MIN_HEIGHT,
    AI_PANEL_MIN_WIDTH,
    AI_PANEL_WIDTH,
    AI_SIZE_KEY,
    GAP,
    ICON_SIZE,
    VIEWPORT_MARGIN
} from '@/components/global-search/constants'

/**
 * AI 面板相对悬浮图标的布局、拖动与缩放。
 * 展开方向跟随图标位置自动切换；面板尺寸由用户拖动缩放并持久化。
 */
export function useAiPanelLayout({ anchor, viewport }) {
    // 读取用户上次拖动后的尺寸，异常时回退默认值
    function readAiSize() {
        try {
            const saved = JSON.parse(localStorage.getItem(AI_SIZE_KEY) || 'null')
            if (saved && Number.isFinite(saved.width) && Number.isFinite(saved.height)) {
                return {
                    width: Math.min(Math.max(saved.width, AI_PANEL_MIN_WIDTH), AI_PANEL_MAX_WIDTH),
                    height: Math.min(Math.max(saved.height, AI_PANEL_MIN_HEIGHT), AI_PANEL_MAX_HEIGHT)
                }
            }
        } catch (e) {
            // localStorage 内容异常时回退默认尺寸
        }
        return { width: AI_PANEL_WIDTH, height: AI_PANEL_DEFAULT_HEIGHT }
    }

    const aiSize = ref(readAiSize())

    // 展开方向在面板打开期间保持稳定，避免拖动缩放时整个面板左右/上下跳动
    const aiPlacement = reactive({ horizontal: 'right', vertical: 'below' })

    function updateAiPlacement() {
        const vw = viewport.value.w
        const vh = viewport.value.h
        const width = Math.min(aiSize.value.width, Math.max(AI_PANEL_MIN_WIDTH, vw - VIEWPORT_MARGIN * 2))
        const rightSpace = vw - (anchor.value.left + ICON_SIZE + GAP) - VIEWPORT_MARGIN
        const leftSpace = anchor.value.left - GAP - VIEWPORT_MARGIN
        aiPlacement.horizontal = rightSpace >= width || rightSpace >= leftSpace ? 'right' : 'left'
        const belowSpace = vh - (anchor.value.top + ICON_SIZE + GAP) - VIEWPORT_MARGIN
        const aboveSpace = anchor.value.top - GAP - VIEWPORT_MARGIN
        aiPlacement.vertical = belowSpace >= aiSize.value.height || belowSpace >= aboveSpace ? 'below' : 'above'
    }

    // 面板拖动偏移与拖动状态：偏移相对基准位置记录，面板跟随图标移动的同时保留用户摆放
    const aiOffset = ref({ left: 0, top: 0 })
    const aiDragging = ref(false)

    // AI 面板基准几何：优先在图标右侧向下滑出，空间不足时自动换向，始终不超出视口
    const aiBaseLayout = computed(() => {
        const vw = viewport.value.w
        const vh = viewport.value.h
        const maxWidth = Math.max(AI_PANEL_MIN_WIDTH, vw - VIEWPORT_MARGIN * 2)
        const width = Math.min(aiSize.value.width, AI_PANEL_MAX_WIDTH, maxWidth)
        const toRight = aiPlacement.horizontal === 'right'
        // 面板相对 gs-root 绝对定位，left 需换算为相对图标的偏移，并把视口边界一并换算
        const offset = toRight ? ICON_SIZE + GAP : -GAP - width
        const minLeft = VIEWPORT_MARGIN - anchor.value.left
        const maxLeft = Math.max(minLeft, vw - width - VIEWPORT_MARGIN - anchor.value.left)
        const left = Math.min(Math.max(minLeft, offset), maxLeft)
        const below = aiPlacement.vertical === 'below'
        const belowSpace = vh - (anchor.value.top + ICON_SIZE + GAP) - VIEWPORT_MARGIN
        const aboveSpace = anchor.value.top - GAP - VIEWPORT_MARGIN
        const available = Math.max(200, below ? belowSpace : aboveSpace)
        // 高度随拖动实时变化，只受可用空间限制，允许用户把面板拖高
        const height = Math.min(aiSize.value.height, AI_PANEL_MAX_HEIGHT, available)
        // 统一用 top 定位：向上展开时面板底边贴着图标上方
        const top = below ? GAP + ICON_SIZE : -(GAP + height)
        return { left, top, width, height, below }
    })

    // 面板最终位置：基准位置 + 用户拖动偏移，并限制在视口内
    const aiLayout = computed(() => {
        const { left, top, width, height, below } = aiBaseLayout.value
        const vw = viewport.value.w
        const vh = viewport.value.h
        const maxLeft = Math.max(VIEWPORT_MARGIN, vw - width - VIEWPORT_MARGIN)
        const maxTop = Math.max(VIEWPORT_MARGIN, vh - height - VIEWPORT_MARGIN)
        // 面板向右上微调，贴近右上角的悬浮搜索图标
        const viewLeft = Math.min(Math.max(VIEWPORT_MARGIN, anchor.value.left + left + aiOffset.value.left + GAP), maxLeft)
        const viewTop = Math.min(Math.max(VIEWPORT_MARGIN, anchor.value.top + top + aiOffset.value.top - GAP), maxTop)
        return {
            left: viewLeft - anchor.value.left,
            top: viewTop - anchor.value.top,
            width,
            height,
            below
        }
    })

    const aiPanelStyle = computed(() => {
        const { left, top, width, height } = aiLayout.value
        return {
            left: left + 'px',
            top: top + 'px',
            width: width + 'px',
            height: height + 'px'
        }
    })

    // 缩放把手落在面板外侧角，横纵方向共同决定位置与光标
    const aiResizeClass = computed(() => `gs-ai-resize-${aiPlacement.horizontal}-${aiPlacement.vertical}`)

    const aiHorizontal = computed(() => aiPlacement.horizontal)
    const aiVertical = computed(() => aiPlacement.vertical)

    let aiDragStart = null

    function onAiHeaderPointerDown(e) {
        if (e.button !== 0) return
        // 头部按钮不参与拖拽
        if (e.target.closest('button')) return
        e.preventDefault()
        aiDragStart = { x: e.clientX, y: e.clientY, left: aiOffset.value.left, top: aiOffset.value.top }
        aiDragging.value = true
        document.body.style.userSelect = 'none'
        window.addEventListener('pointermove', onAiHeaderPointerMove)
        window.addEventListener('pointerup', onAiHeaderPointerUp)
        window.addEventListener('pointercancel', onAiHeaderPointerUp)
    }

    function onAiHeaderPointerMove(e) {
        if (!aiDragStart) return
        aiOffset.value = {
            left: aiDragStart.left + (e.clientX - aiDragStart.x),
            top: aiDragStart.top + (e.clientY - aiDragStart.y)
        }
    }

    function onAiHeaderPointerUp() {
        window.removeEventListener('pointermove', onAiHeaderPointerMove)
        window.removeEventListener('pointerup', onAiHeaderPointerUp)
        window.removeEventListener('pointercancel', onAiHeaderPointerUp)
        document.body.style.userSelect = ''
        aiDragging.value = false
        if (!aiDragStart) return
        aiDragStart = null
        // 按视口范围收敛偏移，避免面板被拖出屏幕后拖不回来
        const base = aiBaseLayout.value
        const maxLeft = Math.max(VIEWPORT_MARGIN, viewport.value.w - base.width - VIEWPORT_MARGIN)
        const maxTop = Math.max(VIEWPORT_MARGIN, viewport.value.h - base.height - VIEWPORT_MARGIN)
        const viewLeft = Math.min(Math.max(VIEWPORT_MARGIN, anchor.value.left + base.left + aiOffset.value.left), maxLeft)
        const viewTop = Math.min(Math.max(VIEWPORT_MARGIN, anchor.value.top + base.top + aiOffset.value.top), maxTop)
        aiOffset.value = {
            left: viewLeft - (anchor.value.left + base.left),
            top: viewTop - (anchor.value.top + base.top)
        }
    }

    let aiResizeStart = null

    function onAiResizeDown(e) {
        if (e.button !== 0) return
        e.preventDefault()
        e.stopPropagation()
        const { width, height } = aiLayout.value
        aiResizeStart = {
            x: e.clientX,
            y: e.clientY,
            width,
            height,
            // 面板向左/向上展开时，鼠标反向移动才是放大
            hSign: aiPlacement.horizontal === 'right' ? 1 : -1,
            vSign: aiPlacement.vertical === 'below' ? 1 : -1
        }
        // 拖动过程中保持缩放光标，避免指针移出把手后光标闪烁
        document.body.style.cursor = (aiPlacement.horizontal === 'right') === (aiPlacement.vertical === 'below')
            ? 'nwse-resize'
            : 'nesw-resize'
        document.body.style.userSelect = 'none'
        window.addEventListener('pointermove', onAiResizeMove)
        window.addEventListener('pointerup', onAiResizeUp)
        window.addEventListener('pointercancel', onAiResizeUp)
    }

    function onAiResizeMove(e) {
        const start = aiResizeStart
        if (!start) return
        const maxWidth = Math.min(AI_PANEL_MAX_WIDTH, Math.max(AI_PANEL_MIN_WIDTH, viewport.value.w - VIEWPORT_MARGIN * 2))
        const maxHeight = Math.max(AI_PANEL_MIN_HEIGHT, viewport.value.h - (ICON_SIZE + GAP + VIEWPORT_MARGIN * 2))
        const width = start.width + (e.clientX - start.x) * start.hSign
        const height = start.height + (e.clientY - start.y) * start.vSign
        aiSize.value = {
            width: Math.min(Math.max(width, AI_PANEL_MIN_WIDTH), maxWidth),
            height: Math.min(Math.max(height, AI_PANEL_MIN_HEIGHT), maxHeight)
        }
    }

    function onAiResizeUp() {
        window.removeEventListener('pointermove', onAiResizeMove)
        window.removeEventListener('pointerup', onAiResizeUp)
        window.removeEventListener('pointercancel', onAiResizeUp)
        document.body.style.cursor = ''
        document.body.style.userSelect = ''
        if (aiResizeStart) {
            try {
                localStorage.setItem(AI_SIZE_KEY, JSON.stringify(aiSize.value))
            } catch (e) {
                // 存储失败不影响本次缩放
            }
        }
        aiResizeStart = null
    }

    // 图标拖动、窗口缩放都会改变锚点，面板展开方向随之重算
    watch([anchor, viewport], updateAiPlacement)
    updateAiPlacement()

    onBeforeUnmount(() => {
        window.removeEventListener('pointermove', onAiHeaderPointerMove)
        window.removeEventListener('pointerup', onAiHeaderPointerUp)
        window.removeEventListener('pointercancel', onAiHeaderPointerUp)
        window.removeEventListener('pointermove', onAiResizeMove)
        window.removeEventListener('pointerup', onAiResizeUp)
        window.removeEventListener('pointercancel', onAiResizeUp)
        document.body.style.cursor = ''
        document.body.style.userSelect = ''
    })

    return {
        aiPanelStyle,
        aiHorizontal,
        aiVertical,
        aiResizeClass,
        headerDragging: aiDragging,
        onAiHeaderPointerDown,
        onAiResizeDown
    }
}
