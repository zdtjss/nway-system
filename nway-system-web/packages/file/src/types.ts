/**
 * 文件中心客户端类型定义。
 */

/** 预览类型：图片 / 视频 / PDF / 其他（其他走下载） */
export type PreviewKind = 'image' | 'video' | 'pdf' | 'other'

/** 票据用途 */
export type TicketPurpose = 'preview' | 'download'

/** 客户端模式：内部登录接口 / 开放平台接口 */
export type FileClientMode = 'internal' | 'open'

/** 文件元数据（与服务端 FileVo 对齐） */
export interface FileMeta {
  id: string
  fileName: string
  fileSize: number
  contentType: string
  fileExt: string
  bizType?: string
  bizId?: string
  remark?: string
  creatorId?: string
  creatorName?: string
  createTime?: string
  downloadUrl?: string
  previewUrl?: string
}

/** 分页查询条件 */
export interface FileQueryParams {
  fileName?: string
  bizType?: string
  creatorId?: string
  startTime?: string
  endTime?: string
  current?: number
  size?: number
  [key: string]: unknown
}

/** 分页结果 */
export interface FilePageResult {
  data: FileMeta[]
  total: number
}

/** 上传选项 */
export interface FileUploadOptions {
  bizType?: string
  bizId?: string
  remark?: string
  onProgress?: (percent: number) => void
  signal?: AbortSignal
}

/** 票据结果 */
export interface FileTicket {
  ticket: string
  url: string
  expiresIn: number
}

/** 客户端配置 */
export interface FileClientOptions {
  /** 服务地址，如 https://host/nway-system 或开发环境 /api/nway-system */
  baseUrl: string
  /** 调用方提供 Token：平台用户返回 JWT，外部系统返回开放平台 Token */
  getToken: () => string | Promise<string>
  /** 接口模式，默认 internal；外部系统使用开放平台 Token 时传 open */
  mode?: FileClientMode
  /** 可注入自定义 fetch（如测试或非浏览器环境） */
  fetchImpl?: typeof fetch
}

/** 客户端契约 */
export interface FileClient {
  readonly baseUrl: string
  readonly mode: FileClientMode

  upload(file: File, options?: FileUploadOptions): Promise<FileMeta>

  queryPage(params: FileQueryParams): Promise<FilePageResult>

  getById(id: string): Promise<FileMeta>

  remove(id: string): Promise<void>

  createTicket(id: string, purpose?: TicketPurpose): Promise<FileTicket>

  download(id: string, fileName?: string): Promise<void>

  /** 将票据直链等服务端返回的相对地址解析为可直接用于 <img>/<video> 的地址 */
  resolveUrl(url: string): string
}

/** 统一错误：由 UI 决定提示方式 */
export class FileClientError extends Error {
  readonly code: number
  readonly status: number

  constructor(code: number, message: string, status = 0) {
    super(message)
    this.name = 'FileClientError'
    this.code = code
    this.status = status
  }
}
