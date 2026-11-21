/**
 * nway-system 文件中心 Vue 3 组件。
 */
export { default as FileUpload } from './FileUpload.vue'
export { default as FilePreview } from './FilePreview.vue'
export {
  IMAGE_EXT,
  VIDEO_EXT,
  PDF_EXT,
  canPreviewPdfNatively,
  extOf,
  fileTypeLabel,
  formatFileSize,
  resolvePreviewKind
} from './fileType'
export type { FileClient, FileMeta, PreviewKind } from '../types'
