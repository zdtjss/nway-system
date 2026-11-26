<template>
    <div class="gs-ai" :class="['gs-ai-' + aiVertical, 'gs-ai-h-' + aiHorizontal]" :style="aiPanelStyle"
        @keydown.esc.stop.prevent="handleEscape">
        <div class="gs-ai-header" :class="{ 'is-dragging': headerDragging }" @pointerdown="onAiHeaderPointerDown">
            <div class="gs-ai-avatar">
                <el-icon><ChatDotRound /></el-icon>
            </div>
            <div class="gs-ai-heading">
                <div class="gs-ai-title">AI 助手</div>
                <div class="gs-ai-sub" :class="{ 'is-streaming': aiStreaming }">
                    {{ aiStreaming ? '正在生成回复…' : '办公问答 · 支持页面提问' }}
                </div>
            </div>
            <div class="gs-ai-head-actions">
                <button type="button" class="gs-ai-icon-btn" :class="{ 'is-active': aiHistoryOpen }" title="历史记录" @click="toggleAiHistory">
                    <el-icon><Clock /></el-icon>
                </button>
                <button type="button" class="gs-ai-icon-btn" title="新对话" @click="onNewChat">
                    <el-icon><Plus /></el-icon>
                </button>
                <button type="button" class="gs-ai-full" :disabled="aiStreaming"
                    :title="aiStreaming ? '回复生成中，请稍候再打开完整对话' : '打开完整对话'"
                    @click="openFullChat">
                    <el-icon><TopRight /></el-icon>
                    <span>完整对话</span>
                </button>
                <button type="button" class="gs-ai-icon-btn" title="关闭" @click="emit('close')">
                    <el-icon><Close /></el-icon>
                </button>
            </div>
        </div>

        <AiHistoryList v-if="aiHistoryOpen" :keyword="aiHistoryKeyword" :loading="aiHistoryLoading"
            :conversations="aiConversations" :total="aiHistoryTotal" :current="aiHistoryCurrent"
            :active-id="aiConversationId" @update:keyword="aiHistoryKeyword = $event"
            @open="onOpenAiConversation" @page-change="onAiHistoryPageChange" @close="aiHistoryOpen = false" />

        <div v-show="!aiHistoryOpen" ref="bodyRef" class="gs-ai-body" v-loading="aiMessagesLoading"
            @scroll="trackFollowScroll">
            <div v-if="aiMessages.length === 0" class="gs-ai-empty">
                <div class="gs-ai-empty-logo">
                    <el-icon><ChatDotRound /></el-icon>
                </div>
                <div class="gs-ai-empty-title">有什么可以帮你？</div>
                <div class="gs-ai-empty-sub">支持文字、附件和当前页面提问；涉及提交确认或历史会话时，请前往完整对话。</div>
            </div>

            <AiMessageItem v-for="msg in aiMessages" :key="msg.id" :msg="msg" :thinking-open="aiThinkingOpen"
                :tool-collapsed="aiToolCollapsed" :speaking-id="speakingId" :tts-supported="ttsSupported"
                @toggle-thinking="toggleAiThinking" @toggle-tool="toggleAiTool" @speak="speakAiMessage"
                @open-full-chat="openFullChat" @preview-file="previewAiFile" @scroll-follow="trackFollowScroll" />
        </div>

        <AiComposer v-show="!aiHistoryOpen" ref="composerRef" v-model:input="input"
            v-model:attachments="attachments" v-model:page-text-enabled="pageTextEnabled"
            v-model:page-shot-enabled="pageShotEnabled" :streaming="aiStreaming" :can-send="aiCanSend"
            :page-context-hint="pageContextHint" :voice-active="voiceActive" :voice-title="voiceTitle"
            :speech-supported="speechRecognitionSupported" @send="onSend" @stop="stopAi"
            @toggle-voice="toggleAiVoice" />

        <!-- 缩放把手：随面板展开方向落到外侧角 -->
        <div class="gs-ai-resize" :class="aiResizeClass" title="拖动调整大小" @pointerdown="onAiResizeDown"></div>

        <AiFilePreview :preview="aiPreview" @close="closeAiPreview" />
    </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Clock, Close, Plus, TopRight } from '@element-plus/icons-vue'
import AiComposer from '@/components/global-search/ai/AiComposer.vue'
import AiFilePreview from '@/components/global-search/ai/AiFilePreview.vue'
import AiHistoryList from '@/components/global-search/ai/AiHistoryList.vue'
import AiMessageItem from '@/components/global-search/ai/AiMessageItem.vue'
import { AI_CHAT_URL } from '@/components/global-search/constants'
import { useAiChatHistory } from '@/composables/useAiChatHistory'
import { useAiChatSession } from '@/composables/useAiChatSession'
import { useAiFilePreview } from '@/composables/useAiFilePreview'
import { useAiPanelLayout } from '@/composables/useAiPanelLayout'
import { useAiVoice } from '@/composables/useAiVoice'
import { useAiChatHandoffStore } from '@/stores/aiChatStore'
import { useCurrentPageStore } from '@/stores/menuStore'
import { followBottom, followLast, trackFollowScroll } from '@/utils/scrollFollow'

const props = defineProps({
    // 悬浮图标的位置与视口尺寸：AI 面板据此决定展开方向与可用高度
    anchor: { type: Object, required: true },
    viewport: { type: Object, required: true }
})

const emit = defineEmits(['close'])

const router = useRouter()
const currentPageStore = useCurrentPageStore()
const aiChatHandoff = useAiChatHandoffStore()

const bodyRef = ref(null)
const composerRef = ref(null)
const input = ref('')
const attachments = ref([])

const {
    aiPanelStyle,
    aiHorizontal,
    aiVertical,
    aiResizeClass,
    headerDragging,
    onAiHeaderPointerDown,
    onAiResizeDown
} = useAiPanelLayout({
    anchor: computed(() => props.anchor),
    viewport: computed(() => props.viewport)
})

// 流式输出过程中贴底；用户手动上滚后暂停跟随
function scrollToBottom(force = false) {
    return nextTick().then(() => followBottom(bodyRef.value, force))
}

// 思考过程等内部滚动区随流式输出贴底
function followTail(selector) {
    nextTick(() => followLast(bodyRef.value, selector))
}

const {
    aiMessages,
    aiStreaming,
    aiMessagesLoading,
    aiConversationId,
    pageTextEnabled,
    pageShotEnabled,
    pageContextHint,
    aiCanSend,
    aiThinkingOpen,
    aiToolCollapsed,
    toggleAiThinking,
    toggleAiTool,
    sendAi,
    stopAi,
    openConversation,
    newAiChat,
    buildHandoffPayload
} = useAiChatSession({ input, attachments, scrollToBottom, followTail })

const {
    aiHistoryOpen,
    aiHistoryKeyword,
    aiHistoryCurrent,
    aiHistoryTotal,
    aiHistoryLoading,
    aiConversations,
    loadAiHistory,
    onAiHistoryPageChange,
    toggleAiHistory
} = useAiChatHistory()

const {
    voiceActive,
    voiceTitle,
    speechRecognitionSupported,
    speakingId,
    ttsSupported,
    toggleAiVoice,
    stopAiVoice,
    speakAiMessage,
    stopAiSpeaking
} = useAiVoice({ input, onDictation: () => composerRef.value?.focus() })

const { aiPreview, previewAiFile, closeAiPreview } = useAiFilePreview()

// 新会话标题由首条消息生成，发送结束后刷新历史列表（仅在历史视图打开时）
watch(aiStreaming, (now, before) => {
    if (before && !now && aiHistoryOpen.value) loadAiHistory()
})

function onNewChat() {
    if (aiStreaming.value) stopAi()
    stopAiVoice()
    stopAiSpeaking()
    aiHistoryOpen.value = false
    newAiChat()
    nextTick(() => composerRef.value?.focus())
}

function onOpenAiConversation(conv) {
    if (aiStreaming.value) {
        ElMessage.warning('请先等待当前回答结束或点击停止')
        return
    }
    stopAiVoice()
    stopAiSpeaking()
    aiHistoryOpen.value = false
    openConversation(conv)
}

function onSend() {
    if (!aiCanSend.value) return
    stopAiVoice()
    stopAiSpeaking()
    sendAi()
}

// 轻量对话 → 完整对话：交接会话内容后打开完整对话页
function openFullChat() {
    const payload = buildHandoffPayload()
    if (payload) {
        aiChatHandoff.setPayload(payload)
        newAiChat()
    }
    emit('close')
    const menu = { name: 'AI 对话', url: AI_CHAT_URL, type: 'PAGE', renderType: 'native' }
    if (currentPageStore.currentComp === 'SystemMenuAllMenu') {
        currentPageStore.justCloseUserMenu()
    }
    currentPageStore.showPage(menu)
    currentPageStore.setShowIndex(false)
    router.push({ path: '/main' })
}

// 预览 > 历史 > 关闭面板：Esc 逐层退出
function handleEscape() {
    if (aiPreview.open) {
        closeAiPreview()
        return
    }
    if (aiHistoryOpen.value) {
        aiHistoryOpen.value = false
        return
    }
    emit('close')
}

onMounted(() => {
    nextTick(() => {
        composerRef.value?.focus()
        scrollToBottom(true)
    })
})

defineExpose({ handleEscape })
</script>

<style scoped>
/* ---------- AI 助手 ---------- */
.gs-ai {
    position: absolute;
    z-index: 2;
    display: flex;
    flex-direction: column;
    background: #fff;
    border: 1px solid rgba(99, 91, 255, 0.14);
    border-radius: 20px;
    box-shadow: 0 36px 90px -24px rgba(17, 24, 51, 0.38), 0 16px 40px -20px rgba(17, 24, 51, 0.18),
        0 0 0 1px rgba(255, 255, 255, 0.7) inset;
    overflow: hidden;
    container-type: inline-size;
}

.gs-ai-header {
    display: flex;
    align-items: center;
    gap: 11px;
    padding: 14px 16px;
    background:
        radial-gradient(120% 160% at 0% 0%, rgba(79, 124, 255, 0.16) 0%, rgba(79, 124, 255, 0) 58%),
        radial-gradient(110% 150% at 100% 0%, rgba(139, 92, 246, 0.14) 0%, rgba(139, 92, 246, 0) 62%),
        linear-gradient(180deg, #fafbff 0%, #f5f8ff 100%);
    border-bottom: 1px solid rgba(226, 232, 248, 0.9);
    flex: 0 0 auto;
    cursor: move;
    user-select: none;
    touch-action: none;
}

.gs-ai-header.is-dragging {
    cursor: grabbing;
}

.gs-ai-avatar {
    position: relative;
    width: 38px;
    height: 38px;
    flex: 0 0 auto;
    border-radius: 12px;
    background: linear-gradient(135deg, #5b8cff 0%, #7b6bff 55%, #a855f7 100%);
    color: #fff;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 19px;
    box-shadow: 0 8px 18px -6px rgba(99, 91, 255, 0.55), 0 0 0 3px rgba(124, 116, 255, 0.1);
}

.gs-ai-avatar::after {
    content: '';
    position: absolute;
    inset: 0;
    border-radius: inherit;
    background: linear-gradient(180deg, rgba(255, 255, 255, 0.34) 0%, rgba(255, 255, 255, 0) 52%);
    pointer-events: none;
}

.gs-ai-avatar .el-icon {
    position: relative;
    z-index: 1;
}

.gs-ai-heading {
    flex: 1;
    min-width: 0;
    overflow: hidden;
}

.gs-ai-title {
    font-size: 15px;
    font-weight: 600;
    letter-spacing: 0.1px;
    color: #161a22;
    line-height: 1.35;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.gs-ai-sub {
    position: relative;
    margin-top: 2px;
    padding-left: 12px;
    font-size: 11.5px;
    color: #8b94a7;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.gs-ai-sub::before {
    content: '';
    position: absolute;
    left: 0;
    top: 50%;
    width: 5px;
    height: 5px;
    margin-top: -2.5px;
    border-radius: 50%;
    background: #2fc26e;
    box-shadow: 0 0 0 3px rgba(47, 194, 110, 0.12);
}

.gs-ai-sub.is-streaming {
    color: #4f7cff;
}

.gs-ai-sub.is-streaming::before {
    background: currentColor;
    box-shadow: 0 0 0 3px rgba(79, 124, 255, 0.12);
    animation: gsAiPulse 1s infinite ease-in-out;
}

@keyframes gsAiPulse {

    0%,
    100% {
        opacity: 0.4;
    }

    50% {
        opacity: 1;
    }
}

.gs-ai-head-actions {
    flex: 0 0 auto;
    display: flex;
    align-items: center;
    gap: 5px;
}

.gs-ai-icon-btn {
    width: 28px;
    height: 28px;
    padding: 0;
    border: none;
    border-radius: 9px;
    background: transparent;
    color: #6f7890;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    transition: background-color 0.15s, color 0.15s;
}

.gs-ai-icon-btn .el-icon {
    font-size: 16px;
}

.gs-ai-icon-btn:hover {
    background: rgba(79, 124, 255, 0.09);
    color: #3b5bdb;
}

.gs-ai-icon-btn.is-active {
    background: rgba(79, 124, 255, 0.12);
    color: #3b5bdb;
}

.gs-ai-full {
    height: 28px;
    padding: 0 12px;
    display: inline-flex;
    align-items: center;
    gap: 4px;
    border: 1px solid rgba(99, 91, 255, 0.2);
    border-radius: 999px;
    background: linear-gradient(135deg, rgba(79, 124, 255, 0.1) 0%, rgba(139, 92, 246, 0.12) 100%);
    color: #4a5bd6;
    font-size: 12px;
    font-weight: 500;
    white-space: nowrap;
    cursor: pointer;
    transition: background-color 0.15s, border-color 0.15s, color 0.15s, box-shadow 0.15s;
}

.gs-ai-full .el-icon {
    font-size: 13px;
}

.gs-ai-full:hover:not(:disabled) {
    background: linear-gradient(135deg, rgba(79, 124, 255, 0.18) 0%, rgba(139, 92, 246, 0.2) 100%);
    border-color: rgba(99, 91, 255, 0.34);
    color: #3346c4;
    box-shadow: 0 4px 12px -6px rgba(99, 91, 255, 0.5);
}

.gs-ai-full:disabled {
    background: #f5f7fa;
    border-color: #e4e7ed;
    color: #c0c4cc;
    cursor: not-allowed;
}

.gs-ai-body {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    overscroll-behavior: contain;
    padding: 16px 14px 18px;
    background:
        radial-gradient(120% 50% at 50% 0%, rgba(79, 124, 255, 0.055) 0%, rgba(79, 124, 255, 0) 70%),
        #f6f7fb;
}

.gs-ai-empty {
    height: 100%;
    min-height: 180px;
    box-sizing: border-box;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    text-align: center;
    padding: 18px;
}

.gs-ai-empty-logo {
    position: relative;
    width: 52px;
    height: 52px;
    margin-bottom: 14px;
    border-radius: 16px;
    background: linear-gradient(135deg, #5b8cff 0%, #7b6bff 55%, #a855f7 100%);
    color: #fff;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 24px;
    box-shadow: 0 12px 28px -10px rgba(99, 91, 255, 0.6), 0 0 0 4px rgba(124, 116, 255, 0.07);
}

.gs-ai-empty-logo::after {
    content: '';
    position: absolute;
    inset: -8px;
    border-radius: 22px;
    border: 1px solid rgba(124, 116, 255, 0.2);
}

.gs-ai-empty-title {
    font-size: 15.5px;
    font-weight: 600;
    color: #232a38;
}

.gs-ai-empty-sub {
    max-width: 300px;
    margin-top: 8px;
    font-size: 12.5px;
    line-height: 1.75;
    color: #949daf;
}

/* ---------- 缩放把手 ---------- */
.gs-ai-resize {
    position: absolute;
    z-index: 3;
    width: 24px;
    height: 24px;
    color: #b6c0d4;
    opacity: 0.5;
    transition: opacity 0.16s, color 0.16s;
    touch-action: none;
}

.gs-ai:hover .gs-ai-resize,
.gs-ai-resize:hover {
    opacity: 1;
    color: #4f7cff;
}

.gs-ai-resize::before {
    content: '';
    position: absolute;
    right: 4px;
    bottom: 4px;
    width: 12px;
    height: 12px;
    border-right: 2px solid currentColor;
    border-bottom: 2px solid currentColor;
    border-radius: 0 0 3px 0;
}

.gs-ai-resize-right-below {
    right: 0;
    bottom: 0;
    cursor: nwse-resize;
}

.gs-ai-resize-right-above {
    right: 0;
    top: 0;
    cursor: nesw-resize;
    transform: scaleY(-1);
}

.gs-ai-resize-left-below {
    left: 0;
    bottom: 0;
    cursor: nesw-resize;
    transform: scaleX(-1);
}

.gs-ai-resize-left-above {
    left: 0;
    top: 0;
    cursor: nwse-resize;
    transform: scale(-1);
}

.gs-ai-body::-webkit-scrollbar {
    width: 6px;
}

.gs-ai-body::-webkit-scrollbar-thumb {
    background-color: rgba(148, 163, 184, 0.24);
    border-radius: 3px;
}

.gs-ai-body::-webkit-scrollbar-thumb:hover {
    background-color: rgba(148, 163, 184, 0.45);
}

.gs-ai-body::-webkit-scrollbar-track {
    background: transparent;
}

/* 面板变窄时收敛头部信息，避免标题与副标题被挤成省略号 */
@container (max-width: 400px) {
    .gs-ai-full {
        width: 28px;
        padding: 0;
        justify-content: center;
    }

    .gs-ai-full span {
        display: none;
    }
}

@container (max-width: 380px) {
    .gs-ai-sub {
        display: none;
    }
}

/* macOS 风格弹出：以靠近图标的角为原点放大，配合淡入与轻微去模糊 */
.gs-ai-below.gs-ai-h-right {
    transform-origin: left top;
}

.gs-ai-above.gs-ai-h-right {
    transform-origin: left bottom;
}

.gs-ai-below.gs-ai-h-left {
    transform-origin: right top;
}

.gs-ai-above.gs-ai-h-left {
    transform-origin: right bottom;
}

.gs-ai-enter-active {
    transition: opacity 0.24s ease-out, transform 0.34s cubic-bezier(0.3, 1.3, 0.5, 1);
    will-change: transform, opacity;
}

.gs-ai-leave-active {
    transition: opacity 0.16s ease-in, transform 0.2s cubic-bezier(0.5, 0, 0.75, 0.3);
    will-change: transform, opacity;
}

.gs-ai-enter-from,
.gs-ai-leave-to {
    opacity: 0;
}

.gs-ai-below.gs-ai-enter-from {
    transform: translate3d(0, -14px, 0) scale(0.78);
}

.gs-ai-below.gs-ai-leave-to {
    transform: translate3d(0, -8px, 0) scale(0.92);
}

.gs-ai-above.gs-ai-enter-from {
    transform: translate3d(0, 14px, 0) scale(0.78);
}

.gs-ai-above.gs-ai-leave-to {
    transform: translate3d(0, 8px, 0) scale(0.92);
}
</style>
