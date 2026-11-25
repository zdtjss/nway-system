/**
 * 语音播报：文本清洗、分句、音色优选与顺序播放。
 *
 * 浏览器 speechSynthesis 有三个必须规避的问题：
 * 1. 一次性把几十段 utterance 全部入队，部分内核会中途静默丢弃后续内容；
 * 2. 单段朗读时间过长会被系统在十几秒处截断；
 * 3. 默认音色通常取到本地 SAPI 音色，电子感明显。
 * 因此这里改为「逐段顺序播报 + 定期唤醒 + 自动选择神经音色」。
 */
import { normalizeView, type AiView } from '@/utils/aiView'

/** 单段朗读上限：留足余量，避免触发系统对长文本的截断 */
const MAX_SPEECH_CHUNK = 60

/** 语音引擎长时间朗读后会静默停止，定期 pause/resume 唤醒 */
const KEEPALIVE_MS = 10_000

/** 表格最多朗读的行数，其余只提示剩余行数 */
const MAX_TABLE_ROWS = 12

/** 单元格 / 视图文本的最大长度，避免朗读整段 JSON */
const MAX_CELL_CHARS = 40

/** 单个结构化视图最多朗读的字符数 */
const MAX_VIEW_CHARS = 400

const NATURAL_VOICE = /natural|neural|online|premium|enhanced|xiaoxiao|xiaoyi|yunxi|yunyang|yunxia|晓晓|晓伊|云希|云扬|云夏/i

const KEEPALIVE_SUPPORTED = typeof navigator !== 'undefined' && /chrome|chromium/i.test(navigator.userAgent)

export const ttsSupported = typeof window !== 'undefined' && 'speechSynthesis' in window

export interface SpeakOptions {
  rate?: number
  onEnd?: () => void
  onError?: () => void
}

let session = 0
let keepAliveTimer: number | null = null

/**
 * 自动选择最合适的音色：神经/自然音色优先，其次 zh-CN，最后任意中文音色。
 * 需要更高质量时可换 Edge，系统会提供 Natural 系列在线音色。
 */
function pickVoice(): SpeechSynthesisVoice | null {
  if (!ttsSupported) return null
  const voices = window.speechSynthesis.getVoices()
    .filter((voice) => /^zh/i.test(voice.lang))
    .sort((a, b) => voiceScore(b) - voiceScore(a))
  return voices[0] || null
}

function voiceScore(voice: SpeechSynthesisVoice): number {
  let score = 0
  if (NATURAL_VOICE.test(voice.name)) score += 100
  if (/^zh[-_]cn/i.test(voice.lang)) score += 20
  if (!voice.localService) score += 10
  return score
}

export interface SpeechStream {
  /** 追加模型增量文本，内部按完整句子切分后排队播报 */
  push(delta: string): void
  /** 本轮输出结束，剩余内容读完后触发 onEnd */
  end(): void
  /** 立即停止并清空队列，不触发 onEnd */
  cancel(): void
}

/**
 * 增量播报：文本可随时 push，内部按句子边界播报；输入结束后补读剩余内容。
 * 同一时间只保留一个播报会话，新建会话会取消上一段。
 */
export function createSpeechStream(options: SpeakOptions = {}): SpeechStream {
  if (!ttsSupported) {
    return { push: () => {}, end: () => {}, cancel: () => {} }
  }
  stopSpeak()
  const token = ++session
  const synth = window.speechSynthesis
  const voice = pickVoice()
  const queue: string[] = []
  let inputEnded = false
  let finished = false
  let playing = false
  let starting = false
  let keepAliveOn = false

  const finish = () => {
    if (finished) return
    finished = true
    stopKeepAlive()
    options.onEnd?.()
  }
  const fail = () => {
    if (finished) return
    finished = true
    stopKeepAlive()
    if (token === session) synth.cancel()
    options.onError?.()
  }

  const playNext = () => {
    if (token !== session || finished) return
    const chunk = queue.shift()
    if (!chunk) {
      playing = false
      if (inputEnded) finish()
      return
    }
    playing = true
    if (!keepAliveOn) {
      keepAliveOn = true
      startKeepAlive(token)
    }
    const utterance = new SpeechSynthesisUtterance(chunk)
    if (voice) utterance.voice = voice
    utterance.lang = voice?.lang || 'zh-CN'
    utterance.rate = options.rate ?? 1
    utterance.onend = () => playNext()
    utterance.onerror = (event) => {
      if (token !== session) return
      const reason = (event as SpeechSynthesisErrorEvent)?.error || ''
      // 用户主动停止由 stopSpeak / cancel 收尾，不算播放失败
      if (reason === 'interrupted' || reason === 'canceled') return
      fail()
    }
    synth.speak(utterance)
  }

  const pump = () => {
    if (finished || playing || starting || !queue.length) return
    starting = true
    // cancel 后立即 speak 在部分浏览器会被忽略，延后一帧再入队
    window.setTimeout(() => {
      starting = false
      if (token !== session || finished) return
      playNext()
    }, 0)
  }

  const emit = (text: string) => {
    if (finished || token !== session) return
    const chunks = splitForSpeech(text)
    if (!chunks.length) return
    queue.push(...chunks)
    pump()
  }

  const extractor = createSpeechExtractor(emit)

  return {
    push(delta: string) {
      if (finished || inputEnded) return
      if (token !== session) {
        // 已被新的播报会话取代，丢弃后续增量
        finished = true
        return
      }
      extractor.push(delta)
    },
    end() {
      if (finished || inputEnded) return
      if (token !== session) {
        finished = true
        return
      }
      inputEnded = true
      extractor.end()
      if (!playing && !queue.length) finish()
    },
    cancel() {
      if (finished) return
      finished = true
      if (token === session) stopSpeak()
    }
  }
}

/** 一次性播报完整文本（历史消息的手动播报入口）。 */
export function speak(text: string, options: SpeakOptions = {}) {
  if (!ttsSupported || !text) return
  const stream = createSpeechStream(options)
  stream.push(text)
  stream.end()
}

export function stopSpeak() {
  session += 1
  stopKeepAlive()
  if (ttsSupported) window.speechSynthesis.cancel()
}

/**
 * Chromium 在连续朗读较长时间后会静默停止，pause + resume 可以把引擎唤醒。
 * Safari 的 pause 存在无法恢复的问题，因此只在 Chromium 内核上启用。
 */
function startKeepAlive(token: number) {
  if (!KEEPALIVE_SUPPORTED) return
  stopKeepAlive()
  keepAliveTimer = window.setInterval(() => {
    if (token !== session) {
      stopKeepAlive()
      return
    }
    const synth = window.speechSynthesis
    if (synth.speaking && !synth.paused) {
      synth.pause()
      synth.resume()
    }
  }, KEEPALIVE_MS)
}

function stopKeepAlive() {
  if (keepAliveTimer) {
    window.clearInterval(keepAliveTimer)
    keepAliveTimer = null
  }
}

/** 播报前的文本清洗：结构化视图转成自然语言，其余 Markdown 标记去掉。 */
export function toSpeakableText(markdown: string): string {
  if (!markdown) return ''
  let text = markdown
    // nway-view 块是表格、指标卡与图表，直接丢弃会让播报缺少大段结果
    .replace(/```[ \t]*nway-view[^\n]*\r?\n([\s\S]*?)```/g, (_, body: string) => `\n${viewToSpeech(body)}\n`)
    // 其余代码块不朗读：逐字符念代码没有意义
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/~~~[\s\S]*?~~~/g, ' ')
  text = tableToSpeech(text)
  return cleanSpeechText(text)
}

/** 去掉 Markdown 标记，只保留可朗读的正文。 */
function cleanSpeechText(text: string): string {
  return text
    .replace(/!\[[^\]]*]\([^)]*\)/g, ' ')
    .replace(/\[([^\]]*)]\([^)]*\)/g, '$1')
    .replace(/^\s*[-*+]\s+/gm, '')
    .replace(/^\s*\d+\.\s+/gm, '')
    .replace(/^\s*#{1,6}\s*/gm, '')
    .replace(/^\s*>\s?/gm, '')
    .replace(/^\s*([-*_])\1{2,}\s*$/gm, '')
    .replace(/[*_~`]/g, '')
    .replace(/<[^>]+>/g, '')
    .replace(/&nbsp;/g, ' ')
    .replace(/\r/g, '')
    .replace(/[ \t]+/g, ' ')
    .replace(/\n{2,}/g, '\n')
    .trim()
}

/** 结构化视图转播报文本；结构非法时返回空串，不朗读占位说明。 */
function viewToSpeech(body: string): string {
  const view = normalizeView(body.trim())
  if (!view) return ''
  const parts: string[] = []
  if (view.title) parts.push(view.title)
  switch (view.type) {
    case 'table':
      parts.push(tableSpeech(view))
      break
    case 'metric':
      parts.push((view.metrics || []).map(metricSpeech).join('；'))
      break
    case 'fields':
      parts.push((view.fields || []).map((field) => `${field.label} ${clip(field.value?.text)}`).join('；'))
      break
    case 'list':
      parts.push((view.list || []).map((item) => clip(item.text)).filter(Boolean).join('；'))
      break
    case 'chart':
      parts.push(chartSpeech(view))
      break
    case 'file':
      parts.push(`文件：${(view.files || []).map((file) => file.name).join('、')}`)
      break
    case 'text':
      parts.push(view.text || '')
      break
    default:
      break
  }
  return clip(parts.filter(Boolean).join('。'), MAX_VIEW_CHARS)
}

function tableSpeech(view: AiView): string {
  const columns = view.columns || []
  const rows = view.rows || []
  const parts: string[] = [`共${view.total ?? rows.length}条`]
  if (view.summary?.length) parts.push(view.summary.map(metricSpeech).join('；'))
  rows.slice(0, MAX_TABLE_ROWS).forEach((row, index) => {
    const cells = row.map((cell, cellIndex) => `${columns[cellIndex]?.label || ''} ${clip(cell?.text)}`.trim())
    parts.push(`第${index + 1}条：${cells.join('，')}`)
  })
  if (rows.length > MAX_TABLE_ROWS) {
    parts.push(`其余${rows.length - MAX_TABLE_ROWS}条见页面表格`)
  }
  return parts.join('。')
}

function metricSpeech(metric: { label: string; value: string; unit?: string }): string {
  return `${metric.label} ${metric.value}${metric.unit || ''}`.trim()
}

function chartSpeech(view: AiView): string {
  const chart = view.chart
  if (!chart) return ''
  const names = chart.series.map((series) => series.name).filter(Boolean)
  const categories = chart.categories.slice(0, 6)
  const samples = chart.series.slice(0, 2).map((series) => {
    const points = series.data.slice(0, categories.length)
      .map((value, index) => `${chart.categories[index]} ${value}`)
      .join('，')
    return `${series.name}：${points}`
  })
  return [
    names.length ? `包含${names.join('、')}` : '',
    ...samples,
    '完整图表见页面'
  ].filter(Boolean).join('。')
}

/** Markdown 表格转成逐行描述，避免整块表格被逐行删除后播报缺内容。 */
function tableToSpeech(text: string): string {
  const lines = text.split('\n')
  const output: string[] = []
  let block: string[] = []

  const flush = () => {
    if (!block.length) return
    const rows = block
      .map((line) => line.trim())
      .filter((line) => !/^\|?[\s:|-]+\|?$/.test(line))
      .map((line) => line.replace(/^\||\|$/g, '').split('|').map((cell) => clip(cell.trim(), 30)))
      .filter((cells) => cells.some(Boolean))
    block = []
    if (rows.length < 2) {
      output.push(...rows.map((cells) => cells.join('，')))
      return
    }
    const header = rows[0]
    output.push(`表格：${header.filter(Boolean).join('、')}，共${rows.length - 1}行。`)
    rows.slice(1, MAX_TABLE_ROWS + 1).forEach((cells, index) => {
      const detail = cells.map((cell, cellIndex) => `${header[cellIndex] || ''} ${cell}`.trim()).join('，')
      output.push(`第${index + 1}行：${detail}。`)
    })
    if (rows.length - 1 > MAX_TABLE_ROWS) {
      output.push(`其余${rows.length - 1 - MAX_TABLE_ROWS}行见页面表格。`)
    }
  }

  lines.forEach((line) => {
    if (/^\s*\|.*\|\s*$/.test(line)) {
      block.push(line)
      return
    }
    flush()
    output.push(line)
  })
  flush()
  return output.join('\n')
}

const SENTENCE_BOUNDARY = new Set(['。', '！', '？', '!', '?', '；', ';'])
const TABLE_ROW = /^\s*\|.*\|\s*$/
const TABLE_START = /^\s*\|/
/** 一直没有标点时最多缓存多少字，超过后按最近的停顿切分，避免长时间不播 */
const MAX_PENDING_CHARS = 120

/**
 * 流式播报文本提取：只处理已经到达的内容，遇到完整句子就吐出一段。
 * 代码块（含 mermaid）整体跳过；nway-view 等结构化视图等围栏闭合后转成自然语言；
 * Markdown 表格整块结束后统一转述，避免读到分隔行和半截单元格。
 */
function createSpeechExtractor(emit: (text: string) => void) {
  let pending = ''
  let line = ''
  let table: string[] = []
  let fence: { marker: string; view: boolean; body: string } | null = null
  let ended = false

  const emitFragment = (raw: string) => {
    const text = cleanSpeechText(raw)
    if (text) emit(text)
  }

  const flushTable = () => {
    if (!table.length) return
    const rows = table.join('\n')
    table = []
    emitFragment(tableToSpeech(rows))
  }

  /** 中文标点直接断句；英文句号只有后面跟空白才断，避免切开小数、域名和版本号 */
  const isSentenceBoundary = (text: string, index: number) => {
    if (SENTENCE_BOUNDARY.has(text[index])) return true
    return text[index] === '.' && /\s/.test(text[index + 1] || '')
  }

  /** 普通正文不等整行结束，按句子边界持续吐字；表格行等换行后再整体处理 */
  const extractSentences = () => {
    if (!line) return
    if (TABLE_START.test(line)) return
    // 表格之后出现第一段普通文字时先把表格读完，保证播报顺序与正文一致
    if (table.length) flushTable()
    while (line) {
      let boundary = -1
      for (let i = 0; i < line.length; i += 1) {
        if (isSentenceBoundary(line, i)) {
          boundary = i + 1
          break
        }
      }
      if (boundary < 0) break
      const fragment = line.slice(0, boundary)
      line = line.slice(boundary)
      emitFragment(fragment)
    }
    while (line.length > MAX_PENDING_CHARS) {
      const head = line.slice(0, MAX_PENDING_CHARS)
      const pause = Math.max(
        head.lastIndexOf('，'),
        head.lastIndexOf('、'),
        head.lastIndexOf(','),
        head.lastIndexOf('：'),
        head.lastIndexOf(' ')
      )
      const cut = pause >= 40 ? pause + 1 : MAX_PENDING_CHARS
      emitFragment(line.slice(0, cut))
      line = line.slice(cut)
    }
  }

  const finishLine = () => {
    const value = line
    line = ''
    if (TABLE_ROW.test(value)) {
      table.push(value)
      return
    }
    flushTable()
    emitFragment(value)
  }

  const processText = (raw: string) => {
    let start = 0
    for (let i = 0; i < raw.length; i += 1) {
      if (raw[i] !== '\n') continue
      line += raw.slice(start, i)
      finishLine()
      start = i + 1
    }
    line += raw.slice(start)
    extractSentences()
  }

  const findMarker = (text: string) => {
    const tick = text.indexOf('```')
    const tilde = text.indexOf('~~~')
    if (tick < 0 && tilde < 0) return null
    if (tick >= 0 && (tilde < 0 || tick < tilde)) return { index: tick, marker: '```' }
    return { index: tilde, marker: '~~~' }
  }

  /** 只保留可能是围栏前缀的末尾 1~2 个反引号/波浪号，其余文本立即处理 */
  const tailKeep = (text: string) => {
    if (ended) return 0
    const match = /(`{1,2}|~{1,2})$/.exec(text)
    return match ? match[1].length : 0
  }

  const openFence = (marker: string, header: string) => {
    finishLine()
    flushTable()
    fence = { marker, view: /^[ \t]*nway-view\b/.test(header), body: '' }
  }

  const scan = () => {
    while (pending) {
      if (fence) {
        const close = pending.indexOf(fence.marker)
        if (close < 0) {
          const keep = tailKeep(pending)
          if (pending.length > keep) {
            fence.body += pending.slice(0, pending.length - keep)
            pending = pending.slice(pending.length - keep)
          }
          if (!ended) return
          // 未闭合的代码块/视图没有可信内容，直接丢弃
          fence = null
          pending = ''
          continue
        }
        fence.body += pending.slice(0, close)
        pending = pending.slice(close + fence.marker.length)
        const block = fence
        fence = null
        if (block.view) emitFragment(viewToSpeech(block.body))
        continue
      }

      const marker = findMarker(pending)
      if (!marker) {
        const keep = tailKeep(pending)
        if (pending.length > keep) {
          processText(pending.slice(0, pending.length - keep))
          pending = pending.slice(pending.length - keep)
        }
        if (!ended) return
        processText(pending)
        pending = ''
        continue
      }

      processText(pending.slice(0, marker.index))
      pending = pending.slice(marker.index)
      const lineEnd = pending.indexOf('\n')
      if (lineEnd < 0) {
        if (!ended) return
        // 结尾只有半截代码围栏，按未闭合处理
        pending = ''
        return
      }
      const header = pending.slice(marker.marker.length, lineEnd)
      pending = pending.slice(lineEnd + 1)
      openFence(marker.marker, header)
    }
  }

  return {
    push(delta: string) {
      if (ended || !delta) return
      pending += delta
      scan()
    },
    end() {
      if (ended) return
      ended = true
      scan()
      finishLine()
      flushTable()
    }
  }
}

/** 长回复按句子切成小段，逐段顺序播报。 */
function splitForSpeech(text: string, max = MAX_SPEECH_CHUNK): string[] {
  const sentences = text.match(/[^。！？!?；;\n]+[。！？!?；;\n]?/g) || [text]
  const chunks: string[] = []
  let current = ''
  const push = () => {
    const value = current.trim()
    if (value) chunks.push(value)
    current = ''
  }
  sentences.forEach((sentence) => {
    const piece = sentence.trim()
    if (!piece) return
    if (current && current.length + piece.length > max) push()
    if (piece.length <= max) {
      current += piece
      return
    }
    for (let i = 0; i < piece.length; i += max) {
      const slice = piece.slice(i, i + max)
      if (slice.length === max) chunks.push(slice)
      else current = slice
    }
  })
  push()
  return chunks
}

function clip(text: string | undefined, max = MAX_CELL_CHARS): string {
  if (!text) return ''
  const value = String(text).replace(/\s+/g, ' ').trim()
  return value.length > max ? `${value.slice(0, max)}…` : value
}
