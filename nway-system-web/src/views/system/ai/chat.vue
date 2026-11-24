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
        <div class="chat-header-right">
          <div class="chat-header-note">AI 生成内容仅供参考</div>
        </div>
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

          <div v-for="msg in messages" :key="msg.id" class="message" :class="msg.role">
            <div v-if="msg.role === 'user'" class="bubble user-bubble">
              <div v-if="msg.text" class="msg-text">{{ msg.text }}</div>
              <div v-if="msg.attachments && msg.attachments.length" class="msg-attachments">
                <div v-for="file in msg.attachments" :key="file.fileId" class="msg-file"
                     :title="`${file.name} · ${formatSize(file.size)}`" @click="previewFile(file)">
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
                <div class="thinking-head" @click="toggleThinking(msg.id)">
                  <el-icon class="thinking-arrow" :class="{ open: thinkingOpen.has(msg.id) }">
                    <ArrowRight />
                  </el-icon>
                  <span>思考过程</span>
                </div>
                <pre v-show="thinkingOpen.has(msg.id)" class="thinking-text"
                     @scroll="trackFollowScroll">{{ msg.thinking }}</pre>
              </div>

              <div v-for="tool in msg.tools" :key="tool.id" class="tool-card" :class="tool.status">
                <div class="tool-head" :class="{ 'is-toggle': hasToolBody(tool) }"
                     @click="hasToolBody(tool) && toggleTool(tool.id)">
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
                  <template v-if="tool.view">
                    <div v-if="tool.view.message" class="tool-message" :class="{ error: tool.view.success === false }">
                      {{ tool.view.message }}
                    </div>
                    <AiViewBlock v-if="tool.view.success !== false" :view="tool.view" />
                  </template>

                  <pre v-else-if="tool.result" class="tool-result"
                       @scroll="trackFollowScroll">{{ toolResultText(tool.result) }}</pre>

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
              <div v-if="msg.streaming" class="typing"><span></span><span></span><span></span></div>
              <div v-else-if="ttsSupported && msg.text" class="msg-actions">
                <button type="button" class="msg-action" :class="{ active: speakingId === msg.id }"
                        :title="speakingId === msg.id ? '停止播报' : '播报这条回复'"
                        @click="speakMessage(msg)">
                  <el-icon>
                    <Mute v-if="speakingId === msg.id" />
                    <Headset v-else />
                  </el-icon>
                  <span>{{ speakingId === msg.id ? '停止播报' : '语音播报' }}</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

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
            <button v-if="file.error" class="attach-retry" @click="retryUpload(file)">重试</button>
            <button class="attach-remove" title="移除" @click="removeAttachment(file)">
              <el-icon><Close /></el-icon>
            </button>
          </div>
        </div>

        <div class="composer-toolbar">
          <el-tooltip content="新对话" placement="top" :show-after="300">
            <button class="tool-btn icon-only" type="button" @click="newChat">
              <el-icon><Plus /></el-icon>
            </button>
          </el-tooltip>

          <el-tooltip :content="autoSpeakTips" placement="top" :show-after="300">
            <button class="tool-btn" :class="{ active: autoSpeak }" type="button"
                    :disabled="!ttsSupported" @click="toggleAutoSpeak">
              <el-icon><Headset /></el-icon>
              <span>自动播报</span>
            </button>
          </el-tooltip>

          <el-popover v-model:visible="promptPopover" placement="top-end" width="min(360px, 90vw)"
                      trigger="click" popper-class="prompt-popover" @show="loadPrompts">
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
                              @click.stop="toggleQuickPrompt(prompt)">
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
                               @click="editPrompt(prompt)">编辑</el-button>
                    <el-button link type="danger" size="small" :icon="Delete"
                               @click="removePrompt(prompt)">删除</el-button>
                  </div>
                </div>
                <div v-if="prompts.length === 0" class="prompt-empty">暂无提示词，点击「新建」创建</div>
              </div>
            </div>
          </el-popover>

          <el-popover v-model:visible="historyPopover" placement="top-end" width="min(380px, 90vw)"
                      trigger="click" popper-class="history-popover" @before-enter="loadHistory">
            <template #reference>
              <button class="tool-btn" type="button" title="历史记录">
                <el-icon><Clock /></el-icon>
                <span>历史记录</span>
              </button>
            </template>
            <div class="history-panel">
              <div class="history-panel-head">
                <div>
                  <div class="history-panel-title">历史记录</div>
                </div>
              </div>
              <el-input v-model="historyKeyword" class="history-search" size="small" clearable
                        placeholder="搜索会话标题" :prefix-icon="Search"
                        @keyup.enter="searchHistory" />
              <div v-loading="historyLoading" class="history-list">
                <div v-for="conv in conversations" :key="conv.id" class="history-item"
                     :class="{ active: conv.id === activeId }" @click="openConversation(conv)">
                  <span class="history-title" :title="conv.title">{{ conv.title || '新对话' }}</span>
                  <span class="history-time">{{ conversationTime(conv) }}</span>
                  <el-dropdown trigger="click" @command="(cmd: string) => onConvCommand(cmd, conv)">
                    <el-icon class="history-more" @click.stop><MoreFilled /></el-icon>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="rename">重命名</el-dropdown-item>
                        <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
                <div v-if="!historyLoading && conversations.length === 0" class="history-empty">
                  {{ historyKeyword ? '没有匹配的会话' : '还没有历史会话' }}
                </div>
              </div>
              <div v-if="historyTotal > historyPageSize" class="history-pagination">
                <el-pagination size="small" background layout="prev, pager, next"
                               :total="historyTotal" :page-size="historyPageSize"
                               :current-page="historyCurrent"
                               @current-change="onHistoryPageChange" />
              </div>
            </div>
          </el-popover>
        </div>

        <div v-if="voiceActive" class="voice-live">
          <span class="voice-live-dot"></span>
          正在聆听，再次点击麦克风结束
        </div>

        <div class="input-row">
          <textarea ref="inputEl" v-model="input" class="chat-input" rows="2"
                    placeholder="输入消息，Enter 发送，Shift + Enter 换行"
                    @keydown="onKeydown" @paste="onPaste" @input="autoResize"></textarea>
          <div class="input-actions">
            <button class="icon-btn" title="添加附件" @click="pickFiles">
              <el-icon><Paperclip /></el-icon>
            </button>
            <button class="icon-btn" :class="{ 'voice-recording': voiceActive }"
                    :title="voiceTitle" :disabled="!speechRecognitionSupported"
                    @click="toggleVoice">
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
            <button v-if="!streaming" class="send-btn" title="发送" :disabled="!canSend" @click="send">
              <el-icon><Promotion /></el-icon>
            </button>
            <button v-else class="send-btn stop" title="停止生成" @click="stop">
              <el-icon><VideoPause /></el-icon>
            </button>
          </div>
        </div>
        <div class="composer-hint">
          支持图片、PDF、Word、Excel、CSV 等文件。附件默认不解析，仅在需要理解内容时按需解析。
        </div>
        <input ref="fileInput" type="file" multiple class="hidden-input" @change="onFilesPicked" />
      </div>
    </main>

    <!-- 写操作确认 -->
    <el-dialog v-model="confirmState.visible" title="确认执行" width="640px" :close-on-click-modal="false"
               class="confirm-dialog" @closed="onConfirmClosed">
      <div class="confirm-body">
        <div class="confirm-tip">以下操作会修改业务数据，请核对参数后执行。</div>
        <div v-for="form in confirmForms" :key="form.toolCallId" class="confirm-section">
          <div class="confirm-title">
            <span class="confirm-name">{{ form.interfaceName || toolLabel(form.name) }}</span>
            <span v-if="form.interfaceCode" class="confirm-code">{{ form.interfaceCode }}</span>
            <el-tag v-if="form.riskLevel === 'HIGH_RISK'" size="small" type="danger">高风险</el-tag>
          </div>
          <el-form label-width="120px" label-position="left">
            <el-form-item v-for="field in form.submittedFields" :key="field.key" :label="field.label"
                          :required="field.required">
              <el-select v-if="isEnum(field)" v-model="form.args[field.key]" clearable style="width: 100%">
                <el-option v-for="opt in field.options || []" :key="String(opt.value)"
                           :label="opt.label" :value="opt.value" />
              </el-select>
              <el-select v-else-if="field.type === 'MULTI_ENUM'" v-model="form.args[field.key]"
                         multiple clearable style="width: 100%">
                <el-option v-for="opt in field.options || []" :key="String(opt.value)"
                           :label="opt.label" :value="opt.value" />
              </el-select>
              <el-switch v-else-if="field.type === 'BOOLEAN'" v-model="form.args[field.key]" />
              <el-input-number v-else-if="field.type === 'INTEGER' || field.type === 'NUMBER'"
                               v-model="form.args[field.key]" style="width: 100%"
                               :precision="field.type === 'INTEGER' ? 0 : 2" />
              <el-date-picker v-else-if="field.type === 'DATE'" v-model="form.args[field.key]"
                              type="date" value-format="YYYY-MM-DD" style="width: 100%" />
              <el-date-picker v-else-if="field.type === 'DATETIME'" v-model="form.args[field.key]"
                              type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
              <el-input v-else-if="field.type === 'TEXT'" v-model="form.args[field.key]"
                        type="textarea" :rows="3" />
              <div v-else-if="field.type === 'FILE' || field.type === 'FILE_ARRAY'" class="file-field">
                <el-tag v-if="form.args[field.key]" type="info">{{ form.args[field.key] }}</el-tag>
                <span class="hint">请先在对话中上传附件，由助手填入文件 ID</span>
              </div>
              <el-input v-else-if="field.type === 'OBJECT' || field.type === 'ARRAY'"
                        type="textarea" :rows="3"
                        :model-value="jsonText(form, field)"
                        @update:model-value="(value: string) => setJsonArg(form, field, value)" />
              <el-input v-else v-model="form.args[field.key]" />
              <div v-if="field.description" class="field-desc">{{ field.description }}</div>
            </el-form-item>
          </el-form>
          <div v-if="!form.fields.length" class="confirm-raw">
            <div class="confirm-raw-tip">该接口未声明入参，将按以下参数原样转发：</div>
            <pre>{{ JSON.stringify(form.args, null, 2) }}</pre>
          </div>
          <div v-else-if="form.unchangedCount" class="confirm-unchanged">
            该接口仅提交以上填写字段，其余 {{ form.unchangedCount }} 个字段保持不变。
          </div>
          <div v-if="form.extraArgs.length && form.fields.length" class="confirm-raw">
            <div class="confirm-raw-tip">以下参数未在接口入参中声明，将原样提交：</div>
            <pre>{{ extraArgsJson(form) }}</pre>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="submitConfirm(false)">取消</el-button>
        <el-button type="primary" @click="submitConfirm(true)">确认执行</el-button>
      </template>
    </el-dialog>

    <!-- 提示词编辑 -->
    <el-dialog v-model="promptDialog" :title="promptForm.id ? '编辑提示词' : '新建提示词'"
               width="min(920px, 94vw)" class="prompt-dialog" destroy-on-close
               @open="seedPromptEditor" @opened="seedPromptEditor">
      <el-form label-position="top">
        <el-form-item label="标题" required>
          <el-input v-model="promptForm.name" maxlength="60" show-word-limit
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
          <el-button :disabled="!activeId" :loading="extracting" @click="extractFromConversation">
            从当前对话提取
          </el-button>
          <div class="prompt-dialog-actions">
            <el-button @click="promptDialog = false">取消</el-button>
            <el-button type="primary" :loading="promptSaving" @click="savePersonalPrompt">保存</el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <el-image-viewer v-if="viewerUrl" :url-list="[viewerUrl]" @close="viewerUrl = ''" />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowRight, ChatDotRound, Clock, Close, Cpu, Delete, Document, Edit, EditPen, Headset, Hide,
  MagicStick, Microphone, MoreFilled, Mute, Paperclip, Plus, Promotion, Search, Star, StarFilled,
  VideoPause, View
} from '@element-plus/icons-vue'
import {
  assetUrl, deleteChatFile, deleteConversation, deletePrompt,
  extractPrompt, getMessages, listChatModels, listConversations, listPrompts, renameConversation,
  savePrompt, streamChat, streamConfirm, updatePrompt, uploadChatFile,
  type AiChatModelVo, type AiConversationVo, type AiFieldDef, type AiFileVo, type AiMessage,
  type AiPromptVo, type ChatEvent
} from '@/api/ai'
import AiViewBlock from '@/components/ai/AiViewBlock.vue'
import AiMarkdown from '@/components/ai/AiMarkdown.vue'
import {
  buildInvokeView, inputFieldLabel, rememberInterfaceConfig, rememberInterfaceFields,
  rememberedInterfaceFields, rememberedInterfaceName, splitViewSegments, toolResultText,
  type AiSegment, type AiView
} from '@/utils/aiView'
import { renderMarkdown } from '@/utils/markdown'
import { followBottom, followLast, trackFollowScroll } from '@/utils/scrollFollow'
import { useAiChatHandoffStore } from '@/stores/aiChatStore'

interface UploadItem extends AiFileVo {
  localId: string
  rawFile?: File
}

interface UiTool {
  id: string
  name: string
  status: 'running' | 'done' | 'error' | 'denied' | 'interrupted'
  result?: string
  /** 调用结果解析成功时的结构化视图，未解析时回退展示原文 */
  view?: AiView | null
}

interface UiMessage {
  id: string
  role: 'user' | 'assistant'
  text: string
  thinking: string
  tools: UiTool[]
  /** 正文按文本/视图/待完成视图拆分后的分段 */
  segments: AiSegment[]
  attachments?: AiFileVo[]
  streaming?: boolean
  error?: string
}

interface ConfirmForm {
  toolCallId: string
  name: string
  interfaceName?: string
  interfaceCode?: string
  riskLevel?: string
  fields: AiFieldDef[]
  /** 本次实际提交的字段：有值的字段 + 缺失的必填字段 */
  submittedFields: AiFieldDef[]
  /** 未提交、保持原值的字段数 */
  unchangedCount: number
  args: Record<string, any>
  /** 未在接口入参声明、但会原样提交的字段 */
  extraArgs: string[]
  /** 原始 tool input（含 interfaceCode），确认时只替换其中的 args */
  fullInput: Record<string, any>
}

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

function modelById(id?: string) {
  return chatModels.value.find((model) => model.id === id)
}

/** 推理深度展示：常见档位中文化，自定义档位原样展示。 */
function reasoningLabel(level?: string) {
  if (!level) return ''
  const labels: Record<string, string> = { low: '低', medium: '中', high: '高', max: '最高' }
  return labels[level] || level
}

const conversations = ref<AiConversationVo[]>([])
const activeId = ref<string | null>(null)
const messages = ref<UiMessage[]>([])
const input = ref('')
const attachments = ref<UploadItem[]>([])
const streaming = ref(false)
const thinkingOpen = ref(new Set<string>())
/** 工具结果默认展开，点击标题收起 */
const toolCollapsed = ref(new Set<string>())
const scrollEl = ref<HTMLElement | null>(null)
const inputEl = ref<HTMLTextAreaElement | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const dragActive = ref(false)
const viewerUrl = ref('')
let abortController: AbortController | null = null

/** 轻量对话面板「完整对话」交接过来的内容，消费后立即清空 */
const aiChatHandoff = useAiChatHandoffStore()

const confirmState = reactive({ visible: false, replyId: '' })
const confirmForms = ref<ConfirmForm[]>([])

const promptPopover = ref(false)
const promptDialog = ref(false)
const prompts = ref<AiPromptVo[]>([])

/** 快捷提示词即被星标标记的已有提示词。 */
const quickPrompts = computed(() => prompts.value.filter((prompt) => quickPromptIds.value.includes(prompt.id)))
const promptSaving = ref(false)
const extracting = ref(false)
const promptForm = reactive({ id: '', name: '', content: '' })
const promptEditorEl = ref<HTMLElement | null>(null)

const canSend = computed(() => {
  const hasText = input.value.trim().length > 0
  const hasFiles = attachments.value.some((f) => !f.error && !f.uploading)
  return (hasText || hasFiles) && !attachments.value.some((f) => f.uploading)
})

const historyPopover = ref(false)
const historyKeyword = ref('')
const historyCurrent = ref(1)
const historyPageSize = 10
const historyTotal = ref(0)
const historyLoading = ref(false)
let historySearchTimer: ReturnType<typeof setTimeout> | null = null
let historyLoadSeq = 0

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

/** 输入关键字自动搜索，防抖避免每次按键都请求。 */
watch(historyKeyword, () => {
  if (historySearchTimer) clearTimeout(historySearchTimer)
  historySearchTimer = setTimeout(searchHistory, 300)
})

/** 历史记录按页加载，支持标题关键字搜索。 */
async function loadHistory() {
  const seq = ++historyLoadSeq
  historyLoading.value = true
  try {
    const res = await listConversations(historyCurrent.value, historyPageSize,
      historyKeyword.value.trim() || undefined)
    if (seq !== historyLoadSeq) return
    // 分页数据在 Result.data 里：{ data: 列表, total: 总数 }
    conversations.value = res.data?.data || []
    historyTotal.value = res.data?.total || 0
  } catch (e) {
    // http 拦截器已提示
  } finally {
    if (seq === historyLoadSeq) historyLoading.value = false
  }
}

function searchHistory() {
  if (historySearchTimer) {
    clearTimeout(historySearchTimer)
    historySearchTimer = null
  }
  historyCurrent.value = 1
  loadHistory()
}

function onHistoryPageChange(page: number) {
  historyCurrent.value = page
  loadHistory()
}

/** 历史列表只展示到分钟，避免长日期挤占标题空间。 */
function conversationTime(conv: AiConversationVo) {
  const raw = conv.lastMessageAt || conv.createTime
  return raw ? raw.slice(5, 16) : ''
}

function newChat() {
  stopVoice()
  stopSpeaking()
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
  stopVoice()
  stopSpeaking()
  // 清掉页面上原有的确认表单，避免提交到已切换的会话
  confirmState.visible = false
  confirmForms.value = []
  confirmState.replyId = ''
  historyPopover.value = false
  activeId.value = handoff.conversationId || null
  messages.value = (handoff.messages || []).map(toHandoffMessage)
  input.value = handoff.input || ''
  attachments.value = (handoff.attachments || []).map((file: AiFileVo) => ({ ...file, localId: uid() }))
  if (handoff.confirm) openConfirm(handoff.confirm)
  nextTick(() => {
    autoResize()
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
    tools: (raw.tools || []).map((tool: any): UiTool => ({
      id: tool.id || uid(),
      name: tool.name || INVOKE_TOOL,
      status: tool.status === 'error' || tool.status === 'denied'
        ? tool.status
        // 面板交接前已停止流式，执行中的调用不会再有结果事件
        : tool.status === 'interrupted' || tool.status === 'running'
          ? 'interrupted'
          : 'done',
      result: tool.result,
      // 无结果时保留面板的兜底说明，避免同一条消息在两处显示不同文案；有结果则按接口元数据重新渲染
      view: tool.result && isResultTool(tool.name || INVOKE_TOOL)
        ? buildInvokeView(tool.result)
        : tool.result ? null : tool.view || null
    })),
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
  stopVoice()
  stopSpeaking()
  activeId.value = conv.id
  messages.value = []
  try {
    const res = await getMessages(conv.id)
    const list = (res.data || []).map(toUiMessage)
    restoreHistoryTools(list)
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
      if (block.name === CONFIG_TOOL) {
        // 查询接口配置不再单独占用工具卡片，并入思考过程展示
        rememberInterfaceConfig(block.result)
        ui.thinking = appendText(ui.thinking, buildConfigThinking(block.result))
        return
      }
      const toolName = block.name || INVOKE_TOOL
      ui.tools.push({
        id: block.id || uid(),
        name: toolName,
        // 只有工具调用、没有结果与状态：通常是等待确认后中断，标记为未完成
        status: block.state === 'ERROR' || block.state === 'DENIED'
          ? 'error'
          : block.result || block.state ? 'done' : 'interrupted',
        result: block.result,
        view: isResultTool(toolName) ? buildInvokeView(block.result) : null
      })
    }
  })
  ui.text = texts.join('\n')
  syncSegments(ui)
  return ui
}

/**
 * 确认执行的写操作，工具结果可能没回填到历史消息里（只有 tool_use 没有 result）。
 * 该轮之后还有助手正文，说明这次调用已经执行完成，不能因为缺少结果就显示未完成。
 */
function restoreHistoryTools(list: UiMessage[]) {
  list.forEach((message, index) => {
    if (!message.tools.some((tool) => tool.status === 'interrupted')) return
    let completed = false
    for (let i = index + 1; i < list.length; i += 1) {
      const next = list[i]
      if (next.role === 'user') break
      if (next.text.trim()) {
        completed = true
        break
      }
    }
    if (!completed) return
    message.tools.forEach((tool) => {
      if (tool.status !== 'interrupted') return
      tool.status = 'done'
      // 结果没有随历史落库：说明调用已完成但历史里不含返回数据，与简易面板保持同一文案
      if (!tool.result) {
        tool.view = { type: 'empty', text: '调用已完成，历史记录中未保留返回数据。' }
      }
    })
  })
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

// ==================== 发送 ====================

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
    event.preventDefault()
    send()
  }
}

function autoResize() {
  const el = inputEl.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 150) + 'px'
}

function isQuickPrompt(prompt: AiPromptVo) {
  return quickPromptIds.value.includes(prompt.id)
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
  if (isQuickPrompt(prompt)) {
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

async function send() {
  if (!canSend.value || streaming.value) return
  stopVoice()
  stopSpeaking()
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
  autoResize()
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

async function runStream(
  starter: (onEvent: (event: ChatEvent) => void, signal: AbortSignal) => Promise<void>,
  target: UiMessage
) {
  streaming.value = true
  target.streaming = true
  stopSpeaking()
  abortController = new AbortController()
  try {
    await starter((event) => handleEvent(event, target), abortController.signal)
  } catch (error: any) {
    if (error?.name === 'AbortError') {
      target.streaming = false
      if (!target.text && !target.thinking && target.tools.length === 0) {
        target.text = '已停止生成。'
      }
    } else {
      target.error = error?.message || '请求失败，请稍后重试'
    }
  } finally {
    target.streaming = false
    syncSegments(target)
    streaming.value = false
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
      break
    case 'thinking.delta':
      target.thinking += event.delta || ''
      followStreamTail('.thinking-text')
      break
    case 'message.delta':
      target.text += event.delta || ''
      syncSegments(target)
      break
    case 'tool.call.start': {
      if (event.name === CONFIG_TOOL) break
      if (!findTool(event.toolCallId)) {
        target.tools.push({ id: event.toolCallId || uid(), name: event.name, status: 'running' })
      }
      break
    }
    case 'tool.result.delta': {
      if (event.name === CONFIG_TOOL) {
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
      if (event.name === CONFIG_TOOL) {
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
        if (isResultTool(tool.name)) tool.view = buildInvokeView(tool.result)
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
      openConfirm(event)
      break
    case 'reply.end':
      target.streaming = false
      syncSegments(target)
      if (autoSpeak.value) speakMessage(target)
      break
    case 'error':
      target.error = event.message || 'AI 服务异常'
      target.streaming = false
      syncSegments(target)
      break
    default:
      break
  }
  scrollToBottom()
}

function stop() {
  abortController?.abort()
  streaming.value = false
  // 停止后不再收到工具结果事件，把执行中的调用标记为未完成
  messages.value.forEach((message) => {
    message.tools?.forEach((tool) => {
      if (tool.status === 'running') tool.status = 'interrupted'
    })
  })
}

// ==================== 语音输入与播报 ====================

/** Web Speech API 的最小类型声明，避免引入额外类型依赖。 */
interface SpeechRecognitionResultLike {
  isFinal: boolean
  [index: number]: { transcript: string }
}

interface SpeechRecognitionEventLike {
  resultIndex: number
  results: {
    length: number
    [index: number]: SpeechRecognitionResultLike
  }
}

interface SpeechRecognitionErrorLike {
  error: string
}

interface SpeechRecognitionLike {
  lang: string
  continuous: boolean
  interimResults: boolean
  onresult: ((event: SpeechRecognitionEventLike) => void) | null
  onerror: ((event: SpeechRecognitionErrorLike) => void) | null
  onend: (() => void) | null
  start: () => void
  stop: () => void
  abort: () => void
}

type SpeechRecognitionCtor = new () => SpeechRecognitionLike

const SpeechRecognitionClass: SpeechRecognitionCtor | null =
  (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition || null
const speechRecognitionSupported = !!SpeechRecognitionClass
const ttsSupported = typeof window !== 'undefined' && 'speechSynthesis' in window

const VOICE_ERRORS: Record<string, string> = {
  'not-allowed': '麦克风权限被拒绝，请在浏览器设置中允许后重试',
  'service-not-allowed': '当前环境未授权语音识别服务',
  'audio-capture': '未检测到可用的麦克风',
  network: '语音识别服务连接失败，请检查网络后重试'
}

const AUTO_SPEAK_KEY = 'nway-ai-auto-speak'
const MAX_SPEECH_CHUNK = 160

const voiceActive = ref(false)
const speakingId = ref<string | null>(null)
const autoSpeak = ref(readAutoSpeak())

let recognition: SpeechRecognitionLike | null = null
let voiceBaseText = ''
let voiceManualStop = false
let speechToken = 0

const voiceTitle = computed(() => {
  if (!speechRecognitionSupported) return '当前浏览器不支持语音输入，建议使用 Chrome 或 Edge'
  return voiceActive.value ? '停止语音输入' : '语音输入'
})

const autoSpeakTips = computed(() => {
  if (!ttsSupported) return '当前浏览器不支持语音播报'
  return autoSpeak.value ? '关闭回复自动播报' : '开启回复自动播报'
})

function readAutoSpeak(): boolean {
  try {
    return localStorage.getItem(AUTO_SPEAK_KEY) === '1'
  } catch (e) {
    return false
  }
}

function toggleAutoSpeak() {
  autoSpeak.value = !autoSpeak.value
  try {
    if (autoSpeak.value) localStorage.setItem(AUTO_SPEAK_KEY, '1')
    else localStorage.removeItem(AUTO_SPEAK_KEY)
  } catch (e) {
    // 本地存储不可用时仅在当前会话生效
  }
  if (!autoSpeak.value) stopSpeaking()
}

function toggleVoice() {
  if (voiceActive.value) stopVoice()
  else startVoice()
}

/** 语音识别结果实时写入输入框：最终结果累加，临时结果只做展示。 */
function startVoice() {
  if (!SpeechRecognitionClass || voiceActive.value) return
  stopSpeaking()
  const instance = new SpeechRecognitionClass()
  instance.lang = 'zh-CN'
  instance.continuous = true
  instance.interimResults = true
  voiceBaseText = input.value
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
    if (finalText) voiceBaseText = appendDictation(voiceBaseText, finalText)
    input.value = voiceBaseText + interimText
    nextTick(() => {
      autoResize()
      inputEl.value?.focus()
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
    voiceBaseText = input.value
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

function stopVoice() {
  voiceManualStop = true
  voiceActive.value = false
  recognition?.stop()
  recognition = null
}

/** 中英文混说时在英文单词之间补空格，中文直接拼接。 */
function appendDictation(base: string, text: string) {
  if (!base) return text
  return /[A-Za-z0-9]$/.test(base) && /^[A-Za-z0-9]/.test(text) ? `${base} ${text}` : base + text
}

function speakMessage(message: UiMessage) {
  if (!ttsSupported || !message.text) return
  if (speakingId.value === message.id) {
    stopSpeaking()
    return
  }
  const text = toSpeakableText(message.text)
  if (!text) return
  stopSpeaking()
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

function stopSpeaking() {
  speechToken += 1
  speakingId.value = null
  if (ttsSupported) window.speechSynthesis.cancel()
}

function pickChineseVoice(): SpeechSynthesisVoice | null {
  const voices = window.speechSynthesis.getVoices()
  return voices.find((voice) => /^zh[-_]cn/i.test(voice.lang))
    || voices.find((voice) => /^zh/i.test(voice.lang))
    || null
}

/** 播报前去掉 Markdown 标记、代码块、链接地址与表格，只保留自然语言。 */
function toSpeakableText(markdown: string): string {
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

/** 长回复按句子切成小段依次播报，避免部分浏览器截断超长文本。 */
function splitForSpeech(text: string): string[] {
  const sentences = text.match(/[^。！？!?；;\n]+[。！？!?；;\n]?/g) || [text]
  const chunks: string[] = []
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

onBeforeUnmount(() => {
  stopVoice()
  stopSpeaking()
  if (historySearchTimer) clearTimeout(historySearchTimer)
})

// ==================== 确认表单 ====================

/**
 * 模型/事件里的入参可能是对象、JSON 字符串，或把参数平铺在 input 上。
 * 统一还原为 { input, args }，保证确认表单能展示全部参数。
 */
function normalizeToolInput(raw: any): { input: Record<string, any>; args: Record<string, any> } {
  let input = raw
  if (typeof input === 'string') {
    try {
      input = JSON.parse(input)
    } catch (e) {
      input = {}
    }
  }
  if (!input || typeof input !== 'object' || Array.isArray(input)) input = {}
  let args = input.args
  if (typeof args === 'string') {
    try {
      args = JSON.parse(args)
    } catch (e) {
      args = {}
    }
  }
  if (!args || typeof args !== 'object' || Array.isArray(args)) {
    // 兼容模型把参数平铺在 input 上：除 interfaceCode / args 外的字段都视为入参
    const { interfaceCode, args: invalidArgs, ...rest } = input
    args = rest
  }
  return { input, args: { ...args } }
}

function normalizeFields(raw: any): AiFieldDef[] {
  if (Array.isArray(raw)) return raw
  if (typeof raw === 'string') {
    try {
      const parsed = JSON.parse(raw)
      return Array.isArray(parsed) ? parsed : []
    } catch (e) {
      return []
    }
  }
  return []
}

function openConfirm(event: ChatEvent) {
  confirmState.replyId = event.replyId || ''
  confirmForms.value = (event.toolCalls || []).map((call: any) => {
    const { input, args } = normalizeToolInput(call.input)
    const interfaceCode = typeof input.interfaceCode === 'string' ? input.interfaceCode : ''
    const declared = normalizeFields(call.inputSchema)
    // 事件未带 inputSchema 时用 get_interface_config 的缓存兜底，避免确认表单缺少参数项
    const fields = declared.length ? declared : rememberedInterfaceFields(interfaceCode)
    const interfaceName = call.interfaceName || rememberedInterfaceName(interfaceCode)
    rememberInterfaceFields(interfaceCode, interfaceName, fields)
    const declaredKeys = new Set(fields.map((field) => field.key))
    // 写接口通常只提交变更字段：有值的字段才展示，避免用户误以为空字段是数据缺失
    const submittedFields = fields.filter((field) => field.required
      || Object.prototype.hasOwnProperty.call(args, field.key))
    return {
      toolCallId: call.id,
      name: call.name,
      interfaceName,
      interfaceCode,
      riskLevel: call.riskLevel,
      fields,
      submittedFields,
      unchangedCount: fields.length - submittedFields.length,
      args,
      extraArgs: Object.keys(args).filter((key) => !declaredKeys.has(key)),
      fullInput: { ...input, args }
    } as ConfirmForm
  })
  confirmState.visible = true
}

/** 未在接口入参中声明的参数同样会提交，单独列出让用户核对。 */
function extraArgsJson(form: ConfirmForm) {
  const extra: Record<string, any> = {}
  form.extraArgs.forEach((key) => {
    extra[key] = form.args[key]
  })
  return JSON.stringify(extra, null, 2)
}

async function submitConfirm(confirmed: boolean) {
  if (!activeId.value) return
  const decisions = confirmForms.value.map((form) => ({
    toolCallId: form.toolCallId,
    name: form.name,
    confirmed,
    args: { ...form.fullInput, args: form.args }
  }))
  const replyId = confirmState.replyId
  confirmState.visible = false
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
    assistant
  )
  if (!confirmed && !assistant.text && !assistant.error) {
    assistant.text = '已取消本次操作。'
  }
}

function onConfirmClosed() {
  confirmForms.value = []
  confirmState.replyId = ''
}

function isEnum(field: AiFieldDef) {
  return field.type === 'ENUM' && !!field.options?.length
}

function jsonText(form: ConfirmForm, field: AiFieldDef) {
  const value = form.args[field.key]
  if (value === undefined || value === null) {
    return field.type === 'ARRAY' ? '[]' : '{}'
  }
  if (typeof value === 'string') return value
  try {
    return JSON.stringify(value, null, 2)
  } catch (e) {
    return String(value)
  }
}

function setJsonArg(form: ConfirmForm, field: AiFieldDef, text: string) {
  try {
    form.args[field.key] = JSON.parse(text || (field.type === 'ARRAY' ? '[]' : '{}'))
  } catch (e) {
    // 保留原始文本，交给后端校验并返回明确错误
    form.args[field.key] = text
  }
}

// ==================== 附件 ====================

function pickFiles() {
  fileInput.value?.click()
}

async function onFilesPicked(event: Event) {
  const target = event.target as HTMLInputElement
  const files = Array.from(target.files || [])
  target.value = ''
  await addFiles(files)
}

async function addFiles(files: File[]) {
  const remain = 5 - attachments.value.length
  if (remain <= 0) {
    ElMessage.warning('单次最多 5 个文件')
    return
  }
  for (const file of files.slice(0, remain)) {
    if (file.size > 20 * 1024 * 1024) {
      ElMessage.warning(`${file.name} 超过 20MB`)
      continue
    }
    const item = reactive<UploadItem>({
      localId: uid(),
      fileId: '',
      name: file.name,
      size: file.size,
      mime: file.type,
      uploading: true,
      progress: 0,
      rawFile: file
    })
    attachments.value.push(item)
    try {
      const res = await uploadChatFile(file, (percent) => { item.progress = percent })
      Object.assign(item, res.data, { uploading: false, progress: 100 })
    } catch (error: any) {
      item.uploading = false
      item.error = error?.response?.data?.msg || error?.message || '上传失败'
    }
  }
}

async function retryUpload(item: UploadItem) {
  if (!item.rawFile) return
  item.error = undefined
  item.uploading = true
  item.progress = 0
  try {
    const res = await uploadChatFile(item.rawFile, (percent) => { item.progress = percent })
    Object.assign(item, res.data, { uploading: false, progress: 100 })
    item.error = undefined
  } catch (error: any) {
    item.uploading = false
    item.error = error?.response?.data?.msg || '上传失败'
  }
}

async function removeAttachment(item: UploadItem) {
  attachments.value = attachments.value.filter((f) => f.localId !== item.localId)
  if (item.fileId) {
    try {
      await deleteChatFile(item.fileId)
    } catch (e) {
      // 已发送或已删除，忽略
    }
  }
}

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
    await addFiles(files)
  }
}

async function onPaste(event: ClipboardEvent) {
  const files = Array.from(event.clipboardData?.files || [])
  if (files.length) {
    event.preventDefault()
    await addFiles(files)
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

function fileKindLabel(file: AiFileVo) {
  if (file.kind === 'IMAGE') return '图片'
  if (file.kind === 'PDF') return 'PDF'
  if (file.kind === 'WORD') return 'Word'
  if (file.kind === 'EXCEL') return 'Excel'
  if (file.kind === 'PPT') return 'PPT'
  if (file.kind === 'TEXT') return '文本'
  return '文件'
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

function resetPromptForm() {
  promptForm.id = ''
  promptForm.name = ''
  promptForm.content = ''
}

function startNewPrompt() {
  resetPromptForm()
  promptPopover.value = false
  promptDialog.value = true
}

function editPrompt(prompt: AiPromptVo) {
  promptForm.id = prompt.id
  promptForm.name = prompt.name
  promptForm.content = prompt.content
  promptPopover.value = false
  promptDialog.value = true
}

/** 点击提示词后填入输入框，方便补充上下文后再发送。 */
function usePrompt(prompt: AiPromptVo) {
  const content = prompt.content || ''
  if (!content) return
  input.value = input.value.trim() ? `${input.value.trimEnd()}\n\n${content}` : content
  promptPopover.value = false
  nextTick(() => {
    autoResize()
    inputEl.value?.focus()
  })
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

// ==================== 提示词富文本编辑 ====================

/** 以预览样式打开编辑区：Markdown 渲染后直接可视化编辑，保存时再还原为 Markdown。 */
async function seedPromptEditor() {
  await nextTick()
  const editor = promptEditorEl.value
  if (!editor) return
  // 已有内容（含用户刚输入的内容）时不重复覆盖
  if (editor.innerHTML.trim() && promptForm.content.trim()) return
  editor.innerHTML = promptForm.content ? renderMarkdown(promptForm.content) : ''
}

/** 外部写入内容（如从对话提取）后强制重建编辑区。 */
function reseedPromptEditor() {
  const editor = promptEditorEl.value
  if (!editor) return
  editor.innerHTML = promptForm.content ? renderMarkdown(promptForm.content) : ''
}

function syncPromptContent() {
  const editor = promptEditorEl.value
  if (!editor) return
  promptForm.content = htmlToMarkdown(editor)
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

/** 将富文本编辑区的 HTML 还原为 Markdown，覆盖提示词常用的排版组件。 */
function htmlToMarkdown(root: HTMLElement): string {
  return normalizeMarkdown(serializeChildren(root))
}

function normalizeMarkdown(text: string) {
  return text
    .replace(/\u00a0/g, ' ')
    .replace(/[ \t]+\n/g, '\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}

function serializeChildren(node: Node): string {
  let result = ''
  node.childNodes.forEach((child) => {
    result += serializeNode(child)
  })
  return result
}

function serializeNode(node: Node): string {
  if (node.nodeType === Node.TEXT_NODE) {
    return (node.textContent || '').replace(/\u00a0/g, ' ')
  }
  if (node.nodeType !== Node.ELEMENT_NODE) return ''
  const el = node as HTMLElement
  const tag = el.tagName.toLowerCase()
  const inner = () => serializeChildren(el)
  const wrap = (mark: string) => {
    const text = inner()
    return text.trim() ? `${mark}${text}${mark}` : text
  }
  switch (tag) {
    case 'br':
      return '\n'
    case 'strong':
    case 'b':
      return wrap('**')
    case 'em':
    case 'i':
      return wrap('*')
    case 'del':
    case 's':
      return wrap('~~')
    case 'code':
      return `\`${el.textContent || ''}\``
    case 'a': {
      const href = el.getAttribute('href') || ''
      return href ? `[${inner()}](${href})` : inner()
    }
    case 'img': {
      const src = el.getAttribute('src') || ''
      return src ? `![${el.getAttribute('alt') || ''}](${src})` : ''
    }
    case 'h1':
    case 'h2':
    case 'h3':
    case 'h4':
    case 'h5':
    case 'h6':
      return `${'#'.repeat(Number(tag[1]))} ${inner().trim()}\n\n`
    case 'p':
      return `${inner()}\n\n`
    case 'div': {
      if (el.classList.contains('mermaid-block')) {
        const code = el.querySelector('.mermaid-source')?.textContent || ''
        return `\`\`\`mermaid\n${code.trim()}\n\`\`\`\n\n`
      }
      return `${inner()}\n\n`
    }
    case 'blockquote': {
      const lines = inner().trim().split('\n')
      return lines.map((line) => (line ? `> ${line}` : '>')).join('\n') + '\n\n'
    }
    case 'pre': {
      const codeEl = el.querySelector('code')
      const code = (codeEl?.textContent ?? el.textContent ?? '').replace(/\n$/, '')
      const lang = (codeEl?.className.match(/language-([\w-]+)/) || [])[1] || ''
      return `\`\`\`${lang}\n${code}\n\`\`\`\n\n`
    }
    case 'ul':
    case 'ol':
      return serializeList(el, 0)
    case 'hr':
      return '---\n\n'
    case 'table':
      return serializeTable(el)
    default:
      return inner()
  }
}

function serializeList(list: HTMLElement, depth: number): string {
  const ordered = list.tagName.toLowerCase() === 'ol'
  const indent = '  '.repeat(depth)
  let result = ''
  let index = 1
  Array.from(list.children).forEach((child) => {
    if (child.tagName.toLowerCase() !== 'li') return
    let text = ''
    let nested = ''
    child.childNodes.forEach((node) => {
      if (node.nodeType === Node.ELEMENT_NODE) {
        const tag = (node as HTMLElement).tagName.toLowerCase()
        if (tag === 'ul' || tag === 'ol') {
          nested += serializeList(node as HTMLElement, depth + 1)
          return
        }
      }
      text += serializeNode(node)
    })
    result += `${indent}${ordered ? `${index}.` : '-'} ${text.replace(/\n+/g, ' ').trim()}\n`
    result += nested
    index += 1
  })
  return result + (depth === 0 ? '\n' : '')
}

function serializeTable(table: HTMLElement): string {
  const rows = Array.from(table.querySelectorAll('tr'))
  if (!rows.length) return ''
  const cellText = (cell: Element) => serializeChildren(cell)
    .replace(/\n+/g, ' ')
    .trim()
    .replace(/\|/g, '\\|')
  const header = Array.from(rows[0].children).map(cellText)
  const lines = [
    `| ${header.join(' | ')} |`,
    `| ${header.map(() => '---').join(' | ')} |`
  ]
  rows.slice(1).forEach((row) => {
    lines.push(`| ${Array.from(row.children).map(cellText).join(' | ')} |`)
  })
  return lines.join('\n') + '\n\n'
}

async function savePersonalPrompt() {
  syncPromptContent()
  if (!promptForm.name.trim() || !promptForm.content.trim()) {
    ElMessage.warning('标题和内容不能为空')
    return
  }
  promptSaving.value = true
  try {
    if (promptForm.id) {
      await updatePrompt(promptForm.id, {
        name: promptForm.name,
        content: promptForm.content,
        promptType: 'TASK'
      })
      ElMessage.success('已保存')
    } else {
      await savePrompt({
        name: promptForm.name,
        content: promptForm.content,
        promptType: 'TASK'
      })
      ElMessage.success('已保存为个人提示词')
    }
    promptDialog.value = false
    resetPromptForm()
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

async function extractFromConversation() {
  if (!activeId.value) return
  extracting.value = true
  try {
    const res = await extractPrompt({
      conversationId: activeId.value,
      name: promptForm.name || '对话提取',
      promptType: 'TASK'
    })
    const draft = res.data || res
    promptForm.name = promptForm.name || draft.name || '对话提取'
    promptForm.content = draft.content || ''
    await nextTick()
    reseedPromptEditor()
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

/** 工具卡片有结果或需要兜底说明时可展开；仅执行中且无内容时不响应收起。 */
function hasToolBody(tool: UiTool) {
  // 未完成与已结束但无结果时也展示兜底文案，避免展开后一片空白
  if (tool.status === 'interrupted' || tool.status === 'done') return true
  return !!(tool.view || tool.result)
}

function toggleTool(id: string) {
  if (toolCollapsed.value.has(id)) toolCollapsed.value.delete(id)
  else toolCollapsed.value.add(id)
  toolCollapsed.value = new Set(toolCollapsed.value)
}

function toolLabel(name?: string) {
  if (name === CONFIG_TOOL) return '查询接口配置'
  if (name === QUERY_TOOL) return '查询结果'
  if (name === INVOKE_TOOL) return '调用业务接口'
  return name || '工具调用'
}

function isResultTool(name?: string) {
  return name === INVOKE_TOOL || name === QUERY_TOOL
}

function toolStatusLabel(tool: UiTool) {
  if (tool.status === 'running') return '执行中'
  if (tool.status === 'error') return '失败'
  if (tool.status === 'denied') return '已拒绝'
  if (tool.status === 'interrupted') return '未完成'
  return '完成'
}

function formatSize(size?: number) {
  if (!size) return '0 B'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

// ==================== 工具结果展示 ====================

const CONFIG_TOOL = 'get_interface_config'
const INVOKE_TOOL = 'invoke_interface'
const QUERY_TOOL = 'query_interface'

/** get_interface_config 的流式结果先暂存，结束后整体解析为思考内容。 */
const pendingConfigResults = new Map<string, string>()

function appendText(base: string, text?: string | null) {
  if (!text) return base
  return base ? `${base}\n\n${text}` : text
}

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

<style scoped lang="scss">
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

/* ============ 历史记录 ============ */
.history-panel-head {
  padding-bottom: 10px;
  border-bottom: 1px solid #eef1f8;
}

.history-panel-title {
  font-size: 15px;
  font-weight: 600;
  color: #232a38;
}

.history-panel-sub {
  margin-top: 2px;
  color: #a4adbe;
  font-size: 11px;
}

.history-search {
  margin-top: 10px;
}

.history-search :deep(.el-input__wrapper) {
  border-radius: 10px;
  box-shadow: 0 0 0 1px #e8ecf5 inset;
}

.history-list {
  position: relative;
  min-height: 60px;
  max-height: 320px;
  overflow-y: auto;
  padding-top: 8px;
}

.history-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 11px;
  margin-bottom: 2px;
  border-radius: 10px;
  cursor: pointer;
  color: #4e5969;
  font-size: 13px;
  transition: background 0.15s, color 0.15s, box-shadow 0.15s;
}

.history-item:hover {
  background: #f2f5fb;
}

.history-item.active {
  background: #fff;
  color: #3b5bdb;
  box-shadow: 0 4px 14px -8px rgba(20, 30, 60, 0.25);
}

.history-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-time {
  flex: 0 0 auto;
  color: #a4adbe;
  font-size: 10.5px;
}

.history-more {
  flex: 0 0 auto;
  color: #a4adbe;
  opacity: 0;
}

.history-item:hover .history-more {
  opacity: 1;
}

.history-empty {
  color: #a4adbe;
  font-size: 12.5px;
  text-align: center;
  padding: 24px 0;
}

.history-pagination {
  display: flex;
  justify-content: center;
  padding-top: 10px;
  border-top: 1px solid #eef1f8;
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

.chat-header-note {
  flex: 0 0 auto;
  padding: 4px 10px;
  border: 1px solid #e8edf6;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  color: #98a1b3;
  font-size: 11.5px;
  white-space: nowrap;
}

.chat-header-right {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 10px;
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
  width: 100%;
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

.markdown-body {
  white-space: normal;
}

.markdown-body :deep(p) {
  margin: 0 0 10px;
}

.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 6px 0 10px;
  padding-left: 22px;
}

.markdown-body :deep(li) {
  margin: 3px 0;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  margin: 16px 0 8px;
  font-weight: 600;
  line-height: 1.4;
  color: #1b2230;
}

.markdown-body :deep(h1) { font-size: 20px; }
.markdown-body :deep(h2) { font-size: 18px; }
.markdown-body :deep(h3) { font-size: 16px; }
.markdown-body :deep(h4) { font-size: 15px; }

.markdown-body :deep(code) {
  background: #f2f4f8;
  border-radius: 5px;
  padding: 2px 6px;
  color: #4a5568;
  font-size: 12.5px;
  font-family: Consolas, Monaco, monospace;
}

.markdown-body :deep(pre) {
  background: #f7f9fd;
  border: 1px solid #e8edf7;
  border-radius: 12px;
  padding: 12px 14px;
  margin: 10px 0;
  overflow-x: auto;
}

.markdown-body :deep(pre code) {
  background: transparent;
  padding: 0;
  font-size: 12.5px;
}

.markdown-body :deep(.mermaid-block) {
  margin: 12px 0;
  padding: 12px 14px;
  background: #fff;
  border: 1px solid #e8edf7;
  border-radius: 12px;
  overflow-x: auto;
  text-align: center;
}

.markdown-body :deep(.mermaid-block.mermaid-error) {
  border-color: #ffd2d2;
}

.markdown-body :deep(.mermaid-block svg) {
  max-width: 100%;
  height: auto;
}

.markdown-body :deep(.mermaid-block .mermaid-source) {
  margin: 0;
  padding: 0;
  border: none;
  background: transparent;
  text-align: left;
}

.markdown-body :deep(blockquote) {
  margin: 10px 0;
  padding: 6px 14px;
  border-left: 3px solid #c3d1ff;
  border-radius: 0 10px 10px 0;
  background: #f7f9ff;
  color: #5a6579;
}

.markdown-body :deep(table) {
  width: 100%;
  border: 1px solid #e8edf7;
  border-collapse: separate;
  border-spacing: 0;
  border-radius: 12px;
  overflow: hidden;
  margin: 10px 0;
  background: #fff;
  font-size: 13.5px;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: none;
  border-bottom: 1px solid #eff2f8;
  padding: 9px 12px;
  text-align: left;
  vertical-align: top;
}

.markdown-body :deep(tr:last-child td) {
  border-bottom: none;
}

.markdown-body :deep(th) {
  background: #f7f9fd;
  color: #5b6579;
  font-weight: 600;
}

.markdown-body :deep(tbody tr:nth-child(even) td) {
  background: #fbfcfe;
}

.markdown-body :deep(a) {
  color: #3b5bdb;
  text-decoration: none;
}

.markdown-body :deep(a:hover) {
  text-decoration: underline;
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

/* ============ 确认弹窗 ============ */
.confirm-body {
  max-height: 62vh;
  overflow-y: auto;
  padding-right: 4px;
}

.confirm-tip {
  color: #8f959e;
  font-size: 12px;
  margin-bottom: 12px;
}

.confirm-section {
  border: 1px solid #eef0f3;
  border-radius: 12px;
  padding: 12px 14px 0;
  margin-bottom: 12px;
}

.confirm-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  margin-bottom: 12px;
}

.confirm-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.confirm-code {
  color: #a4adbe;
  font-size: 12px;
  font-weight: 400;
}

.confirm-unchanged {
  padding: 9px 12px;
  margin-bottom: 12px;
  border-radius: 8px;
  background: #f7f9fd;
  color: #8f959e;
  font-size: 12px;
  line-height: 1.6;
}

.confirm-raw {
  margin-bottom: 12px;
}

.confirm-raw-tip {
  color: #8f959e;
  font-size: 12px;
  margin-bottom: 6px;
}

.confirm-raw pre {
  margin: 0;
  padding: 10px 12px;
  max-height: 240px;
  overflow: auto;
  background: #f7f8fa;
  border-radius: 8px;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
}

.field-desc {
  width: 100%;
  font-size: 11px;
  color: #a9aeb8;
  line-height: 1.5;
  margin-top: 2px;
}

.file-field {
  display: flex;
  align-items: center;
  gap: 8px;
}

.file-field .hint {
  color: #a9aeb8;
  font-size: 12px;
}

/* ============ 提示词 ============ */
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
  justify-content: space-between;
}

.prompt-dialog-actions {
  display: flex;
  gap: 8px;
}

/* ============ 滚动条 ============ */
.message-scroll::-webkit-scrollbar,
.history-list::-webkit-scrollbar,
.prompt-panel-list::-webkit-scrollbar,
.prompt-canvas::-webkit-scrollbar,
.thinking-text::-webkit-scrollbar,
.tool-result::-webkit-scrollbar,
.confirm-body::-webkit-scrollbar,
.confirm-raw pre::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.message-scroll::-webkit-scrollbar-thumb,
.history-list::-webkit-scrollbar-thumb,
.prompt-panel-list::-webkit-scrollbar-thumb,
.prompt-canvas::-webkit-scrollbar-thumb,
.thinking-text::-webkit-scrollbar-thumb,
.tool-result::-webkit-scrollbar-thumb,
.confirm-body::-webkit-scrollbar-thumb,
.confirm-raw pre::-webkit-scrollbar-thumb {
  background-color: rgba(148, 163, 184, 0.24);
  border: 2px solid transparent;
  background-clip: padding-box;
  border-radius: 8px;
}

.message-scroll::-webkit-scrollbar-thumb:hover,
.history-list::-webkit-scrollbar-thumb:hover,
.prompt-panel-list::-webkit-scrollbar-thumb:hover,
.prompt-canvas::-webkit-scrollbar-thumb:hover,
.thinking-text::-webkit-scrollbar-thumb:hover,
.tool-result::-webkit-scrollbar-thumb:hover,
.confirm-body::-webkit-scrollbar-thumb:hover,
.confirm-raw pre::-webkit-scrollbar-thumb:hover {
  background-color: rgba(148, 163, 184, 0.45);
}

.message-scroll::-webkit-scrollbar-track,
.history-list::-webkit-scrollbar-track,
.prompt-panel-list::-webkit-scrollbar-track,
.prompt-canvas::-webkit-scrollbar-track,
.thinking-text::-webkit-scrollbar-track,
.tool-result::-webkit-scrollbar-track,
.confirm-body::-webkit-scrollbar-track,
.confirm-raw pre::-webkit-scrollbar-track {
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

  .chat-header-note {
    display: none;
  }

  .composer {
    max-width: calc(100% - 44px);
    margin-bottom: 12px;
    padding: 10px 12px 12px;
  }

  .assistant-block {
    padding: 12px 14px 14px;
    border-radius: 16px 16px 16px 6px;
  }

  .prompt-editor {
    height: 60vh;
  }

  .prompt-toolbar-tip {
    display: none;
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

  .composer-toolbar {
    flex-wrap: wrap;
    justify-content: flex-start;
  }

  .model-select {
    width: 130px;
  }
}
</style>
