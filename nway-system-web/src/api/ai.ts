/**
 * AI 助手 API。
 *
 * 流式接口使用 fetch + ReadableStream 解析 SSE（EventSource 不支持 POST + Authorization）。
 */
import http from '@/js/httpProxy.js'
import { getToken } from '@/utils/auth'

const env = import.meta.env
const API_BASE: string = env.VITE_API_URL || ''

export interface Result<T = any> {
  code: number
  msg: string
  data: T
}

export interface PageVo<T = any> {
  data: T[]
  total: number
}

export interface AiFieldDef {
  key: string
  label: string
  type: string
  required?: boolean
  default?: any
  description?: string
  location?: string
  valueSource?: string
  valueExpression?: string
  validation?: Record<string, any>
  options?: Array<{ label: string; value: any }>
  optionSource?: Record<string, any>
  optionalHint?: string
  jsonPath?: string
  items?: any
  properties?: AiFieldDef[]
}

/** 出参数组元素结构，仅用于元数据说明与展示，运行时不逐字段映射 */
export interface AiOutputItem {
  type: string
  description?: string
  properties?: AiOutputField[]
}

export interface AiOutputField {
  key: string
  label?: string
  path?: string
  type: string
  description?: string
  sensitive?: boolean
  items?: AiOutputItem
  properties?: AiOutputField[]
}

export interface AiInterfaceConfig {
  /** 编辑时回传，后端按主键更新；新建时为空 */
  id?: string
  interfaceCode: string
  name: string
  description: string
  appCode: string
  httpMethod: string
  path: string
  contentType?: string
  riskLevel: string
  confirmRequired: boolean
  inputSchema: AiFieldDef[]
  outputSchema?: AiOutputField[]
  invokeConfig?: Record<string, any>
  searchKeywords?: string
  examples?: string[]
  metadataConfidence?: string
  status?: string
  version?: number
  tenantCode?: string
  /** PUBLIC：所有具备 AI 对话权限的用户可调用；RESTRICTED：需显式授权 execute */
  accessMode?: 'PUBLIC' | 'RESTRICTED'
}

export interface AiInterfaceVo extends AiInterfaceConfig {
  id: string
  /** 本接口直接授权的主体数（不含 ai:* 分组授权） */
  grantCount?: number
  /** “全部 AI 接口”分组授权的主体数 */
  allGrantCount?: number
  createTime?: string
  modifiedTime?: string
}

export interface AiInvokeResultVo {
  success: boolean
  interfaceCode?: string
  code?: any
  message?: string
  data?: any
  latencyMs?: number
  errorCode?: string
}

/** 接口配置可选的应用（sys_app，租户隔离后包含公共租户的共享应用） */
export interface AiInterfaceAppOption {
  appCode: string
  appName: string
  apiBaseUrl?: string
  tenantCode?: string
  tenantName?: string
}

/** 接口配置可选的归属租户（当前用户可管理范围） */
export interface AiTenantOption {
  tenantCode: string
  tenantName: string
}

export interface AiModelVo {
  id: string
  name: string
  baseUrl: string
  modelName: string
  temperature?: number
  maxTokens?: number
  supportsToolCalling?: boolean
  isDefault?: boolean
  status?: string
  apiKeyConfigured?: boolean
  modifiedTime?: string
}

export interface AiPromptVo {
  id: string
  name: string
  promptType: string
  scopeType: string
  scopeId?: string
  roleCode?: string
  content: string
  variablesJson?: string
  priority?: number
  status?: string
  scopeLabel?: string
  modifiedTime?: string
}

export interface AiConversationVo {
  id: string
  title: string
  status?: string
  lastMessageAt?: string
  createTime?: string
}

export interface AiFileVo {
  fileId: string
  name: string
  size?: number
  mime?: string
  kind?: string
  previewUrl?: string
  extractionStatus?: string
  summary?: string
  /** 前端本地状态 */
  uploading?: boolean
  progress?: number
  error?: string
}

export interface AiMessageBlock {
  type: 'text' | 'thinking' | 'tool_use' | 'attachments' | string
  text?: string
  id?: string
  name?: string
  input?: any
  result?: string
  state?: string
  /** type=attachments 时随消息展示的附件（预览地址在读取历史时重新生成） */
  files?: AiFileVo[]
}

export interface AiMessage {
  id: string
  role: 'user' | 'assistant' | 'tool'
  timestamp?: string
  blocks: AiMessageBlock[]
}

export interface ChatEvent {
  type: string
  [key: string]: any
}

// ==================== 接口配置 ====================

export const listInterfaces = (params?: Record<string, any>) =>
  http.get<Result<AiInterfaceVo[]>>('/ai/interfaces', { params }).then((r: any) => r.data)

export const getInterface = (id: string) =>
  http.get<Result<AiInterfaceVo>>(`/ai/interfaces/${id}`).then((r: any) => r.data)

/** 应用下拉：sys_app 中当前用户可读的应用（含公共租户的公共应用） */
export const listInterfaceApps = () =>
  http.get<Result<AiInterfaceAppOption[]>>('/ai/interfaces/apps').then((r: any) => r.data)

/** 归属租户下拉：当前用户可管理的租户（公共租户需显式授权） */
export const listInterfaceTenants = () =>
  http.get<Result<AiTenantOption[]>>('/ai/interfaces/tenants').then((r: any) => r.data)

export const saveInterface = (config: AiInterfaceConfig) =>
  http.post<Result<AiInterfaceVo>>('/ai/interfaces', config).then((r: any) => r.data)

export const publishInterface = (id: string) =>
  http.post<Result<void>>(`/ai/interfaces/${id}/publish`).then((r: any) => r.data)

export const offlineInterface = (id: string) =>
  http.post<Result<void>>(`/ai/interfaces/${id}/offline`).then((r: any) => r.data)

export const deleteInterface = (id: string) =>
  http.delete<Result<void>>(`/ai/interfaces/${id}`).then((r: any) => r.data)

export const importInterfaces = (payload: any) =>
  http.post<Result<any>>('/ai/interfaces/import', payload).then((r: any) => r.data)

export const exportInterfaces = () =>
  http.get<Result<any>>('/ai/interfaces/export').then((r: any) => r.data)

/** 测试调用等待上限：覆盖后端测试默认 120s 超时并留出余量，避免慢速大模型接口被前端提前中断 */
const TEST_TIMEOUT_MS = 130_000

/** 接口调用测试：args 按 inputSchema 校验后发往目标应用 */
export const testInterface = (id: string, args: Record<string, any>) =>
  http.post<Result<AiInvokeResultVo>>(`/ai/interfaces/${id}/test`, { args }, { timeout: TEST_TIMEOUT_MS }).then((r: any) => r.data)

// ==================== 接口权限 ====================

export interface AiPermissionGrantPayload {
  subjectType: 'ROLE' | 'USER' | 'POSITION'
  subjectId: string
  effect: 'ALLOW' | 'DENY'
}

export interface AiPermissionGrantItem extends AiPermissionGrantPayload {
  id: string
  subjectName?: string
  actions?: string
  tenantCode?: string
  /** INTERFACE：本接口；ALL：全部 AI 接口（ai:*） */
  scope: 'INTERFACE' | 'ALL'
}

export interface AiPermissionDetail {
  id: string
  interfaceCode: string
  resourceCode: string
  accessMode: 'PUBLIC' | 'RESTRICTED'
  resourceRegistered: boolean
  resourceTenantScope?: string
  grants: AiPermissionGrantItem[]
}

export interface AiPermissionCheck {
  employeeId: string
  tenantCode?: string
  interfaceCode: string
  resourceCode: string
  accessMode: 'PUBLIC' | 'RESTRICTED'
  allowed: boolean
  reason: string
  decisionId?: string
  matchedGrantIds: string[]
  resourceRegistered: boolean
  resourceTenantScope?: string
  grantCount: number
  allGrantCount: number
}

export const getInterfacePermissions = (id: string) =>
  http.get<Result<AiPermissionDetail>>(`/ai/interfaces/${id}/permissions`).then((r: any) => r.data)

export const saveInterfacePermissions = (
  id: string,
  payload: {
    accessMode: 'PUBLIC' | 'RESTRICTED'
    grants: AiPermissionGrantPayload[]
    allGrants: AiPermissionGrantPayload[]
  }
) => http.put<Result<AiPermissionDetail>>(`/ai/interfaces/${id}/permissions`, payload).then((r: any) => r.data)

export const checkInterfacePermission = (id: string, employeeId: string) =>
  http.get<Result<AiPermissionCheck>>(`/ai/interfaces/${id}/permissions/check`, { params: { employeeId } })
    .then((r: any) => r.data)

export interface AiPermissionPreview {
  affectedUsers: number
  sampleUsers: string[]
}

export const previewInterfacePermissions = (
  id: string,
  payload: {
    accessMode: 'PUBLIC' | 'RESTRICTED'
    grants: AiPermissionGrantPayload[]
    allGrants: AiPermissionGrantPayload[]
  }
) => http.post<Result<AiPermissionPreview>>(`/ai/interfaces/${id}/permissions/preview`, payload)
  .then((r: any) => r.data)

// ==================== 模型 ====================

export const listModels = () =>
  http.get<Result<AiModelVo[]>>('/ai/models').then((r: any) => r.data)

export const saveModel = (model: any) =>
  http.post<Result<AiModelVo>>('/ai/models', model).then((r: any) => r.data)

export const deleteModel = (id: string) =>
  http.delete<Result<void>>(`/ai/models/${id}`).then((r: any) => r.data)

// ==================== 提示词 ====================

export const listPrompts = (includeAll = false) =>
  http.get<Result<AiPromptVo[]>>('/ai/prompts', { params: { includeAll } }).then((r: any) => r.data)

export const savePrompt = (prompt: any) =>
  http.post<Result<AiPromptVo>>('/ai/prompts', prompt).then((r: any) => r.data)

export const updatePrompt = (id: string, prompt: any) =>
  http.put<Result<AiPromptVo>>(`/ai/prompts/${id}`, prompt).then((r: any) => r.data)

export const deletePrompt = (id: string) =>
  http.delete<Result<void>>(`/ai/prompts/${id}`).then((r: any) => r.data)

export const promotePrompt = (id: string, scopeType: string, scopeId?: string, roleCode?: string) =>
  http.post<Result<AiPromptVo>>(`/ai/prompts/${id}/promote`, null, { params: { scopeType, scopeId, roleCode } })
    .then((r: any) => r.data)

export const extractPrompt = (body: { conversationId: string; name?: string; promptType?: string }) =>
  http.post<Result<{ content: string; name: string; promptType: string }>>('/ai/prompts/extract', body)
    .then((r: any) => r.data)

// ==================== 会话 ====================

export const listConversations = (current = 1, size = 50, keyword?: string) =>
  http.get<Result<PageVo<AiConversationVo>>>('/ai/conversations', { params: { current, size, keyword } })
    .then((r: any) => r.data)

export const createConversation = (message?: string) =>
  http.post<Result<AiConversationVo>>('/ai/conversations', { message }).then((r: any) => r.data)

export const getMessages = (conversationId: string) =>
  http.get<Result<AiMessage[]>>(`/ai/conversations/${conversationId}/messages`).then((r: any) => r.data)

export const renameConversation = (id: string, title: string) =>
  http.patch<Result<void>>(`/ai/conversations/${id}`, { title }).then((r: any) => r.data)

export const archiveConversation = (id: string) =>
  http.post<Result<void>>(`/ai/conversations/${id}/archive`).then((r: any) => r.data)

export const deleteConversation = (id: string) =>
  http.delete<Result<void>>(`/ai/conversations/${id}`).then((r: any) => r.data)

// ==================== 附件 ====================

export const uploadChatFile = (file: File, onProgress?: (percent: number) => void) => {
  const form = new FormData()
  form.append('file', file)
  return http.post<Result<AiFileVo>>('/ai/files', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: (e: any) => {
      if (onProgress && e.total) {
        onProgress(Math.round((e.loaded / e.total) * 100))
      }
    }
  }).then((r: any) => r.data)
}

export const deleteChatFile = (id: string) =>
  http.delete<Result<void>>(`/ai/files/${id}`).then((r: any) => r.data)

export const retryExtract = (id: string) =>
  http.post<Result<AiFileVo>>(`/ai/files/${id}/extract`).then((r: any) => r.data)

/**
 * 把后端返回的 context-path 相对地址转换为浏览器可访问地址。
 * 开发环境 VITE_API_URL=/api/nway-system，后端返回 /nway-system/file/... → /api/nway-system/file/...
 */
export function assetUrl(path?: string): string {
  if (!path) return ''
  if (/^https?:\/\//i.test(path)) return path
  const prefix = API_BASE.replace(/\/[^/]+$/, '')
  return prefix + (path.startsWith('/') ? path : `/${path}`)
}

// ==================== SSE ====================

/**
 * 解析单个 SSE 事件：多行 data: 用换行拼接，再交给业务按 JSON 解析。
 */
function dispatchSseChunk(chunk: string, onEvent: (event: ChatEvent) => void) {
  const dataLines = chunk.split('\n')
    .map((line) => line.replace(/\r$/, ''))
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).replace(/^ /, ''))
  if (dataLines.length === 0) return
  const raw = dataLines.join('\n').trim()
  if (!raw) return
  try {
    onEvent(JSON.parse(raw))
  } catch (e) {
    console.warn('无法解析 SSE 事件', raw)
  }
}

async function streamSse(url: string, body: any, onEvent: (event: ChatEvent) => void,
                         signal?: AbortSignal): Promise<void> {
  const response = await fetch(API_BASE + url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: getToken() || ''
    },
    body: JSON.stringify(body),
    signal
  })
  if (!response.ok) {
    if (response.status === 401) {
      location.href = '/login'
      throw new Error('登录已失效，请重新登录')
    }
    const text = await response.text().catch(() => '')
    throw new Error(text || `请求失败（HTTP ${response.status}）`)
  }
  if (!response.body) {
    throw new Error('浏览器不支持流式响应')
  }
  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  // 统一换行符，避免服务端/代理使用 \r\n 时无法按空行切分事件
  const append = (text: string) => {
    buffer = (buffer + text).replace(/\r\n/g, '\n')
  }
  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    append(decoder.decode(value, { stream: true }))
    let index = buffer.indexOf('\n\n')
    while (index >= 0) {
      dispatchSseChunk(buffer.slice(0, index), onEvent)
      buffer = buffer.slice(index + 2)
      index = buffer.indexOf('\n\n')
    }
  }
  // 尾包可能没有以空行结束，仍需分发，避免丢失最后一个事件
  append(decoder.decode())
  if (buffer.trim()) {
    dispatchSseChunk(buffer, onEvent)
  }
}

/** 当前页面上下文：由前端在发送消息时提取的页面文本快照 */
export interface AiPageContext {
  title: string
  url: string
  renderType: string
  text: string
  truncated?: boolean
}

export const streamChat = (
  body: {
    conversationId?: string | null
    message: string
    fileIds?: string[]
    pageContext?: AiPageContext | null
  },
  onEvent: (event: ChatEvent) => void,
  signal?: AbortSignal
) => streamSse('/ai/chat/stream', body, onEvent, signal)

export const streamConfirm = (
  body: { conversationId: string; replyId: string; decisions: any[] },
  onEvent: (event: ChatEvent) => void,
  signal?: AbortSignal
) => streamSse('/ai/chat/confirm', body, onEvent, signal)
