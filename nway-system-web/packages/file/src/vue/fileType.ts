/**
 * 文件类型判定与预览策略。
 *
 * 以服务端 fileExt 为准，前端 MIME 作为兜底；SVG 默认不进入图片预览（避免脚本型 XSS）。
 */
import type { FileMeta, PreviewKind } from '../types'

export const IMAGE_EXT = new Set(['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp'])

export const VIDEO_EXT = new Set(['mp4', 'webm', 'ogg'])

export const PDF_EXT = new Set(['pdf'])

export function resolvePreviewKind(file: Partial<FileMeta> | null | undefined): PreviewKind {
  if (!file) {
    return 'other'
  }
  const ext = (file.fileExt || extOf(file.fileName) || '').toLowerCase()
  if (IMAGE_EXT.has(ext)) {
    return 'image'
  }
  if (VIDEO_EXT.has(ext)) {
    return 'video'
  }
  if (PDF_EXT.has(ext)) {
    return 'pdf'
  }
  const contentType = (file.contentType || '').toLowerCase()
  if (!ext) {
    if (contentType.startsWith('application/pdf')) {
      return 'pdf'
    }
    if (contentType.startsWith('image/') && !contentType.includes('svg')) {
      return 'image'
    }
    if (contentType.startsWith('video/')) {
      return 'video'
    }
  }
  return 'other'
}

/**
 * 浏览器是否内置 PDF 阅读器（可在 iframe / 新窗口内联打开）。
 * Chrome、Edge、Firefox 通过 pdfViewerEnabled 判断；Safari 等 WebKit 无该字段，回退到 MIME 注册信息。
 */
export function canPreviewPdfNatively(): boolean {
  if (typeof navigator === 'undefined') {
    return false
  }
  const nav = navigator as Navigator & { pdfViewerEnabled?: boolean }
  if (typeof nav.pdfViewerEnabled === 'boolean') {
    return nav.pdfViewerEnabled
  }
  if (nav.mimeTypes && nav.mimeTypes.namedItem('application/pdf')) {
    return true
  }
  // 桌面/iOS Safari 内置 PDF 阅读器，但不注册 MIME，也没有 pdfViewerEnabled
  const ua = nav.userAgent || ''
  return /applewebkit/i.test(ua) && !/chrome|chromium|edg|android/i.test(ua)
}

/** 从文件名推断小写扩展名（仅在服务端未返回 fileExt 时兜底） */
export function extOf(fileName?: string): string {
  if (!fileName) {
    return ''
  }
  const dot = fileName.lastIndexOf('.')
  return dot >= 0 ? fileName.substring(dot + 1).toLowerCase() : ''
}

/** 文件类型图标文本（无图标库依赖） */
export function fileTypeLabel(file: Partial<FileMeta> | null | undefined): string {
  const kind = resolvePreviewKind(file)
  if (kind === 'image') {
    return 'IMG'
  }
  if (kind === 'video') {
    return 'VID'
  }
  if (kind === 'pdf') {
    return 'PDF'
  }
  const ext = (file?.fileExt || extOf(file?.fileName)).toUpperCase()
  return ext && ext.length <= 4 ? ext : 'FILE'
}

/** 字节数格式化 */
export function formatFileSize(size?: number): string {
  const value = Number(size)
  if (!Number.isFinite(value) || value <= 0) {
    return '0 B'
  }
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let index = 0
  let num = value
  while (num >= 1024 && index < units.length - 1) {
    num /= 1024
    index++
  }
  return `${num.toFixed(index === 0 ? 0 : 1)} ${units[index]}`
}
