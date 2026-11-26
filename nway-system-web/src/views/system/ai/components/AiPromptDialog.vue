<template>
  <el-dialog :model-value="visible" :title="form.id ? '编辑提示词' : '新建提示词'"
             width="min(920px, 94vw)" class="prompt-dialog" destroy-on-close
             @update:model-value="visible = $event" @open="seedPromptEditor" @opened="seedPromptEditor">
    <el-form label-position="top">
      <el-form-item label="标题" required>
        <el-input v-model="form.name" maxlength="60" show-word-limit
                  placeholder="例如：会议纪要整理" />
      </el-form-item>
      <el-form-item label="内容" required>
        <div class="prompt-editor">
          <div class="prompt-toolbar">
            <button type="button" class="prompt-tool" title="正文" @mousedown.prevent
                    @click="execBlock('p')">正文</button>
            <button type="button" class="prompt-tool" title="标题" @mousedown.prevent
                    @click="execBlock('h2')">标题</button>
            <span class="prompt-tool-sep"></span>
            <button type="button" class="prompt-tool bold" title="加粗" @mousedown.prevent
                    @click="execEditor('bold')">B</button>
            <button type="button" class="prompt-tool italic" title="斜体" @mousedown.prevent
                    @click="execEditor('italic')">I</button>
            <span class="prompt-tool-sep"></span>
            <button type="button" class="prompt-tool" title="无序列表" @mousedown.prevent
                    @click="execEditor('insertUnorderedList')">• 列表</button>
            <button type="button" class="prompt-tool" title="有序列表" @mousedown.prevent
                    @click="execEditor('insertOrderedList')">1. 编号</button>
            <button type="button" class="prompt-tool" title="引用" @mousedown.prevent
                    @click="execBlock('blockquote')">引用</button>
            <span class="prompt-tool-sep"></span>
            <button type="button" class="prompt-tool" title="代码块" @mousedown.prevent
                    @click="insertCodeBlock">代码块</button>
            <button type="button" class="prompt-tool" title="表格" @mousedown.prevent
                    @click="insertTable">表格</button>
            <button type="button" class="prompt-tool" title="流程图" @mousedown.prevent
                    @click="insertMermaid">流程图</button>
            <span class="prompt-toolbar-tip">支持 Markdown / Mermaid</span>
          </div>
          <div ref="promptEditorEl" class="prompt-canvas markdown-body" contenteditable="true"
               data-placeholder="填写角色、任务、输入变量、输出格式与约束，可直接使用上方组件排版。"
               @input="syncPromptContent" @blur="syncPromptContent"></div>
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <div class="prompt-dialog-footer">
        <div class="prompt-dialog-actions">
          <el-button @click="visible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="savePrompt">保存</el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { nextTick, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { renderMarkdown } from '@/utils/markdown'
import { htmlToMarkdown } from '@/utils/promptMarkdown'
import type { PromptDraft } from './types'

const props = defineProps({
  prompt: { type: Object as () => PromptDraft | null, default: null },
  saving: { type: Boolean, default: false }
})

const emit = defineEmits<{ (e: 'save', draft: PromptDraft): void }>()

const visible = defineModel<boolean>('visible', { default: false })

const form = reactive({ id: '', name: '', content: '' })
const promptEditorEl = ref<HTMLElement | null>(null)

watch([() => props.prompt, visible], () => {
  if (!visible.value) return
  form.id = props.prompt?.id || ''
  form.name = props.prompt?.name || ''
  form.content = props.prompt?.content || ''
  nextTick(seedPromptEditor)
}, { immediate: true })

/** 以预览样式打开编辑区：Markdown 渲染后直接可视化编辑，保存时再还原为 Markdown。 */
async function seedPromptEditor() {
  await nextTick()
  const editor = promptEditorEl.value
  if (!editor) return
  // 已有内容（含用户刚输入的内容）时不重复覆盖
  if (editor.innerHTML.trim() && form.content.trim()) return
  editor.innerHTML = form.content ? renderMarkdown(form.content) : ''
}

function syncPromptContent() {
  const editor = promptEditorEl.value
  if (!editor) return
  form.content = htmlToMarkdown(editor)
}

function execEditor(command: string, value?: string) {
  const editor = promptEditorEl.value
  if (!editor) return
  editor.focus()
  document.execCommand(command, false, value)
  syncPromptContent()
}

function execBlock(tag: string) {
  execEditor('formatBlock', `<${tag}>`)
}

function insertHtml(html: string) {
  execEditor('insertHTML', html)
}

function insertCodeBlock() {
  insertHtml('<pre><code>在这里输入代码</code></pre><p><br></p>')
}

function insertTable() {
  insertHtml(
    '<table><thead><tr><th>列1</th><th>列2</th></tr></thead>'
    + '<tbody><tr><td>内容</td><td>内容</td></tr><tr><td>内容</td><td>内容</td></tr></tbody></table>'
    + '<p><br></p>'
  )
}

function insertMermaid() {
  insertHtml(
    '<div class="mermaid-block"><pre class="mermaid-source">graph TD\n  A[开始] --> B[结束]</pre></div>'
    + '<p><br></p>'
  )
}

function savePrompt() {
  syncPromptContent()
  if (!form.name.trim() || !form.content.trim()) {
    ElMessage.warning('标题和内容不能为空')
    return
  }
  emit('save', { id: form.id, name: form.name, content: form.content })
}
</script>

<style scoped>
.prompt-dialog {
  max-width: 94vw;
}

.prompt-dialog :deep(.el-dialog__body) {
  padding-top: 8px;
}

.prompt-editor {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 420px;
  border: 1px solid #e8edf7;
  border-radius: 12px;
  background: #fff;
  overflow: hidden;
}

.prompt-toolbar {
  flex: 0 0 auto;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 2px;
  padding: 7px 9px;
  background: #f7f9fd;
  border-bottom: 1px solid #eef1f8;
}

.prompt-tool {
  height: 26px;
  padding: 0 8px;
  border: 1px solid transparent;
  border-radius: 7px;
  background: transparent;
  color: #4e5969;
  font-size: 12px;
  font-family: inherit;
  cursor: pointer;
}

.prompt-tool:hover {
  background: #fff;
  border-color: #e3e8f3;
  color: #3b5bdb;
}

.prompt-tool.bold {
  font-weight: 700;
}

.prompt-tool.italic {
  font-style: italic;
}

.prompt-tool-sep {
  width: 1px;
  height: 16px;
  margin: 0 4px;
  background: #e5e6eb;
}

.prompt-toolbar-tip {
  margin-left: auto;
  color: #a9aeb8;
  font-size: 11px;
}

.prompt-canvas {
  flex: 1;
  overflow-y: auto;
  padding: 14px 16px;
  outline: none;
  font-size: 13px;
  line-height: 1.7;
}

.prompt-canvas:empty::before {
  content: attr(data-placeholder);
  color: #c9cdd4;
  pointer-events: none;
}

.prompt-dialog-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
}

.prompt-dialog-actions {
  display: flex;
  gap: 8px;
}

.prompt-canvas::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.prompt-canvas::-webkit-scrollbar-thumb {
  background-color: rgba(148, 163, 184, 0.24);
  border: 2px solid transparent;
  background-clip: padding-box;
  border-radius: 8px;
}

.prompt-canvas::-webkit-scrollbar-thumb:hover {
  background-color: rgba(148, 163, 184, 0.45);
}

.prompt-canvas::-webkit-scrollbar-track {
  background: transparent;
}

@media (max-width: 900px) {
  .prompt-editor {
    height: 60vh;
  }

  .prompt-toolbar-tip {
    display: none;
  }
}
</style>

<style scoped src="../../../../assets/aiMarkdown.css"></style>
