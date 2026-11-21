/**
 * cURL 命令解析（bash / Windows CMD / PowerShell）。
 *
 * 仅用于从一段可用的 curl 命令提取方法、URL/路径、查询参数、请求头与请求体，
 * 不做任何网络请求，也不执行任何 shell 命令。
 */

export interface CurlQueryParam {
  name: string
  value: string
}

export interface CurlFormField {
  name: string
  value: string
  file: boolean
}

export interface CurlBody {
  /** JSON=请求体为 JSON；FORM=键值表单；RAW=无法解析的原始文本 */
  mode: 'JSON' | 'FORM' | 'RAW'
  value: any
  raw: string
  /** FORM 中的文件字段名（-F name=@file） */
  fileFields?: string[]
}

export interface ParsedCurl {
  method: string
  /** 原始 URL，可能为相对路径 */
  url: string
  /** 协议 + 域名 + 端口，相对 URL 时为空 */
  origin: string
  /** 去掉协议域名后的路径，始终以 / 开头 */
  path: string
  query: CurlQueryParam[]
  /** Content-Type，未显式指定时为空 */
  contentType: string
  /** 需要固定发送的业务请求头（已排除认证与传输相关请求头） */
  headers: Record<string, string>
  body: CurlBody | null
  warnings: string[]
}

/** 需要取值的短选项（支持 -XPOST 这类紧凑写法） */
const SHORT_WITH_VALUE = new Set([
  '-X', '-H', '-d', '-F', '-u', '-b', '-A', '-e', '-o', '-m', '-T', '-w', '-x', '-U', '-E'
])

/** 需要取值但无需解析的长选项 */
const LONG_WITH_VALUE = new Set([
  '--output', '--max-time', '--connect-timeout', '--retry', '--retry-delay', '--retry-max-time',
  '--cert', '--key', '--cacert', '--capath', '--resolve', '--connect-to', '--interface',
  '--limit-rate', '--max-filesize', '--proto', '--proto-redir', '--ciphers', '--write-out',
  '--upload-file', '--oauth2-bearer', '--aws-sigv4', '--proxy', '--proxy-user', '--dns-servers',
  '--http-version', '--local-port', '--max-redirs', '--netrc-file'
])

/** 不产生请求语义、直接忽略的开关 */
const IGNORED_FLAGS = new Set([
  '-s', '-S', '-k', '-L', '-i', '-v', '-I', '-N', '-f', '-g', '-j', '-#', '-q', '--%',
  '--silent', '--show-error', '--insecure', '--location', '--location-trusted', '--compressed',
  '--fail', '--globoff', '--verbose', '--include', '--head', '--no-buffer', '--raw',
  '--path-as-is', '--no-progress-meter', '--progress-bar', '--http1.1', '--http2',
  '--tlsv1.2', '--tlsv1.3'
])

/** 传输层/认证请求头，不写入 invokeConfig.headers */
const IGNORED_HEADERS = new Set([
  'accept', 'accept-encoding', 'accept-language', 'connection', 'content-length', 'host',
  'origin', 'referer', 'user-agent', 'cache-control', 'pragma', 'upgrade-insecure-requests',
  'sec-fetch-dest', 'sec-fetch-mode', 'sec-fetch-site', 'sec-ch-ua', 'sec-ch-ua-mobile',
  'sec-ch-ua-platform'
])

/**
 * 解析 cURL 命令。
 *
 * @throws 命令缺失、URL 缺失、引号未闭合或方法不在 GET/POST/PUT/DELETE 内时抛出
 */
export function parseCurl(raw: string): ParsedCurl {
  if (!raw || !raw.trim()) {
    throw new Error('请粘贴 cURL 命令')
  }
  const warnings: string[] = []
  const tokens = tokenize(normalizeCommand(raw))
  const start = tokens.findIndex(isCurlCommand)
  if (start < 0) {
    throw new Error('未找到 curl 命令，请确认粘贴内容以 curl 开头')
  }

  let method = ''
  let url = ''
  let forceGet = false
  let jsonFlag = false
  const headerLines: string[] = []
  const dataParts: string[] = []
  const formFields: CurlFormField[] = []

  let index = start
  const nextValue = (name: string, inline?: string): string => {
    if (inline !== undefined) {
      return inline
    }
    index += 1
    if (index >= tokens.length) {
      throw new Error(`参数 ${name} 缺少取值`)
    }
    return tokens[index]
  }

  for (index += 1; index < tokens.length; index += 1) {
    const token = tokens[index]
    if (!token) {
      continue
    }
    if (!token.startsWith('-')) {
      if (!url) {
        url = token
      } else {
        warnings.push(`已忽略多余参数：${token}`)
      }
      continue
    }

    let name = token
    let inline: string | undefined
    const eq = token.indexOf('=')
    if (token.startsWith('--') && eq > 2) {
      name = token.slice(0, eq)
      inline = token.slice(eq + 1)
    } else if (!token.startsWith('--') && token.length > 2 && SHORT_WITH_VALUE.has(token.slice(0, 2))) {
      name = token.slice(0, 2)
      inline = token.slice(2)
    }

    switch (name) {
      case '-X':
      case '--request':
        method = nextValue(name, inline).toUpperCase()
        break
      case '-H':
      case '--header':
        headerLines.push(nextValue(name, inline))
        break
      case '-d':
      case '--data':
      case '--data-raw':
      case '--data-ascii':
      case '--data-binary':
      case '--data-urlencode':
        dataParts.push(nextValue(name, inline))
        break
      case '--json':
        jsonFlag = true
        dataParts.push(nextValue(name, inline))
        break
      case '-F':
      case '--form':
        formFields.push(parseFormEntry(nextValue(name, inline), true))
        break
      case '--form-string':
        formFields.push(parseFormEntry(nextValue(name, inline), false))
        break
      case '-G':
      case '--get':
        forceGet = true
        break
      case '--url':
        url = nextValue(name, inline)
        break
      case '-u':
      case '--user':
        nextValue(name, inline)
        warnings.push('已忽略 -u 基础认证：调用时使用当前登录用户的 Authorization')
        break
      case '-b':
      case '--cookie':
        nextValue(name, inline)
        warnings.push('已忽略 Cookie 请求头：调用时使用当前登录用户的身份')
        break
      case '-A':
      case '--user-agent':
      case '-e':
      case '--referer':
        nextValue(name, inline)
        break
      default:
        if (LONG_WITH_VALUE.has(name)) {
          nextValue(name, inline)
        } else if (!IGNORED_FLAGS.has(name) && !/^-[A-Za-z]{2,}$/.test(name)) {
          warnings.push(`已忽略未知参数：${name}`)
        }
        break
    }
  }

  const { contentType, headers } = parseHeaders(headerLines, warnings)
  const effectiveContentType = contentType || (jsonFlag ? 'application/json' : '')

  let fullUrl = url.trim()
  if (!fullUrl) {
    throw new Error('未找到请求 URL')
  }
  const originMatch = /^[a-z][a-z0-9+.-]*:\/\/[^/?#]*/i.exec(fullUrl)
  const origin = originMatch ? originMatch[0] : ''
  let rest = origin ? fullUrl.slice(origin.length) : fullUrl
  const hashIndex = rest.indexOf('#')
  if (hashIndex >= 0) {
    rest = rest.slice(0, hashIndex)
  }
  let path = rest
  let queryString = ''
  const queryIndex = rest.indexOf('?')
  if (queryIndex >= 0) {
    path = rest.slice(0, queryIndex)
    queryString = rest.slice(queryIndex + 1)
  }
  if (!path) {
    path = '/'
  }
  if (!path.startsWith('/')) {
    path = `/${path}`
  }

  let body: CurlBody | null = null
  const dataBody = dataParts.join('&')
  if (forceGet && (dataBody || formFields.length)) {
    warnings.push('检测到 -G：请求数据已作为查询参数处理')
    const extra = [dataBody, formFields.map((field) => `${field.name}=${field.value}`).join('&')]
      .filter(Boolean)
      .join('&')
    queryString = [queryString, extra].filter(Boolean).join('&')
  } else if (formFields.length) {
    const value: Record<string, any> = {}
    const fileFields: string[] = []
    for (const field of formFields) {
      if (field.file) {
        fileFields.push(field.name)
        value[field.name] = ''
      } else {
        value[field.name] = field.value
      }
    }
    if (fileFields.length) {
      warnings.push(`表单文件字段（${fileFields.join(', ')}）无法自动映射，请补充 fileMapping`)
    }
    warnings.push('检测到 multipart 表单（-F），已按普通表单字段生成，请确认目标接口是否接受 application/x-www-form-urlencoded')
    body = { mode: 'FORM', value, raw: dataBody, fileFields }
  } else if (dataBody) {
    body = parseBody(dataBody, effectiveContentType, warnings)
  }

  const finalMethod = (forceGet && !method ? 'GET' : method || (body ? 'POST' : 'GET')).toUpperCase()
  if (!['GET', 'POST', 'PUT', 'DELETE'].includes(finalMethod)) {
    throw new Error(`仅支持 GET/POST/PUT/DELETE，当前方法为 ${finalMethod}`)
  }

  return {
    method: finalMethod,
    url: fullUrl,
    origin,
    path,
    query: parseQueryString(queryString),
    contentType: effectiveContentType,
    headers,
    body,
    warnings
  }
}

/** 统一换行与续行符：bash `\`、CMD `^`、PowerShell 反引号 */
function normalizeCommand(raw: string): string {
  let text = raw.replace(/\r\n?/g, '\n')
  // Windows CMD 转义：Chrome「Copy as cURL (cmd)」把参数引号写成 ^"、& 写成 ^&、括号写成 ^(；
  // 不还原会把 ^ 带进 URL、查询参数与请求头（如 employeeId=^、^authorization）。
  // 仅当出现 CMD 风格的克拉引号时按 CMD 规则还原，避免误伤 bash/PowerShell 中含 ^ 的参数值。
  if (/(^|\s)\^"/.test(text) || /\^"(?=\s|$)/.test(text)) {
    text = text.replace(/\^([^ \t\n])/g, '$1')
  }
  return text
    .replace(/\\[ \t]*\n/g, ' ')
    .replace(/\^[ \t]*\n/g, ' ')
    .replace(/`[ \t]*\n/g, ' ')
    .replace(/^\s*(\$|>)\s+/, '')
    .trim()
}

/** 按 shell 引号规则切分参数，兼容 `\"`、`\\` 与 PowerShell 反引号转义 */
function tokenize(input: string): string[] {
  const tokens: string[] = []
  let current = ''
  let hasToken = false
  let quote: '"' | "'" | null = null

  for (let i = 0; i < input.length; i += 1) {
    const char = input[i]
    if (quote === "'") {
      if (char === "'") {
        quote = null
      } else {
        current += char
      }
      continue
    }
    if (quote === '"') {
      if (char === '`' && i + 1 < input.length && ['"', '`', '$', '\\'].includes(input[i + 1])) {
        current += input[i + 1]
        i += 1
        continue
      }
      if (char === '\\' && i + 1 < input.length) {
        const next = input[i + 1]
        if (next === '"' || next === '\\' || next === '$' || next === '`') {
          current += next
          i += 1
          continue
        }
        current += char
        continue
      }
      if (char === '"') {
        // CMD / PowerShell 用 "" 表示字符串内的双引号
        if (input[i + 1] === '"' && i + 2 < input.length && !/\s/.test(input[i + 2])) {
          current += '"'
          i += 1
          continue
        }
        quote = null
      } else {
        current += char
      }
      continue
    }
    if (char === '"' || char === "'") {
      quote = char
      hasToken = true
      continue
    }
    if (/\s/.test(char)) {
      if (hasToken) {
        tokens.push(current)
        current = ''
        hasToken = false
      }
      continue
    }
    current += char
    hasToken = true
  }

  if (quote) {
    throw new Error('引号未闭合，请检查 cURL 内容')
  }
  if (hasToken) {
    tokens.push(current)
  }
  return tokens
}

function isCurlCommand(token: string): boolean {
  const base = token.split(/[\\/]/).pop() || ''
  return /^curl(\.exe)?$/i.test(base)
}

function parseFormEntry(entry: string, allowFile: boolean): CurlFormField {
  const eq = entry.indexOf('=')
  if (eq < 0) {
    return { name: entry.trim(), value: '', file: false }
  }
  const name = entry.slice(0, eq).trim()
  const value = entry.slice(eq + 1)
  const file = allowFile && (value.startsWith('@') || value.startsWith('<'))
  return { name, value, file }
}

function parseHeaders(headerLines: string[], warnings: string[]) {
  let contentType = ''
  const headers: Record<string, string> = {}
  for (const line of headerLines) {
    const index = line.indexOf(':')
    const name = (index < 0 ? line : line.slice(0, index)).trim()
    const value = index < 0 ? '' : line.slice(index + 1).trim()
    if (!name) {
      continue
    }
    const lower = name.toLowerCase()
    if (lower === 'content-type') {
      contentType = value
      continue
    }
    if (lower === 'authorization' || lower === 'cookie') {
      warnings.push(`已忽略 ${name} 请求头：调用时由后端使用当前登录用户身份`)
      continue
    }
    if (IGNORED_HEADERS.has(lower) || !value) {
      continue
    }
    headers[name] = value
  }
  return { contentType, headers }
}

function parseBody(raw: string, contentType: string, warnings: string[]): CurlBody {
  const type = contentType.toLowerCase()
  const trimmed = raw.trim()
  if (type.includes('/json') || trimmed.startsWith('{') || trimmed.startsWith('[')) {
    try {
      return { mode: 'JSON', value: JSON.parse(trimmed), raw }
    } catch (e: any) {
      warnings.push(`请求体不是合法 JSON（${e.message}），未生成字段`)
      return { mode: 'RAW', value: null, raw }
    }
  }
  if (type.includes('x-www-form-urlencoded') || isFormBody(trimmed)) {
    return { mode: 'FORM', value: parsePairs(trimmed), raw }
  }
  warnings.push('请求体无法识别为 JSON 或表单，未生成字段')
  return { mode: 'RAW', value: null, raw }
}

function isFormBody(text: string): boolean {
  return text.length > 0 && /^[^=&\s]+=[^&]*(?:&[^=&\s]+=[^&]*)*$/.test(text)
}

function parsePairs(text: string): Record<string, string> {
  const result: Record<string, string> = {}
  for (const part of text.split('&')) {
    if (!part) {
      continue
    }
    const eq = part.indexOf('=')
    const name = decode(eq < 0 ? part : part.slice(0, eq))
    if (!name) {
      continue
    }
    result[name] = eq < 0 ? '' : decode(part.slice(eq + 1))
  }
  return result
}

function parseQueryString(text: string): CurlQueryParam[] {
  const result: CurlQueryParam[] = []
  if (!text) {
    return result
  }
  for (const part of text.split('&')) {
    if (!part) {
      continue
    }
    const eq = part.indexOf('=')
    const name = decode(eq < 0 ? part : part.slice(0, eq))
    if (!name) {
      continue
    }
    result.push({ name, value: eq < 0 ? '' : decode(part.slice(eq + 1)) })
  }
  return result
}

function decode(value: string): string {
  try {
    return decodeURIComponent(value.replace(/\+/g, ' '))
  } catch {
    return value
  }
}
