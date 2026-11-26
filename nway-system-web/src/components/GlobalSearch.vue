<template>
    <div v-if="visible" ref="rootRef" class="gs-root"
        :class="{ 'is-expanded': menuOpen, 'is-ai-open': aiOpen }"
        :style="{ left: pos.left + 'px', top: pos.top + 'px', '--gs-panel-w': placement.panelWidth + 'px' }"
        @mouseenter="onEnter" @mouseleave="onLeave">
        <!-- 入口图标：拖动改变位置，点击展开菜单搜索 -->
        <div class="gs-icon" :class="{ 'is-active': menuOpen || aiOpen, 'is-dragging': dragging }"
             @pointerdown="onPointerDown" @click="onIconClick" @dragstart.prevent>
            <i class="gs-icon-img icon_search"></i>
        </div>

        <!-- 悬停时在图标一侧上下排列的两个入口：菜单搜索、办公智能体 -->
        <transition name="gs-actions">
            <div v-if="actionsVisible" class="gs-actions" :class="'gs-' + placement.horizontal">
                <button type="button" class="gs-action gs-action-ai" title="AI 助手" @pointerdown.stop @click.stop="startAiChat">
                    <el-icon><ChatDotRound /></el-icon>
                </button>
                <button type="button" class="gs-action" title="菜单搜索" @pointerdown.stop @click.stop="startMenuSearch">
                    <el-icon><Menu /></el-icon>
                </button>
            </div>
        </transition>

        <!-- 菜单搜索：输入框与结果浮层 -->
        <MenuSearchPanel ref="menuPanelRef" :expanded="menuOpen" :horizontal="placement.horizontal"
            :vertical="placement.vertical" :result-width="placement.resultWidth"
            @select="openMenu" @close="closeMenu" />

        <!-- 办公智能体：办公问答，复用完整对话的流式接口 -->
        <transition name="gs-ai">
            <AiChatPanel v-if="aiOpen" ref="aiPanelRef" :anchor="pos" :viewport="viewport" @close="closeAi" />
        </transition>
    </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChatDotRound, Menu } from '@element-plus/icons-vue'
import AiChatPanel from '@/components/global-search/AiChatPanel.vue'
import MenuSearchPanel from '@/components/global-search/MenuSearchPanel.vue'
import { useFloatingIcon } from '@/composables/useFloatingIcon'
import { useCurrentPageStore } from '@/stores/menuStore'
import { getToken } from '@/utils/auth'

const route = useRoute()
const router = useRouter()
const currentPageStore = useCurrentPageStore()

const rootRef = ref(null)
const menuPanelRef = ref(null)
const aiPanelRef = ref(null)

// 登录后且在非登录页展示
const visible = computed(() => route.name !== 'login' && !!getToken())

// 当前展开的面板：'' | 'menu' | 'ai'
const mode = ref('')
const menuOpen = computed(() => mode.value === 'menu')
const aiOpen = computed(() => mode.value === 'ai')

const hovered = ref(false)
const { pos, viewport, dragging, placement, onPointerDown, wasJustDragged } = useFloatingIcon()

// 仅空闲悬停时展示两个入口；任一面板展开后由面板自身接管交互
const actionsVisible = computed(() => hovered.value && mode.value === '')

function startMenuSearch() {
    mode.value = 'menu'
    menuPanelRef.value?.focus()
}

function startAiChat() {
    mode.value = 'ai'
}

function closeMenu() {
    if (mode.value !== 'menu') return
    mode.value = ''
    menuPanelRef.value?.blur()
}

function closeAi() {
    if (mode.value !== 'ai') return
    mode.value = ''
}

function closeAll() {
    if (mode.value === 'menu') closeMenu()
    else if (mode.value === 'ai') closeAi()
}

function onIconClick() {
    if (wasJustDragged()) return
    if (menuOpen.value) {
        menuPanelRef.value?.focus()
    } else {
        startMenuSearch()
    }
}

function openMenu(menu) {
    if (!menu || !menu.url) return
    // 与 allMenu 保持一致：从“所有菜单”页打开页面时关闭该页签
    if (currentPageStore.currentComp === 'SystemMenuAllMenu') {
        currentPageStore.justCloseUserMenu()
    }
    currentPageStore.showPage(menu)
    currentPageStore.setShowIndex(false)
    router.push({ path: '/main' })
    closeAll()
}

function onEnter() {
    hovered.value = true
}

function onLeave() {
    hovered.value = false
}

// 点击组件外部时收起（不用 blur：点击结果区滚动条等内部区域不应关闭）
function onDocumentPointerDown(e) {
    if (!mode.value) return
    if (rootRef.value && rootRef.value.contains(e.target)) return
    // 附件预览通过 Teleport 挂在 body 上，点击预览浮层不应关闭对话面板
    if (e.target.closest?.('.gs-preview, .el-image-viewer__wrapper')) return
    closeAll()
}

// Esc 兜底关闭：焦点不在输入框/面板内时同样生效
function onDocumentKeydown(e) {
    if (e.key !== 'Escape' || !mode.value) return
    // 预览、历史、面板逐层退出由 AI 面板处理；菜单搜索直接收起
    if (mode.value === 'ai') {
        aiPanelRef.value?.handleEscape?.()
        return
    }
    closeMenu()
}

// 路由切换后收起，避免浮层残留
watch(() => route.fullPath, () => closeAll())

onMounted(() => {
    document.addEventListener('mousedown', onDocumentPointerDown)
    document.addEventListener('keydown', onDocumentKeydown)
})

onBeforeUnmount(() => {
    document.removeEventListener('mousedown', onDocumentPointerDown)
    document.removeEventListener('keydown', onDocumentKeydown)
})
</script>

<style scoped>
.gs-root {
    position: fixed;
    z-index: 100000001;
    width: 40px;
    height: 40px;
    font-family: inherit;
}

.gs-icon {
    position: relative;
    z-index: 1;
    width: 40px;
    height: 40px;
    box-sizing: border-box;
    border-radius: 50%;
    background: #fff;
    border: 1px solid #e4e7ed;
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.16);
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: grab;
    user-select: none;
    touch-action: none;
    transition: background-color 0.18s, border-color 0.18s, box-shadow 0.18s;
}

.gs-icon:hover,
.gs-icon.is-active {
    background: #0078d7;
    border-color: #0078d7;
    box-shadow: 0 4px 18px rgba(0, 120, 215, 0.35);
}

.gs-root.is-ai-open .gs-icon {
    background: linear-gradient(135deg, #4f7cff 0%, #8b5cf6 100%);
    border-color: transparent;
    box-shadow: 0 4px 18px rgba(99, 91, 255, 0.38);
    animation: gsIconPop 0.4s cubic-bezier(0.3, 1.3, 0.5, 1);
}

/* 打开面板时图标轻微回弹，呼应面板弹出 */
@keyframes gsIconPop {
    0% {
        transform: scale(1);
    }

    35% {
        transform: scale(0.9);
    }

    100% {
        transform: scale(1);
    }
}

.gs-icon.is-dragging {
    cursor: grabbing;
}

.gs-icon-img {
    width: 20px;
    height: 20px;
    background-size: contain;
    background-repeat: no-repeat;
    background-position: center;
    pointer-events: none;
    transition: filter 0.18s;
}

.gs-icon:hover .gs-icon-img,
.gs-icon.is-active .gs-icon-img {
    filter: brightness(0) invert(1);
}

/* ---------- 悬停入口 ---------- */
.gs-actions {
    position: absolute;
    top: 0;
    display: flex;
    flex-direction: column;
    gap: 6px;
}

/* 预留 8px 内边距作为指针过渡区，避免从图标移向按钮时触发 mouseleave */
.gs-actions.gs-right {
    left: 40px;
    padding-left: 8px;
}

.gs-actions.gs-left {
    right: 40px;
    padding-right: 8px;
    align-items: flex-end;
}

.gs-action {
    width: 22px;
    height: 22px;
    box-sizing: border-box;
    padding: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 50%;
    border: 1px solid #e4e7ed;
    background: #fff;
    color: #5f6673;
    box-shadow: 0 3px 10px rgba(0, 0, 0, 0.14);
    cursor: pointer;
    transition: transform 0.18s, background-color 0.18s, color 0.18s, border-color 0.18s, box-shadow 0.18s;
}

.gs-action .el-icon {
    font-size: 11px;
}

.gs-action:hover {
    background: #0078d7;
    border-color: #0078d7;
    color: #fff;
    transform: translateY(-1px);
    box-shadow: 0 5px 14px rgba(0, 120, 215, 0.32);
}

.gs-action-ai {
    border-color: #e3e9ff;
    background: linear-gradient(135deg, #eef2ff 0%, #f2ecff 100%);
    color: #5b6cff;
    box-shadow: 0 2px 8px rgba(99, 91, 255, 0.14);
}

.gs-action-ai:hover {
    background: linear-gradient(135deg, #e2e9ff 0%, #e9ddff 100%);
    border-color: #c9d4ff;
    color: #4353f5;
    transform: translateY(-1px);
    box-shadow: 0 4px 12px rgba(99, 91, 255, 0.28);
}

.gs-actions-enter-active,
.gs-actions-leave-active {
    transition: opacity 0.18s ease, transform 0.18s ease;
}

.gs-actions-enter-from,
.gs-actions-leave-to {
    opacity: 0;
    transform: translateY(-4px);
}
</style>
