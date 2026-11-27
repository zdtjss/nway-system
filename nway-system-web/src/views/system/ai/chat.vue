<template>
  <div class="ai-chat"
       @dragover.prevent="onDragOver" @dragleave.prevent="onDragLeave" @drop.prevent="onDrop">
    <div v-if="dragActive" class="drag-mask">
      <div class="drag-mask-inner">松开上传附件（最多 5 个，单个不超过 20MB）</div>
    </div>

    <main class="chat-main">
      <header class="chat-header">
        <div class="chat-header-left">
          <div class="chat-avatar">
            <el-icon><ChatDotRound /></el-icon>
          </div>
          <div class="chat-heading">
            <div class="chat-title">AI 助手</div>
            <div class="chat-subtitle">
              <span class="chat-status"></span>
              办公问答 · 附件解析 · 业务办理
            </div>
          </div>
        </div>
        <button type="button" class="chat-export" :disabled="!activeId || streaming || exporting"
                title="导出当前会话记录" @click="exportCurrentConversation">
          <el-icon><Download /></el-icon>
          <span>导出对话</span>
        </button>
      </header>

      <div ref="scrollEl" class="message-scroll" @scroll="trackFollowScroll">
        <div class="message-inner">
          <div v-if="messages.length === 0" class="empty-state">
            <div class="empty-logo"><el-icon><ChatDotRound /></el-icon></div>
            <h2>有什么可以帮你？</h2>
            <p class="empty-sub">可以回答业务问题、解析附件内容；已接入的业务接口可直接查询或发起办理，提交前会请你确认。</p>
            <div v-if="quickPrompts.length" class="quick-list">
              <button v-for="prompt in quickPrompts" :key="prompt.id" class="quick-item"
                      @click="usePrompt(prompt)">
                {{ prompt.name }}
              </button>
            </div>
          </div>

          <AiChatMessage v-for="msg in messages" :key="msg.id" :msg="msg" :thinking-open="thinkingOpen"
                         :tool-collapsed="toolCollapsed" :speaking-id="speakingId" :tts-supported="ttsSupported"
                         @toggle-thinking="toggleThinking" @toggle-tool="toggleTool" @speak="speakAiMessage"
                         @preview-file="previewFile" @scroll-follow="trackFollowScroll" />
        </div>
      </div>

      <AiChatComposer ref="composerRef" v-model:input="input" v-model:attachments="attachments"
                      v-model:selected-model-id="selectedModelId" :streaming="streaming"
                      :stream-cancelable="streamCancelable" :can-send="canSend" :voice-active="voiceActive"
                      :voice-title="voiceTitle" :speech-supported="speechRecognitionSupported"
                      :chat-models="chatModels" :auto-speak="autoSpeak" :auto-speak-tips="autoSpeakTips"
                      :tts-supported="ttsSupported" @send="send" @stop="stop" @new-chat="newChat"
                      @toggle-voice="toggleAiVoice" @toggle-auto-speak="toggleAutoSpeak">
        <template #toolbar-extra>
          <AiPromptManager :prompts="prompts" :quick-prompt-ids="quickPromptIds" :active-id="activeId"
                           :extracting="extracting" @use="usePrompt" @edit="editPrompt"
                           @delete="removePrompt" @create="startNewPrompt"
                           @extract="extractFromConversation" @toggle-quick="toggleQuickPrompt"
                           @open="loadPrompts" />
          <AiHistoryPopover v-model:visible="historyPopover" :keyword="historyKeyword"
                            :loading="historyLoading" :conversations="conversations" :total="historyTotal"
                            :current="historyCurrent" :page-size="historyPageSize" :active-id="activeId"
                            @update:keyword="historyKeyword = $event" @search="searchHistory"
                            @open-conversation="openConversation"
                            @rename="(conv) => onConvCommand('rename', conv)"
                            @delete="(conv) => onConvCommand('delete', conv)"
                            @page-change="onHistoryPageChange" @open="loadHistory" />
        </template>
      </AiChatComposer>
    </main>

    <!-- 写操作确认 -->
    <AiConfirmDialog ref="confirmDialogRef" @submit="onConfirmSubmit" />

    <!-- 提示词编辑 -->
    <AiPromptDialog v-model:visible="promptDialogVisible" :prompt="promptDialogPrompt"
                    :saving="promptSaving" @save="savePersonalPrompt" />

    <el-image-viewer v-if="viewerUrl" :url-list="[viewerUrl]" @close="viewerUrl = ''" />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChatDotRound, Download } from '@element-plus/icons-vue'
import {
  assetUrl, cancelChat, deleteConversation, deletePrompt, exportConversation, extractPrompt, getMessages,
  listChatModels, listPrompts, mergeAssistantMessages, renameConversation, savePrompt, streamChat, streamConfirm,
  updatePrompt,
  type AiChatModelVo, type AiConversationVo, type AiFileVo, type AiMessage, type AiPromptVo, type ChatEvent
} from '@/api/ai'
import AiChatComposer from './components/AiChatComposer.vue'
import AiChatMessage from './components/AiChatMessage.vue'
import AiConfirmDialog from './components/AiConfirmDialog.vue'
import AiHistoryPopover from './components/AiHistoryPopover.vue'
import AiPromptDialog from './components/AiPromptDialog.vue'
import AiPromptManager from './components/AiPromptManager.vue'
import type { ConfirmSubmitPayload, PromptDraft, UiMessage, UiTool } from './components/types'
import { useAiChatHistory } from '@/composables/useAiChatHistory'
import { useAiVoice } from '@/composables/useAiVoice'
import { useAiChatHandoffStore } from '@/stores/aiChatStore'
import {
  appendText, buildToolViews, INVOKE_TOOL, isConfigTool, restoreCompletedTools
} from '@/utils/aiTool'
import { inputFieldLabel, rememberInterfaceConfig, splitViewSegments } from '@/utils/aiView'
import { followBottom, followLast, trackFollowScroll } from '@/utils/scrollFollow'

const QUICK_PROMPT_KEY = 'nway-ai-quick-prompt-ids'
const QUICK_PROMPT_LIMIT = 8
/** 用户选择的对话模型，按人记忆，切换页面后保持 */
const MODEL_KEY = 'nway-ai-chat-model-id'

function readQuickPromptIds(): string[] {
  try {
    const raw = localStorage.getItem(QUICK_PROMPT_KEY)
    const list = raw ? JSON.parse(raw) : []
    if (!Array.isArray(list)) return []
    return list
      .filter((item): item is string => typeof item === 'string')
      .slice(0, QUICK_PROMPT_LIMIT)
  } catch (e) {
    return []
  }
}

/** 快捷提示词只记录提示词 ID，内容始终取自提示词列表，最多 8 条。 */
const quickPromptIds = ref<string[]>(readQuickPromptIds())

const chatModels = ref<AiChatModelVo[]>([])
const selectedModelId = ref(readSelectedModelId())

function readSelectedModelId(): string {
  try {
    return localStorage.getItem(MODEL_KEY) || ''
  } catch (e) {
    return ''
  }
}

function persistSelectedModel() {
  try {
    if (selectedModelId.value) {
      localStorage.setItem(MODEL_KEY, selectedModelId.value)
    } else {
      localStorage.removeItem(MODEL_KEY)
    }
  } catch (e) {
    // 本地存储不可用时仅在当前会话生效
  }
}

/** 可选模型由后端按租户过滤；本地选中的模型失效时回退到默认模型。 */
async function loadChatModels() {
  try {
    const res = await listChatModels()
    chatModels.value = res.data || []
    if (!chatModels.value.some((model) => model.id === selectedModelId.value)) {
      const fallback = chatModels.value.find((model) => model.isDefault) || chatModels.value[0]
      selectedModelId.value = fallback?.id || ''
    }
  } catch (e) {
    // 模型列表不可用时隐藏选择器，后端按默认模型处理
    chatModels.value = []
  }
}

const activeId = ref<string | null>(null)
const messages = ref<UiMessage[]>([])
const input = ref('')
const attachments = ref<Array<AiFileVo & { localId: string; rawFile?: File }>>([])
const streaming = ref(false)
/** 当前流是否允许停止：写操作确认后的执行流不允许打断，避免业务调用执行到一半被取消 */
const streamCancelable = ref(true)
const thinkingOpen = ref(new Set<string>())
/** 工具结果默认展开，点击标题收起 */
const toolCollapsed = ref(new Set<string>())
const scrollEl = ref<HTMLElement | null>(null)
const composerRef = ref<any>(null)
const confirmDialogRef = ref<any>(null)
const dragActive = ref(false)
const viewerUrl = ref('')
let abortController: AbortController | null = null

/** 轻量对话面板「完整对话」交接过来的内容，消费后立即清空 */
const aiChatHandoff = useAiChatHandoffStore()

const promptDialogVisible = ref(false)
const promptDialogPrompt = ref<PromptDraft | null>(null)
const prompts = ref<AiPromptVo[]>([])

/** 快捷提示词即被星标标记的已有提示词。 */
const quickPrompts = computed(() => prompts.value.filter((prompt) => quickPromptIds.value.includes(prompt.id)))
const promptSaving = ref(false)
const extracting = ref(false)
/** 会话导出进行中：避免重复提交 */
const exporting = ref(false)

const {
  aiHistoryOpen: historyPopover,
  aiHistoryKeyword: historyKeyword,
  aiHistoryCurrent: historyCurrent,
  aiHistoryTotal: historyTotal,
  aiHistoryLoading: historyLoading,
  aiConversations: conversations,
  aiHistoryPageSize: historyPageSize,
  loadAiHistory: loadHistory,
  searchAiHistory: searchHistory,
  onAiHistoryPageChange: onHistoryPageChange
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
  stopAiSpeaking,
  autoSpeak,
  autoSpeakTips,
  toggleAutoSpeak,
  startAutoSpeech,
  pushAutoSpeech,
  finishAutoSpeech,
  cancelAutoSpeech,
  cancelPendingAutoSpeech,
  isAutoSpeechTarget
} = useAiVoice({
  input,
  onDictation: () => composerRef.value?.focus(),
  enableAutoSpeak: true
})

const canSend = computed(() => {
  const hasText = input.value.trim().length > 0
  const hasFiles = attachments.value.some((f) => !f.error && !f.uploading)
  return (hasText || hasFiles) && !attachments.value.some((f) => f.uploading)
})

onMounted(() => {
  loadChatModels()
  loadHistory()
  loadPrompts()
  applyHandoff()
})

/** 页面已打开（keep-alive 缓存）时由面板交接触发，直接接管内容 */
watch(() => aiChatHandoff.payload, applyHandoff)

/** 记住用户选择的模型，下次进入页面继续使用 */
watch(selectedModelId, persistSelectedModel)

function newChat() {
  stopAiVoice()
  stopAiSpeaking()
  activeId.value = null
  messages.value = []
  attachments.value = []
  input.value = ''
}

/** 接管轻量对话面板交接的会话、消息、输入、附件与待确认操作，消费后清空。 */
function applyHandoff() {
  const handoff = aiChatHandoff.consume()
  if (!handoff) return
  // 页面正在生成回复时先停止，避免交接内容覆盖当前消息
  if (streaming.value) stop()
  stopAiVoice()
  stopAiSpeaking()
  // 清掉页面上原有的确认表单，避免提交到已切换的会话
  confirmDialogRef.value?.close()
  historyPopover.value = false
  activeId.value = handoff.conversationId || null
  messages.value = (handoff.messages || []).map(toHandoffMessage)
  input.value = handoff.input || ''
  attachments.value = (handoff.attachments || []).map((file: AiFileVo) => ({ ...file, localId: uid() }))
  if (handoff.confirm) confirmDialogRef.value?.open(handoff.confirm)
  nextTick(() => {
    composerRef.value?.autoResize()
    scrollToBottom(true)
  })
  loadHistory()
}

/** 面板消息字段与完整对话页一致，补齐缺省值并去掉仅面板使用的状态。 */
function toHandoffMessage(raw: any): UiMessage {
  return {
    id: raw.id || uid(),
    role: raw.role === 'user' ? 'user' : 'assistant',
    text: raw.text || '',
    thinking: raw.thinking || '',
    tools: (raw.tools || []).map((tool: any): UiTool => {
      const name = tool.name || INVOKE_TOOL
      const views = tool.result ? buildToolViews(name, tool.result) : []
      return {
        id: tool.id || uid(),
        name,
        status: tool.status === 'error' || tool.status === 'denied'
          ? tool.status
          // 面板交接前已停止流式，执行中的调用不会再有结果事件
          : tool.status === 'interrupted' || tool.status === 'running'
            ? 'interrupted'
            : 'done',
        result: tool.result,
        // 无结果时保留面板的兜底说明；有结果则按工具类型重新渲染（导出为文件卡片，接口按出参结构）
        view: views[0] || tool.view || null,
        views
      }
    }),
    // 按已结束状态重新分段，避免把面板流式中的占位段带过来
    segments: splitViewSegments(raw.text || '', false),
    attachments: raw.attachments || [],
    streaming: false,
    // 「请到完整对话确认」的提示只在面板内展示，完整对话页直接弹出确认表单
    error: raw.needFullChat ? undefined : raw.error || undefined
  }
}

async function openConversation(conv: AiConversationVo) {
  if (streaming.value) {
    ElMessage.warning('请先等待当前回答结束或点击停止')
    return
  }
  historyPopover.value = false
  stopAiVoice()
  stopAiSpeaking()
  activeId.value = conv.id
  messages.value = []
  try {
    const res = await getMessages(conv.id)
    const list = mergeAssistantMessages(res.data || []).map(toUiMessage)
    restoreCompletedTools(list)
    messages.value = list
    scrollToBottom(true)
  } catch (e) {
    // 已提示
  }
}

function toUiMessage(message: AiMessage): UiMessage {
  const ui: UiMessage = {
    id: message.id || uid(),
    role: message.role === 'user' ? 'user' : 'assistant',
    text: '',
    thinking: '',
    tools: [],
    segments: [],
    attachments: []
  }
  const texts: string[] = []
  ;(message.blocks || []).forEach((block) => {
    if (block.type === 'text') texts.push(block.text || '')
    else if (block.type === 'thinking') ui.thinking = appendText(ui.thinking, block.text)
    else if (block.type === 'attachments') {
      // 历史附件：后端按 fileId 重新生成预览地址，直接作为用户消息的附件展示
      ui.attachments = [...(ui.attachments || []), ...(block.files || [])]
    } else if (block.type === 'tool_use') {
      if (isConfigTool(block.name)) {
        // 查询接口配置不再单独占用工具卡片，并入思考过程展示
        rememberInterfaceConfig(block.result)
        ui.thinking = appendText(ui.thinking, buildConfigThinking(block.result))
        return
      }
      const toolName = block.name || INVOKE_TOOL
      const views = buildToolViews(toolName, block.result)
      ui.tools.push({
        id: block.id || uid(),
        name: toolName,
        // 只有工具调用、没有结果与状态：通常是等待确认后中断，标记为未完成
        status: block.state === 'ERROR' || block.state === 'DENIED'
          ? 'error'
          : block.result || block.state ? 'done' : 'interrupted',
        result: block.result,
        view: views[0] || null,
        views
      })
    }
  })
  ui.text = texts.join('\n')
  syncSegments(ui)
  return ui
}

/** 正文变化后重新分段：已闭合的视图块渲染，流式期间的未闭合块显示占位。 */
function syncSegments(message: UiMessage) {
  message.segments = splitViewSegments(message.text, !!message.streaming)
}

async function onConvCommand(command: string, conv: AiConversationVo) {
  if (command === 'rename') {
    try {
      const { value } = await ElMessageBox.prompt('新的会话名称', '重命名', {
        inputValue: conv.title || '',
        inputValidator: (v: string) => (v && v.trim() ? true : '名称不能为空')
      })
      await renameConversation(conv.id, value.trim())
      conv.title = value.trim()
    } catch (e) {
      // 取消
    }
    return
  }
  if (command === 'delete') {
    try {
      await ElMessageBox.confirm('删除后会话将从列表移除，确定继续？', '删除会话', { type: 'warning' })
      await deleteConversation(conv.id)
      if (activeId.value === conv.id) newChat()
      // 删除后总数变化，回到上一页避免停留在空页
      if (conversations.value.length === 1 && historyCurrent.value > 1) {
        historyCurrent.value -= 1
      }
      await loadHistory()
    } catch (e) {
      // 取消
    }
  }
}

// ==================== 会话导出 ====================

/** 导出当前会话：直接读取已落库消息生成文件，不重新调用模型分析。 */
async function exportCurrentConversation() {
  if (!activeId.value) {
    ElMessage.warning('当前还没有可导出的会话')
    return
  }
  if (exporting.value) return
  exporting.value = true
  try {
    const res = await exportConversation(activeId.value, { format: 'docx' })
    const url = assetUrl(res?.data?.url)
    if (url) window.open(url, '_blank')
    ElMessage.success('会话已导出')
  } finally {
    exporting.value = false
  }
}

// ==================== 发送 ====================

async function send() {
  if (!canSend.value || streaming.value) return
  stopAiVoice()
  stopAiSpeaking()
  const text = input.value.trim()
  const files = attachments.value.filter((f) => !f.error && !f.uploading)
  const userMessage: UiMessage = {
    id: uid(),
    role: 'user',
    text,
    thinking: '',
    tools: [],
    segments: [],
    attachments: files.map((f) => ({ ...f }))
  }
  const assistant: UiMessage = reactive({
    id: uid(),
    role: 'assistant',
    text: '',
    thinking: '',
    tools: [],
    segments: [],
    streaming: true
  })
  messages.value.push(userMessage, assistant)
  input.value = ''
  attachments.value = []
  scrollToBottom(true)
  await runStream(
    (onEvent, signal) => streamChat({
      conversationId: activeId.value,
      message: text,
      fileIds: files.map((f) => f.fileId),
      modelId: selectedModelId.value || undefined
    }, onEvent, signal),
    assistant
  )
}

/** 收到这些事件说明本轮已正常收尾，后端会自行结束事件流并释放会话写入权 */
const SETTLED_EVENT_TYPES = new Set(['reply.end', 'error', 'confirm.required'])

async function runStream(
  starter: (onEvent: (event: ChatEvent) => void, signal: AbortSignal) => Promise<void>,
  target: UiMessage,
  options: { cancelable?: boolean } = {}
) {
  const cancelable = options.cancelable !== false
  streaming.value = true
  streamCancelable.value = cancelable
  target.streaming = true
  stopAiSpeaking()
  abortController = new AbortController()
  let replyId = ''
  let settled = false
  try {
    await starter((event) => {
      if (event.type === 'reply.start' && event.replyId) replyId = event.replyId
      if (SETTLED_EVENT_TYPES.has(event.type)) settled = true
      handleEvent(event, target)
    }, abortController.signal)
  } catch (error: any) {
    if (error?.name === 'AbortError') {
      target.streaming = false
      if (!target.text && !target.thinking && target.tools.length === 0) {
        target.text = '已停止生成。'
      }
    } else {
      target.error = error?.message || '请求失败，请稍后重试'
    }
    // 用户停止、空闲超时或连接中断时，后端不一定能感知客户端已断开，
    // 主动结束本轮，避免遗留任务占着会话写入权导致下一条消息排队或报 active writer。
    // 写操作执行流不可取消：让后端把业务调用执行完，结果以历史记录为准
    if (cancelable && !settled && replyId && activeId.value) {
      cancelChat({ conversationId: activeId.value, replyId }).catch(() => {})
    }
  } finally {
    target.streaming = false
    syncSegments(target)
    // 没有收到 reply.end / confirm.required 就结束的流，不再继续播报
    cancelPendingAutoSpeech(target)
    streaming.value = false
    streamCancelable.value = true
    abortController = null
    scrollToBottom()
    loadHistory()
  }
}

/**
 * 按 toolCallId 在所有消息中查找工具卡片。
 * 写操作确认后，工具结果在 /ai/chat/confirm 的新流里返回，而工具卡片仍停留在上一条消息上。
 */
function findTool(toolCallId?: string) {
  if (!toolCallId) return undefined
  for (const message of messages.value) {
    const tool = message.tools.find((item) => item.id === toolCallId)
    if (tool) return tool
  }
  return undefined
}

function handleEvent(event: ChatEvent, target: UiMessage) {
  switch (event.type) {
    case 'conversation':
      if (event.conversationId) {
        activeId.value = event.conversationId
      }
      break
    case 'reply.start':
      if (event.replyId) target.id = event.replyId
      startAutoSpeech(target)
      break
    case 'thinking.delta':
      target.thinking += event.delta || ''
      followStreamTail('.thinking-text')
      break
    case 'message.delta': {
      target.text += event.delta || ''
      syncSegments(target)
      pushAutoSpeech(target, event.delta || '')
      break
    }
    case 'message.end':
      // 文本块结束（可能后续还有工具调用）时把未闭合的句子先读完
      pushAutoSpeech(target, '\n')
      break
    case 'tool.call.start': {
      if (isConfigTool(event.name)) break
      if (!findTool(event.toolCallId)) {
        target.tools.push({ id: event.toolCallId || uid(), name: event.name, status: 'running' })
      }
      break
    }
    case 'tool.result.delta': {
      if (isConfigTool(event.name)) {
        const key = event.toolCallId || ''
        pendingConfigResults.set(key, (pendingConfigResults.get(key) || '') + (event.delta || ''))
        break
      }
      const tool = findTool(event.toolCallId)
      if (tool) tool.result = (tool.result || '') + (event.delta || '')
      followStreamTail('.tool-result')
      break
    }
    case 'tool.result.end': {
      if (isConfigTool(event.name)) {
        const key = event.toolCallId || ''
        const raw = pendingConfigResults.get(key) || ''
        pendingConfigResults.delete(key)
        rememberInterfaceConfig(raw)
        target.thinking = appendText(target.thinking, buildConfigThinking(raw))
        followStreamTail('.thinking-text')
        break
      }
      const tool = findTool(event.toolCallId)
      if (tool) {
        tool.status = event.state === 'ERROR' || event.state === 'DENIED' ? 'error' : 'done'
        const views = buildToolViews(tool.name, tool.result)
        tool.views = views
        tool.view = views[0] || null
      }
      break
    }
    case 'tool.denied':
      (event.toolCalls || []).forEach((call: any) => {
        const tool = findTool(call.id)
        if (tool) tool.status = 'denied'
      })
      break
    case 'confirm.required':
      target.streaming = false
      // 本轮输出暂停，先把已到达的内容读完，用户确认后再为新回复建立会话
      finishAutoSpeech(target)
      confirmDialogRef.value?.open(event)
      break
    case 'reply.end':
      target.streaming = false
      syncSegments(target)
      // 模型因单次输出上限结束时没有异常，只能靠结束原因识别
      if (event.truncated || /length|max_token/i.test(String(event.finishReason || ''))) {
        target.truncated = true
      }
      if (isAutoSpeechTarget(target)) finishAutoSpeech(target)
      // 生成中途才打开自动播报（或未收到 reply.start）时，用完整正文补播一次
      else if (autoSpeak.value && ttsSupported && !target.error) speakAiMessage(target)
      break
    case 'error':
      target.error = event.message || 'AI 服务异常'
      target.streaming = false
      syncSegments(target)
      cancelAutoSpeech(target)
      break
    default:
      break
  }
  scrollToBottom()
}

function stop() {
  // 写操作执行流不允许打断，按钮此时也是禁用状态
  if (!streamCancelable.value) return
  abortController?.abort()
  streaming.value = false
  stopAiSpeaking()
  // 停止后不再收到工具结果事件，把执行中的调用标记为未完成
  messages.value.forEach((message) => {
    message.tools?.forEach((tool) => {
      if (tool.status === 'running') tool.status = 'interrupted'
    })
  })
}

// ==================== 确认表单 ====================

async function onConfirmSubmit({ confirmed, replyId, decisions }: ConfirmSubmitPayload) {
  if (!activeId.value) return
  const assistant: UiMessage = reactive({
    id: uid(),
    role: 'assistant',
    text: '',
    thinking: '',
    tools: [],
    segments: [],
    streaming: true
  })
  messages.value.push(assistant)
  await runStream(
    (onEvent, signal) => streamConfirm({
      conversationId: activeId.value as string,
      replyId,
      decisions,
      modelId: selectedModelId.value || undefined
    }, onEvent, signal),
    assistant,
    { cancelable: false }
  )
  if (!confirmed && !assistant.text && !assistant.error) {
    assistant.text = '已取消本次操作。'
  }
}

// ==================== 附件 ====================

function onDragOver(event: DragEvent) {
  if (event.dataTransfer?.types?.includes('Files')) {
    dragActive.value = true
  }
}

function onDragLeave(event: DragEvent) {
  if (!event.relatedTarget) {
    dragActive.value = false
  }
}

async function onDrop(event: DragEvent) {
  dragActive.value = false
  const files = Array.from(event.dataTransfer?.files || [])
  if (files.length) {
    await composerRef.value?.addFiles(files)
  }
}

function previewFile(file: AiFileVo) {
  if (!file.previewUrl) return
  const url = assetUrl(file.previewUrl)
  if (file.kind === 'IMAGE') {
    viewerUrl.value = url
  } else {
    window.open(url, '_blank')
  }
}

// ==================== 提示词 ====================

async function loadPrompts() {
  try {
    const res = await listPrompts(false)
    prompts.value = res.data || []
    const ids = new Set(prompts.value.map((prompt) => prompt.id))
    const validIds = quickPromptIds.value.filter((id) => ids.has(id))
    if (validIds.length !== quickPromptIds.value.length) {
      quickPromptIds.value = validIds
      persistQuickPromptIds()
    }
  } catch (e) {
    // 已提示
  }
}

function persistQuickPromptIds() {
  try {
    if (quickPromptIds.value.length) {
      localStorage.setItem(QUICK_PROMPT_KEY, JSON.stringify(quickPromptIds.value))
    } else {
      localStorage.removeItem(QUICK_PROMPT_KEY)
    }
  } catch (e) {
    // 本地存储不可用时仅在当前会话生效
  }
}

function toggleQuickPrompt(prompt: AiPromptVo) {
  if (quickPromptIds.value.includes(prompt.id)) {
    quickPromptIds.value = quickPromptIds.value.filter((id) => id !== prompt.id)
  } else {
    if (quickPromptIds.value.length >= QUICK_PROMPT_LIMIT) {
      ElMessage.warning(`快捷提示词最多 ${QUICK_PROMPT_LIMIT} 条`)
      return
    }
    quickPromptIds.value = [...quickPromptIds.value, prompt.id]
  }
  persistQuickPromptIds()
}

function startNewPrompt() {
  promptDialogPrompt.value = { id: '', name: '', content: '' }
  promptDialogVisible.value = true
}

function editPrompt(prompt: AiPromptVo) {
  promptDialogPrompt.value = { id: prompt.id, name: prompt.name, content: prompt.content }
  promptDialogVisible.value = true
}

/** 点击提示词后填入输入框，方便补充上下文后再发送。 */
function usePrompt(prompt: AiPromptVo) {
  const content = prompt.content || ''
  if (!content) return
  input.value = input.value.trim() ? `${input.value.trimEnd()}\n\n${content}` : content
  nextTick(() => composerRef.value?.focus())
}

async function savePersonalPrompt(draft: PromptDraft) {
  promptSaving.value = true
  try {
    if (draft.id) {
      await updatePrompt(draft.id, {
        name: draft.name,
        content: draft.content,
        promptType: 'TASK'
      })
      ElMessage.success('已保存')
    } else {
      await savePrompt({
        name: draft.name,
        content: draft.content,
        promptType: 'TASK'
      })
      ElMessage.success('已保存为个人提示词')
    }
    promptDialogVisible.value = false
    promptDialogPrompt.value = null
    await loadPrompts()
  } finally {
    promptSaving.value = false
  }
}

async function removePrompt(prompt: AiPromptVo) {
  try {
    await ElMessageBox.confirm(`删除提示词「${prompt.name}」？`, '删除', { type: 'warning' })
    await deletePrompt(prompt.id)
    await loadPrompts()
  } catch (e) {
    // 取消
  }
}

/** 从当前对话提取提示词草稿：完成后打开编辑弹窗，便于调整后保存。 */
async function extractFromConversation() {
  if (!activeId.value || extracting.value) return
  extracting.value = true
  try {
    const res = await extractPrompt({
      conversationId: activeId.value,
      name: '对话提取',
      promptType: 'TASK'
    })
    const draft = res.data || res
    promptDialogPrompt.value = {
      id: '',
      name: draft.name || '对话提取',
      content: draft.content || ''
    }
    promptDialogVisible.value = true
    ElMessage.success('已生成草稿，可编辑后保存')
  } catch (e) {
    // 已提示
  } finally {
    extracting.value = false
  }
}

// ==================== 展示工具 ====================

function toggleThinking(id: string) {
  if (thinkingOpen.value.has(id)) thinkingOpen.value.delete(id)
  else thinkingOpen.value.add(id)
  thinkingOpen.value = new Set(thinkingOpen.value)
}

function toggleTool(id: string) {
  if (toolCollapsed.value.has(id)) toolCollapsed.value.delete(id)
  else toolCollapsed.value.add(id)
  toolCollapsed.value = new Set(toolCollapsed.value)
}

// ==================== 工具结果展示 ====================

/** get_interface_config 的流式结果先暂存，结束后整体解析为思考内容。 */
const pendingConfigResults = new Map<string, string>()

/** 查询接口配置并入思考过程：列出候选接口、说明与入参字段。 */
function buildConfigThinking(raw?: string | null): string {
  if (!raw) return ''
  let data: any
  try {
    data = JSON.parse(raw)
  } catch (e) {
    return `查询接口配置：${raw}`
  }
  if (data && typeof data === 'object' && data.error) {
    return `查询接口配置失败：${data.message || data.error}`
  }
  const candidates = Array.isArray(data?.candidates) ? data.candidates : []
  if (!candidates.length) return '查询接口配置：未匹配到可用接口'
  const lines = candidates.map((item: any, index: number) => {
    const name = item?.name || item?.interfaceCode || '未命名接口'
    const code = item?.interfaceCode ? `（${item.interfaceCode}）` : ''
    const desc = item?.description ? `\n   说明：${item.description}` : ''
    const fields = Array.isArray(item?.inputSchema) && item.inputSchema.length
      ? `\n   入参：${item.inputSchema.map((field: any) => inputFieldLabel(field)).filter(Boolean).join('、')}`
      : ''
    return `${index + 1}. ${name}${code}${desc}${fields}`
  })
  return [`查询接口配置：匹配到 ${candidates.length} 个接口`, ...lines].join('\n')
}

function uid() {
  return Math.random().toString(36).slice(2) + Date.now().toString(36)
}

// ==================== 滚动 ====================

function scrollToBottom(force = false) {
  nextTick(() => followBottom(scrollEl.value, force))
}

/** 思考过程、工具响应等内部滚动区随流式输出贴底 */
function followStreamTail(selector: string) {
  nextTick(() => followLast(scrollEl.value, selector))
}
</script>

<style scoped>
.ai-chat {
  /* 消息区、思考过程、工具结果与输入区共用同一内容宽度 */
  --chat-content-width: min(1200px, 100%);
  display: flex;
  /* 原生页面外层容器高度为 auto，百分比高度无法生效；
     以绝对定位铺满 .content-wrap（已按视口与工具栏固定状态定好位置），从而占满可视区域 */
  position: absolute;
  inset: 0;
  min-height: 520px;
  background:
    radial-gradient(70% 42% at 50% 0%, rgba(91, 140, 255, 0.1) 0%, rgba(91, 140, 255, 0) 70%),
    radial-gradient(50% 36% at 100% 0%, rgba(168, 85, 247, 0.07) 0%, rgba(168, 85, 247, 0) 70%),
    #f6f8fc;
  color: #1f2329;
  overflow: hidden;
  margin: 5px;
  border: 1px solid rgba(226, 232, 248, 0.9);
  border-radius: 16px;
}

/* ============ 对话区 ============ */
.chat-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  position: relative;
}

.chat-header {
  position: relative;
  z-index: 2;
  height: 64px;
  flex: 0 0 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 28px;
  border-bottom: 1px solid rgba(226, 232, 248, 0.9);
  background:
    radial-gradient(120% 160% at 0% 0%, rgba(79, 124, 255, 0.14) 0%, rgba(79, 124, 255, 0) 58%),
    radial-gradient(110% 150% at 100% 0%, rgba(139, 92, 246, 0.12) 0%, rgba(139, 92, 246, 0) 62%),
    rgba(255, 255, 255, 0.74);
}

.chat-header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.chat-avatar {
  flex: 0 0 auto;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  background: linear-gradient(135deg, #5b8cff 0%, #7b6bff 55%, #a855f7 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 19px;
  box-shadow: 0 8px 18px -6px rgba(99, 91, 255, 0.55), 0 0 0 3px rgba(124, 116, 255, 0.1);
}

.chat-heading {
  min-width: 0;
}

.chat-title {
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 0.1px;
  color: #161a22;
  line-height: 1.35;
}

.chat-subtitle {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 2px;
  color: #8b94a7;
  font-size: 11.5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-status {
  flex: 0 0 auto;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #2fc26e;
  box-shadow: 0 0 0 3px rgba(47, 194, 110, 0.12);
}

.chat-export {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 14px;
  border: 1px solid #e6ebf5;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.86);
  color: #4e5969;
  font-size: 12.5px;
  font-family: inherit;
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s, background-color 0.15s;
}

.chat-export:hover:not(:disabled) {
  border-color: rgba(99, 91, 255, 0.35);
  background: #fff;
  color: #3b5bdb;
}

.chat-export:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.message-scroll {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  padding: 28px 24px 16px;
  scroll-behavior: smooth;
  /* 两侧始终预留滚动条槽位（8px），使消息列居中位置不受滚动条出现/消失影响，
     并与 .composer 的 max-width（100% - 64px = 左右各 32px）保持一致 */
  scrollbar-gutter: stable both-edges;
}

.message-inner {
  flex: 1;
  width: 100%;
  max-width: var(--chat-content-width);
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.empty-state {
  margin: auto 0;
  padding: 32px 16px;
  text-align: center;
}

.empty-logo {
  position: relative;
  width: 64px;
  height: 64px;
  border-radius: 19px;
  background: linear-gradient(135deg, #5b8cff 0%, #7b6bff 55%, #a855f7 100%);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 29px;
  box-shadow: 0 16px 36px -14px rgba(99, 91, 255, 0.6), 0 0 0 5px rgba(124, 116, 255, 0.07);
}

.empty-logo::after {
  content: '';
  position: absolute;
  inset: -9px;
  border-radius: 26px;
  border: 1px solid rgba(124, 116, 255, 0.18);
}

.empty-state h2 {
  margin: 20px 0 8px;
  font-size: 24px;
  font-weight: 600;
  letter-spacing: 0.2px;
  color: #1b2230;
}

.empty-sub {
  max-width: 560px;
  margin: 0 auto 24px;
  color: #8b94a7;
  font-size: 13.5px;
  line-height: 1.75;
}

.quick-list {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
}

.quick-item {
  border: 1px solid #e6ebf5;
  background: rgba(255, 255, 255, 0.86);
  border-radius: 999px;
  padding: 10px 16px;
  font-size: 13px;
  color: #4e5969;
  cursor: pointer;
  box-shadow: 0 2px 8px -4px rgba(17, 24, 51, 0.08);
  transition: border-color 0.15s, color 0.15s, background-color 0.15s, box-shadow 0.15s,
    transform 0.15s;
}

.quick-item:hover {
  border-color: rgba(99, 91, 255, 0.35);
  background: #fff;
  color: #3b5bdb;
  transform: translateY(-1px);
  box-shadow: 0 8px 18px -10px rgba(91, 92, 255, 0.4);
}

/* ============ 拖拽遮罩 ============ */
.drag-mask {
  position: absolute;
  inset: 0;
  z-index: 20;
  background: rgba(79, 124, 255, 0.06);
  border: 2px dashed rgba(99, 91, 255, 0.55);
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}

.drag-mask-inner {
  background: #fff;
  padding: 14px 22px;
  border: 1px solid rgba(99, 91, 255, 0.12);
  border-radius: 14px;
  color: #3b5bdb;
  font-size: 14px;
  box-shadow: 0 16px 40px -18px rgba(91, 92, 255, 0.4);
}

/* ============ 滚动条 ============ */
.message-scroll::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.message-scroll::-webkit-scrollbar-thumb {
  background-color: rgba(148, 163, 184, 0.24);
  border: 2px solid transparent;
  background-clip: padding-box;
  border-radius: 8px;
}

.message-scroll::-webkit-scrollbar-thumb:hover {
  background-color: rgba(148, 163, 184, 0.45);
}

.message-scroll::-webkit-scrollbar-track {
  background: transparent;
}

/* ============ 响应式 ============ */
@media (max-width: 900px) {
  .message-scroll {
    padding: 20px 14px 10px;
  }

  .chat-header {
    height: 58px;
    flex-basis: 58px;
    padding: 0 16px;
  }
}

@media (max-width: 600px) {
  .chat-header {
    height: 56px;
    flex-basis: 56px;
  }

  .chat-avatar {
    width: 34px;
    height: 34px;
    border-radius: 10px;
    font-size: 17px;
  }

  .chat-title {
    font-size: 15px;
  }

  .chat-subtitle {
    display: none;
  }

  .empty-state {
    margin-top: 6vh;
  }

  .empty-logo {
    width: 56px;
    height: 56px;
    border-radius: 17px;
    font-size: 25px;
  }

  .empty-state h2 {
    font-size: 21px;
  }

  .quick-item {
    padding: 9px 14px;
    font-size: 12.5px;
  }
}
</style>
