import type { AiFileVo } from '@/api/ai'
import type { AiSegment, AiView } from '@/utils/aiView'

export interface UiTool {
  id: string
  name: string
  status: 'running' | 'done' | 'error' | 'denied' | 'interrupted'
  result?: string
  /** 调用结果解析成功时的结构化视图，未解析时回退展示原文 */
  view?: AiView | null
}

export interface UiMessage {
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
  /** 模型因单次输出上限被截断，正文可能不完整 */
  truncated?: boolean
}

export interface PromptDraft {
  id: string
  name: string
  content: string
}

export interface ConfirmSubmitPayload {
  confirmed: boolean
  replyId: string
  decisions: Array<{ toolCallId: string; name: string; confirmed: boolean; args: Record<string, any> }>
}
