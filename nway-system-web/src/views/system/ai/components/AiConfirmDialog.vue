<template>
  <el-dialog v-model="visible" title="确认执行" width="640px" :close-on-click-modal="false"
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
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { AiFieldDef, ChatEvent } from '@/api/ai'
import { toolLabel } from '@/utils/aiTool'
import { rememberedInterfaceFields, rememberedInterfaceName } from '@/utils/aiView'
import type { ConfirmSubmitPayload } from './types'

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

const emit = defineEmits<{ (e: 'submit', payload: ConfirmSubmitPayload): void }>()

const visible = ref(false)
const replyId = ref('')
const confirmForms = ref<ConfirmForm[]>([])

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

/** 打开确认弹窗：由流式事件的 confirm.required 或面板交接内容触发 */
function open(event: ChatEvent) {
  replyId.value = event.replyId || ''
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
  visible.value = true
}

/** 未在接口入参中声明的参数同样会提交，单独列出让用户核对。 */
function extraArgsJson(form: ConfirmForm) {
  const extra: Record<string, any> = {}
  form.extraArgs.forEach((key) => {
    extra[key] = form.args[key]
  })
  return JSON.stringify(extra, null, 2)
}

function submitConfirm(confirmed: boolean) {
  const decisions = confirmForms.value.map((form) => ({
    toolCallId: form.toolCallId,
    name: form.name,
    confirmed,
    args: { ...form.fullInput, args: form.args }
  }))
  const currentReplyId = replyId.value
  visible.value = false
  emit('submit', { confirmed, replyId: currentReplyId, decisions })
}

function onConfirmClosed() {
  confirmForms.value = []
  replyId.value = ''
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

/** 关闭并清空（面板交接前或页面重置时调用） */
function close() {
  visible.value = false
  onConfirmClosed()
}

defineExpose({ open, close })
</script>

<style scoped>
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

.confirm-body::-webkit-scrollbar,
.confirm-raw pre::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.confirm-body::-webkit-scrollbar-thumb,
.confirm-raw pre::-webkit-scrollbar-thumb {
  background-color: rgba(148, 163, 184, 0.24);
  border: 2px solid transparent;
  background-clip: padding-box;
  border-radius: 8px;
}

.confirm-body::-webkit-scrollbar-thumb:hover,
.confirm-raw pre::-webkit-scrollbar-thumb:hover {
  background-color: rgba(148, 163, 184, 0.45);
}

.confirm-body::-webkit-scrollbar-track,
.confirm-raw pre::-webkit-scrollbar-track {
  background: transparent;
}
</style>
