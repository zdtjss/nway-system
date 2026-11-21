/**
 * AI 结构化视图：invoke_interface 结果视图与 nway-view 协议块的解析、归一化。
 *
 * - invoke_interface：由调用结果与接口出参定义自动推断表格/字段/清单，前端直接展示；
 * - nway-view：模型在正文中输出的受控代码块，用于文件分析、聚合统计、多接口合并等场景。
 *
 * 视图只包含纯数据，渲染层统一使用文本绑定，不使用 v-html。
 */

export type AiViewValueKind = 'text' | 'number' | 'boolean' | 'link' | 'code' | 'empty'

/** 视图中的单个值，按 JSON 类型选择展示方式。 */
export interface AiViewValue {
  text: string
  kind: AiViewValueKind
  bool?: boolean
  href?: string
}

export interface AiViewColumn {
  key: string
  label: string
  /** 协议可选类型：tag 时按标签展示，其余按文本 */
  type?: string
}

export interface AiViewField {
  key: string
  label: string
  value: AiViewValue
}

export interface AiViewMetric {
  label: string
  value: string
  unit?: string
  trend?: string
}

export interface AiViewSeries {
  name: string
  data: number[]
}

export interface AiViewChart {
  kind: 'bar' | 'line' | 'pie'
  categories: string[]
  series: AiViewSeries[]
}

export interface AiViewFile {
  name: string
  url?: string
  size?: string
}

export type AiViewType = 'table' | 'metric' | 'chart' | 'fields' | 'list' | 'file' | 'text' | 'empty'

export interface AiView {
  type: AiViewType
  title?: string
  columns?: AiViewColumn[]
  rows?: AiViewValue[][]
  total?: number
  /** 表格上方的摘要指标 */
  summary?: AiViewMetric[]
  /** 字段列表；表格模式下为列表外的摘要字段 */
  fields?: AiViewField[]
  list?: AiViewValue[]
  metrics?: AiViewMetric[]
  chart?: AiViewChart
  files?: AiViewFile[]
  text?: string
  /** invoke_interface 结果的提示信息与状态 */
  message?: string
  success?: boolean
  latency?: string
  interfaceCode?: string
  interfaceName?: string
  /** 数据超限被截断时置为 true */
  truncated?: boolean
}

/** 正文分段：普通文本、结构化视图、流式期间的未闭合视图块。 */
export interface AiSegment {
  type: 'text' | 'view' | 'pending'
  text?: string
  view?: AiView
}

export const AI_VIEW_LIMITS = {
  rows: 200,
  columns: 20,
  series: 4,
  bytes: 100 * 1024
}

const MAX_TABLE_ROWS = 100

/** 响应信封常见字段：只有这些字段时，data 内部才是真正的业务数据。 */
const ENVELOPE_KEYS = new Set([
  'code', 'msg', 'message', 'success', 'error', 'errorCode', 'timestamp', 'traceId', 'requestId'
])

/** 通用字段文案，兜底接口未定义出参时的可读性。 */
const COMMON_LABELS: Record<string, string> = {
  code: '状态码',
  msg: '提示信息',
  message: '提示信息',
  total: '总数',
  data: '数据列表',
  list: '列表',
  records: '记录',
  rows: '数据',
  currentPage: '当前页',
  pageSize: '每页条数',
  totalPages: '总页数'
}

/** 接口字段定义（取自 inputSchema / outputSchema），用于结果字段的文案与类型渲染。 */
export interface FieldMeta {
  key: string
  label: string
  type: string
  options: Map<string, string>
}

/** 出参字段定义（含 properties/items 的层级结构），用于按 outputSchema 裁剪展示字段。 */
interface OutputFieldMeta {
  key: string
  label: string
  type: string
  properties?: OutputFieldMeta[]
  items?: { type: string; properties?: OutputFieldMeta[] }
}

interface InterfaceMeta {
  name: string
  fields: Map<string, FieldMeta>
  outputs?: OutputFieldMeta[]
}

/** 接口定义缓存：按接口编码保存名称与字段文案。 */
const interfaceMeta = new Map<string, InterfaceMeta>()

function isPlainObject(value: any): boolean {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function toNumber(value: any): number {
  const num = Number(value)
  return Number.isFinite(num) ? num : 0
}

function safeUrl(value: any): string | undefined {
  const text = typeof value === 'string' ? value : ''
  return /^https?:\/\//i.test(text) ? text : undefined
}

// ==================== 接口字段定义 ====================

/** 确认弹窗携带的 inputSchema 也是接口字段定义来源，补充到缓存。 */
export function rememberInterfaceFields(interfaceCode?: string, name?: string, inputSchema?: any) {
  if (!interfaceCode) return
  const existed = interfaceMeta.get(interfaceCode)
  const fields = new Map(existed?.fields || [])
  inputFieldMeta(inputSchema).forEach((value, key) => {
    if (!fields.has(key)) fields.set(key, value)
  })
  interfaceMeta.set(interfaceCode, {
    name: existed?.name || name || interfaceCode,
    fields,
    outputs: existed?.outputs
  })
}

/** get_interface_config 的结果：缓存接口名、字段定义与出参结构，供调用结果渲染使用。 */
export function rememberInterfaceConfig(raw?: string | null) {
  if (!raw) return
  try {
    const data = JSON.parse(raw)
    const candidates = Array.isArray(data?.candidates) ? data.candidates : []
    candidates.forEach((item: any) => {
      const code = typeof item?.interfaceCode === 'string' ? item.interfaceCode : ''
      if (!code) return
      // 先以 inputSchema 为默认字段定义，再用 outputSchema 覆盖更贴近返回数据的文案
      const fields = inputFieldMeta(item.inputSchema)
      outputFieldMeta(item.outputSchema).forEach((value, key) => fields.set(key, value))
      interfaceMeta.set(code, {
        name: typeof item.name === 'string' && item.name ? item.name : code,
        fields,
        outputs: outputFieldTree(item.outputSchema)
      })
    })
  } catch (e) {
    // 结果不完整时忽略，等待完整结果
  }
}

/** 出参字段树：保留 properties/items，供结果按 outputSchema 裁剪。 */
function outputFieldTree(schema: any): OutputFieldMeta[] {
  if (!Array.isArray(schema)) return []
  const fields: OutputFieldMeta[] = []
  schema.forEach((field: any) => {
    if (!field || typeof field !== 'object') return
    const key = typeof field.key === 'string' ? field.key.trim() : ''
    if (!key) return
    const label = (typeof field.label === 'string' && field.label.trim())
      || (typeof field.description === 'string' && field.description.trim())
      || key
    const node: OutputFieldMeta = {
      key,
      label,
      type: typeof field.type === 'string' ? field.type.toUpperCase() : ''
    }
    const properties = outputFieldTree(field.properties)
    if (properties.length) node.properties = properties
    if (field.items && typeof field.items === 'object') {
      const itemProperties = outputFieldTree(field.items.properties)
      node.items = {
        type: typeof field.items.type === 'string' ? field.items.type.toUpperCase() : '',
        ...(itemProperties.length ? { properties: itemProperties } : {})
      }
    }
    fields.push(node)
  })
  return fields
}

/** inputSchema 的字段定义，递归收集 OBJECT/ARRAY 内嵌字段。 */
function inputFieldMeta(schema: any): Map<string, FieldMeta> {
  const fields = new Map<string, FieldMeta>()
  collectInputFields(schema, fields)
  return fields
}

/** 入参中文名：label 优先；label 与 key 相同时用 description 补充业务含义。 */
export function inputFieldLabel(field: any): string {
  const key = typeof field?.key === 'string' ? field.key : ''
  const label = typeof field?.label === 'string' ? field.label.trim() : ''
  const description = typeof field?.description === 'string' ? field.description.trim() : ''
  return (label && label !== key ? label : '') || description || label || key
}

function collectInputFields(schema: any, target: Map<string, FieldMeta>) {
  if (!Array.isArray(schema)) return
  schema.forEach((field: any) => {
    if (!field || typeof field !== 'object') return
    const key = typeof field.key === 'string' ? field.key : ''
    if (key) {
      const options = new Map<string, string>()
      if (Array.isArray(field.options)) {
        field.options.forEach((option: any) => {
          if (option && option.value !== undefined && option.value !== null) {
            options.set(String(option.value), String(option.label ?? option.value))
          }
        })
      }
      target.set(key, {
        key,
        label: inputFieldLabel(field),
        type: typeof field.type === 'string' ? field.type.toUpperCase() : '',
        options
      })
    }
    if (Array.isArray(field.properties)) {
      collectInputFields(field.properties, target)
    }
    const items = field.items
    if (Array.isArray(items)) {
      collectInputFields(items, target)
    } else if (items && typeof items === 'object' && Array.isArray(items.properties)) {
      collectInputFields(items.properties, target)
    }
  })
}

/**
 * 出参定义里的字段文案：支持省略 path（按 $.{key} 取值），
 * 有 path 时同时按 key 和路径末段注册，兼容列表内字段；
 * 数组元素与嵌套对象的字段也会递归注册，供结果表格使用。
 */
function outputFieldMeta(schema: any): Map<string, FieldMeta> {
  const fields = new Map<string, FieldMeta>()
  collectOutputFieldMeta(schema, fields, true)
  return fields
}

function collectOutputFieldMeta(schema: any, fields: Map<string, FieldMeta>, overwrite: boolean) {
  if (!Array.isArray(schema)) return
  schema.forEach((field: any) => {
    if (!field || typeof field !== 'object') return
    const key = typeof field.key === 'string' ? field.key : ''
    if (key) {
      const path = typeof field.path === 'string' ? field.path : ''
      const segments = path.replace(/^\$\.?/, '').replace(/\[\]/g, '').split('.').filter(Boolean)
      const last = segments[segments.length - 1]
      const label = (typeof field.label === 'string' && field.label.trim())
        || (typeof field.description === 'string' && field.description.trim())
        || last || key
      const names = Array.from(new Set([key, last].filter(Boolean)))
      names.forEach((name) => {
        if (overwrite || !fields.has(name)) {
          fields.set(name, {
            key: name,
            label,
            type: typeof field.type === 'string' ? field.type.toUpperCase() : '',
            options: new Map()
          })
        }
      })
    }
    collectOutputFieldMeta(field.properties, fields, false)
    if (field.items && typeof field.items === 'object') {
      collectOutputFieldMeta(field.items.properties, fields, false)
    }
  })
}

// ==================== invoke_interface 结果视图 ====================

/** 调用结果按接口字段定义与 data 的 JSON 结构选择表格、字段列表或清单展示。 */
export function buildInvokeView(raw?: string | null): AiView | null {
  if (!raw) return null
  const result = parseToolResult(raw)
  if (!isPlainObject(result)) return null
  const interfaceCode = typeof result.interfaceCode === 'string' ? result.interfaceCode : ''
  const meta = interfaceCode ? interfaceMeta.get(interfaceCode) : undefined
  const view: AiView = {
    type: 'empty',
    success: result.success !== false && !result.error,
    interfaceCode,
    interfaceName: meta?.name || interfaceCode || undefined,
    message: typeof result.message === 'string' ? result.message : '',
    latency: typeof result.latencyMs === 'number' ? `${result.latencyMs} ms` : ''
  }
  if (/…\[结果已截断\]\s*$/.test(raw)) {
    view.message = [view.message, '结果较长，已截断显示'].filter(Boolean).join(' · ')
  }
  if (!view.success) return view
  // outputSchema 是唯一出参口径：先按定义裁剪，未声明子结构的字段保持原样
  const root: OutputFieldMeta | undefined = meta?.outputs?.length
    ? { key: '', label: '', type: 'OBJECT', properties: meta.outputs }
    : undefined
  const resolved = resolveInvokeData(result.data, root)
  if (resolved.value === null || resolved.value === undefined) return view
  fillDataView(view, resolved.value, meta?.fields, resolved.node)
  return view
}

/**
 * 按出参定义裁剪数据并取出真正的业务数据：
 * 声明了 properties / items.properties 时只保留声明的子字段；
 * 兼容 JSON 字符串包装与 code/msg/data 信封。
 */
function resolveInvokeData(raw: any, node?: OutputFieldMeta, depth = 0): { value: any; node?: OutputFieldMeta } {
  let value = projectBySchema(raw, node)
  if (depth > 3) return { value, node }
  if (typeof value === 'string') {
    const text = value.trim()
    if (!/^[{\[]/.test(text)) return { value, node }
    try {
      return resolveInvokeData(JSON.parse(text), node, depth + 1)
    } catch (e) {
      return { value, node }
    }
  }
  if (isPlainObject(value) && 'data' in value) {
    const keys = Object.keys(value)
    if (keys.every((key) => key === 'data' || ENVELOPE_KEYS.has(key))) {
      const inner = value.data
      if (inner !== undefined && inner !== null && inner !== '') {
        return resolveInvokeData(inner, schemaChild(node, 'data'), depth + 1)
      }
    }
  }
  return { value, node }
}

/** 按出参定义裁剪单个值：数组按 items.properties 裁剪元素，对象按 properties 裁剪字段。 */
function projectBySchema(value: any, node?: OutputFieldMeta): any {
  if (!node || value === null || value === undefined) return value
  if (Array.isArray(value)) {
    const itemFields = node.items?.properties
    if (!itemFields?.length) return value
    return value.map((item) => (isPlainObject(item) ? projectObjectBySchema(item, itemFields) : item))
  }
  if (isPlainObject(value) && node.properties?.length) {
    return projectObjectBySchema(value, node.properties)
  }
  return value
}

/** 只保留出参定义声明的字段，未声明字段不进入展示数据。 */
function projectObjectBySchema(value: Record<string, any>, fields: OutputFieldMeta[]): Record<string, any> {
  const projected: Record<string, any> = {}
  fields.forEach((field) => {
    if (Object.prototype.hasOwnProperty.call(value, field.key)) {
      projected[field.key] = projectBySchema(value[field.key], field)
    }
  })
  return projected
}

function schemaChild(node: OutputFieldMeta | undefined, key: string): OutputFieldMeta | undefined {
  return node?.properties?.find((field) => field.key === key)
}

/** 数据本体按结构渲染：对象列表转表格，标量清单转列表，其余按字段逐项展示。 */
function fillDataView(view: AiView, data: any, fields?: Map<string, FieldMeta>, node?: OutputFieldMeta) {
  if (Array.isArray(data)) {
    if (!data.length) return
    if (data.every(isPlainObject)) {
      fillTableView(view, data, fields, undefined, node?.items?.properties)
      return
    }
    if (data.every((item) => !isPlainObject(item) && !Array.isArray(item))) {
      view.type = 'list'
      view.list = data.map((item) => buildValue(item))
      return
    }
    // 混合类型或嵌套数组：逐条转成可读摘要，避免直接展示 JSON
    view.type = 'list'
    view.list = data.map((item, index) => ({
      text: `${index + 1}. ${readableText(item, fields)}`,
      kind: 'text'
    }))
    return
  }

  if (typeof data === 'object') {
    const entries = Object.entries(data)
    if (!entries.length) return
    // 分页结构（如 { total, list }）：列表转表格，其余标量字段作为摘要展示
    const listEntry = entries
      .filter(([, value]) => Array.isArray(value) && value.length > 0 && value.every(isPlainObject))
      .sort((a, b) => (b[1] as any[]).length - (a[1] as any[]).length)[0]
    if (listEntry) {
      const [listKey, listValue] = listEntry
      fillTableView(view, listValue as any[], fields, fieldLabel(listKey, fields),
        schemaChild(node, listKey)?.items?.properties)
      view.fields = entries
        .filter(([key]) => key !== listKey)
        .map(([key, value]) => ({
          key,
          label: fieldLabel(key, fields),
          value: buildValue(value, false, fields?.get(key), fields)
        }))
      return
    }
    view.type = 'fields'
    view.fields = entries.map(([key, value]) => ({
      key,
      label: fieldLabel(key, fields),
      value: buildValue(value, false, fields?.get(key), fields)
    }))
    return
  }

  view.type = 'text'
  view.text = String(data)
}

/** 对象列表统一按二维表格展示：出参定义优先决定列与顺序，未定义时才从数据推断。 */
function fillTableView(view: AiView, rows: any[], fields?: Map<string, FieldMeta>, title?: string,
                       itemFields?: OutputFieldMeta[]) {
  const columns = buildColumns(rows, fields, itemFields)
  if (!columns.length) {
    view.type = 'text'
    view.text = `共 ${rows.length} 条记录，字段为空`
    return
  }
  view.type = 'table'
  if (title) view.title = title
  view.columns = columns
  view.rows = rows.slice(0, MAX_TABLE_ROWS)
    .map((row) => columns.map((col) => buildValue(row[col.key], true, fields?.get(col.key), fields)))
  view.total = rows.length
}

/** 字段文案：接口定义优先，其次通用业务字段，最后回退到原始 key。 */
function fieldLabel(key: string, fields?: Map<string, FieldMeta>): string {
  const label = fields?.get(key)?.label
  // 定义里的 label 与 key 相同时视为未翻译，优先使用通用中文文案
  return (label && label !== key ? label : '') || COMMON_LABELS[key] || label || key
}

function buildColumns(rows: any[], fields?: Map<string, FieldMeta>,
                      itemFields?: OutputFieldMeta[]): AiViewColumn[] {
  const seen = new Set<string>()
  const keys: string[] = []
  if (itemFields?.length) {
    // 数组元素已声明字段：只展示声明的列，顺序与 outputSchema 一致
    itemFields.forEach((field) => {
      if (!seen.has(field.key) && rows.some((row) => Object.prototype.hasOwnProperty.call(row, field.key))) {
        seen.add(field.key)
        keys.push(field.key)
      }
    })
    return keys.map((key) => ({ key, label: fieldLabel(key, fields) }))
  }
  // 接口定义的字段优先，保证列顺序与业务字段一致
  fields?.forEach((field) => {
    if (!seen.has(field.key) && rows.some((row) => Object.prototype.hasOwnProperty.call(row, field.key))) {
      seen.add(field.key)
      keys.push(field.key)
    }
  })
  rows.forEach((row) => {
    Object.keys(row).forEach((key) => {
      if (!seen.has(key)) {
        seen.add(key)
        keys.push(key)
      }
    })
  })
  return keys.map((key) => ({ key, label: fieldLabel(key, fields) }))
}

function buildValue(value: any, compact = false, meta?: FieldMeta, fields?: Map<string, FieldMeta>): AiViewValue {
  if (value === null || value === undefined || value === '') {
    return { text: '—', kind: 'empty' }
  }
  if (meta?.options.size) {
    if (Array.isArray(value)) {
      const text = value
        .map((item) => meta.options.get(String(item)) ?? String(item))
        .join('、')
      return { text: text || '—', kind: text ? 'text' : 'empty' }
    }
    if (typeof value !== 'object') {
      const text = meta.options.get(String(value))
      if (text !== undefined) return { text, kind: 'text' }
    }
  }
  if (meta?.type === 'BOOLEAN') {
    const bool = toBoolean(value)
    if (bool !== undefined) {
      return { text: bool ? '是' : '否', kind: 'boolean', bool }
    }
  }
  if (typeof value === 'boolean') {
    return { text: value ? '是' : '否', kind: 'boolean', bool: value }
  }
  if (typeof value === 'number') {
    return { text: Number.isFinite(value) ? String(value) : '—', kind: 'number' }
  }
  if (typeof value === 'string') {
    if (/^https?:\/\//i.test(value)) {
      return { text: value, kind: 'link', href: value }
    }
    return { text: value, kind: 'text' }
  }
  if (Array.isArray(value) && value.every((item) => !isPlainObject(item) && !Array.isArray(item))) {
    const text = readableText(value, fields)
    return { text: text || '—', kind: text ? 'text' : 'empty' }
  }
  if (compact) {
    // 单元格文本保留完整内容，超长由样式省略，鼠标移入通过 title 展示全文
    return { text: readableText(value, fields), kind: 'text' }
  }
  return { text: readableText(value, fields, true), kind: 'code' }
}

function toBoolean(value: any): boolean | undefined {
  if (typeof value === 'boolean') return value
  if (value === 1 || value === '1' || value === 'true' || value === 'Y' || value === 'YES') return true
  if (value === 0 || value === '0' || value === 'false' || value === 'N' || value === 'NO') return false
  return undefined
}

/** 把任意 JSON 值转成业务人员可读的文案，避免直接暴露花括号与引号。 */
function readableText(value: any, fields?: Map<string, FieldMeta>, multiline = false): string {
  if (value === null || value === undefined || value === '') return '—'
  if (typeof value === 'boolean') return value ? '是' : '否'
  if (typeof value === 'number') return Number.isFinite(value) ? String(value) : '—'
  if (typeof value === 'string') return value
  if (Array.isArray(value)) {
    if (!value.length) return '—'
    if (value.every((item) => !isPlainObject(item) && !Array.isArray(item))) {
      return value
        .map((item) => (item === null || item === undefined ? '—' : typeof item === 'boolean' ? (item ? '是' : '否') : String(item)))
        .join('、')
    }
    // 表格单元格等紧凑场景只给条数，表单内的多行值则完整展开
    if (!multiline) return `共 ${value.length} 条`
    return value
      .map((item, index) => `${index + 1}. ${readableText(item, fields, false)}`)
      .join('\n')
  }
  if (typeof value === 'object') {
    const entries = Object.entries(value)
    if (!entries.length) return '—'
    const parts = entries.map(([key, item]) =>
      `${fieldLabel(key, fields)}：${readableText(item, fields, multiline)}`)
    return parts.join(multiline ? '\n' : '；')
  }
  return String(value)
}

/** 结果 JSON 可能因长度被后端截断，解析失败时回退到最后一个完整节点。 */
function parseToolResult(raw?: string | null): any {
  if (!raw) return null
  const text = raw.replace(/…\[结果已截断\]\s*$/, '').trim()
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch (e) {
    return repairTruncatedJson(text)
  }
}

/** 截断的 JSON 无法直接解析：回溯到最后一个完整的对象/数组边界并补齐括号。 */
function repairTruncatedJson(text: string): any {
  const stack: string[] = []
  const snapshots: { index: number; stack: string[] }[] = []
  let inString = false
  let escaped = false
  for (let i = 0; i < text.length; i++) {
    const char = text[i]
    if (inString) {
      if (escaped) escaped = false
      else if (char === '\\') escaped = true
      else if (char === '"') inString = false
      continue
    }
    if (char === '"') inString = true
    else if (char === '{' || char === '[') stack.push(char)
    else if (char === '}' || char === ']') {
      stack.pop()
      snapshots.push({ index: i + 1, stack: [...stack] })
    }
  }
  for (let i = snapshots.length - 1; i >= 0; i--) {
    const { index, stack: open } = snapshots[i]
    const tail = open.map((char) => (char === '{' ? '}' : ']')).reverse().join('')
    try {
      return JSON.parse(text.slice(0, index) + tail)
    } catch (e) {
      // 当前边界仍不完整，继续向前回退
    }
  }
  return null
}

/** 目标接口可能把业务数据包在 code/msg/data 信封里，渲染前取出真正的 data。 */
function normalizeInvokeData(data: any, depth = 0): any {
  if (depth > 3) return data
  if (typeof data === 'string') {
    const text = data.trim()
    if (/^[{\[]/.test(text)) {
      try {
        return normalizeInvokeData(JSON.parse(text), depth + 1)
      } catch (e) {
        return data
      }
    }
    return data
  }
  if (isPlainObject(data) && 'data' in data) {
    const keys = Object.keys(data)
    if (keys.every((key) => key === 'data' || ENVELOPE_KEYS.has(key))) {
      const inner = normalizeInvokeData(data.data, depth + 1)
      if (inner !== undefined && inner !== null && inner !== '') return inner
    }
  }
  return data
}

/**
 * 工具结果是后端序列化的 JSON 文本，直接展示会变成一串原始 JSON。
 * 这里按结果结构还原为可读内容：错误取 message，候选接口列成清单，其余按字段渲染。
 */
export function toolResultText(raw?: string): string {
  if (!raw) return ''
  const data = parseToolResult(raw)
  if (data === null || data === undefined) return raw
  if (isPlainObject(data)) {
    const message = typeof data.message === 'string' ? data.message : ''
    if (data.error) {
      return message || String(data.error)
    }
    if (Array.isArray(data.candidates)) {
      const lines = data.candidates.map((item: any, index: number) => {
        const name = item?.name || item?.interfaceCode || '未命名接口'
        const code = item?.interfaceCode ? `（${item.interfaceCode}）` : ''
        const desc = item?.description ? `：${item.description}` : ''
        return `${index + 1}. ${name}${code}${desc}`
      })
      return [`匹配到 ${data.candidates.length} 个候选接口`, ...lines].join('\n')
    }
    if ('data' in data) {
      const summary = [
        message,
        data.code !== undefined && data.code !== null ? `状态码 ${data.code}` : ''
      ].filter(Boolean).join(' · ')
      return [summary, readableText(normalizeInvokeData(data.data), undefined, true)]
        .filter(Boolean).join('\n')
    }
    return readableText(data, undefined, true)
  }
  return typeof data === 'string' ? data : readableText(data, undefined, true)
}

// ==================== nway-view 协议 ====================

/** 把协议中的任意值转为视图值，用于表格与字段。 */
function toViewValue(value: any, columnType?: string): AiViewValue {
  if (value === null || value === undefined || value === '') {
    return { text: '—', kind: 'empty' }
  }
  if (typeof value === 'boolean') {
    return { text: value ? '是' : '否', kind: 'boolean', bool: value }
  }
  if (typeof value === 'number') {
    return { text: Number.isFinite(value) ? String(value) : '—', kind: 'number' }
  }
  if (typeof value === 'string') {
    const href = safeUrl(value)
    if (href && columnType !== 'tag') return { text: value, kind: 'link', href }
    return { text: value, kind: 'text' }
  }
  if (Array.isArray(value) && value.every((item) => !isPlainObject(item) && !Array.isArray(item))) {
    return { text: readableText(value), kind: 'text' }
  }
  return { text: readableText(value), kind: 'text' }
}

function toStringValue(value: any): string {
  if (value === null || value === undefined) return ''
  if (typeof value === 'number') return Number.isFinite(value) ? String(value) : ''
  if (typeof value === 'boolean') return value ? '是' : '否'
  if (typeof value === 'string') return value
  return readableText(value)
}

function normalizeMetrics(raw: any[]): AiViewMetric[] {
  return raw
    .filter(isPlainObject)
    .slice(0, 12)
    .map((item: any) => ({
      label: toStringValue(item.label),
      value: toStringValue(item.value),
      unit: toStringValue(item.unit) || undefined,
      trend: toStringValue(item.trend) || undefined
    }))
    .filter((item) => !!item.label)
}

function normalizeColumns(spec: any[], rows: any[]): AiViewColumn[] {
  let columns: AiViewColumn[] = spec
    .filter(isPlainObject)
    .slice(0, AI_VIEW_LIMITS.columns)
    .map((col: any) => ({
      key: toStringValue(col.key) || toStringValue(col.label),
      label: toStringValue(col.label) || toStringValue(col.key),
      type: toStringValue(col.type) || undefined
    }))
    .filter((col) => !!col.key)
  if (!columns.length && rows.length) {
    // 未提供 columns 时从数据行推导，兼容模型只给 rows 的情况
    const keys: string[] = []
    rows.slice(0, 20).forEach((row) => {
      if (!isPlainObject(row)) return
      Object.keys(row).forEach((key) => {
        if (!keys.includes(key) && keys.length < AI_VIEW_LIMITS.columns) keys.push(key)
      })
    })
    columns = keys.map((key) => ({ key, label: key }))
  }
  return columns
}

function normalizeRows(spec: any[], columns: AiViewColumn[]): { rows: AiViewValue[][]; truncated: boolean } {
  const truncated = spec.length > AI_VIEW_LIMITS.rows
  const rows = spec.slice(0, AI_VIEW_LIMITS.rows).map((row: any) => {
    if (Array.isArray(row)) {
      return columns.map((_, index) => toViewValue(row[index], columns[index]?.type))
    }
    const obj = isPlainObject(row) ? row : {}
    return columns.map((col) => toViewValue(obj[col.key], col.type))
  })
  return { rows, truncated }
}

function normalizeChart(raw: any, rows: any[]): AiViewChart | null {
  const spec = isPlainObject(raw) ? raw : {}
  const kindText = toStringValue(spec.kind)
  const kind: AiViewChart['kind'] = kindText === 'line' || kindText === 'pie' ? kindText : 'bar'
  let categories: string[] = Array.isArray(spec.categories)
    ? spec.categories.slice(0, AI_VIEW_LIMITS.rows).map(toStringValue)
    : []
  let series: AiViewSeries[] = Array.isArray(spec.series)
    ? spec.series
      .filter(isPlainObject)
      .slice(0, AI_VIEW_LIMITS.series)
      .map((item: any) => ({
        name: toStringValue(item.name),
        data: Array.isArray(item.data)
          ? item.data.slice(0, AI_VIEW_LIMITS.rows).map(toNumber)
          : []
      }))
    : []
  if (!series.length && rows.length) {
    // 兼容 chart 只给类型、数据放在 rows 的写法
    const objects = rows.filter(isPlainObject)
    if (objects.length) {
      const keys = Object.keys(objects[0])
      const categoryKey = keys.find((key) => typeof objects[0][key] === 'string') || keys[0]
      categories = objects.map((row: any) => toStringValue(row[categoryKey]))
      const valueKeys = keys
        .filter((key) => key !== categoryKey && objects.some((row: any) => typeof row[key] === 'number'))
        .slice(0, AI_VIEW_LIMITS.series)
      series = valueKeys.map((key) => ({
        name: key,
        data: objects.map((row: any) => toNumber(row[key]))
      }))
    }
  }
  if (!series.length) return null
  if (!categories.length) {
    const maxLength = Math.max(...series.map((item) => item.data.length))
    categories = Array.from({ length: maxLength }, (_, index) => String(index + 1))
  }
  // 各系列长度对齐到分类数量，避免渲染时出现空点
  series = series.map((item) => ({
    name: item.name,
    data: categories.map((_, index) => item.data[index] ?? 0)
  }))
  return { kind, categories, series }
}

function enforceViewSize(view: AiView): AiView {
  if (JSON.stringify(view).length <= AI_VIEW_LIMITS.bytes) return view
  view.truncated = true
  if (view.rows?.length) {
    let rows = view.rows
    while (rows.length > 1 && JSON.stringify({ ...view, rows }).length > AI_VIEW_LIMITS.bytes) {
      rows = rows.slice(0, Math.max(1, Math.floor(rows.length / 2)))
    }
    view.rows = rows
  }
  if (view.list?.length) {
    let list = view.list
    while (list.length > 1 && JSON.stringify({ ...view, list }).length > AI_VIEW_LIMITS.bytes) {
      list = list.slice(0, Math.max(1, Math.floor(list.length / 2)))
    }
    view.list = list
  }
  return view
}

/**
 * 归一化 nway-view 协议块。
 * 结构非法、类型不支持或缺少必要数据时返回 null，调用方按普通代码块降级展示。
 */
export function normalizeView(input: unknown): AiView | null {
  let raw: any = input
  if (typeof raw === 'string') {
    try {
      raw = JSON.parse(raw)
    } catch (e) {
      return null
    }
  }
  if (!isPlainObject(raw)) return null
  const type = toStringValue(raw.type).toLowerCase()
  const title = toStringValue(raw.title) || undefined

  if (type === 'table') {
    const rowsSpec = Array.isArray(raw.rows) ? raw.rows : []
    const columnsSpec = Array.isArray(raw.columns) ? raw.columns : []
    const columns = normalizeColumns(columnsSpec, rowsSpec)
    if (!columns.length) return null
    const { rows, truncated } = normalizeRows(rowsSpec, columns)
    const total = typeof raw.total === 'number' && Number.isFinite(raw.total) ? raw.total : rowsSpec.length
    const view: AiView = { type: 'table', title, columns, rows, total }
    if (Array.isArray(raw.summary)) view.summary = normalizeMetrics(raw.summary)
    view.truncated = truncated || columnsSpec.length > AI_VIEW_LIMITS.columns
    return enforceViewSize(view)
  }

  if (type === 'metric') {
    const list = Array.isArray(raw.metrics) ? raw.metrics : Array.isArray(raw.summary) ? raw.summary : []
    const metrics = normalizeMetrics(list)
    if (!metrics.length) return null
    const view: AiView = { type: 'metric', title, metrics, truncated: list.length > 12 }
    return enforceViewSize(view)
  }

  if (type === 'chart') {
    const rowsSpec = Array.isArray(raw.rows) ? raw.rows : []
    const chart = normalizeChart(raw.chart, rowsSpec)
    if (!chart) return null
    const view: AiView = { type: 'chart', title, chart }
    if (isPlainObject(raw.chart) && Array.isArray(raw.chart.series)
      && raw.chart.series.length > AI_VIEW_LIMITS.series) {
      view.truncated = true
    }
    return enforceViewSize(view)
  }

  if (type === 'fields') {
    const entries: [string, any][] = Array.isArray(raw.fields)
      ? raw.fields.filter(isPlainObject).map((field: any, index: number) =>
        [toStringValue(field.key) || String(index), field] as [string, any])
      : isPlainObject(raw.fields)
        ? Object.entries(raw.fields)
        : []
    const fields: AiViewField[] = entries.slice(0, 100).map(([key, field]) => {
      const meta = isPlainObject(field) && 'value' in field ? field : null
      return {
        key,
        label: meta ? toStringValue(meta.label) || key : key,
        value: toViewValue(meta ? meta.value : field)
      }
    })
    if (!fields.length) return null
    return enforceViewSize({ type: 'fields', title, fields, truncated: entries.length > 100 })
  }

  if (type === 'list') {
    const items = Array.isArray(raw.items) ? raw.items : Array.isArray(raw.list) ? raw.list : []
    if (!items.length) return null
    const list = items.slice(0, AI_VIEW_LIMITS.rows).map((item) => toViewValue(item))
    return enforceViewSize({ type: 'list', title, list, truncated: items.length > AI_VIEW_LIMITS.rows })
  }

  if (type === 'file') {
    const list = Array.isArray(raw.files) ? raw.files : []
    const files: AiViewFile[] = list
      .filter(isPlainObject)
      .slice(0, 20)
      .map((file: any) => ({
        name: toStringValue(file.name) || '文件',
        url: safeUrl(file.url),
        size: toStringValue(file.size) || undefined
      }))
    if (!files.length) return null
    return { type: 'file', title, files }
  }

  if (type === 'text') {
    const text = toStringValue(raw.text)
    if (!text) return null
    return { type: 'text', title, text }
  }

  return null
}

/**
 * 把正文拆分为文本段、视图段与流式待完成段。
 *
 * - 未闭合的 nway-view 块在流式期间标记为 pending，结束后按普通代码块展示；
 * - JSON 非法或类型不支持时整块回退为文本段，交给 Markdown 按代码块渲染，不丢内容。
 */
export function splitViewSegments(text: string, streaming = false): AiSegment[] {
  if (!text) return []
  const segments: AiSegment[] = []
  const fence = /```[ \t]*nway-view[^\n]*\r?\n/g
  let cursor = 0
  let match: RegExpExecArray | null

  const pushText = (value: string) => {
    const chunk = value.replace(/^\n+|\n+$/g, '')
    if (chunk) segments.push({ type: 'text', text: chunk })
  }

  while ((match = fence.exec(text)) !== null) {
    const start = match.index
    const bodyStart = start + match[0].length
    const close = text.indexOf('```', bodyStart)
    pushText(text.slice(cursor, start))
    if (close < 0) {
      if (streaming) {
        segments.push({ type: 'pending' })
      } else {
        pushText(text.slice(start))
      }
      cursor = text.length
      break
    }
    const rawFence = text.slice(start, close + 3)
    const view = normalizeView(text.slice(bodyStart, close))
    if (view) segments.push({ type: 'view', view })
    else pushText(rawFence)
    cursor = close + 3
    fence.lastIndex = cursor
  }
  pushText(text.slice(cursor))
  return segments
}
