<template>
    <div class="gs-ai-composer">
        <div v-if="aiInputDragActive" class="gs-ai-drop-tip">
            <span class="gs-ai-drop-dot"></span>
            松开即可上传附件（最多 5 个，单个不超过 20MB）
        </div>
        <div v-if="attachments.length" class="gs-ai-attach-tray">
            <div v-for="file in attachments" :key="file.localId" class="gs-ai-attach"
                :class="{ 'is-failed': !!file.error }">
                <div class="gs-ai-attach-icon">
                    <el-icon><Document /></el-icon>
                </div>
                <div class="gs-ai-attach-main">
                    <div class="gs-ai-attach-name" :title="file.name">{{ file.name }}</div>
                    <div class="gs-ai-attach-meta">
                        <span v-if="file.uploading">上传中 {{ file.progress || 0 }}%</span>
                        <span v-else-if="file.error" class="gs-ai-attach-error">{{ file.error }}</span>
                        <span v-else>{{ formatFileSize(file.size) }}</span>
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
                :title="voiceTitle" :disabled="!speechSupported" @click="emit('toggle-voice')">
                <el-icon><Microphone /></el-icon>
            </button>
            <textarea ref="inputEl" v-model="input" class="gs-ai-input" rows="1" placeholder="给 AI 发消息，Enter 发送"
                @keydown="onAiKeydown" @paste="onAiPaste"></textarea>
            <button v-if="!streaming" type="button" class="gs-ai-send" title="发送" :disabled="!canSend" @click="emit('send')">
                <el-icon><Promotion /></el-icon>
            </button>
            <button v-else type="button" class="gs-ai-send is-stop" title="停止生成" @click="emit('stop')">
                <el-icon><VideoPause /></el-icon>
            </button>
        </div>
        <div v-if="voiceActive" class="gs-ai-voice-live">
            <span class="gs-ai-voice-dot"></span>
            正在聆听，再次点击麦克风结束
        </div>
        <div class="gs-ai-foot">
        </div>

        <input ref="fileInput" type="file" multiple class="gs-ai-hidden-input" @change="onAiFilesPicked" />
    </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Camera, Close, Document, Microphone, Paperclip, Promotion, Reading, VideoPause } from '@element-plus/icons-vue'
import { useAiAttachments } from '@/composables/useAiAttachments'
import { formatFileSize } from '@/utils/fileSize'

const input = defineModel('input', { type: String, default: '' })
const attachments = defineModel('attachments', { type: Array, default: () => [] })
const pageTextEnabled = defineModel('pageTextEnabled', { type: Boolean, default: false })
const pageShotEnabled = defineModel('pageShotEnabled', { type: Boolean, default: false })

defineProps({
    streaming: { type: Boolean, default: false },
    canSend: { type: Boolean, default: false },
    pageContextHint: { type: String, default: '' },
    voiceActive: { type: Boolean, default: false },
    voiceTitle: { type: String, default: '' },
    speechSupported: { type: Boolean, default: false }
})

const emit = defineEmits(['send', 'stop', 'toggle-voice'])

const inputEl = ref(null)
const fileInput = ref(null)
const {
    aiInputDragActive,
    addAiFiles,
    retryAiUpload,
    removeAiAttachment,
    resetAiDragState,
    onAiInputDragEnter,
    onAiInputDragOver,
    onAiInputDragLeave,
    onAiInputDrop,
    onAiPaste
} = useAiAttachments(attachments)

function pickAiFiles() {
    fileInput.value?.click()
}

function onAiFilesPicked(event) {
    const target = event.target
    const files = Array.from(target.files || [])
    target.value = ''
    addAiFiles(files)
}

function onAiKeydown(event) {
    if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
        event.preventDefault()
        emit('send')
    }
}

function autoResizeAi() {
    const el = inputEl.value
    if (!el) return
    el.style.height = 'auto'
    el.style.height = Math.min(el.scrollHeight, 96) + 'px'
}

// 发送后清空、语音识别写入都会改变输入值，统一在这里跟随高度
watch(input, () => nextTick(autoResizeAi))

onMounted(() => {
    autoResizeAi()
    // 拖放结束（无论是否落在输入框、上传是否成功）都清掉虚线高亮
    document.addEventListener('drop', resetAiDragState)
    document.addEventListener('dragend', resetAiDragState)
})

onBeforeUnmount(() => {
    document.removeEventListener('drop', resetAiDragState)
    document.removeEventListener('dragend', resetAiDragState)
})

function focus() {
    inputEl.value?.focus()
}

defineExpose({ focus })
</script>

<style scoped>
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

@keyframes gsAiPulse {

    0%,
    100% {
        opacity: 0.4;
    }

    50% {
        opacity: 1;
    }
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

.gs-ai-input::-webkit-scrollbar {
    width: 6px;
}

.gs-ai-input::-webkit-scrollbar-thumb {
    background-color: rgba(148, 163, 184, 0.24);
    border-radius: 3px;
}

.gs-ai-input::-webkit-scrollbar-thumb:hover {
    background-color: rgba(148, 163, 184, 0.45);
}

.gs-ai-input::-webkit-scrollbar-track {
    background: transparent;
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
</style>
