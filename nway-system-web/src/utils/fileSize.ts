/** 格式化文件大小，AI 附件输入区与消息附件共用。 */
export function formatFileSize(size?: number): string {
  if (!size) return ''
  if (size < 1024) return size + ' B'
  if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB'
  return (size / 1024 / 1024).toFixed(1) + ' MB'
}
