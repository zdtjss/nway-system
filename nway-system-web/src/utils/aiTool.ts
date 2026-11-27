/**
 * AI 对话工具调用的公共定义与处理。
 * 轻量面板与完整对话页共用工具名称、视图构建与历史工具状态恢复逻辑。
 */
import {
  EXPORT_TOOL,
  buildBatchQueryViews,
  buildExportView,
  buildInvokeView,
  type AiView
} from '@/utils/aiView'

/** 接口检索（新名称）；旧会话仍使用 get_interface_config */
export const SEARCH_TOOL = 'search_interfaces'
export const CONFIG_TOOL = 'get_interface_config'
export const INVOKE_TOOL = 'invoke_interface'
export const QUERY_TOOL = 'query_interface'
export const QUERY_BATCH_TOOL = 'query_interfaces'
export const EXPORT_QUERY_DATA_TOOL = 'export_query_data'
export const EXPORT_CONVERSATION_TOOL = 'export_conversation'
export const EXPORT_TOOL_RESULT_TOOL = 'export_tool_result'
export const READ_FILE_TOOL = 'read_file'

/** 接口检索工具：新旧名称都并入思考过程，不单独占用工具卡片 */
export const CONFIG_TOOLS = new Set([SEARCH_TOOL, CONFIG_TOOL])
/** 只读查询工具：结果直接展示，不经过模型分析 */
export const QUERY_TOOLS = new Set([QUERY_TOOL, QUERY_BATCH_TOOL])
/** 会返回可下载文件的导出工具 */
export const EXPORT_TOOLS = new Set([
  EXPORT_TOOL,
  EXPORT_QUERY_DATA_TOOL,
  EXPORT_CONVERSATION_TOOL,
  EXPORT_TOOL_RESULT_TOOL
])

export function isConfigTool(name?: string): boolean {
  return !!name && CONFIG_TOOLS.has(name)
}

/** 会返回业务结果的工具：接口调用 / 查询 / 导出 */
export function isResultTool(name?: string): boolean {
  return name === INVOKE_TOOL || (!!name && QUERY_TOOLS.has(name)) || (!!name && EXPORT_TOOLS.has(name))
}

/** 工具结果视图：导出工具渲染为文件卡片，批量查询渲染为多个结果卡片，其余按出参结构渲染。 */
export function buildToolView(name?: string, result?: string): AiView | null {
  const views = buildToolViews(name, result)
  return views.length ? views[0] : null
}

/** 工具结果视图列表：批量查询每个结果一个卡片；其余工具最多一个。 */
export function buildToolViews(name?: string, result?: string): AiView[] {
  if (name === QUERY_BATCH_TOOL) return buildBatchQueryViews(result)
  if (name && EXPORT_TOOLS.has(name)) {
    const view = buildExportView(result)
    return view ? [view] : []
  }
  if (!isResultTool(name)) return []
  const view = buildInvokeView(result)
  return view ? [view] : []
}

/** 工具名称展示文案。 */
export function toolLabel(name?: string): string {
  if (name === SEARCH_TOOL || name === CONFIG_TOOL) return '查询接口配置'
  if (name === QUERY_TOOL) return '查询结果'
  if (name === QUERY_BATCH_TOOL) return '批量查询结果'
  if (name === INVOKE_TOOL) return '调用业务接口'
  if (name === EXPORT_QUERY_DATA_TOOL) return '导出业务数据'
  if (name === EXPORT_CONVERSATION_TOOL) return '导出对话记录'
  if (name === EXPORT_TOOL_RESULT_TOOL) return '导出查询结果'
  if (name === EXPORT_TOOL) return '导出分析结果'
  if (name === READ_FILE_TOOL) return '读取附件'
  return name || '工具调用'
}

/** 追加一段文本，空值忽略，多段之间空行分隔。 */
export function appendText(base: string, text?: string | null): string {
  if (!text) return base
  return base ? `${base}\n\n${text}` : text
}

interface HistoryTool {
  status?: string
  result?: string
  view?: any
}

interface HistoryMessage {
  role?: string
  text?: string
  tools?: HistoryTool[]
}

/**
 * 确认执行的写操作，工具结果可能没回填到历史消息里（只有 tool_use 没有 result）。
 * 该轮之后还有助手正文，说明调用已经执行完成，不能因为缺少结果就显示未完成。
 */
export function restoreCompletedTools(messages: HistoryMessage[]): void {
  messages.forEach((message, index) => {
    const tools = message.tools || []
    if (!tools.some((tool) => tool.status === 'interrupted')) return
    let completed = !!(message.text || '').trim()
    for (let i = index + 1; !completed && i < messages.length; i += 1) {
      const next = messages[i]
      if (next.role === 'user') break
      if ((next.text || '').trim()) {
        completed = true
        break
      }
    }
    if (!completed) return
    tools.forEach((tool) => {
      if (tool.status !== 'interrupted') return
      tool.status = 'done'
      // 结果没有随历史落库：说明调用已完成但历史里不含返回数据
      if (!tool.result) {
        tool.view = { type: 'empty', text: '调用已完成，历史记录中未保留返回数据。' }
      }
    })
  })
}

/** 历史列表只展示到分钟，避免长日期挤占标题空间。 */
export function conversationTime(conv: { lastMessageAt?: string; createTime?: string }): string {
  const raw = conv.lastMessageAt || conv.createTime
  return raw ? raw.slice(5, 16) : ''
}
