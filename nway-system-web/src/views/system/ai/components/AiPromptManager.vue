<template>
  <el-popover v-model:visible="promptPopover" placement="top-end" width="min(360px, 90vw)"
              trigger="click" popper-class="prompt-popover" @show="emit('open')">
    <template #reference>
      <button class="tool-btn" type="button" title="提示词">
        <el-icon><MagicStick /></el-icon>
        <span>提示词</span>
      </button>
    </template>
    <div class="prompt-panel">
      <div class="prompt-panel-head">
        <div>
          <div class="prompt-panel-title">提示词</div>
          <div class="prompt-panel-sub">点击提示词填入输入框，星标可设为快捷提示词</div>
        </div>
        <el-button link type="primary" :icon="Plus" @click="startNewPrompt">新建</el-button>
      </div>
      <button type="button" class="prompt-extract" :disabled="!activeId || extracting"
              @click="emit('extract')">
        <el-icon class="prompt-extract-icon" :class="{ 'is-loading': extracting }">
          <Loading v-if="extracting" />
          <MagicStick v-else />
        </el-icon>
        <span class="prompt-extract-main">
          <span class="prompt-extract-title">{{ extracting ? '正在提取提示词…' : '从当前对话提取' }}</span>
          <span class="prompt-extract-sub">
            {{ activeId ? '根据当前会话归纳可复用提示词' : '当前没有可提取的会话' }}
          </span>
        </span>
      </button>
      <div class="prompt-panel-list">
        <div v-for="prompt in prompts" :key="prompt.id" class="prompt-card" @click="usePrompt(prompt)">
          <div class="prompt-card-head">
            <span class="prompt-card-name" :title="prompt.name">{{ prompt.name }}</span>
            <el-tag size="small"
                    :type="prompt.scopeType === 'USER' ? 'success' : prompt.scopeType === 'ROLE' ? 'warning' : 'info'">
              {{ prompt.scopeLabel }}
            </el-tag>
            <el-tooltip :content="isQuickPrompt(prompt) ? '取消快捷' : '设为快捷'"
                        placement="top" :show-after="300">
              <button type="button" class="prompt-quick" :class="{ active: isQuickPrompt(prompt) }"
                      @click.stop="emit('toggle-quick', prompt)">
                <el-icon>
                  <StarFilled v-if="isQuickPrompt(prompt)" />
                  <Star v-else />
                </el-icon>
              </button>
            </el-tooltip>
          </div>
          <div class="prompt-card-content">{{ promptSummary(prompt.content) }}</div>
          <div v-if="prompt.scopeType === 'USER'" class="prompt-card-ops" @click.stop>
            <el-button link type="primary" size="small" :icon="Edit"
                       @click="emit('edit', prompt)">编辑</el-button>
            <el-button link type="danger" size="small" :icon="Delete"
                       @click="emit('delete', prompt)">删除</el-button>
          </div>
        </div>
        <div v-if="prompts.length === 0" class="prompt-empty">暂无提示词，点击「新建」创建</div>
      </div>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Delete, Edit, Loading, MagicStick, Plus, Star, StarFilled } from '@element-plus/icons-vue'
import type { AiPromptVo } from '@/api/ai'

const props = defineProps({
  prompts: { type: Array as () => AiPromptVo[], default: () => [] },
  quickPromptIds: { type: Array as () => string[], default: () => [] },
  activeId: { type: String as () => string | null, default: null },
  extracting: { type: Boolean, default: false }
})

const emit = defineEmits<{
  (e: 'use', prompt: AiPromptVo): void
  (e: 'edit', prompt: AiPromptVo): void
  (e: 'delete', prompt: AiPromptVo): void
  (e: 'create'): void
  (e: 'extract'): void
  (e: 'toggle-quick', prompt: AiPromptVo): void
  (e: 'open'): void
}>()

const promptPopover = ref(false)

function isQuickPrompt(prompt: AiPromptVo) {
  return props.quickPromptIds.includes(prompt.id)
}

function usePrompt(prompt: AiPromptVo) {
  promptPopover.value = false
  emit('use', prompt)
}

function startNewPrompt() {
  promptPopover.value = false
  emit('create')
}

/** Popover 中用纯文本摘要展示提示词，避免代码块占满列表。 */
function promptSummary(content?: string) {
  if (!content) return '暂无内容'
  const plain = content
    .replace(/```[\s\S]*?```/g, '[图表]')
    .replace(/[#>*_`~]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
  return plain.length > 100 ? `${plain.slice(0, 100)}…` : plain
}
</script>

<style scoped>
/* 工具按钮：与输入区工具栏按钮保持一致 */
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

.prompt-panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  padding-bottom: 10px;
  border-bottom: 1px solid #eef1f8;
}

.prompt-panel-title {
  font-size: 15px;
  font-weight: 600;
  color: #232a38;
}

.prompt-panel-sub {
  margin-top: 2px;
  color: #a4adbe;
  font-size: 11px;
}

.prompt-extract {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  margin-top: 10px;
  padding: 10px 12px;
  border: 1px dashed rgba(99, 91, 255, 0.35);
  border-radius: 12px;
  background: rgba(99, 91, 255, 0.05);
  font-family: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s, box-shadow 0.15s;
}

.prompt-extract:hover:not(:disabled) {
  border-color: rgba(99, 91, 255, 0.55);
  background: rgba(99, 91, 255, 0.09);
  box-shadow: 0 6px 16px -12px rgba(91, 92, 255, 0.4);
}

.prompt-extract:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.prompt-extract-icon {
  flex: 0 0 auto;
  font-size: 18px;
  color: #635bff;
}

.prompt-extract-main {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

.prompt-extract-title {
  font-size: 13px;
  font-weight: 600;
  color: #2f3542;
}

.prompt-extract-sub {
  font-size: 11px;
  color: #a4adbe;
}

.prompt-panel-list {
  max-height: 340px;
  overflow-y: auto;
  padding-top: 8px;
}

.prompt-card {
  padding: 11px 13px;
  margin-bottom: 8px;
  border: 1px solid #e9eef9;
  border-radius: 12px;
  background: #fff;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s, box-shadow 0.15s;
}

.prompt-card:hover {
  border-color: rgba(99, 91, 255, 0.3);
  background: #fbfcff;
  box-shadow: 0 6px 16px -12px rgba(91, 92, 255, 0.4);
}

.prompt-card-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.prompt-card-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13.5px;
  font-weight: 600;
  color: #2f3542;
}

.prompt-quick {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  padding: 0;
  border: none;
  border-radius: 7px;
  background: transparent;
  color: #c0c4cc;
  cursor: pointer;
  transition: color 0.15s, background 0.15s;
}

.prompt-quick:hover,
.prompt-quick.active {
  color: #f7ba2a;
  background: #fff8e6;
}

.prompt-card-content {
  margin-top: 6px;
  color: #8b94a7;
  font-size: 12px;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.prompt-card-ops {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
  margin-top: 4px;
}

.prompt-empty {
  padding: 28px 0;
  text-align: center;
  color: #a4adbe;
  font-size: 12.5px;
}

.prompt-panel-list::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.prompt-panel-list::-webkit-scrollbar-thumb {
  background-color: rgba(148, 163, 184, 0.24);
  border: 2px solid transparent;
  background-clip: padding-box;
  border-radius: 8px;
}

.prompt-panel-list::-webkit-scrollbar-thumb:hover {
  background-color: rgba(148, 163, 184, 0.45);
}

.prompt-panel-list::-webkit-scrollbar-track {
  background: transparent;
}
</style>
