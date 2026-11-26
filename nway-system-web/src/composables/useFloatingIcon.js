import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import {
    GAP,
    ICON_SIZE,
    MENU_PANEL_WIDTH,
    MENU_POS_KEY,
    MENU_RESULT_WIDTH,
    VIEWPORT_MARGIN
} from '@/components/global-search/constants'

/**
 * 悬浮入口图标的位置与拖动。
 * 位置持久化到 localStorage；窗口缩放时按所靠边缘跟随，菜单面板按剩余空间决定展开方向。
 */
export function useFloatingIcon() {
    const viewport = ref({ w: window.innerWidth, h: window.innerHeight })

    function clampPos(p) {
        const maxLeft = Math.max(VIEWPORT_MARGIN, viewport.value.w - ICON_SIZE - VIEWPORT_MARGIN)
        const maxTop = Math.max(VIEWPORT_MARGIN, viewport.value.h - ICON_SIZE - VIEWPORT_MARGIN)
        return {
            left: Math.min(Math.max(VIEWPORT_MARGIN, p.left), maxLeft),
            top: Math.min(Math.max(VIEWPORT_MARGIN, p.top), maxTop)
        }
    }

    function readPos() {
        try {
            const saved = JSON.parse(localStorage.getItem(MENU_POS_KEY) || 'null')
            if (saved && Number.isFinite(saved.left) && Number.isFinite(saved.top)) {
                return clampPos(saved)
            }
        } catch (e) {
            // localStorage 内容异常时回退默认位置
        }
        // 默认右上角（避开顶部自动隐藏工具栏）
        return clampPos({ left: window.innerWidth - ICON_SIZE - 20, top: 56 })
    }

    const pos = ref(readPos())
    const dragging = ref(false)

    let dragStart = null
    let dragMoved = false
    let lastDragEndAt = 0

    function onPointerDown(e) {
        if (e.button !== 0) return
        // 避免拖动时选中文字/输入框失焦
        e.preventDefault()
        dragStart = { x: e.clientX, y: e.clientY, left: pos.value.left, top: pos.value.top }
        dragMoved = false
        dragging.value = true
        window.addEventListener('pointermove', onPointerMove)
        window.addEventListener('pointerup', onPointerUp)
        window.addEventListener('pointercancel', onPointerUp)
    }

    function onPointerMove(e) {
        if (!dragStart) return
        const dx = e.clientX - dragStart.x
        const dy = e.clientY - dragStart.y
        if (!dragMoved && Math.abs(dx) + Math.abs(dy) > 3) {
            dragMoved = true
        }
        if (!dragMoved) return
        pos.value = clampPos({ left: dragStart.left + dx, top: dragStart.top + dy })
    }

    function onPointerUp() {
        window.removeEventListener('pointermove', onPointerMove)
        window.removeEventListener('pointerup', onPointerUp)
        window.removeEventListener('pointercancel', onPointerUp)
        dragging.value = false
        dragStart = null
        if (dragMoved) {
            // 忽略拖动结束后紧随的 click，避免误触发展开
            lastDragEndAt = Date.now()
            try {
                localStorage.setItem(MENU_POS_KEY, JSON.stringify(pos.value))
            } catch (e) {
                // 存储失败不影响本次拖动
            }
        }
    }

    // 以更近的一侧作为锚点换算新视口下的坐标：窗口缩放（最大化/还原、拖拽边框）时
    // 图标跟随所靠边缘移动，而不是停留在原来的绝对像素位置
    function anchorPos(p, from, to) {
        const rightOffset = from.w - p.left - ICON_SIZE
        const bottomOffset = from.h - p.top - ICON_SIZE
        return {
            left: rightOffset < p.left ? to.w - ICON_SIZE - rightOffset : p.left,
            top: bottomOffset < p.top ? to.h - ICON_SIZE - bottomOffset : p.top
        }
    }

    function onResize() {
        const prev = viewport.value
        viewport.value = { w: window.innerWidth, h: window.innerHeight }
        pos.value = clampPos(anchorPos(pos.value, prev, viewport.value))
    }

    // 菜单面板展开方向：右侧放得下面板则向右推出，否则向空间更大的一侧展开
    const placement = computed(() => {
        const vw = viewport.value.w
        const vh = viewport.value.h
        const rightSpace = vw - pos.value.left - ICON_SIZE - GAP
        const leftSpace = pos.value.left - GAP
        const horizontal = rightSpace >= MENU_PANEL_WIDTH || rightSpace >= leftSpace ? 'right' : 'left'
        const sideSpace = horizontal === 'right' ? rightSpace : leftSpace
        const belowSpace = vh - pos.value.top - ICON_SIZE - GAP
        const aboveSpace = pos.value.top - GAP
        const vertical = belowSpace < 220 && aboveSpace > belowSpace ? 'above' : 'below'
        return {
            horizontal,
            vertical,
            panelWidth: Math.max(180, Math.min(MENU_PANEL_WIDTH, sideSpace)),
            resultWidth: Math.max(180, Math.min(MENU_RESULT_WIDTH, sideSpace))
        }
    })

    // 拖动结束后紧随的 click 视为拖动误触
    function wasJustDragged() {
        return Date.now() - lastDragEndAt < 300
    }

    onMounted(() => {
        window.addEventListener('resize', onResize)
    })

    onBeforeUnmount(() => {
        window.removeEventListener('resize', onResize)
        window.removeEventListener('pointermove', onPointerMove)
        window.removeEventListener('pointerup', onPointerUp)
        window.removeEventListener('pointercancel', onPointerUp)
    })

    return { pos, viewport, dragging, placement, onPointerDown, wasJustDragged }
}
