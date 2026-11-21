/**
 * nway-system 文件中心客户端。
 *
 * 零运行时依赖：上传用 XMLHttpRequest（进度/取消），其余请求用 fetch。
 * 平台用户与外部系统共用同一实现，通过 mode + getToken 区分鉴权方式。
 */
import {
  FileClientError,
  type FileClient,
  type FileClientOptions,
  type FileMeta,
  type FilePageResult,
  type FileQueryParams,
  type FileTicket,
  type FileUploadOptions,
  type TicketPurpose
} from './types'

export * from './types'

interface ResultBody<T> {
  code: number
  msg?: string
  data?: T
}

const INTERNAL_PREFIX = '/file'
const OPEN_PREFIX = '/open/file'

/**
 * 创建文件客户端。
 */
export function createFileClient(options: FileClientOptions): FileClient {
  const baseUrl = normalizeBaseUrl(options.baseUrl)
  const mode = options.mode ?? 'internal'
  const fetchImpl: typeof fetch = options.fetchImpl ?? ((input, init) => globalThis.fetch(input, init))
  const prefix = mode === 'open' ? OPEN_PREFIX : INTERNAL_PREFIX

  async function authHeaders(): Promise<Record<string, string>> {
    const token = await options.getToken()
    return token ? { Authorization: `Bearer ${token}` } : {}
  }

  async function parseResult<T>(response: Response): Promise<T> {
    if (!response.ok) {
      throw new FileClientError(response.status, await errorMessage(response, `请求失败（${response.status}）`), response.status)
    }
    let body: ResultBody<T>
    try {
      body = (await response.json()) as ResultBody<T>
    } catch (e) {
      throw new FileClientError(500, '响应格式错误', response.status)
    }
    if (body.code !== 200) {
      throw new FileClientError(body.code ?? 500, body.msg || '操作失败', response.status)
    }
    return body.data as T
  }

  async function errorMessage(response: Response, fallback: string): Promise<string> {
    try {
      const body = (await response.json()) as ResultBody<unknown>
      return body.msg || fallback
    } catch (e) {
      return fallback
    }
  }

  async function request<T>(path: string, init: RequestInit): Promise<T> {
    return (await requestWithUrl<T>(path, init)).data
  }

  /** 同时返回响应地址：请求可能被微前端沙箱重写，实际地址才是媒体可访问的源 */
  async function requestWithUrl<T>(path: string, init: RequestInit): Promise<{ data: T; url: string }> {
    const headers = { ...(init.headers as Record<string, string>), ...(await authHeaders()) }
    const response = await fetchImpl(baseUrl + path, { ...init, headers })
    return { data: await parseResult<T>(response), url: response.url }
  }

  function queryString(params: Record<string, unknown>): string {
    const usp = new URLSearchParams()
    Object.entries(params).forEach(([key, value]) => {
      if (value === undefined || value === null || value === '') {
        return
      }
      usp.append(key, String(value))
    })
    const query = usp.toString()
    return query ? `?${query}` : ''
  }

  function resolveUrl(url: string): string {
    if (/^https?:\/\//i.test(url)) {
      return url
    }
    if (url.startsWith('/')) {
      // baseUrl 为绝对地址时按 origin 解析；开发环境 baseUrl 是 /api 前缀，直接使用服务端返回的 context-path 地址
      return /^https?:\/\//i.test(baseUrl) ? new URL(url, baseUrl).toString() : url
    }
    return `${baseUrl}/${url}`
  }

  const client: FileClient = {
    baseUrl,
    mode,

    async upload(file: File, uploadOptions: FileUploadOptions = {}): Promise<FileMeta> {
      const token = await options.getToken()
      return uploadWithXhr({
        // 业务归属放 query：审计切面只记录 query string，便于按业务定位上传/删除
        url: baseUrl + `${prefix}/upload` + queryString({
          bizType: uploadOptions.bizType,
          bizId: uploadOptions.bizId,
          remark: uploadOptions.remark
        }),
        token,
        file,
        options: uploadOptions
      })
    },

    queryPage(params: FileQueryParams): Promise<FilePageResult> {
      const path = mode === 'open' ? `${OPEN_PREFIX}/query` : `${INTERNAL_PREFIX}/queryPage`
      return request<FilePageResult>(path + queryString(params), { method: 'GET' })
    },

    getById(id: string): Promise<FileMeta> {
      const path = mode === 'open'
        ? `${OPEN_PREFIX}/get/${encodeURIComponent(id)}`
        : `${INTERNAL_PREFIX}/getById${queryString({ id })}`
      return request<FileMeta>(path, { method: 'GET' })
    },

    async remove(id: string): Promise<void> {
      if (mode === 'open') {
        throw new FileClientError(403, '开放平台接口不支持删除文件')
      }
      const body = new URLSearchParams({ id })
      await request<void>(`${INTERNAL_PREFIX}/delete`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body
      })
    },

    async createTicket(id: string, purpose: TicketPurpose = 'preview'): Promise<FileTicket> {
      const body = new URLSearchParams({ id, purpose })
      const { data, url: requestUrl } = await requestWithUrl<FileTicket>(`${prefix}/ticket`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body
      })
      // 票据下载始终走内部匿名路径；按 baseUrl 拼接，避免部署前缀（/api、context-path）不一致
      const downloadUrl = `${baseUrl}${INTERNAL_PREFIX}/ticketDownload${queryString({ ticket: data.ticket })}`
      return {
        ticket: data.ticket,
        expiresIn: data.expiresIn,
        // 微前端沙箱（micro-app/wujie）只重写 fetch/XHR，<img>/<video> 的 src 会按宿主源解析，
        // 宿主未代理文件路径时媒体必然加载失败；这里用票据接口的实际请求地址补成绝对地址
        url: absoluteUrl(downloadUrl, requestUrl)
      }
    },

    async download(id: string, fileName?: string): Promise<void> {
      const path = `${prefix}/download/${encodeURIComponent(id)}`
      const response = await fetchImpl(baseUrl + path, { method: 'GET', headers: await authHeaders() })
      if (!response.ok) {
        throw new FileClientError(response.status, await errorMessage(response, '下载失败'), response.status)
      }
      const blob = await response.blob()
      let name = fileName
      if (!name) {
        try {
          name = (await client.getById(id)).fileName
        } catch (e) {
          name = id
        }
      }
      saveBlob(blob, name || id)
    },

    resolveUrl
  }
  return client
}

interface XhrUploadConfig {
  url: string
  token: string
  file: File
  options: FileUploadOptions
}

/**
 * 用 XHR 上传以获得进度与取消能力。
 */
function uploadWithXhr(config: XhrUploadConfig): Promise<FileMeta> {
  const { url, token, file, options } = config
  return new Promise<FileMeta>((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', url)
    if (token) {
      xhr.setRequestHeader('Authorization', `Bearer ${token}`)
    }

    const abortHandler = () => xhr.abort()
    if (options.signal) {
      if (options.signal.aborted) {
        reject(new FileClientError(499, '上传已取消'))
        return
      }
      options.signal.addEventListener('abort', abortHandler)
    }

    const cleanup = () => {
      if (options.signal) {
        options.signal.removeEventListener('abort', abortHandler)
      }
    }

    xhr.upload.onprogress = (event) => {
      if (options.onProgress && event.lengthComputable) {
        options.onProgress(Math.round((event.loaded / event.total) * 100))
      }
    }
    xhr.onerror = () => {
      cleanup()
      reject(new FileClientError(0, '网络异常，上传失败'))
    }
    xhr.onabort = () => {
      cleanup()
      reject(new FileClientError(499, '上传已取消'))
    }
    xhr.onload = () => {
      cleanup()
      let body: ResultBody<FileMeta> | null = null
      try {
        body = JSON.parse(xhr.responseText) as ResultBody<FileMeta>
      } catch (e) {
        body = null
      }
      if (!body) {
        reject(new FileClientError(xhr.status || 500, '响应格式错误', xhr.status))
        return
      }
      if (xhr.status < 200 || xhr.status >= 300 || body.code !== 200) {
        reject(new FileClientError(body.code || xhr.status, body.msg || '上传失败', xhr.status))
        return
      }
      resolve(body.data as FileMeta)
    }

    const form = new FormData()
    form.append('file', file)
    xhr.send(form)
  })
}

function saveBlob(blob: Blob, fileName: string) {
  const href = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = href
  link.download = fileName
  link.style.display = 'none'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  setTimeout(() => URL.revokeObjectURL(href), 1000)
}

function normalizeBaseUrl(baseUrl: string): string {
  return baseUrl.replace(/\/+$/, '')
}

/**
 * 把站内绝对路径补全为绝对地址（origin 取自实际请求地址）。
 * 无法补全时原样返回，交由浏览器按当前文档解析。
 */
function absoluteUrl(url: string, requestUrl: string): string {
  if (!url.startsWith('/') || !requestUrl) {
    return url
  }
  try {
    return new URL(url, requestUrl).toString()
  } catch (e) {
    return url
  }
}
