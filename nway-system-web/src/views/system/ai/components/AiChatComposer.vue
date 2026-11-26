<template>
  <div class="composer">
    <div v-if="attachments.length" class="attach-tray">
      <div v-for="file in attachments" :key="file.localId" class="attach-card" :class="{ failed: !!file.error }">
        <div class="attach-icon">
          <el-icon><Document /></el-icon>
        </div>
        <div class="attach-main">
          <div class="attach-name" :title="file.name">{{ file.name }}</div>
          <div class="attach-meta">
            <span v-if="file.uploading">上传中 {{ file.progress || 0 }}%</span>
            <span v-else-if="file.error" class="attach-error">{{ file.error }}</span>
            <span v-else>{{ formatSize(file.size) }} · {{ fileKindLabel(file) }}</span>
          </div>
        </div>
        <button v-if="file.error" class="attach-retry" @click="retryAiUpload(file)">重试</button>
        <button class="attach-remove" title="移除" @click="removeAiAttachment(file)">
          <el-icon><Close /></el-icon>
        </button>
      </div>
    </div>

    <div class="composer-toolbar">
      <el-tooltip content="新对话" placement="top" :show-after="300">
        <button class="tool-btn icon-only" type="button" @click="emit('new-chat')">
          <el-icon><Plus /></el-icon>
        </button>
      </el-tooltip>

      <el-tooltip :content="autoSpeakTips" placement="top" :show-after="300">
        <button class="tool-btn" :class="{ active: autoSpeak }" type="button"
                :disabled="!ttsSupported" @click="emit('toggle-auto-speak')">
          <el-icon><Headset /></el-icon>
          <span>自动播报</span>
        </button>
      </el-tooltip>

      <slot name="toolbar-extra" />
    </div>

    <div v-if="voiceActive" class="voice-live">
      <span class="voice-live-dot"></span>
      正在聆听，再次点击麦克风结束
    </div>

    <div class="input-row">
      <textarea ref="inputEl" v-model="input" class="chat-input" rows="2"
                placeholder="输入消息，Enter 发送，Shift + Enter 换行"
                @keydown="onKeydown" @paste="onPaste"></textarea>
      <div class="input-actions">
        <button class="icon-btn" title="添加附件" @click="pickFiles">
          <el-icon><Paperclip /></el-icon>
        </button>
        <button class="icon-btn" :class="{ 'voice-recording': voiceActive }"
                :title="voiceTitle" :disabled="!speechSupported"
                @click="emit('toggle-voice')">
          <el-icon><Microphone /></el-icon>
        </button>
        <span class="input-actions-gap"></span>
        <el-select v-if="chatModels.length" v-model="selectedModelId" class="model-select"
                   size="small" placeholder="默认模型" :disabled="streaming">
          <template #label="{ value }">
            <span class="model-selected">
              <el-icon v-if="modelById(value)?.supportsVision" class="model-flag vision"
                       title="支持图片视觉分析">
                <View />
              </el-icon>
              <el-icon v-else-if="modelById(value)" class="model-flag muted"
                       title="不支持图片视觉分析">
                <Hide />
              </el-icon>
              <span class="model-selected-name">{{ modelById(value)?.name || '默认模型' }}</span>
              <span v-if="modelById(value)?.reasoningEffort" class="model-effort"
                    :title="`推理深度：${reasoningLabel(modelById(value)?.reasoningEffort)}`">
                {{ reasoningLabel(modelById(value)?.reasoningEffort) }}
              </span>
            </span>
          </template>
          <el-option v-for="model in chatModels" :key="model.id" :label="model.name"
                     :value="model.id">
            <div class="model-option">
              <span class="model-option-name">{{ model.name }}</span>
              <span class="model-flags">
                <el-icon v-if="model.supportsVision" class="model-flag vision"
                         title="支持图片视觉分析">
                  <View />
                </el-icon>
                <el-icon v-else class="model-flag muted" title="不支持图片视觉分析">
                  <Hide />
                </el-icon>
                <el-icon v-if="model.thinkingEnabled" class="model-flag think" title="已开启思考">
                  <Cpu />
                </el-icon>
                <span v-if="model.reasoningEffort" class="model-effort"
                      :title="`推理深度：${reasoningLabel(model.reasoningEffort)}`">
                  {{ reasoningLabel(model.reasoningEffort) }}
                </span>
              </span>
            </div>
          </el-option>
        </el-select>
        <button v-if="!streaming" class="send-btn" title="发送" :disabled="!canSend" @click="emit('send')">
          <el-icon><Promotion /></el-icon>
        </button>
        <button v-else-if="!streamCancelable" class="send-btn stop" disabled
                title="写操作执行中，请稍候">
          <el-icon><VideoPause /></el-icon>
        </button>
        <button v-else class="send-btn stop" title="停止生成" @click="emit('stop')">
          <el-icon><VideoPause /></el-icon>
        </button>
      </div>
    </div>
    <div class="composer-hint">
      支持图片、PDF、Word、Excel、CSV 等文件。附件默认不解析，仅在需要理解内容时按需解析。
    </div>
    <input ref="fileInput" type="file" multiple class="hidden-input" @change="onFilesPicked" />
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import {
  Close, Cpu, Document, Headset, Hide, Microphone, Paperclip, Plus, Promotion, VideoPause, View
} from '@element-plus/icons-vue'
import type { AiChatModelVo, AiFileVo } from '@/api/ai'
import { useAiAttachments } from '@/composables/useAiAttachments'
import { formatFileSize } from '@/utils/fileSize'

interface UploadItem extends AiFileVo {
  localId: string
  rawFile?: File
}

const input = defineModel<string>('input', { default: '' })
const attachments = defineModel<UploadItem[]>('attachments', { default: () => [] })
const selectedModelId = defineModel<string>('selectedModelId', { default: '' })

const props = defineProps({
  streaming: { type: Boolean, default: false },
  streamCancelable: { type: Boolean, default: true },
  canSend: { type: Boolean, default: false },
  voiceActive: { type: Boolean, default: false },
  voiceTitle: { type: String, default: '' },
  speechSupported: { type: Boolean, default: false },
  chatModels: { type: Array as () => AiChatModelVo[], default: () => [] },
  autoSpeak: { type: Boolean, default: false },
  autoSpeakTips: { type: String, default: '' },
  ttsSupported: { type: Boolean, default: false }
})

const emit = defineEmits<{
  (e: 'send'): void
  (e: 'stop'): void
  (e: 'new-chat'): void
  (e: 'toggle-voice'): void
  (e: 'toggle-auto-speak'): void
}>()

const inputEl = ref<HTMLTextAreaElement | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const { addAiFiles, retryAiUpload, removeAiAttachment, onAiPaste } = useAiAttachments(attachments, {
  uidPrefix: 'ai-chat-file'
})

function pickFiles() {
  fileInput.value?.click()
}

async function onFilesPicked(event: Event) {
  const target = event.target as HTMLInputElement
  const files = Array.from(target.files || [])
  target.value = ''
  await addAiFiles(files)
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
    event.preventDefault()
    emit('send')
  }
}

function autoResize() {
  const el = inputEl.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 150) + 'px'
}

// 发送后清空、提示词填入、语音识别写入都会改变输入值，统一跟随高度
watch(input, () => nextTick(autoResize))

onMounted(autoResize)

function focus() {
  inputEl.value?.focus()
}

function modelById(id?: string) {
  return props.chatModels.find((model) => model.id === id)
}

/** 推理深度展示：常见档位中文化，自定义档位原样展示。 */
function reasoningLabel(level?: string) {
  if (!level) return ''
  const labels: Record<string, string> = { low: '低', medium: '中', high: '高', max: '最高' }
  return labels[level] || level
}

function formatSize(size?: number) {
  return formatFileSize(size) || '0 B'
}

function fileKindLabel(file: AiFileVo) {
  if (file.kind === 'IMAGE') return '图片'
  if (file.kind === 'PDF') return 'PDF'
  if (file.kind === 'WORD') return 'Word'
  if (file.kind === 'EXCEL') return 'Excel'
  if (file.kind === 'PPT') return 'PPT'
  if (file.kind === 'TEXT') return '文本'
  return '文件'
}

defineExpose({ focus, autoResize, addFiles: addAiFiles })
</script>

<style scoped>
/* ============ 输入区 ============ */
/* 与输入框同宽、悬浮于底部：仍在 flex 流中占位，不遮挡会话内容 */
.composer {
  flex: 0 0 auto;
  box-sizing: border-box;
  width: var(--chat-content-width);
  /* 24px 内边距 + 8px 滚动条槽位，与 .message-inner 的可视宽度对齐 */
  max-width: calc(100% - 64px);
  margin: 0 auto 16px;
  padding: 10px 14px 12px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(14px);
  border: 1px solid rgba(226, 232, 248, 0.9);
  border-radius: 20px;
}

.attach-tray {
  max-width: var(--chat-content-width);
  margin: 0 auto 10px;
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 2px;
}

.attach-card {
  flex: 0 0 220px;
  display: flex;
  align-items: center;
  gap: 8px;
  background: #fff;
  border: 1px solid #e8edf6;
  border-radius: 12px;
  padding: 8px 10px;
  box-shadow: 0 4px 12px -10px rgba(17, 24, 51, 0.2);
}

.attach-card.failed {
  border-color: #f6dada;
}

.attach-icon {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: linear-gradient(135deg, #eef3ff 0%, #f4efff 100%);
  color: #4a5bd6;
  display: flex;
  align-items: center;
  justify-content: center;
}

.attach-main {
  flex: 1;
  min-width: 0;
}

.attach-name {
  font-size: 12px;
  color: #303540;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attach-meta {
  font-size: 11px;
  color: #98a1b3;
}

.attach-error {
  color: #f53f3f;
}

.attach-retry,
.attach-remove {
  border: none;
  background: transparent;
  color: #98a1b3;
  cursor: pointer;
  padding: 2px;
}

.attach-retry {
  color: #3b5bdb;
  font-size: 12px;
}

.composer-toolbar {
  max-width: var(--chat-content-width);
  margin: 0 auto 8px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

/* 模型选择与发送按钮同排，采用无边框样式融入输入区 */
.model-select {
  width: 190px;
  margin-right: 2px;
}

.model-select :deep(.el-select__wrapper) {
  min-height: 30px;
  padding: 0 8px;
  border-radius: 9px;
  background: transparent;
  box-shadow: none;
  font-size: 13px;
  color: #5f6b80;
}

.model-select :deep(.el-select__wrapper:hover),
.model-select :deep(.el-select__wrapper.is-focused) {
  background: rgba(79, 124, 255, 0.09);
  box-shadow: none;
}

.model-select :deep(.el-select__wrapper.is-disabled) {
  background: transparent;
  box-shadow: none;
}

/* 模型能力标识：视觉、思考与推理深度 */
.model-selected {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-width: 0;
  max-width: 100%;
}

.model-selected-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.model-option {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.model-option-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.model-flags {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.model-flag {
  font-size: 13px;
}

.model-flag.vision {
  color: #5b8cff;
}

.model-flag.think {
  color: #f0a020;
}

.model-flag.muted {
  color: #c9cdd4;
}

.model-effort {
  flex: 0 0 auto;
  padding: 0 5px;
  border-radius: 4px;
  background: #f2f4f8;
  color: #6b7488;
  font-size: 10.5px;
  line-height: 16px;
}

.tool-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  height: 30px;
  padding: 0 12px;
  border: 1px solid #e6ebf5;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.8);
  color: #5f6b80;
  font-size: 12.5px;
  font-family: inherit;
  cursor: pointer;
  box-shadow: 0 1px 2px rgba(17, 24, 51, 0.04);
  transition: border-color 0.15s, color 0.15s, background-color 0.15s, box-shadow 0.15s;
}

.tool-btn:hover {
  border-color: #b9c8f2;
  background: #fff;
  color: #3b5bdb;
  box-shadow: 0 4px 12px -8px rgba(91, 92, 255, 0.4);
}

.tool-btn.active {
  border-color: rgba(99, 91, 255, 0.34);
  background: linear-gradient(135deg, rgba(79, 124, 255, 0.12) 0%, rgba(139, 92, 246, 0.14) 100%);
  color: #3b5bdb;
}

.tool-btn:disabled {
  border-color: #e6ebf5;
  background: #fafbfe;
  color: #c9cdd4;
  cursor: not-allowed;
  box-shadow: none;
}

.tool-btn.icon-only {
  width: 30px;
  padding: 0;
}

.input-row {
  max-width: var(--chat-content-width);
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
  background: #fff;
  border: 1px solid #e3e8f3;
  border-radius: 18px;
  padding: 12px 14px 10px;
  box-shadow: 0 12px 32px -24px rgba(17, 24, 51, 0.35);
  transition: border-color 0.18s, box-shadow 0.18s;
}

.input-row:focus-within {
  border-color: #b3c3ff;
  box-shadow: 0 0 0 4px rgba(99, 91, 255, 0.1), 0 16px 40px -26px rgba(17, 24, 51, 0.4);
}

.input-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 2px;
}

.input-actions-gap {
  flex: 1;
}

.icon-btn {
  width: 32px;
  height: 32px;
  padding: 0;
  border: none;
  background: transparent;
  color: #7d8698;
  cursor: pointer;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  transition: background-color 0.15s, color 0.15s;
}

.icon-btn:hover {
  background: rgba(79, 124, 255, 0.09);
  color: #3b5bdb;
}

.icon-btn:disabled {
  background: transparent;
  color: #c9cdd4;
  cursor: not-allowed;
}

.icon-btn.voice-recording {
  background: #ffece8;
  color: #f53f3f;
  animation: voice-pulse 1.4s infinite;
}

@keyframes voice-pulse {
  0% { box-shadow: 0 0 0 0 rgba(245, 63, 63, 0.35); }
  70% { box-shadow: 0 0 0 6px rgba(245, 63, 63, 0); }
  100% { box-shadow: 0 0 0 0 rgba(245, 63, 63, 0); }
}

.voice-live {
  max-width: var(--chat-content-width);
  margin: 0 auto 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: #f53f3f;
  font-size: 12px;
}

.voice-live-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #f53f3f;
  box-shadow: 0 0 0 3px rgba(245, 63, 63, 0.14);
  animation: pulse 1s infinite;
}

@keyframes pulse {
  0% { opacity: 0.4; }
  50% { opacity: 1; }
  100% { opacity: 0.4; }
}

.chat-input {
  width: 100%;
  box-sizing: border-box;
  border: none;
  outline: none;
  resize: none;
  font-size: 14.5px;
  line-height: 1.7;
  max-height: 180px;
  min-height: 28px;
  padding: 2px 0 6px;
  font-family: inherit;
  color: #2f3542;
  background: transparent;
}

.chat-input::placeholder {
  color: #a4adbe;
}

.send-btn {
  border: none;
  background: linear-gradient(135deg, #5b8cff 0%, #7b6bff 100%);
  color: #fff;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 38px;
  font-size: 17px;
  box-shadow: 0 8px 18px -8px rgba(91, 92, 255, 0.65);
  transition: transform 0.15s, box-shadow 0.15s, background-color 0.15s;
}

.send-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 12px 24px -10px rgba(91, 92, 255, 0.75);
}

.send-btn:disabled {
  background: #d3d9e4;
  color: #fff;
  cursor: not-allowed;
  box-shadow: none;
}

.send-btn.stop {
  background: #f0f2f6;
  color: #5f6673;
  box-shadow: none;
}

.send-btn.stop:hover {
  background: #e6e9ef;
  transform: none;
}

.send-btn.stop:disabled,
.send-btn.stop:disabled:hover {
  background: #f0f2f6;
  color: #b3bac7;
  cursor: not-allowed;
  transform: none;
}

.composer-hint {
  max-width: var(--chat-content-width);
  margin: 10px auto 0;
  font-size: 11.5px;
  color: #a4adbe;
  text-align: center;
}

.hidden-input {
  display: none;
}

@media (max-width: 900px) {
  .composer {
    max-width: calc(100% - 44px);
    margin-bottom: 12px;
    padding: 10px 12px 12px;
  }
}

@media (max-width: 600px) {
  .composer-toolbar {
    flex-wrap: wrap;
    justify-content: flex-start;
  }

  .model-select {
    width: 130px;
  }
}
</style>
