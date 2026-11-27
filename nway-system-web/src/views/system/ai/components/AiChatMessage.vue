<template>
  <div class="message" :class="msg.role">
    <div v-if="msg.role === 'user'" class="bubble user-bubble">
      <div v-if="msg.text" class="msg-text">{{ msg.text }}</div>
      <div v-if="msg.attachments && msg.attachments.length" class="msg-attachments">
        <div v-for="file in msg.attachments" :key="file.fileId || file.localId" class="msg-file"
             :title="`${file.name} · ${formatSize(file.size)}`" @click="emit('preview-file', file)">
          <el-icon class="msg-file-icon"><Document /></el-icon>
          <span class="file-name">{{ file.name }}</span>
          <span class="file-size">{{ formatSize(file.size) }}</span>
        </div>
      </div>
    </div>

    <div v-else class="assistant-block">
      <div class="assistant-meta">
        <span class="assistant-avatar">
          <el-icon><ChatDotRound /></el-icon>
        </span>
        <span class="assistant-name">AI 助手</span>
        <span v-if="msg.streaming" class="assistant-live">生成中…</span>
      </div>

      <div v-if="msg.thinking" class="thinking-box" :class="{ open: thinkingOpen.has(msg.id) }">
        <div class="thinking-head" @click="emit('toggle-thinking', msg.id)">
          <el-icon class="thinking-arrow" :class="{ open: thinkingOpen.has(msg.id) }">
            <ArrowRight />
          </el-icon>
          <span>思考过程</span>
        </div>
        <pre v-show="thinkingOpen.has(msg.id)" class="thinking-text"
             @scroll="emit('scroll-follow', $event)">{{ msg.thinking }}</pre>
      </div>

      <div v-for="tool in msg.tools || []" :key="tool.id" class="tool-card" :class="tool.status">
        <div class="tool-head" :class="{ 'is-toggle': hasToolBody(tool) }"
             @click="hasToolBody(tool) && emit('toggle-tool', tool.id)">
          <el-icon v-if="hasToolBody(tool)" class="tool-arrow"
                   :class="{ open: !toolCollapsed.has(tool.id) }">
            <ArrowRight />
          </el-icon>
          <span class="tool-dot" :class="tool.status"></span>
          <span class="tool-name">{{ toolLabel(tool.name) }}</span>
          <span v-if="tool.view?.interfaceName" class="tool-target">{{ tool.view.interfaceName }}</span>
          <span class="tool-status">{{ toolStatusLabel(tool) }}</span>
          <span v-if="tool.view?.latency" class="tool-latency">{{ tool.view.latency }}</span>
        </div>

        <div v-if="hasToolBody(tool)" v-show="!toolCollapsed.has(tool.id)" class="tool-body">
          <template v-if="toolViews(tool).length">
            <template v-for="(view, viewIndex) in toolViews(tool)" :key="viewIndex">
              <div v-if="view.message" class="tool-message" :class="{ error: view.success === false }">
                {{ view.message }}
              </div>
              <AiViewBlock v-if="view.success !== false" :view="view" />
            </template>
          </template>

          <pre v-else-if="tool.result" class="tool-result"
               @scroll="emit('scroll-follow', $event)">{{ toolResultText(tool.result) }}</pre>

          <div v-else class="tool-message">
            {{ tool.status === 'interrupted'
              ? '本次调用未完成（等待确认或会话已中断），暂无返回结果。'
              : '调用已完成，暂无返回数据。' }}
          </div>
        </div>
      </div>

      <template v-for="(segment, index) in msg.segments" :key="index">
        <AiMarkdown v-if="segment.type === 'text' && segment.text" class="assistant-text"
                    :text="segment.text" :streaming="msg.streaming" />
        <AiViewBlock v-else-if="segment.type === 'view' && segment.view" class="msg-view"
                     :view="segment.view" />
        <div v-else-if="segment.type === 'pending'" class="msg-view-pending">正在整理数据…</div>
      </template>
      <div v-if="msg.error" class="msg-error">{{ msg.error }}</div>
      <div v-if="msg.truncated" class="msg-truncated">
        回答已达到模型单次输出上限，内容可能不完整。可回复「继续」让模型接着输出。
      </div>
      <div v-if="msg.streaming" class="typing"><span></span><span></span><span></span></div>
      <div v-else-if="ttsSupported && msg.text" class="msg-actions">
        <button type="button" class="msg-action" :class="{ active: speakingId === msg.id }"
                :title="speakingId === msg.id ? '停止播报' : '播报这条回复'"
                @click="emit('speak', msg)">
          <el-icon>
            <Mute v-if="speakingId === msg.id" />
            <Headset v-else />
          </el-icon>
          <span>{{ speakingId === msg.id ? '停止播报' : '语音播报' }}</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ArrowRight, ChatDotRound, Document, Headset, Mute } from '@element-plus/icons-vue'
import AiMarkdown from '@/components/ai/AiMarkdown.vue'
import AiViewBlock from '@/components/ai/AiViewBlock.vue'
import type { AiFileVo } from '@/api/ai'
import { toolLabel } from '@/utils/aiTool'
import { toolResultText } from '@/utils/aiView'
import { formatFileSize } from '@/utils/fileSize'
import type { UiMessage, UiTool } from './types'

const props = defineProps({
  msg: { type: Object as () => UiMessage, required: true },
  thinkingOpen: { type: Object as () => Set<string>, default: () => new Set<string>() },
  toolCollapsed: { type: Object as () => Set<string>, default: () => new Set<string>() },
  speakingId: { type: String as () => string | null, default: null },
  ttsSupported: { type: Boolean, default: false }
})

const emit = defineEmits<{
  (e: 'toggle-thinking', id: string): void
  (e: 'toggle-tool', id: string): void
  (e: 'speak', message: UiMessage): void
  (e: 'preview-file', file: AiFileVo): void
  (e: 'scroll-follow', event: Event): void
}>()

function hasToolBody(tool: UiTool) {
  // 未完成与已结束但无结果时也展示兜底文案，避免展开后一片空白
  if (tool.status === 'interrupted' || tool.status === 'done') return true
  return !!(tool.view || (tool.views && tool.views.length) || tool.result)
}

/** 批量查询等场景一个工具会有多个结果视图，统一按列表渲染。 */
function toolViews(tool: UiTool) {
  if (tool.views && tool.views.length) return tool.views
  return tool.view ? [tool.view] : []
}

function toolStatusLabel(tool: UiTool) {
  if (tool.status === 'running') return '执行中'
  if (tool.status === 'error') return '失败'
  if (tool.status === 'denied') return '已拒绝'
  if (tool.status === 'interrupted') return '未完成'
  return '完成'
}

function formatSize(size?: number) {
  return formatFileSize(size) || '0 B'
}
</script>

<style scoped>
.message {
  display: flex;
  width: 100%;
  animation: chatMsgIn 0.22s ease-out both;
}

@keyframes chatMsgIn {
  from {
    opacity: 0;
    transform: translateY(6px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.message.user {
  justify-content: flex-end;
}

/* 用户与助手消息占满同一内容宽度 */
.message.user .bubble {
  max-width: 100%;
}

.bubble {
  border-radius: 18px 18px 6px 18px;
  padding: 12px 16px;
  font-size: 14px;
  line-height: 1.68;
}

.user-bubble {
  background: linear-gradient(135deg, #eef3ff 0%, #f1ecff 100%);
  color: #26324d;
  border: 1px solid rgba(79, 124, 255, 0.14);
  box-shadow: 0 1px 2px rgba(16, 24, 48, 0.03), 0 10px 24px -24px rgba(16, 24, 48, 0.2);
}

.assistant-block {
  position: relative;
  max-width: 100%;
  width: 100%;
  min-width: 0;
  padding: 14px 18px 16px;
  background: #fff;
  border: 1px solid rgba(228, 233, 244, 0.95);
  border-radius: 18px 18px 18px 6px;
  box-shadow: 0 1px 2px rgba(16, 24, 48, 0.03), 0 16px 36px -30px rgba(16, 24, 48, 0.28);
  font-size: 14px;
  line-height: 1.7;
}

.assistant-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.assistant-avatar {
  flex: 0 0 auto;
  width: 24px;
  height: 24px;
  border-radius: 8px;
  background: linear-gradient(135deg, #5b8cff 0%, #8b5cf6 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  box-shadow: 0 4px 10px -4px rgba(99, 91, 255, 0.5);
}

.assistant-name {
  color: #3a4356;
  font-size: 12.5px;
  font-weight: 600;
  letter-spacing: 0.1px;
}

.assistant-live {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-left: 2px;
  color: #4f7cff;
  font-size: 11.5px;
}

.assistant-live::before {
  content: '';
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
  animation: pulse 1s infinite;
}

.msg-text {
  white-space: pre-wrap;
  word-break: break-word;
}

.assistant-text {
  color: #2f3542;
  word-break: break-word;
}

/* 正文中的结构化视图与流式占位 */
.msg-view {
  margin-top: 8px;
}

.msg-view-pending {
  margin-top: 8px;
  color: #a9aeb8;
  font-size: 12px;
}

.msg-attachments {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px;
  margin-top: 8px;
}

.msg-attachments:first-child {
  margin-top: 0;
}

.msg-file {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 240px;
  height: 28px;
  padding: 0 10px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(79, 124, 255, 0.16);
  border-radius: 999px;
  font-size: 12px;
  color: #3f4a63;
  cursor: pointer;
  transition: border-color 0.16s, background-color 0.16s, box-shadow 0.16s;
}

.msg-file:hover {
  background: #fff;
  border-color: rgba(79, 124, 255, 0.4);
  box-shadow: 0 4px 12px -6px rgba(79, 124, 255, 0.45);
}

.msg-file-icon {
  flex: 0 0 auto;
  font-size: 13px;
  color: #4f7cff;
}

.file-name {
  flex: 0 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-size {
  flex: 0 0 auto;
  font-size: 11px;
  color: #9aa3b5;
}

.thinking-box {
  margin-bottom: 12px;
  padding: 2px 0 2px 12px;
  border-left: 2px solid #e1e8f8;
  border-radius: 0;
  background: transparent;
  transition: background-color 0.16s, border-color 0.16s;
}

.thinking-box.open {
  padding: 10px 12px 12px 13px;
  border: 1px solid #e9edf9;
  border-left: 2px solid #c3d1ff;
  border-radius: 12px;
  background: linear-gradient(180deg, #f7f9ff 0%, #f4f6fd 100%);
}

.thinking-head {
  display: flex;
  align-items: center;
  gap: 7px;
  color: #8b95a7;
  font-size: 12.5px;
  font-weight: 500;
  cursor: pointer;
  user-select: none;
  transition: color 0.15s;
}

.thinking-head:hover {
  color: #40527f;
}

.thinking-arrow {
  transition: transform 0.15s;
}

.thinking-arrow.open {
  transform: rotate(90deg);
}

.thinking-text {
  margin: 10px 0 0;
  white-space: pre-wrap;
  font-size: 12.5px;
  line-height: 1.75;
  color: #6b7488;
  max-height: 280px;
  overflow-y: auto;
  font-family: inherit;
}

.tool-card {
  margin-bottom: 10px;
  padding: 10px 12px;
  background: linear-gradient(180deg, #f8fafe 0%, #f5f8fd 100%);
  border: 1px solid #e9eef9;
  border-radius: 12px;
  transition: border-color 0.16s, background-color 0.16s;
}

.tool-card.error,
.tool-card.denied {
  border-color: #f6dada;
  background: #fffafa;
}

.tool-card.interrupted {
  border-color: #ffe2c2;
  background: #fffaf3;
}

.tool-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.tool-head.is-toggle {
  cursor: pointer;
}

.tool-arrow {
  flex: 0 0 auto;
  font-size: 11px;
  color: #97a1b6;
  transition: transform 0.15s;
}

.tool-arrow.open {
  transform: rotate(90deg);
}

.tool-head.is-toggle:hover .tool-name {
  color: #3b5bdb;
}

.tool-body {
  min-width: 0;
}

.tool-dot {
  flex: 0 0 auto;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #c9cdd4;
  box-shadow: 0 0 0 3px rgba(201, 205, 212, 0.16);
}

.tool-dot.running {
  background: #4f7cff;
  box-shadow: 0 0 0 3px rgba(79, 124, 255, 0.16);
  animation: pulse 1s infinite;
}

.tool-dot.done {
  background: #00b42a;
  box-shadow: 0 0 0 3px rgba(0, 180, 42, 0.14);
}

.tool-dot.error,
.tool-dot.denied {
  background: #f53f3f;
  box-shadow: 0 0 0 3px rgba(245, 63, 63, 0.14);
}

.tool-dot.interrupted {
  background: #ff7d00;
  box-shadow: 0 0 0 3px rgba(255, 125, 0, 0.14);
}

@keyframes pulse {
  0% { opacity: 0.4; }
  50% { opacity: 1; }
  100% { opacity: 0.4; }
}

.tool-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #33405a;
  font-weight: 600;
}

.tool-target {
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding: 1px 8px;
  border-radius: 999px;
  background: #eef4ff;
  color: #3b5bdb;
  font-size: 11.5px;
  line-height: 18px;
}

.tool-status {
  flex: 0 0 auto;
  margin-left: auto;
  color: #98a1b3;
  font-size: 11.5px;
}

.tool-latency {
  flex: 0 0 auto;
  margin-left: 0;
  color: #a4adbe;
  font-size: 11px;
}

.tool-result {
  margin: 10px 0 0;
  padding: 10px 12px;
  background: #f7f9fd;
  border: 1px solid #eef1f8;
  border-radius: 10px;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12.5px;
  color: #667085;
  max-height: 240px;
  overflow-y: auto;
  font-family: inherit;
}

.tool-message {
  margin-top: 10px;
  color: #5a6579;
  font-size: 12.5px;
  line-height: 1.6;
  word-break: break-word;
}

.tool-message.error {
  color: #f53f3f;
}

.msg-error {
  margin-top: 10px;
  color: #f53f3f;
  font-size: 13px;
}

.msg-truncated {
  margin-top: 10px;
  padding: 7px 12px;
  border: 1px solid #f7e2b7;
  border-radius: 10px;
  background: #fffaf0;
  color: #a8730a;
  font-size: 12.5px;
  line-height: 1.6;
}

.msg-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.msg-action {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 26px;
  padding: 0 11px;
  border: 1px solid #e6ebf5;
  border-radius: 999px;
  background: #fafbfe;
  color: #8892a6;
  font-size: 11.5px;
  font-family: inherit;
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s, background-color 0.15s;
}

.msg-action:hover,
.msg-action.active {
  border-color: #b9c8f2;
  background: #fff;
  color: #3b5bdb;
}

.typing {
  display: inline-flex;
  gap: 5px;
  padding: 8px 0 2px;
}

.typing span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #b7c0d4;
  animation: blink 1.2s infinite;
}

.typing span:nth-child(2) { animation-delay: 0.2s; }
.typing span:nth-child(3) { animation-delay: 0.4s; }

@keyframes blink {
  0%, 80%, 100% { opacity: 0.3; }
  40% { opacity: 1; }
}

.thinking-text::-webkit-scrollbar,
.tool-result::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.thinking-text::-webkit-scrollbar-thumb,
.tool-result::-webkit-scrollbar-thumb {
  background-color: rgba(148, 163, 184, 0.24);
  border: 2px solid transparent;
  background-clip: padding-box;
  border-radius: 8px;
}

.thinking-text::-webkit-scrollbar-thumb:hover,
.tool-result::-webkit-scrollbar-thumb:hover {
  background-color: rgba(148, 163, 184, 0.45);
}

.thinking-text::-webkit-scrollbar-track,
.tool-result::-webkit-scrollbar-track {
  background: transparent;
}

@media (max-width: 900px) {
  .assistant-block {
    padding: 12px 14px 14px;
    border-radius: 16px 16px 16px 6px;
  }
}
</style>

<style scoped src="../../../../assets/aiMarkdown.css"></style>
