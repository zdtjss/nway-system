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
                <button type="button" class="gs-action" title="菜单搜索"
                    @pointerdown.stop @click.stop="startMenuSearch">
                    <el-icon><Menu /></el-icon>
                </button>
                <button type="button" class="gs-action gs-action-ai" title="AI 助手"
                    @pointerdown.stop @click.stop="startAiChat">
                    <el-icon><ChatDotRound /></el-icon>
                </button>
            </div>
        </transition>

        <!-- 菜单搜索框：点击菜单入口后由图标侧向推出 -->
        <div class="gs-panel" :class="'gs-' + placement.horizontal">
            <input ref="inputRef" v-model="keyword" class="gs-input" type="text" placeholder="请输入菜单名称"
                autocomplete="off" @input="onInput" @keydown="onKeydown">
        </div>

        <!-- 菜单搜索结果（浮层，靠图标一侧展开） -->
        <div v-if="showResult" ref="resultRef" class="gs-result"
            :class="['gs-' + placement.horizontal, 'gs-' + placement.vertical]"
            :style="{ width: placement.resultWidth + 'px' }">
            <div class="gs-result-header">
                <span class="gs-result-title">搜索结果</span>
                <span class="gs-result-count">共 {{ resultList.length }} 项</span>
            </div>
            <div v-for="(item, index) in resultList" :key="item.id || item.url || index" class="gs-result-item"
                :class="{ active: index === selectedIndex }" @mouseenter="selectedIndex = index" @mousedown.prevent
                @click="openMenu(item)">
                <span class="gs-result-name" v-html="highlightName(item.name)"></span>
                <span v-if="item.module" class="gs-result-module" :title="item.module">{{ item.module }}</span>
            </div>
            <div v-if="resultList.length === 0" class="gs-result-empty">未找到相关菜单，请更换关键词重试</div>
        </div>

        <!-- 办公智能体：办公问答，复用完整对话的流式接口 -->
        <transition name="gs-ai">
            <div v-if="aiOpen" class="gs-ai"
                :class="['gs-ai-' + aiVertical, 'gs-ai-h-' + aiPlacement.horizontal]" :style="aiPanelStyle"
                @keydown.esc.stop.prevent="onAiEscape">
                <div class="gs-ai-header" :class="{ 'is-dragging': aiDragging }"
                    @pointerdown="onAiHeaderPointerDown">
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
                        <button type="button" class="gs-ai-icon-btn" :class="{ 'is-active': aiHistoryOpen }"
                            title="历史记录" @click="toggleAiHistory">
                            <el-icon><Clock /></el-icon>
                        </button>
                        <button type="button" class="gs-ai-icon-btn" title="新对话" @click="newAiChat">
                            <el-icon><Plus /></el-icon>
                        </button>
                        <button type="button" class="gs-ai-full" :disabled="aiStreaming"
                            :title="aiStreaming ? '回复生成中，请稍候再打开完整对话' : '打开完整对话'"
                            @click="openFullChat">
                            <el-icon><TopRight /></el-icon>
                            <span>完整对话</span>
                        </button>
                        <button type="button" class="gs-ai-icon-btn" title="关闭" @click="closeAi">
                            <el-icon><Close /></el-icon>
                        </button>
                    </div>
                </div>

                <!-- 历史会话：在面板内切换查看，选中后继续该会话 -->
                <div v-if="aiHistoryOpen" class="gs-ai-history">
                    <div class="gs-ai-history-head">
                        <div class="gs-ai-history-title">历史记录</div>
                        <button type="button" class="gs-ai-icon-btn" title="返回对话" @click="aiHistoryOpen = false">
                            <el-icon><Close /></el-icon>
                        </button>
                    </div>
                    <el-input v-model="aiHistoryKeyword" class="gs-ai-history-search" size="small" clearable
                        placeholder="搜索会话标题" :prefix-icon="Search" />
                    <div v-loading="aiHistoryLoading" class="gs-ai-history-list">
                        <div v-for="conv in aiConversations" :key="conv.id" class="gs-ai-history-item"
                            :class="{ active: conv.id === aiConversationId }" @click="openAiConversation(conv)">
                            <span class="gs-ai-history-item-title" :title="conv.title">{{ conv.title || '新对话' }}</span>
                            <span class="gs-ai-history-item-time">{{ conversationTime(conv) }}</span>
                        </div>
                        <div v-if="!aiHistoryLoading && aiConversations.length === 0" class="gs-ai-history-empty">
                            {{ aiHistoryKeyword ? '没有匹配的会话' : '还没有历史会话' }}
                        </div>
                    </div>
                    <div v-if="aiHistoryTotal > AI_HISTORY_PAGE_SIZE" class="gs-ai-history-pagination">
                        <el-pagination size="small" background layout="prev, pager, next" :pager-count="5"
                            :total="aiHistoryTotal" :page-size="AI_HISTORY_PAGE_SIZE"
                            :current-page="aiHistoryCurrent" @current-change="onAiHistoryPageChange" />
                    </div>
                </div>

                <div v-show="!aiHistoryOpen" ref="aiBodyRef" class="gs-ai-body" v-loading="aiMessagesLoading"
                    @scroll="trackFollowScroll">
                    <div v-if="aiMessages.length === 0" class="gs-ai-empty">
                        <div class="gs-ai-empty-logo">
                            <el-icon><ChatDotRound /></el-icon>
                        </div>
                        <div class="gs-ai-empty-title">有什么可以帮你？</div>
                        <div class="gs-ai-empty-sub">支持文字、附件和当前页面提问；涉及提交确认或历史会话时，请前往完整对话。</div>
                    </div>

                    <div v-for="msg in aiMessages" :key="msg.id" class="gs-ai-msg" :class="msg.role">
                        <div class="gs-ai-bubble"
                            :class="{ 'is-error': !!msg.error, 'has-view': hasAiView(msg) || hasAiTable(msg) }">
                            <template v-if="msg.role === 'assistant'">
                                <div v-if="msg.thinking" class="gs-ai-thinking"
                                    :class="{ 'is-open': aiThinkingOpen.has(msg.id) }">
                                    <button type="button" class="gs-ai-thinking-head"
                                        @click="toggleAiThinking(msg.id)">
                                        <el-icon class="gs-ai-thinking-arrow"
                                            :class="{ 'is-open': aiThinkingOpen.has(msg.id) }">
                                            <ArrowRight />
                                        </el-icon>
                                        <span>思考过程</span>
                                        <span v-if="msg.streaming && !msg.text"
                                            class="gs-ai-thinking-live">思考中…</span>
                                    </button>
                                    <pre v-show="aiThinkingOpen.has(msg.id)" class="gs-ai-thinking-text"
                                        @scroll="trackFollowScroll">{{ msg.thinking }}</pre>
                                </div>
                                <template v-for="tool in msg.tools || []" :key="tool.id">
                                    <div v-if="tool.view" class="gs-ai-tool"
                                        :class="{ 'is-open': !aiToolCollapsed.has(tool.id), 'is-failed': tool.view.success === false }">
                                        <button type="button" class="gs-ai-tool-head"
                                            @click="toggleAiTool(tool.id)">
                                            <el-icon class="gs-ai-tool-arrow"
                                                :class="{ 'is-open': !aiToolCollapsed.has(tool.id) }">
                                                <ArrowRight />
                                            </el-icon>
                                            <span class="gs-ai-tool-name">
                                                {{ tool.view.interfaceName || '接口调用结果' }}
                                            </span>
                                            <span v-if="tool.view.success === false"
                                                class="gs-ai-tool-flag">调用失败</span>
                                        </button>
                                        <div v-show="!aiToolCollapsed.has(tool.id)" class="gs-ai-tool-body">
                                            <div v-if="tool.view.message" class="gs-ai-tool-msg"
                                                :class="{ 'is-error': tool.view.success === false }">
                                                {{ tool.view.message }}
                                            </div>
                                            <AiViewBlock v-if="tool.view.success !== false" :view="tool.view" compact />
                                        </div>
                                    </div>
                                </template>
                                <template v-for="(segment, index) in msg.segments" :key="index">
                                    <AiMarkdown v-if="segment.type === 'text' && segment.text"
                                        class="gs-ai-text" :text="segment.text" :streaming="msg.streaming" />
                                    <AiViewBlock v-else-if="segment.type === 'view' && segment.view" class="gs-ai-view"
                                        :view="segment.view" compact />
                                    <div v-else-if="segment.type === 'pending'" class="gs-ai-pending">正在整理数据…</div>
                                </template>
                                <div v-if="msg.streaming && !msg.text" class="gs-ai-typing">
                                    <span></span><span></span><span></span>
                                </div>
                                <div v-if="msg.toolTip" class="gs-ai-tool-tip">{{ msg.toolTip }}</div>
                                <div v-if="msg.error" class="gs-ai-error">{{ msg.error }}</div>
                                <button v-if="msg.needFullChat" type="button" class="gs-ai-jump" @click="openFullChat">
                                    打开完整对话继续
                                    <el-icon><TopRight /></el-icon>
                                </button>
                                <button v-if="ttsSupported && msg.text && !msg.streaming" type="button"
                                    class="gs-ai-speak" :class="{ 'is-active': speakingId === msg.id }"
                                    :title="speakingId === msg.id ? '停止播报' : '播报这条回复'"
                                    @click="speakAiMessage(msg)">
                                    <el-icon>
                                        <Mute v-if="speakingId === msg.id" />
                                        <Headset v-else />
                                    </el-icon>
                                    <span>{{ speakingId === msg.id ? '停止播报' : '语音播报' }}</span>
                                </button>
                            </template>
                            <template v-else>
                                <div v-if="msg.text">{{ msg.text }}</div>
                                <div v-if="msg.attachments && msg.attachments.length" class="gs-ai-msg-files">
                                    <div v-for="file in msg.attachments" :key="file.fileId || file.localId"
                                        class="gs-ai-msg-file" :class="{ 'is-link': !!file.previewUrl }"
                                        :title="file.size ? `${file.name} · ${formatAiSize(file.size)}` : file.name"
                                        @click="previewAiFile(file)">
                                        <el-icon><Document /></el-icon>
                                        <span class="gs-ai-msg-file-name">{{ file.name }}</span>
                                        <span v-if="file.size" class="gs-ai-msg-file-size">{{ formatAiSize(file.size) }}</span>
                                    </div>
                                </div>
                            </template>
                        </div>
                    </div>
                </div>

                <div v-show="!aiHistoryOpen" class="gs-ai-composer">
                    <div v-if="aiInputDragActive" class="gs-ai-drop-tip">
                        <span class="gs-ai-drop-dot"></span>
                        松开即可上传附件（最多 5 个，单个不超过 20MB）
                    </div>
                    <div v-if="aiAttachments.length" class="gs-ai-attach-tray">
                        <div v-for="file in aiAttachments" :key="file.localId" class="gs-ai-attach"
                            :class="{ 'is-failed': !!file.error }">
                            <div class="gs-ai-attach-icon">
                                <el-icon><Document /></el-icon>
                            </div>
                            <div class="gs-ai-attach-main">
                                <div class="gs-ai-attach-name" :title="file.name">{{ file.name }}</div>
                                <div class="gs-ai-attach-meta">
                                    <span v-if="file.uploading">上传中 {{ file.progress || 0 }}%</span>
                                    <span v-else-if="file.error" class="gs-ai-attach-error">{{ file.error }}</span>
                                    <span v-else>{{ formatAiSize(file.size) }}</span>
                                </div>
                            </div>
                            <button v-if="file.error" type="button" class="gs-ai-attach-retry"
                                @click="retryAiUpload(file)">重试</button>
                            <button type="button" class="gs-ai-attach-remove" title="移除"
                                @click="removeAiAttachment(file)">
                                <el-icon><Close /></el-icon>
                            </button>
                        </div>
                    </div>

                    <div class="gs-ai-ctx-bar">
                        <button type="button" class="gs-ai-ctx" :class="{ 'is-on': pageTextEnabled }"
                            :title="pageTextEnabled ? '已开启：本次发送附带当前页面文本快照，发送后自动取消' : '页面文本：本次发送附带当前页面文本快照'"
                            @click="pageTextEnabled = !pageTextEnabled">
                            <el-icon><Reading /></el-icon>
                        </button>
                        <button type="button" class="gs-ai-ctx" :class="{ 'is-on': pageShotEnabled }"
                            :title="pageShotEnabled ? '已开启：本次发送附带当前页面截图（DOM 截图），发送后自动取消' : '页面截图：本次发送附带当前页面截图（DOM 截图）'"
                            @click="pageShotEnabled = !pageShotEnabled">
                            <el-icon><Camera /></el-icon>
                        </button>
                        <button type="button" class="gs-ai-ctx" title="添加附件" @click="pickAiFiles">
                            <el-icon><Paperclip /></el-icon>
                        </button>
                        <span class="gs-ai-ctx-hint" :title="pageContextHint">{{ pageContextHint }}</span>
                    </div>

                    <div class="gs-ai-input-row" :class="{ 'is-drop-active': aiInputDragActive }"
                        @dragenter="onAiInputDragEnter" @dragover="onAiInputDragOver"
                        @dragleave="onAiInputDragLeave" @drop="onAiInputDrop">
                        <button type="button" class="gs-ai-voice-btn" :class="{ 'is-recording': voiceActive }"
                            :title="voiceTitle" :disabled="!speechRecognitionSupported" @click="toggleAiVoice">
                            <el-icon><Microphone /></el-icon>
                        </button>
                        <textarea ref="aiInputEl" v-model="aiInput" class="gs-ai-input" rows="1" placeholder="给 AI 发消息，Enter 发送"
                            @keydown="onAiKeydown" @input="autoResizeAi"@paste="onAiPaste"></textarea>
                        <button v-if="!aiStreaming" type="button" class="gs-ai-send" title="发送" :disabled="!aiCanSend" @click="sendAi">
                            <el-icon><Promotion /></el-icon>
                        </button>
                        <button v-else type="button" class="gs-ai-send is-stop" title="停止生成" @click="stopAi">
                            <el-icon><VideoPause /></el-icon>
                        </button>
                    </div>
                    <div v-if="voiceActive" class="gs-ai-voice-live">
                        <span class="gs-ai-voice-dot"></span>
                        正在聆听，再次点击麦克风结束
                    </div>
                    <div class="gs-ai-foot">
                    </div>
                </div>

                <input ref="aiFileInput" type="file" multiple class="gs-ai-hidden-input" @change="onAiFilesPicked" />

                <!-- 缩放把手：随面板展开方向落到外侧角 -->
                <div class="gs-ai-resize" :class="aiResizeClass" title="拖动调整大小" @pointerdown="onAiResizeDown"></div>
            </div>
        </transition>

        <!-- 附件页内预览：图片走全屏看图（同完整对话页），PDF/文本用半透明无标题栏浮层 -->
        <el-image-viewer v-if="aiPreview.open && aiPreview.type === 'image'" :url-list="[aiPreview.url]"
            @close="closeAiPreview" />
        <transition name="gs-preview">
            <div v-if="aiPreview.open && aiPreview.type !== 'image'" class="gs-preview" @click.self="closeAiPreview">
                <button type="button" class="gs-preview-close" title="关闭" @click="closeAiPreview">
                    <el-icon><Close /></el-icon>
                </button>
                <div v-if="aiPreview.type === 'pdf' || aiPreview.type === 'text'" v-loading="aiPreview.loading"
                    class="gs-preview-paper">
                    <iframe v-if="aiPreview.type === 'pdf'" :src="aiPreview.url" title="PDF 预览"></iframe>
                    <pre v-else>{{ aiPreview.text }}</pre>
                </div>
                <div v-else class="gs-preview-tip">
                    <el-icon><Document /></el-icon>
                    <p>该文件类型暂不支持页内预览</p>
                </div>
            </div>
        </transition>
    </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { match } from 'pinyin-pro'
import { ElMessage } from 'element-plus'
import {
    ArrowRight, Camera, ChatDotRound, Clock, Close, Document, Headset, Menu, Microphone, Mute,
    Paperclip, Plus, Promotion, Reading, Search, TopRight, VideoPause
} from '@element-plus/icons-vue'
import http from '@/js/httpProxy.js'
import { assetUrl, deleteChatFile, getMessages, listConversations, streamChat, uploadChatFile } from '@/api/ai'
import AiMarkdown from '@/components/ai/AiMarkdown.vue'
import AiViewBlock from '@/components/ai/AiViewBlock.vue'
import { buildInvokeView, rememberInterfaceConfig, splitViewSegments } from '@/utils/aiView'
import { followBottom, followLast, trackFollowScroll } from '@/utils/scrollFollow'
import { getToken } from '@/utils/auth'
import { buildPageTextSnapshot, capturePageScreenshot } from '@/utils/pageContext'
import { useCurrentPageStore } from '@/stores/menuStore'
import { useAiChatHandoffStore } from '@/stores/aiChatStore'

// 图标位置（图标左上角）持久化 key
const POS_KEY = 'nway_global_search_pos'
const ICON_SIZE = 40
const GAP = 8
const PANEL_WIDTH = 300
const RESULT_WIDTH = 360
const VIEWPORT_MARGIN = 8
const AI_PANEL_WIDTH = 440
// 面板可拖拽到接近视口高度，仅在超出视口时才截断
const AI_PANEL_MAX_HEIGHT = 900
const AI_PANEL_DEFAULT_HEIGHT = 600
const AI_PANEL_MIN_WIDTH = 300
const AI_PANEL_MIN_HEIGHT = 280
const AI_PANEL_MAX_WIDTH = 760
const AI_MAX_FILES = 5
const AI_MAX_FILE_SIZE = 20 * 1024 * 1024
// AI 面板缩放尺寸持久化 key
const AI_SIZE_KEY = 'nway_global_search_ai_size'
// AI 助手完整对话页（与菜单 url 一致，由 main.ts 的 views/system 自动注册为 SystemAiChat）
const AI_CHAT_URL = '/views/system/ai/chat'

const route = useRoute()
const router = useRouter()
const currentPageStore = useCurrentPageStore()
const aiChatHandoff = useAiChatHandoffStore()

const rootRef = ref(null)
const inputRef = ref(null)
const resultRef = ref(null)

// 登录后且在非登录页展示
const visible = computed(() => route.name !== 'login' && !!getToken())

// 当前展开的面板：'' | 'menu' | 'ai'
const mode = ref('')
const menuOpen = computed(() => mode.value === 'menu')
const aiOpen = computed(() => mode.value === 'ai')

const hovered = ref(false)
const dragging = ref(false)
const keyword = ref('')
const selectedIndex = ref(-1)
const menus = ref(null)

// ---------- 位置与拖动 ----------
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
        const saved = JSON.parse(localStorage.getItem(POS_KEY) || 'null')
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
    // 图标移动后重新判断 AI 面板展开方向
    if (aiOpen.value) updateAiPlacement()
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
            localStorage.setItem(POS_KEY, JSON.stringify(pos.value))
        } catch (e) {
            // 存储失败不影响本次拖动
        }
    }
}

function onIconClick() {
    if (Date.now() - lastDragEndAt < 300) return
    if (menuOpen.value) {
        inputRef.value?.focus()
    } else {
        startMenuSearch()
    }
}

function onResize() {
    viewport.value = { w: window.innerWidth, h: window.innerHeight }
    pos.value = clampPos(pos.value)
    if (mode.value === 'ai') updateAiPlacement()
}

// ---------- 展开方向与尺寸 ----------
const placement = computed(() => {
    const vw = viewport.value.w
    const vh = viewport.value.h
    const rightSpace = vw - pos.value.left - ICON_SIZE - GAP
    const leftSpace = pos.value.left - GAP
    // 右侧放得下面板则向右推出，否则向空间更大的一侧展开
    const horizontal = rightSpace >= PANEL_WIDTH || rightSpace >= leftSpace ? 'right' : 'left'
    const sideSpace = horizontal === 'right' ? rightSpace : leftSpace
    const belowSpace = vh - pos.value.top - ICON_SIZE - GAP
    const aboveSpace = pos.value.top - GAP
    const vertical = belowSpace < 220 && aboveSpace > belowSpace ? 'above' : 'below'
    return {
        horizontal,
        vertical,
        panelWidth: Math.max(180, Math.min(PANEL_WIDTH, sideSpace)),
        resultWidth: Math.max(180, Math.min(RESULT_WIDTH, sideSpace))
    }
})

// ---------- AI 面板尺寸与缩放 ----------
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
    const rightSpace = vw - (pos.value.left + ICON_SIZE + GAP) - VIEWPORT_MARGIN
    const leftSpace = pos.value.left - GAP - VIEWPORT_MARGIN
    aiPlacement.horizontal = rightSpace >= width || rightSpace >= leftSpace ? 'right' : 'left'
    const belowSpace = vh - (pos.value.top + ICON_SIZE + GAP) - VIEWPORT_MARGIN
    const aboveSpace = pos.value.top - GAP - VIEWPORT_MARGIN
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
    const minLeft = VIEWPORT_MARGIN - pos.value.left
    const maxLeft = Math.max(minLeft, vw - width - VIEWPORT_MARGIN - pos.value.left)
    const left = Math.min(Math.max(minLeft, offset), maxLeft)
    const below = aiPlacement.vertical === 'below'
    const belowSpace = vh - (pos.value.top + ICON_SIZE + GAP) - VIEWPORT_MARGIN
    const aboveSpace = pos.value.top - GAP - VIEWPORT_MARGIN
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
    const viewLeft = Math.min(Math.max(VIEWPORT_MARGIN, pos.value.left + left + aiOffset.value.left), maxLeft)
    const viewTop = Math.min(Math.max(VIEWPORT_MARGIN, pos.value.top + top + aiOffset.value.top), maxTop)
    return {
        left: viewLeft - pos.value.left,
        top: viewTop - pos.value.top,
        width,
        height,
        below
    }
})

const aiVertical = computed(() => (aiBaseLayout.value.below ? 'below' : 'above'))

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

// ---------- AI 面板拖动 ----------
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
    const viewLeft = Math.min(Math.max(VIEWPORT_MARGIN, pos.value.left + base.left + aiOffset.value.left), maxLeft)
    const viewTop = Math.min(Math.max(VIEWPORT_MARGIN, pos.value.top + base.top + aiOffset.value.top), maxTop)
    aiOffset.value = {
        left: viewLeft - (pos.value.left + base.left),
        top: viewTop - (pos.value.top + base.top)
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

// ---------- 入口与收起 ----------
// 仅空闲悬停时展示两个入口；任一面板展开后由面板自身接管交互
const actionsVisible = computed(() => hovered.value && mode.value === '')

function startMenuSearch() {
    mode.value = 'menu'
    ensureMenus()
    nextTick(() => inputRef.value?.focus())
}

function startAiChat() {
    // 每次打开都回到图标旁，避免上次手动摆放的位置造成困惑
    aiOffset.value = { left: 0, top: 0 }
    aiHistoryOpen.value = false
    resetAiDragState()
    updateAiPlacement()
    mode.value = 'ai'
    nextTick(() => {
        autoResizeAi()
        aiInputEl.value?.focus()
        scrollAiToBottom(true)
    })
}

function closeMenu() {
    if (mode.value !== 'menu') return
    mode.value = ''
    keyword.value = ''
    selectedIndex.value = -1
    inputRef.value?.blur()
}

function closeAi() {
    if (mode.value !== 'ai') return
    closeAiPreview()
    // 关闭面板视为手动结束本轮回复，避免不可见的后台生成继续消耗
    if (aiStreaming.value) stopAi()
    stopAiVoice()
    stopAiSpeaking()
    aiHistoryOpen.value = false
    resetAiDragState()
    mode.value = ''
}

function closeAll() {
    if (mode.value === 'menu') closeMenu()
    else if (mode.value === 'ai') closeAi()
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
    closeAll()
}

// Esc 兜底关闭：焦点不在输入框/面板内时同样生效
function onDocumentKeydown(e) {
    if (e.key !== 'Escape' || !mode.value) return
    // 预览打开时优先关闭预览，避免一下退出整个对话
    if (aiPreview.open) {
        closeAiPreview()
        return
    }
    closeAll()
}

function onInput() {
    selectedIndex.value = -1
}

// 路由切换后收起，避免浮层残留
watch(() => route.fullPath, () => closeAll())

// ---------- 菜单数据与搜索 ----------
let menusLoading = false

function ensureMenus() {
    if (menus.value || menusLoading) return
    menusLoading = true
    http.get('/myMenu')
        .then(({ data }) => {
            const d = data && data.data
            if (!d) return
            menus.value = {
                favorite: Array.isArray(d.favorite) ? d.favorite.filter((item) => item && item.url) : [],
                sys: Array.isArray(d.sys) ? d.sys : [],
                menuList: Array.isArray(d.menuList) ? d.menuList : []
            }
        })
        .catch(() => {
            // 加载失败时保持未加载状态，下次展开重试
        })
        .finally(() => {
            menusLoading = false
        })
}

// 与 allMenu 搜索一致：名称包含或拼音匹配，仅返回 PAGE 节点
function deepSearch(nodes, title, out) {
    if (!Array.isArray(nodes)) return
    for (const node of nodes) {
        if (!node) continue
        const name = node.name || ''
        if (node.type === 'PAGE' && (name.includes(keyword.value) || match(name, keyword.value))) {
            out.push({ ...node, module: title })
        }
        if (node.children && node.children.length > 0) {
            const childTitle = node.type === 'MODULE' ? node.name : title
            deepSearch(node.children, childTitle, out)
        }
    }
}

const resultList = computed(() => {
    const kw = keyword.value
    if (!menus.value || !kw || kw.length < 2) return []
    const out = []
    deepSearch(menus.value.favorite, '我的常用', out)
    deepSearch(menus.value.sys, '系统/应用', out)
    deepSearch(menus.value.menuList, undefined, out)
    return out
})

const showResult = computed(() => menuOpen.value && !!menus.value && keyword.value.length >= 2)

function highlightName(name) {
    const kw = keyword.value
    if (!kw || !name || !name.includes(kw)) return name
    const idx = name.indexOf(kw)
    return name.slice(0, idx) + '<em class="gs-hl">' + name.slice(idx, idx + kw.length) + '</em>' + name.slice(idx + kw.length)
}

function scrollSelectedIntoView() {
    const container = resultRef.value
    if (!container || selectedIndex.value < 0) return
    const el = container.querySelectorAll('.gs-result-item')[selectedIndex.value]
    if (!el) return
    const cRect = container.getBoundingClientRect()
    const eRect = el.getBoundingClientRect()
    if (eRect.top < cRect.top) {
        container.scrollTop = Math.max(0, container.scrollTop - (cRect.top - eRect.top))
    } else if (eRect.bottom > cRect.bottom) {
        container.scrollTop += eRect.bottom - cRect.bottom
    }
}

function onKeydown(event) {
    if (event.key === 'Escape') {
        event.stopPropagation()
        closeMenu()
        return
    }
    const list = resultList.value
    if (list.length === 0) return
    if (event.key === 'ArrowDown') {
        event.preventDefault()
        selectedIndex.value = selectedIndex.value < 0 ? 0 : (selectedIndex.value + 1) % list.length
        scrollSelectedIntoView()
    } else if (event.key === 'ArrowUp') {
        event.preventDefault()
        selectedIndex.value = selectedIndex.value < 0 ? list.length - 1 : (selectedIndex.value - 1 + list.length) % list.length
        scrollSelectedIntoView()
    } else if (event.key === 'Enter') {
        event.preventDefault()
        if (selectedIndex.value >= 0 && selectedIndex.value < list.length) {
            openMenu(list[selectedIndex.value])
        }
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

// ---------- AI 轻量对话 ----------
const aiMessages = ref([])
const aiInput = ref('')
const aiStreaming = ref(false)
const aiBodyRef = ref(null)
const aiInputEl = ref(null)
const aiFileInput = ref(null)
const aiAttachments = ref([])
// 输入框拖放高亮：仅文件拖到聊天内容输入框区域时展示
const aiInputDragActive = ref(false)

// 页面上下文开关：默认不附带，按需开启
const pageTextEnabled = ref(false)
const pageShotEnabled = ref(false)

const currentPageName = computed(() => {
    const url = currentPageStore.currentUrl
    const menu = currentPageStore.pageList?.find((item) => item.url === url)
    return menu?.name || currentPageStore.currentComp || currentPageStore.currentApp?.name || ''
})

const pageContextHint = computed(() => {
    if (!pageTextEnabled.value && !pageShotEnabled.value) return '仅发送输入内容'
    const parts = []
    if (pageTextEnabled.value) parts.push('文本')
    if (pageShotEnabled.value) parts.push('截图')
    const name = currentPageName.value
    return name ? `附带${parts.join(' + ')}：${name}` : `附带页面${parts.join(' + ')}`
})

function currentPageMeta() {
    return {
        title: currentPageName.value || document.title,
        url: currentPageStore.currentUrl || currentPageStore.currentApp?.host || '',
        renderType: currentPageStore.currentApp?.renderType || 'native'
    }
}

// dragenter/dragleave 在子元素间移动时会反复触发，用计数避免高亮闪烁
let aiInputDragDepth = 0

let aiConversationId = null
// 待确认的写操作：完整对话页接管后可直接弹出确认表单继续提交
let aiPendingConfirm = null
let aiAbort = null
// 每轮流的序号：停止后立即发新消息时，旧请求的收尾不能覆盖新请求的状态
let aiStreamSeq = 0
let aiSeq = 0

// ---------- AI 历史会话 ----------
const AI_HISTORY_PAGE_SIZE = 10
const aiHistoryOpen = ref(false)
const aiHistoryKeyword = ref('')
const aiHistoryCurrent = ref(1)
const aiHistoryTotal = ref(0)
const aiHistoryLoading = ref(false)
const aiMessagesLoading = ref(false)
const aiConversations = ref([])
let aiHistoryTimer = null
let aiHistorySeq = 0
let aiMessageSeq = 0

function aiUid(role) {
    aiSeq += 1
    return `gs-ai-${role}-${aiSeq}-${Date.now()}`
}

const aiCanSend = computed(() => {
    if (aiStreaming.value) return false
    const hasText = aiInput.value.trim().length > 0
    const hasFiles = aiAttachments.value.some((file) => !file.error && !file.uploading)
    const uploading = aiAttachments.value.some((file) => file.uploading)
    return (hasText || hasFiles) && !uploading
})

function onAiKeydown(event) {
    if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
        event.preventDefault()
        sendAi()
    }
}

function autoResizeAi() {
    const el = aiInputEl.value
    if (!el) return
    el.style.height = 'auto'
    el.style.height = Math.min(el.scrollHeight, 96) + 'px'
}

async function scrollAiToBottom(force = false) {
    await nextTick()
    followBottom(aiBodyRef.value, force)
}

// 思考过程等内部滚动区随流式输出贴底
function followAiStreamTail(selector) {
    nextTick(() => followLast(aiBodyRef.value, selector))
}

// 正文变化后重新分段：已闭合的视图块渲染，流式期间的未闭合块显示占位
function syncAiSegments(message) {
    message.segments = splitViewSegments(message.text, !!message.streaming)
}

// 消息中是否包含结构化视图，面板内视图块需要占满气泡宽度
function hasAiView(message) {
    const hasSegmentView = Array.isArray(message.segments)
        && message.segments.some((segment) => segment.type === 'view')
    const hasToolView = Array.isArray(message.tools) && message.tools.some((tool) => !!tool.view)
    return hasSegmentView || hasToolView
}

// 正文包含 Markdown 表格时同样占满气泡宽度，避免宽表格被压缩
function hasAiTable(message) {
    return Array.isArray(message.segments) && message.segments.some(
        (segment) => segment.type === 'text' && !!segment.text && /^\s*\|.*\|\s*$/m.test(segment.text)
    )
}

// 思考过程默认收起，流式生成思考时自动展开
const aiThinkingOpen = ref(new Set())

function toggleAiThinking(id) {
    if (aiThinkingOpen.value.has(id)) aiThinkingOpen.value.delete(id)
    else aiThinkingOpen.value.add(id)
    aiThinkingOpen.value = new Set(aiThinkingOpen.value)
}

// 接口调用结果默认展开，点击标题收起
const aiToolCollapsed = ref(new Set())

function toggleAiTool(id) {
    if (aiToolCollapsed.value.has(id)) aiToolCollapsed.value.delete(id)
    else aiToolCollapsed.value.add(id)
    aiToolCollapsed.value = new Set(aiToolCollapsed.value)
}

const CONFIG_TOOL = 'get_interface_config'
const INVOKE_TOOL = 'invoke_interface'
// get_interface_config 的流式结果先暂存，结束后整体解析为接口字段定义
const aiPendingConfig = new Map()

function handleAiEvent(event, target) {
    switch (event.type) {
        case 'conversation':
            if (event.conversationId) aiConversationId = event.conversationId
            break
        case 'reply.start':
            if (event.replyId) target.id = event.replyId
            break
        case 'thinking.delta':
            target.thinking += event.delta || ''
            if (target.streaming && !aiThinkingOpen.value.has(target.id)) {
                aiThinkingOpen.value = new Set(aiThinkingOpen.value).add(target.id)
            }
            followAiStreamTail('.gs-ai-thinking-text')
            break
        case 'message.delta':
            target.text += event.delta || ''
            syncAiSegments(target)
            break
        case 'tool.call.start':
            target.toolTip = '正在调用：' + (event.name || '工具')
            if (event.name === INVOKE_TOOL) {
                target.tools.push({
                    id: event.toolCallId || aiUid('tool'),
                    name: INVOKE_TOOL,
                    status: 'running',
                    result: '',
                    view: null
                })
            }
            break
        case 'tool.result.delta':
            if (event.name === CONFIG_TOOL) {
                const key = event.toolCallId || ''
                aiPendingConfig.set(key, (aiPendingConfig.get(key) || '') + (event.delta || ''))
            } else if (event.name === INVOKE_TOOL) {
                const tool = target.tools.find((item) => item.id === event.toolCallId)
                if (tool) tool.result += event.delta || ''
            }
            break
        case 'tool.result.end':
            target.toolTip = ''
            if (event.name === CONFIG_TOOL) {
                const key = event.toolCallId || ''
                rememberInterfaceConfig(aiPendingConfig.get(key) || '')
                aiPendingConfig.delete(key)
            } else if (event.name === INVOKE_TOOL) {
                const tool = target.tools.find((item) => item.id === event.toolCallId)
                if (tool) {
                    tool.status = event.state === 'ERROR' || event.state === 'DENIED' ? 'error' : 'done'
                    tool.view = buildInvokeView(tool.result)
                }
            }
            break
        case 'confirm.required':
            target.streaming = false
            syncAiSegments(target)
            target.needFullChat = true
            target.error = '该操作需要你确认后才能提交，请到「完整对话」继续完成。'
            aiPendingConfirm = {
                type: 'confirm.required',
                replyId: event.replyId || '',
                toolCalls: event.toolCalls || []
            }
            break
        case 'reply.end':
            target.streaming = false
            syncAiSegments(target)
            break
        case 'error':
            target.streaming = false
            syncAiSegments(target)
            target.error = event.message || 'AI 服务异常'
            break
        default:
            break
    }
    scrollAiToBottom()
}

async function sendAi() {
    const text = aiInput.value.trim()
    const files = aiAttachments.value.filter((file) => !file.error && !file.uploading)
    if ((!text && files.length === 0) || aiStreaming.value) return
    if (aiAttachments.value.some((file) => file.uploading)) return
    stopAiVoice()
    stopAiSpeaking()
    // 新消息会重新发起一轮对话，之前待确认的操作不再保留
    aiPendingConfirm = null
    aiMessages.value.push({
        id: aiUid('user'),
        role: 'user',
        text,
        attachments: files.map((file) => ({ ...file }))
    })
    // 取响应式代理：截图上传成功后追加到该条用户消息的附件区
    const userMessage = aiMessages.value[aiMessages.value.length - 1]
    const target = reactive({
        id: aiUid('assistant'),
        role: 'assistant',
        text: '',
        thinking: '',
        segments: [],
        tools: [],
        error: '',
        toolTip: '',
        needFullChat: false,
        streaming: true
    })
    aiMessages.value.push(target)
    aiInput.value = ''
    aiAttachments.value = []
    autoResizeAi()
    scrollAiToBottom(true)
    aiStreaming.value = true
    const controller = new AbortController()
    aiAbort = controller
    const streamSeq = ++aiStreamSeq
    const pageMeta = currentPageMeta()
    const usePageText = pageTextEnabled.value
    const usePageShot = pageShotEnabled.value
    // 页面上下文为一次性附带：发送即取消选择，避免后续消息继续携带页面信息浪费 token
    pageTextEnabled.value = false
    pageShotEnabled.value = false
    const fileIds = files.map((file) => file.fileId)
    let pageContext = null
    if (usePageText) {
        try {
            pageContext = buildPageTextSnapshot(pageMeta)
        } catch (e) {
            console.warn('[page-context] 页面文本快照失败', e)
        }
    }
    try {
        if (usePageShot) {
            if (fileIds.length >= AI_MAX_FILES) {
                ElMessage.warning(`附件已达 ${AI_MAX_FILES} 个，本次未附带页面截图`)
            } else {
                target.toolTip = '正在截取当前页面…'
                try {
                    const shot = await capturePageScreenshot(pageMeta)
                    if (shot) {
                        const res = await uploadChatFile(shot)
                        if (res?.data?.fileId) {
                            fileIds.push(res.data.fileId)
                            userMessage.attachments.push({
                                localId: aiUid('file'),
                                fileId: res.data.fileId,
                                name: shot.name,
                                size: shot.size,
                                mime: shot.type
                            })
                        }
                    }
                } catch (e) {
                    console.warn('[page-context] 页面截图失败', e)
                    ElMessage.warning('页面截图失败，本次未附带截图')
                } finally {
                    target.toolTip = ''
                }
            }
        }
        if (usePageText && !pageContext) {
            ElMessage.warning('当前页面内容无法读取，本次未附带页面文本')
        }
        // 截图/上传期间用户已点击停止：不再发起流式请求
        if (controller.signal.aborted) {
            if (!target.text) target.text = '已停止生成。'
            return
        }
        await streamChat({
            conversationId: aiConversationId,
            message: text,
            fileIds,
            pageContext
        }, (event) => handleAiEvent(event, target), controller.signal)
    } catch (e) {
        if (e?.name === 'AbortError') {
            if (!target.text) target.text = '已停止生成。'
        } else {
            target.error = e?.message || '请求失败，请稍后重试'
        }
    } finally {
        target.streaming = false
        target.toolTip = ''
        syncAiSegments(target)
        // 仅清理本轮流式状态：停止后立即发送的新消息不能被旧请求收尾覆盖
        if (streamSeq === aiStreamSeq) {
            aiStreaming.value = false
            aiAbort = null
        }
        scrollAiToBottom()
        // 新会话标题由首条消息生成，发送结束后刷新历史列表
        if (aiHistoryOpen.value) loadAiHistory()
    }
}

function stopAi() {
    aiAbort?.abort()
    aiStreaming.value = false
}

// 关键字输入防抖搜索，避免每次按键都请求
watch(aiHistoryKeyword, () => {
    if (aiHistoryTimer) clearTimeout(aiHistoryTimer)
    aiHistoryTimer = setTimeout(searchAiHistory, 300)
})

function onAiEscape() {
    if (aiPreview.open) {
        closeAiPreview()
        return
    }
    if (aiHistoryOpen.value) {
        aiHistoryOpen.value = false
        return
    }
    closeAi()
}

function toggleAiHistory() {
    if (aiHistoryOpen.value) {
        aiHistoryOpen.value = false
        return
    }
    aiHistoryOpen.value = true
    loadAiHistory()
}

async function loadAiHistory() {
    const seq = ++aiHistorySeq
    aiHistoryLoading.value = true
    try {
        const res = await listConversations(aiHistoryCurrent.value, AI_HISTORY_PAGE_SIZE,
            aiHistoryKeyword.value.trim() || undefined)
        if (seq !== aiHistorySeq) return
        // 分页数据在 Result.data 里：{ data: 列表, total: 总数 }
        aiConversations.value = res.data?.data || []
        aiHistoryTotal.value = res.data?.total || 0
    } catch (e) {
        // http 拦截器已提示
    } finally {
        if (seq === aiHistorySeq) aiHistoryLoading.value = false
    }
}

function searchAiHistory() {
    if (aiHistoryTimer) {
        clearTimeout(aiHistoryTimer)
        aiHistoryTimer = null
    }
    aiHistoryCurrent.value = 1
    loadAiHistory()
}

function onAiHistoryPageChange(page) {
    aiHistoryCurrent.value = page
    loadAiHistory()
}

// 历史列表只展示到分钟，避免长日期挤占标题空间
function conversationTime(conv) {
    const raw = conv.lastMessageAt || conv.createTime
    return raw ? raw.slice(5, 16) : ''
}

async function openAiConversation(conv) {
    if (aiStreaming.value) {
        ElMessage.warning('请先等待当前回答结束或点击停止')
        return
    }
    aiHistoryOpen.value = false
    stopAiVoice()
    stopAiSpeaking()
    aiConversationId = conv.id
    aiMessages.value = []
    aiThinkingOpen.value = new Set()
    aiToolCollapsed.value = new Set()
    aiPendingConfig.clear()
    aiPendingConfirm = null
    const seq = ++aiMessageSeq
    aiMessagesLoading.value = true
    try {
        const res = await getMessages(conv.id)
        if (seq !== aiMessageSeq) return
        aiMessages.value = (res.data || []).map(toPanelMessage)
        scrollAiToBottom(true)
    } catch (e) {
        // http 拦截器已提示
    } finally {
        if (seq === aiMessageSeq) aiMessagesLoading.value = false
    }
}

// ---------- 附件页内预览 ----------
const aiPreview = reactive({
    open: false,
    url: '',
    type: '',
    text: '',
    loading: false
})
let aiPreviewSeq = 0

// 历史消息的预览地址由后端重新签发；图片走全屏看图，PDF/文本在当前页浮层展示
function previewAiFile(file) {
    if (!file?.previewUrl) return
    const kind = String(file.kind || '').toUpperCase()
    aiPreviewSeq += 1
    Object.assign(aiPreview, {
        open: true,
        url: assetUrl(file.previewUrl),
        type: kind === 'IMAGE' ? 'image' : kind === 'PDF' ? 'pdf' : kind === 'TEXT' ? 'text' : 'other',
        text: '',
        loading: kind === 'TEXT'
    })
    if (kind === 'TEXT') loadAiPreviewText(aiPreview.url)
}

async function loadAiPreviewText(url) {
    const seq = aiPreviewSeq
    try {
        const res = await fetch(url)
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        const text = await res.text()
        if (seq !== aiPreviewSeq || !aiPreview.open) return
        aiPreview.text = text || '（文件内容为空）'
    } catch (e) {
        if (seq !== aiPreviewSeq || !aiPreview.open) return
        aiPreview.text = '文件内容加载失败，请稍后重试'
    } finally {
        if (seq === aiPreviewSeq && aiPreview.open) aiPreview.loading = false
    }
}

function closeAiPreview() {
    aiPreviewSeq += 1
    aiPreview.open = false
    aiPreview.loading = false
    aiPreview.text = ''
}

// 历史消息块转面板消息结构：文本/思考/工具结果按现有气泡样式展示
function toPanelMessage(message) {
    const ui = {
        id: message.id || aiUid('assistant'),
        role: message.role === 'user' ? 'user' : 'assistant',
        text: '',
        thinking: '',
        segments: [],
        tools: [],
        attachments: [],
        error: '',
        toolTip: '',
        needFullChat: false,
        streaming: false
    }
    const texts = []
    ;(message.blocks || []).forEach((block) => {
        if (!block) return
        if (block.type === 'text') {
            if (block.text) texts.push(block.text)
        } else if (block.type === 'thinking') {
            if (block.text) ui.thinking = ui.thinking ? `${ui.thinking}\n\n${block.text}` : block.text
        } else if (block.type === 'attachments') {
            // 历史附件：后端已按 fileId 重新生成预览地址
            ui.attachments.push(...(block.files || []))
        } else if (block.type === 'tool_use') {
            const name = block.name || INVOKE_TOOL
            if (name === CONFIG_TOOL) {
                rememberInterfaceConfig(block.result)
                return
            }
            ui.tools.push({
                id: block.id || aiUid('tool'),
                name,
                status: block.state === 'ERROR' || block.state === 'DENIED' ? 'error' : 'done',
                result: block.result || '',
                view: name === INVOKE_TOOL ? buildInvokeView(block.result) : null
            })
        }
    })
    ui.text = texts.join('\n')
    ui.segments = splitViewSegments(ui.text, false)
    return ui
}

function newAiChat() {
    if (aiStreaming.value) stopAi()
    stopAiVoice()
    stopAiSpeaking()
    aiHistoryOpen.value = false
    // 取消未完成的历史消息加载，避免回来后覆盖新对话
    aiMessageSeq += 1
    aiMessagesLoading.value = false
    aiMessages.value = []
    aiAttachments.value = []
    aiThinkingOpen.value = new Set()
    aiToolCollapsed.value = new Set()
    aiConversationId = null
    aiPendingConfirm = null
    aiInput.value = ''
    aiPendingConfig.clear()
    autoResizeAi()
    nextTick(() => aiInputEl.value?.focus())
}

// ---------- AI 附件上传 ----------
function formatAiSize(size) {
    if (!size) return ''
    if (size < 1024) return size + ' B'
    if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB'
    return (size / 1024 / 1024).toFixed(1) + ' MB'
}

function pickAiFiles() {
    aiFileInput.value?.click()
}

function onAiFilesPicked(event) {
    const target = event.target
    const files = Array.from(target.files || [])
    target.value = ''
    addAiFiles(files)
}

async function addAiFiles(files) {
    const remain = AI_MAX_FILES - aiAttachments.value.length
    if (remain <= 0) {
        ElMessage.warning(`单次最多 ${AI_MAX_FILES} 个文件`)
        return
    }
    for (const file of files.slice(0, remain)) {
        if (file.size > AI_MAX_FILE_SIZE) {
            ElMessage.warning(`${file.name} 超过 20MB`)
            continue
        }
        const item = reactive({
            localId: aiUid('file'),
            fileId: '',
            name: file.name,
            size: file.size,
            mime: file.type,
            uploading: true,
            progress: 0,
            rawFile: file
        })
        aiAttachments.value.push(item)
        try {
            const res = await uploadChatFile(file, (percent) => { item.progress = percent })
            Object.assign(item, res.data, { uploading: false, progress: 100 })
        } catch (error) {
            item.uploading = false
            item.error = error?.response?.data?.msg || error?.message || '上传失败'
        }
    }
}

async function retryAiUpload(item) {
    if (!item.rawFile) return
    item.error = undefined
    item.uploading = true
    item.progress = 0
    try {
        const res = await uploadChatFile(item.rawFile, (percent) => { item.progress = percent })
        Object.assign(item, res.data, { uploading: false, progress: 100 })
        item.error = undefined
    } catch (error) {
        item.uploading = false
        item.error = error?.response?.data?.msg || '上传失败'
    }
}

async function removeAiAttachment(item) {
    aiAttachments.value = aiAttachments.value.filter((file) => file.localId !== item.localId)
    if (item.fileId) {
        try {
            await deleteChatFile(item.fileId)
        } catch (e) {
            // 已发送或已删除，忽略
        }
    }
}

/** 仅接管文件拖拽，文本拖拽保持浏览器/输入框默认行为。 */
function isFileDrag(event) {
    const types = event.dataTransfer?.types
    return !!types && Array.from(types).includes('Files')
}

// 清理输入框拖放高亮（拖放结束或面板收起时调用）
function resetAiDragState() {
    aiInputDragDepth = 0
    aiInputDragActive.value = false
}

// 聊天内容输入框：文件拖放上传
function onAiInputDragEnter(event) {
    if (!isFileDrag(event)) return
    event.preventDefault()
    aiInputDragDepth += 1
    aiInputDragActive.value = true
}

function onAiInputDragOver(event) {
    if (!isFileDrag(event)) return
    event.preventDefault()
    if (event.dataTransfer) event.dataTransfer.dropEffect = 'copy'
    aiInputDragActive.value = true
}

function onAiInputDragLeave(event) {
    if (!isFileDrag(event)) return
    aiInputDragDepth = Math.max(0, aiInputDragDepth - 1)
    if (aiInputDragDepth === 0) aiInputDragActive.value = false
}

async function onAiInputDrop(event) {
    const files = Array.from(event.dataTransfer?.files || [])
    // 先结束拖放的视觉状态，再走上传流程，虚线框不依赖上传结果
    resetAiDragState()
    if (!files.length) return
    event.preventDefault()
    await addAiFiles(files)
}

async function onAiPaste(event) {
    const files = Array.from(event.clipboardData?.files || [])
    if (files.length) {
        event.preventDefault()
        await addAiFiles(files)
    }
}

// ---------- 语音输入与播报 ----------
const SpeechRecognitionClass = window.SpeechRecognition || window.webkitSpeechRecognition || null
const speechRecognitionSupported = !!SpeechRecognitionClass
const ttsSupported = typeof window !== 'undefined' && 'speechSynthesis' in window

const VOICE_ERRORS = {
    'not-allowed': '麦克风权限被拒绝，请在浏览器设置中允许后重试',
    'service-not-allowed': '当前环境未授权语音识别服务',
    'audio-capture': '未检测到可用的麦克风',
    network: '语音识别服务连接失败，请检查网络后重试'
}

const MAX_SPEECH_CHUNK = 160

const voiceActive = ref(false)
const speakingId = ref(null)

let recognition = null
let voiceBaseText = ''
let voiceManualStop = false
let speechToken = 0

const voiceTitle = computed(() => {
    if (!speechRecognitionSupported) return '当前浏览器不支持语音输入，建议使用 Chrome 或 Edge'
    return voiceActive.value ? '停止语音输入' : '语音输入'
})

function toggleAiVoice() {
    if (voiceActive.value) stopAiVoice()
    else startAiVoice()
}

/** 语音识别结果实时写入输入框：最终结果累加，临时结果只做展示。 */
function startAiVoice() {
    if (!SpeechRecognitionClass || voiceActive.value) return
    stopAiSpeaking()
    const instance = new SpeechRecognitionClass()
    instance.lang = 'zh-CN'
    instance.continuous = true
    instance.interimResults = true
    voiceBaseText = aiInput.value
    voiceManualStop = false
    instance.onresult = (event) => {
        let finalText = ''
        let interimText = ''
        for (let i = event.resultIndex; i < event.results.length; i += 1) {
            const result = event.results[i]
            const transcript = result?.[0]?.transcript || ''
            if (!transcript) continue
            if (result.isFinal) finalText += transcript
            else interimText += transcript
        }
        if (finalText) voiceBaseText = appendAiDictation(voiceBaseText, finalText)
        aiInput.value = voiceBaseText + interimText
        nextTick(() => {
            autoResizeAi()
            aiInputEl.value?.focus()
        })
    }
    instance.onerror = (event) => {
        if (event.error === 'aborted') return
        voiceManualStop = true
        voiceActive.value = false
        recognition = null
        const message = VOICE_ERRORS[event.error]
        if (message) ElMessage.warning(message)
    }
    instance.onend = () => {
        if (voiceManualStop || !voiceActive.value) {
            voiceActive.value = false
            return
        }
        // 浏览器在静音后会自动结束识别，未手动停止时继续监听
        voiceBaseText = aiInput.value
        try {
            instance.start()
        } catch (e) {
            voiceActive.value = false
        }
    }
    try {
        instance.start()
        recognition = instance
        voiceActive.value = true
    } catch (e) {
        ElMessage.warning('语音输入启动失败，请稍后重试')
    }
}

function stopAiVoice() {
    voiceManualStop = true
    voiceActive.value = false
    recognition?.stop()
    recognition = null
}

/** 中英文混说时在英文单词之间补空格，中文直接拼接。 */
function appendAiDictation(base, text) {
    if (!base) return text
    return /[A-Za-z0-9]$/.test(base) && /^[A-Za-z0-9]/.test(text) ? `${base} ${text}` : base + text
}

function speakAiMessage(message) {
    if (!ttsSupported || !message.text) return
    if (speakingId.value === message.id) {
        stopAiSpeaking()
        return
    }
    const text = toSpeakableText(message.text)
    if (!text) return
    stopAiSpeaking()
    const token = ++speechToken
    speakingId.value = message.id
    const chunks = splitForSpeech(text)
    // cancel 后立即 speak 在部分浏览器会被忽略，延后一帧再入队
    window.setTimeout(() => {
        if (token !== speechToken) return
        const voice = pickChineseVoice()
        chunks.forEach((chunk, index) => {
            const utterance = new SpeechSynthesisUtterance(chunk)
            if (voice) utterance.voice = voice
            utterance.lang = voice?.lang || 'zh-CN'
            utterance.rate = 1
            if (index === chunks.length - 1) {
                utterance.onend = () => {
                    if (token === speechToken) speakingId.value = null
                }
            }
            utterance.onerror = () => {
                if (token === speechToken) speakingId.value = null
            }
            window.speechSynthesis.speak(utterance)
        })
    }, 0)
}

function stopAiSpeaking() {
    speechToken += 1
    speakingId.value = null
    if (ttsSupported) window.speechSynthesis.cancel()
}

function pickChineseVoice() {
    const voices = window.speechSynthesis.getVoices()
    return voices.find((voice) => /^zh[-_]cn/i.test(voice.lang))
        || voices.find((voice) => /^zh/i.test(voice.lang))
        || null
}

// 播报前去掉 Markdown 标记、代码块、链接地址与表格，只保留自然语言
function toSpeakableText(markdown) {
    return markdown
        .replace(/```[\s\S]*?```/g, ' ')
        .replace(/~~~[\s\S]*?~~~/g, ' ')
        .replace(/!\[[^\]]*]\([^)]*\)/g, ' ')
        .replace(/\[([^\]]*)]\([^)]*\)/g, '$1')
        .replace(/^\s*\|.*\|\s*$/gm, '')
        .replace(/^\s*[-*+]\s+/gm, '')
        .replace(/^\s*\d+\.\s+/gm, '')
        .replace(/^\s*#{1,6}\s*/gm, '')
        .replace(/^\s*>\s?/gm, '')
        .replace(/^\s*([-*_])\1{2,}\s*$/gm, '')
        .replace(/[*_~`]/g, '')
        .replace(/<[^>]+>/g, '')
        .replace(/&nbsp;/g, ' ')
        .replace(/\n{2,}/g, '\n')
        .trim()
}

// 长回复按句子切成小段依次播报，避免部分浏览器截断超长文本
function splitForSpeech(text) {
    const sentences = text.match(/[^。！？!?；;\n]+[。！？!?；;\n]?/g) || [text]
    const chunks = []
    let current = ''
    sentences.forEach((sentence) => {
        const piece = sentence.trim()
        if (!piece) return
        if (current && current.length + piece.length > MAX_SPEECH_CHUNK) {
            chunks.push(current)
            current = ''
        }
        if (piece.length <= MAX_SPEECH_CHUNK) {
            current += piece
            return
        }
        for (let i = 0; i < piece.length; i += MAX_SPEECH_CHUNK) {
            const slice = piece.slice(i, i + MAX_SPEECH_CHUNK)
            if (slice.length === MAX_SPEECH_CHUNK) chunks.push(slice)
            else current = slice
        }
    })
    if (current) chunks.push(current)
    return chunks
}

// 轻量对话 → 完整对话：把会话、消息、未发送的输入与附件、待确认操作一并交接过去
function handoffToFullChat() {
    const hasContent = aiMessages.value.length > 0 || aiInput.value.trim().length > 0
        || aiAttachments.value.length > 0 || !!aiConversationId
    if (!hasContent) return
    // 生成中的回复先停止，已生成的部分随消息一起带去完整对话页
    if (aiStreaming.value) stopAi()
    aiChatHandoff.setPayload({
        conversationId: aiConversationId,
        // 深拷贝脱离面板响应式状态，之后面板重置不会影响已交接的内容
        messages: JSON.parse(JSON.stringify(aiMessages.value)),
        input: aiInput.value,
        attachments: aiAttachments.value
            .filter((file) => file.fileId && !file.uploading && !file.error)
            .map((file) => ({ ...file })),
        confirm: aiPendingConfirm ? JSON.parse(JSON.stringify(aiPendingConfirm)) : null
    })
    // 内容已交给完整对话页，面板重置为新对话，避免两处同时续写同一会话
    newAiChat()
}

// 打开完整对话页：与菜单打开页面行为一致
function openFullChat() {
    handoffToFullChat()
    closeAll()
    const menu = { name: 'AI 对话', url: AI_CHAT_URL, type: 'PAGE', renderType: 'native' }
    if (currentPageStore.currentComp === 'SystemMenuAllMenu') {
        currentPageStore.justCloseUserMenu()
    }
    currentPageStore.showPage(menu)
    currentPageStore.setShowIndex(false)
    router.push({ path: '/main' })
}

onMounted(() => {
    window.addEventListener('resize', onResize)
    document.addEventListener('mousedown', onDocumentPointerDown)
    document.addEventListener('keydown', onDocumentKeydown)
    // 拖放结束（无论是否落在输入框、上传是否成功）都清掉虚线高亮
    document.addEventListener('drop', resetAiDragState)
    document.addEventListener('dragend', resetAiDragState)
})

onBeforeUnmount(() => {
    window.removeEventListener('resize', onResize)
    document.removeEventListener('mousedown', onDocumentPointerDown)
    document.removeEventListener('keydown', onDocumentKeydown)
    document.removeEventListener('drop', resetAiDragState)
    document.removeEventListener('dragend', resetAiDragState)
    window.removeEventListener('pointermove', onPointerMove)
    window.removeEventListener('pointerup', onPointerUp)
    window.removeEventListener('pointercancel', onPointerUp)
    window.removeEventListener('pointermove', onAiResizeMove)
    window.removeEventListener('pointerup', onAiResizeUp)
    window.removeEventListener('pointercancel', onAiResizeUp)
    window.removeEventListener('pointermove', onAiHeaderPointerMove)
    window.removeEventListener('pointerup', onAiHeaderPointerUp)
    window.removeEventListener('pointercancel', onAiHeaderPointerUp)
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
    stopAiVoice()
    stopAiSpeaking()
    aiAbort?.abort()
    if (aiHistoryTimer) clearTimeout(aiHistoryTimer)
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

/* ---------- 菜单搜索 ---------- */
.gs-panel {
    position: absolute;
    top: 0;
    width: 0;
    height: 40px;
    opacity: 0;
    overflow: hidden;
    background: #fff;
    border-radius: 20px;
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.16);
    transition: width 0.45s cubic-bezier(0.25, 0.8, 0.35, 1), opacity 0.3s ease;
    pointer-events: none;
}

.gs-root.is-expanded .gs-panel {
    width: var(--gs-panel-w, 300px);
    opacity: 1;
    pointer-events: auto;
}

.gs-panel.gs-right {
    left: 48px;
}

.gs-panel.gs-left {
    right: 48px;
}

.gs-input {
    width: 100%;
    height: 100%;
    box-sizing: border-box;
    padding: 0 16px;
    border: none;
    outline: none;
    background: transparent;
    font-size: 14px;
    color: #303133;
}

.gs-input::placeholder {
    color: #a8abb2;
}

.gs-result {
    position: absolute;
    max-width: calc(100vw - 24px);
    max-height: 60vh;
    overflow-y: auto;
    box-sizing: border-box;
    padding: 6px;
    background: #fff;
    border: 1px solid #ebeef5;
    border-radius: 10px;
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
    animation: gsResultIn 0.15s ease-out;
}

.gs-result.gs-right {
    left: 48px;
}

.gs-result.gs-left {
    right: 48px;
}

.gs-result.gs-below {
    top: 48px;
}

.gs-result.gs-above {
    bottom: 48px;
}

@keyframes gsResultIn {
    from {
        opacity: 0;
        transform: translateY(-4px);
    }

    to {
        opacity: 1;
        transform: translateY(0);
    }
}

.gs-result-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 4px 10px 8px 10px;
}

.gs-result-title {
    font-size: 13px;
    font-weight: 600;
    color: #303133;
}

.gs-result-count {
    font-size: 12px;
    color: #909399;
}

.gs-result-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 7px 10px;
    border-radius: 6px;
    cursor: pointer;
    transition: background-color 0.15s;
}

.gs-result-item.active {
    background: #e6f2ff;
}

.gs-result-name {
    flex: 1;
    min-width: 0;
    font-size: 13px;
    color: #303133;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.gs-result-item.active .gs-result-name {
    color: #0078d7;
}

.gs-result-name :deep(em.gs-hl) {
    font-style: normal;
    color: #409eff;
    font-weight: 600;
}

.gs-result-module {
    flex-shrink: 0;
    padding: 1px 6px;
    font-size: 11px;
    color: #909399;
    background: #f4f4f5;
    border-radius: 4px;
    white-space: nowrap;
    max-width: 110px;
    overflow: hidden;
    text-overflow: ellipsis;
}

.gs-result-empty {
    padding: 20px 0;
    text-align: center;
    font-size: 13px;
    color: #909399;
}

.gs-result::-webkit-scrollbar {
    width: 6px;
}

.gs-result::-webkit-scrollbar-thumb {
    background: #dcdfe6;
    border-radius: 3px;
}

.gs-result::-webkit-scrollbar-thumb:hover {
    background: #c0c4cc;
}

.gs-result::-webkit-scrollbar-track {
    background: transparent;
}

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

/* ---------- 历史会话 ---------- */
.gs-ai-history {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
    padding: 14px 14px 12px;
    background:
        radial-gradient(120% 50% at 50% 0%, rgba(79, 124, 255, 0.05) 0%, rgba(79, 124, 255, 0) 70%),
        #f6f7fb;
}

.gs-ai-history-head {
    flex: 0 0 auto;
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-bottom: 10px;
}

.gs-ai-history-title {
    font-size: 14px;
    font-weight: 600;
    color: #232a38;
}

.gs-ai-history-search {
    flex: 0 0 auto;
    margin-bottom: 10px;
}

.gs-ai-history-search :deep(.el-input__wrapper) {
    border-radius: 10px;
    background: #fff;
    box-shadow: 0 0 0 1px #e8ecf5 inset;
}

.gs-ai-history-list {
    position: relative;
    flex: 1;
    min-height: 60px;
    overflow-y: auto;
    overscroll-behavior: contain;
}

.gs-ai-history-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 9px 10px;
    margin-bottom: 2px;
    border-radius: 10px;
    color: #4e5969;
    font-size: 12.5px;
    cursor: pointer;
    transition: background-color 0.15s, color 0.15s, box-shadow 0.15s;
}

.gs-ai-history-item:hover {
    background: #eef3ff;
}

.gs-ai-history-item.active {
    background: #fff;
    color: #3b5bdb;
    box-shadow: 0 2px 10px -4px rgba(20, 30, 60, 0.14);
}

.gs-ai-history-item-title {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.gs-ai-history-item-time {
    flex: 0 0 auto;
    font-size: 10.5px;
    color: #98a1b3;
}

.gs-ai-history-empty {
    padding: 24px 0;
    text-align: center;
    font-size: 12px;
    color: #98a1b3;
}

.gs-ai-history-pagination {
    flex: 0 0 auto;
    display: flex;
    justify-content: center;
    padding-top: 6px;
}

.gs-ai-history-list::-webkit-scrollbar {
    width: 6px;
}

.gs-ai-history-list::-webkit-scrollbar-thumb {
    background-color: rgba(148, 163, 184, 0.24);
    border-radius: 3px;
}

.gs-ai-history-list::-webkit-scrollbar-thumb:hover {
    background-color: rgba(148, 163, 184, 0.45);
}

.gs-ai-history-list::-webkit-scrollbar-track {
    background: transparent;
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

.gs-ai-msg {
    display: flex;
    margin-bottom: 14px;
    animation: gsAiMsgIn 0.22s ease-out both;
}

@keyframes gsAiMsgIn {
    from {
        opacity: 0;
        transform: translateY(6px);
    }

    to {
        opacity: 1;
        transform: translateY(0);
    }
}

.gs-ai-msg:last-child {
    margin-bottom: 0;
}

.gs-ai-msg.user {
    justify-content: flex-end;
}

.gs-ai-bubble {
    box-sizing: border-box;
    padding: 7px 11px;
    border-radius: 14px;
    font-size: 13.5px;
    line-height: 1.68;
    white-space: pre-wrap;
    word-break: break-word;
}

.gs-ai-msg.user .gs-ai-bubble {
    max-width: 86%;
    padding: 9px 14px;
    border-radius: 16px 16px 6px 16px;
    background: linear-gradient(135deg, #eef3ff 0%, #f1ecff 100%);
    font-size: 13.5px;
    line-height: 1.6;
    color: #26324d;
    border: 1px solid rgba(79, 124, 255, 0.14);
    box-shadow: 0 1px 2px rgba(16, 24, 48, 0.03), 0 8px 20px -22px rgba(16, 24, 48, 0.18);
}

.gs-ai-msg.assistant .gs-ai-bubble {
    max-width: 100%;
    padding: 11px 13px 12px;
    background: #fff;
    color: #2f3542;
    border: 1px solid rgba(228, 233, 244, 0.95);
    border-radius: 16px 16px 16px 6px;
    box-shadow: 0 1px 2px rgba(16, 24, 48, 0.03), 0 12px 28px -22px rgba(16, 24, 48, 0.22);
    /* 助手气泡由块级元素拼装，需取消 pre-wrap，否则模板换行会撑出空行 */
    white-space: normal;
}

.gs-ai-bubble.is-error {
    border-color: #f3d5d5;
}

/* 含结构化视图的回复占满面板宽度，避免表格被气泡收缩 */
.gs-ai-bubble.has-view {
    width: 100%;
    max-width: 100%;
}

.gs-ai-text {
    white-space: normal;
    overflow-x: auto;
    word-break: break-word;
}

/* 回复正文的 Markdown 样式 */
.gs-ai-text :deep(p) {
    margin: 0 0 8px;
}

.gs-ai-text :deep(p:last-child) {
    margin-bottom: 0;
}

.gs-ai-text :deep(ul),
.gs-ai-text :deep(ol) {
    margin: 4px 0 6px;
    padding-left: 18px;
}

.gs-ai-text :deep(li) {
    margin: 2px 0;
}

.gs-ai-text :deep(h1),
.gs-ai-text :deep(h2),
.gs-ai-text :deep(h3),
.gs-ai-text :deep(h4),
.gs-ai-text :deep(h5),
.gs-ai-text :deep(h6) {
    margin: 10px 0 5px;
    font-weight: 600;
    line-height: 1.4;
}

.gs-ai-text :deep(h1) {
    font-size: 16px;
}

.gs-ai-text :deep(h2) {
    font-size: 15px;
}

.gs-ai-text :deep(h3),
.gs-ai-text :deep(h4),
.gs-ai-text :deep(h5),
.gs-ai-text :deep(h6) {
    font-size: 14px;
}

.gs-ai-text :deep(code) {
    padding: 1px 4px;
    border-radius: 4px;
    background: #f2f4f8;
    font-size: 12px;
    font-family: Consolas, Monaco, monospace;
}

.gs-ai-text :deep(pre) {
    margin: 8px 0;
    padding: 10px 12px;
    background: #f7f9fd;
    border: 1px solid #edf1f8;
    border-radius: 10px;
    overflow-x: auto;
}

.gs-ai-text :deep(pre code) {
    padding: 0;
    background: transparent;
}

.gs-ai-text :deep(blockquote) {
    margin: 6px 0;
    padding: 2px 10px;
    border-left: 3px solid #dbe3f0;
    color: #6b7280;
}

.gs-ai-text :deep(table) {
    width: 100%;
    margin: 8px 0;
    border: 1px solid #e8edf7;
    border-collapse: separate;
    border-spacing: 0;
    border-radius: 10px;
    overflow: hidden;
    background: #fff;
    font-size: 12.5px;
}

.gs-ai-text :deep(th),
.gs-ai-text :deep(td) {
    padding: 7px 10px;
    border: none;
    border-bottom: 1px solid #eff2f8;
    text-align: left;
}

.gs-ai-text :deep(tr:last-child td) {
    border-bottom: none;
}

.gs-ai-text :deep(th) {
    background: #f7f9fd;
    color: #5b6579;
    font-weight: 600;
}

.gs-ai-text :deep(tbody tr:nth-child(even) td) {
    background: #fbfcfe;
}

.gs-ai-text :deep(a) {
    color: #2f6bff;
    text-decoration: none;
}

.gs-ai-text :deep(img) {
    max-width: 100%;
}

.gs-ai-text :deep(hr) {
    margin: 8px 0;
    border: none;
    border-top: 1px solid #edf1f8;
}

.gs-ai-text :deep(.mermaid-block) {
    margin: 6px 0;
    padding: 8px;
    background: #fff;
    border: 1px solid #edf1f8;
    border-radius: 8px;
    overflow-x: auto;
    text-align: center;
}

.gs-ai-text :deep(.mermaid-block.mermaid-error) {
    border-color: #f3d5d5;
}

.gs-ai-text :deep(.mermaid-block svg) {
    max-width: 100%;
    height: auto;
}

.gs-ai-text :deep(.mermaid-block .mermaid-source) {
    margin: 0;
    padding: 0;
    border: none;
    background: transparent;
    text-align: left;
}

.gs-ai-view {
    margin-top: 6px;
}

.gs-ai-pending {
    margin-top: 6px;
    color: #8b95a7;
    font-size: 12px;
}

/* 思考过程：收起时是一条带左侧强调线的轻量标签，展开后成为浅色卡片 */
.gs-ai-thinking {
    margin-bottom: 7px;
    padding: 2px 0 2px 10px;
    border-left: 2px solid #e1e8f8;
    border-radius: 0;
    transition: border-color 0.16s, background-color 0.16s;
}

.gs-ai-thinking.is-open {
    padding: 8px 10px 9px 11px;
    border: 1px solid #e9edf9;
    border-left: 2px solid #c3d1ff;
    border-radius: 11px;
    background: linear-gradient(180deg, #f7f9ff 0%, #f4f6fd 100%);
}

.gs-ai-thinking-head {
    display: flex;
    align-items: center;
    gap: 6px;
    width: 100%;
    padding: 0;
    border: none;
    background: transparent;
    color: #8b95a7;
    font-size: 11.5px;
    font-weight: 500;
    font-family: inherit;
    cursor: pointer;
    transition: color 0.15s;
}

.gs-ai-thinking-head:hover {
    color: #40527f;
}

.gs-ai-thinking-arrow {
    font-size: 11px;
    transition: transform 0.15s;
}

.gs-ai-thinking-arrow.is-open {
    transform: rotate(90deg);
}

.gs-ai-thinking-live {
    margin-left: auto;
    color: #4f7cff;
}

.gs-ai-thinking-text {
    margin: 8px 0 0;
    max-height: 135px;
    overflow-y: auto;
    white-space: pre-wrap;
    word-break: break-word;
    font-size: 11.5px;
    line-height: 1.7;
    color: #6b7488;
    font-family: inherit;
}

/* 面板内的接口调用结果：紧凑分组，避免与正文混排 */
.gs-ai-tool {
    min-width: 0;
    margin-bottom: 9px;
    padding: 8px 10px;
    background: linear-gradient(180deg, #f8fafe 0%, #f5f8fd 100%);
    border: 1px solid #e9eef9;
    border-radius: 11px;
    transition: border-color 0.16s, background-color 0.16s;
}

.gs-ai-tool.is-failed {
    border-color: #f6dada;
    background: #fffafa;
}

.gs-ai-tool-head {
    display: flex;
    align-items: center;
    gap: 6px;
    width: 100%;
    padding: 0;
    border: 0;
    background: transparent;
    font-family: inherit;
    text-align: left;
    cursor: pointer;
}

.gs-ai-tool-arrow {
    flex: 0 0 auto;
    font-size: 11px;
    color: #97a1b6;
    transition: transform 0.15s;
}

.gs-ai-tool-arrow.is-open {
    transform: rotate(90deg);
}

.gs-ai-tool-name {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    color: #33405a;
    font-size: 12.5px;
    font-weight: 600;
    line-height: 1.5;
    transition: color 0.15s;
}

.gs-ai-tool-head:hover .gs-ai-tool-name {
    color: #3b5bdb;
}

.gs-ai-tool-flag {
    flex: 0 0 auto;
    margin-left: auto;
    padding: 0 7px;
    border-radius: 999px;
    background: #fdecec;
    color: #d9483f;
    font-size: 10.5px;
    line-height: 17px;
}

.gs-ai-tool-body {
    margin-top: 6px;
}

.gs-ai-tool-msg {
    margin-top: 0;
    color: #6b7280;
    font-size: 11px;
    line-height: 1.6;
}

.gs-ai-tool-msg.is-error {
    color: #d9534f;
}

/* 用户消息里的附件：底部右对齐的紧凑胶囊 */
.gs-ai-msg-files {
    display: flex;
    flex-wrap: wrap;
    justify-content: flex-end;
    gap: 6px;
    margin-top: 8px;
}

.gs-ai-msg-files:first-child {
    margin-top: 0;
}

.gs-ai-msg-file {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    max-width: 220px;
    height: 26px;
    padding: 0 9px;
    border-radius: 999px;
    background: rgba(255, 255, 255, 0.72);
    border: 1px solid rgba(79, 124, 255, 0.16);
    font-size: 12px;
    color: #3f4a63;
    transition: border-color 0.16s, background-color 0.16s, box-shadow 0.16s;
}

.gs-ai-msg-file.is-link {
    cursor: pointer;
}

.gs-ai-msg-file.is-link:hover {
    background: #fff;
    border-color: rgba(79, 124, 255, 0.4);
    box-shadow: 0 4px 12px -6px rgba(79, 124, 255, 0.45);
}

.gs-ai-msg-file .el-icon {
    flex: 0 0 auto;
    font-size: 13px;
    color: #4f7cff;
}

.gs-ai-msg-file-name {
    flex: 0 1 auto;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.gs-ai-msg-file-size {
    flex: 0 0 auto;
    font-size: 11px;
    color: #9aa3b5;
}

/* 附件页内预览：半透明无标题栏浮层 */
.gs-preview {
    position: fixed;
    inset: 0;
    z-index: 10;
    box-sizing: border-box;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 5vh 5vw;
    background: rgba(15, 23, 42, 0.55);
    backdrop-filter: blur(2px);
}

.gs-preview-close {
    position: absolute;
    top: 20px;
    right: 24px;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 36px;
    height: 36px;
    padding: 0;
    border: none;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.14);
    color: #fff;
    font-size: 18px;
    cursor: pointer;
    transition: background-color 0.16s;
}

.gs-preview-close:hover {
    background: rgba(255, 255, 255, 0.28);
}

/* PDF / 文本内容：浮在遮罩上的白纸，无标题栏 */
.gs-preview-paper {
    display: flex;
    box-sizing: border-box;
    width: min(1000px, 100%);
    height: 100%;
    background: #fff;
    border-radius: 10px;
    overflow: hidden;
    box-shadow: 0 30px 80px -30px rgba(0, 0, 0, 0.6);
}

.gs-preview-paper iframe {
    width: 100%;
    height: 100%;
    border: 0;
    background: #fff;
}

.gs-preview-paper pre {
    width: 100%;
    height: 100%;
    margin: 0;
    padding: 20px 22px;
    box-sizing: border-box;
    overflow: auto;
    color: #33405c;
    font-size: 12.5px;
    line-height: 1.7;
    white-space: pre-wrap;
    word-break: break-word;
}

.gs-preview-tip {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8px;
    color: rgba(255, 255, 255, 0.82);
    font-size: 13px;
}

.gs-preview-tip .el-icon {
    font-size: 30px;
    color: rgba(255, 255, 255, 0.5);
}

.gs-preview-tip p {
    margin: 0;
}

.gs-preview-enter-active,
.gs-preview-leave-active {
    transition: opacity 0.18s ease;
}

.gs-preview-enter-from,
.gs-preview-leave-to {
    opacity: 0;
}

/* 回复播报按钮 */
.gs-ai-speak {
    margin-top: 10px;
    height: 24px;
    padding: 0 10px;
    display: inline-flex;
    align-items: center;
    gap: 4px;
    border: 1px solid #e6ebf5;
    border-radius: 999px;
    background: #fafbfe;
    color: #8892a6;
    font-size: 11px;
    font-family: inherit;
    cursor: pointer;
    transition: border-color 0.15s, color 0.15s, background-color 0.15s;
}

.gs-ai-speak .el-icon {
    font-size: 12px;
}

.gs-ai-speak:hover,
.gs-ai-speak.is-active {
    border-color: #b9c8f2;
    background: #fff;
    color: #3b5bdb;
}

.gs-ai-typing {
    display: flex;
    align-items: center;
    gap: 4px;
    height: 18px;
}

.gs-ai-typing span {
    width: 5px;
    height: 5px;
    border-radius: 50%;
    background: #9aa7bd;
    animation: gsAiTyping 1.2s infinite ease-in-out;
}

.gs-ai-typing span:nth-child(2) {
    animation-delay: 0.15s;
}

.gs-ai-typing span:nth-child(3) {
    animation-delay: 0.3s;
}

@keyframes gsAiTyping {

    0%,
    60%,
    100% {
        opacity: 0.35;
        transform: translateY(0);
    }

    30% {
        opacity: 1;
        transform: translateY(-3px);
    }
}

.gs-ai-tool-tip {
    margin-top: 6px;
    display: flex;
    align-items: center;
    gap: 5px;
    font-size: 11px;
    color: #8b95a7;
}

.gs-ai-tool-tip::before {
    content: '';
    width: 5px;
    height: 5px;
    border-radius: 50%;
    background: #7aa7ff;
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

.gs-ai-error {
    margin-top: 6px;
    font-size: 12px;
    line-height: 1.5;
    color: #d9534f;
}

.gs-ai-jump {
    margin-top: 8px;
    padding: 0;
    display: inline-flex;
    align-items: center;
    gap: 3px;
    border: none;
    background: transparent;
    color: #2f6bff;
    font-size: 12px;
    cursor: pointer;
}

.gs-ai-jump:hover {
    text-decoration: underline;
}

.gs-ai-composer {
    flex: 0 0 auto;
    padding: 12px 14px 10px;
    background: linear-gradient(180deg, #ffffff 0%, #fafbff 100%);
    border-top: 1px solid #eef1f8;
    box-shadow: 0 -12px 30px -24px rgba(16, 24, 48, 0.28);
}

/* 待发送附件 */
.gs-ai-attach-tray {
    display: flex;
    gap: 6px;
    overflow-x: auto;
    padding-bottom: 7px;
}

.gs-ai-attach {
    flex: 0 0 200px;
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 6px 8px;
    background: #fff;
    border: 1px solid #eef1f6;
    border-radius: 10px;
}

.gs-ai-attach.is-failed {
    border-color: #f3d5d5;
}

.gs-ai-attach-icon {
    flex: 0 0 auto;
    width: 26px;
    height: 26px;
    border-radius: 7px;
    background: #eaf2ff;
    color: #3c66c4;
    display: flex;
    align-items: center;
    justify-content: center;
}

.gs-ai-attach-icon .el-icon {
    font-size: 14px;
}

.gs-ai-attach-main {
    flex: 1;
    min-width: 0;
}

.gs-ai-attach-name {
    font-size: 12px;
    color: #303540;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.gs-ai-attach-meta {
    font-size: 11px;
    color: #98a1b3;
}

.gs-ai-attach-error {
    color: #d9534f;
}

.gs-ai-attach-retry {
    flex: 0 0 auto;
    padding: 0 2px;
    border: none;
    background: transparent;
    color: #2f6bff;
    font-size: 12px;
    cursor: pointer;
}

.gs-ai-attach-remove {
    flex: 0 0 auto;
    width: 20px;
    height: 20px;
    padding: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    border: none;
    border-radius: 6px;
    background: transparent;
    color: #98a1b3;
    cursor: pointer;
}

.gs-ai-attach-remove:hover {
    background: #f2f4f8;
    color: #5f6673;
}

/* 语音输入按钮 */
.gs-ai-voice-btn {
    flex: 0 0 auto;
    width: 28px;
    height: 28px;
    margin-bottom: 2px;
    padding: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    border: none;
    border-radius: 9px;
    background: transparent;
    color: #7d8698;
    cursor: pointer;
    transition: background-color 0.15s, color 0.15s;
}

.gs-ai-voice-btn .el-icon {
    font-size: 16px;
}

.gs-ai-voice-btn:hover:not(:disabled) {
    background: rgba(79, 124, 255, 0.09);
    color: #3b5bdb;
}

.gs-ai-voice-btn:disabled {
    color: #c8cdd6;
    cursor: not-allowed;
}

.gs-ai-voice-btn.is-recording {
    background: #ffece8;
    color: #f53f3f;
    animation: gsAiVoicePulse 1.4s infinite;
}

@keyframes gsAiVoicePulse {
    0% {
        box-shadow: 0 0 0 0 rgba(245, 63, 63, 0.35);
    }

    70% {
        box-shadow: 0 0 0 6px rgba(245, 63, 63, 0);
    }

    100% {
        box-shadow: 0 0 0 0 rgba(245, 63, 63, 0);
    }
}

.gs-ai-voice-live {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-top: 6px;
    font-size: 11px;
    color: #f53f3f;
}

.gs-ai-voice-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: #f53f3f;
    animation: gsAiPulse 1s infinite ease-in-out;
}

.gs-ai-hidden-input {
    display: none;
}

/* 页面上下文开关 */
.gs-ai-ctx-bar {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-bottom: 8px;
    min-height: 24px;
}

.gs-ai-ctx {
    flex: 0 0 auto;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 24px;
    height: 24px;
    padding: 0;
    border: 1px solid #e4e9f3;
    border-radius: 7px;
    background: rgba(255, 255, 255, 0.85);
    color: #7d8698;
    font-size: 11px;
    cursor: pointer;
    transition: border-color 0.15s, background-color 0.15s, color 0.15s, box-shadow 0.15s;
}

.gs-ai-ctx .el-icon {
    font-size: 14px;
}

.gs-ai-ctx:hover {
    border-color: #c5d2f5;
    background: #fff;
    color: #3b5bdb;
}

.gs-ai-ctx.is-on {
    border-color: transparent;
    background: linear-gradient(135deg, #4f7cff 0%, #7b6bff 100%);
    color: #fff;
    box-shadow: 0 4px 10px -2px rgba(79, 124, 255, 0.45);
}

.gs-ai-ctx-hint {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    text-align: right;
    font-size: 11px;
    color: #9aa3b5;
}

.gs-ai-input-row {
    display: flex;
    align-items: flex-end;
    gap: 2px;
    padding: 8px 8px 8px 10px;
    border: 1px solid #e3e8f3;
    border-radius: 14px;
    background: #f5f7fc;
    transition: border-color 0.18s, background-color 0.18s, box-shadow 0.18s;
}

.gs-ai-input-row:focus-within {
    border-color: #b3c3ff;
    background: #fff;
    box-shadow: 0 0 0 3px rgba(99, 91, 255, 0.1);
}

/* 文件拖到聊天内容输入框时的高亮反馈 */
.gs-ai-input-row.is-drop-active {
    border-style: dashed;
    border-color: #4f7cff;
    background: #eef3ff;
    box-shadow: 0 0 0 3px rgba(79, 124, 255, 0.12);
}

.gs-ai-drop-tip {
    display: flex;
    align-items: center;
    gap: 6px;
    padding-bottom: 7px;
    font-size: 11px;
    color: #2f6bff;
}

.gs-ai-drop-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: #4f7cff;
    animation: gsAiPulse 1s infinite ease-in-out;
}

.gs-ai-input {
    flex: 1;
    min-width: 0;
    min-height: 21px;
    max-height: 96px;
    /* 单行时与左右按钮共用同一条中线，多行时随输入框增高保持底部对齐 */
    align-self: center;
    padding: 0 2px;
    border: none;
    outline: none;
    resize: none;
    overflow-y: auto;
    background: transparent;
    font-family: inherit;
    font-size: 13.5px;
    line-height: 21px;
    color: #2f3542;
}

.gs-ai-input::placeholder {
    color: #a4adbe;
}

.gs-ai-send {
    flex: 0 0 auto;
    width: 32px;
    height: 32px;
    padding: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    border: none;
    border-radius: 10px;
    background: linear-gradient(135deg, #4f7cff 0%, #7b6bff 100%);
    color: #fff;
    cursor: pointer;
    box-shadow: 0 6px 14px -6px rgba(91, 92, 255, 0.65);
    transition: transform 0.15s, box-shadow 0.15s, background-color 0.15s;
}

.gs-ai-send .el-icon {
    font-size: 16px;
}

.gs-ai-send:hover:not(:disabled) {
    transform: translateY(-1px);
    box-shadow: 0 10px 20px -8px rgba(91, 92, 255, 0.75);
}

.gs-ai-send:disabled {
    background: #d3d9e4;
    color: #fff;
    box-shadow: none;
    cursor: not-allowed;
}

.gs-ai-send.is-stop {
    background: #f0f2f6;
    color: #5f6673;
    box-shadow: none;
}

.gs-ai-send.is-stop:hover {
    background: #e6e9ef;
    transform: none;
}

.gs-ai-foot {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    padding: 5px 2px 0;
    font-size: 10.5px;
    color: #a4adbe;
}

.gs-ai-foot-hint {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    white-space: nowrap;
}

.gs-ai-foot-hint span {
    margin: 0 -1px;
}

.gs-ai-foot kbd {
    display: inline-flex;
    align-items: center;
    height: 17px;
    padding: 0 5px;
    border: 1px solid #e7ebf3;
    border-bottom-width: 2px;
    border-radius: 5px;
    background: #f4f6fa;
    color: #8a93a6;
    font-family: inherit;
    font-size: 10px;
    line-height: 1;
}

.gs-ai-foot-brand {
    flex: 0 0 auto;
    font-weight: 600;
    letter-spacing: 0.4px;
    background: linear-gradient(90deg, #7b87a6 0%, #9b8cf0 100%);
    -webkit-background-clip: text;
    background-clip: text;
    color: transparent;
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

.gs-ai-body::-webkit-scrollbar,
.gs-ai-thinking-text::-webkit-scrollbar,
.gs-ai-input::-webkit-scrollbar {
    width: 6px;
}

.gs-ai-body::-webkit-scrollbar-thumb,
.gs-ai-thinking-text::-webkit-scrollbar-thumb,
.gs-ai-input::-webkit-scrollbar-thumb {
    background-color: rgba(148, 163, 184, 0.24);
    border-radius: 3px;
}

.gs-ai-body::-webkit-scrollbar-thumb:hover,
.gs-ai-thinking-text::-webkit-scrollbar-thumb:hover,
.gs-ai-input::-webkit-scrollbar-thumb:hover {
    background-color: rgba(148, 163, 184, 0.45);
}

.gs-ai-body::-webkit-scrollbar-track,
.gs-ai-thinking-text::-webkit-scrollbar-track,
.gs-ai-input::-webkit-scrollbar-track {
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
