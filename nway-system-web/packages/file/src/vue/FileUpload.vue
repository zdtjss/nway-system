<template>
  <div class="nway-file-upload">
    <div
      v-if="!disabled && !isFull"
      class="nway-file-upload__drop"
      :class="{ 'is-dragover': dragover }"
      @click="pickFiles"
      @dragover.prevent="dragover = true"
      @dragleave.prevent="dragover = false"
      @drop.prevent="onDrop"
    >
      <slot name="trigger">
        <div class="nway-file-upload__trigger">
          <svg class="nway-file-upload__trigger-icon" viewBox="0 0 24 24" aria-hidden="true">
            <path fill="currentColor"
              d="M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96zM14 13v4h-4v-4H7l5-5 5 5h-3z" />
          </svg>
          <span>上传附件</span>
        </div>
      </slot>
      <input
        ref="inputRef"
        class="nway-file-upload__input"
        type="file"
        :multiple="multiple"
        :accept="accept"
        @change="onFilesSelected"
      />
    </div>

    <ul v-if="rows.length" class="nway-file-upload__list">
      <li v-for="row in rows" :key="row.key" class="nway-file-upload__item">
        <span class="nway-file-upload__icon">{{ fileTypeLabel(row.meta) }}</span>
        <span class="nway-file-upload__name" :title="row.meta.fileName">{{ row.meta.fileName }}</span>
        <span class="nway-file-upload__size">{{ formatFileSize(row.meta.fileSize) }}</span>

        <template v-if="row.pending">
          <div class="nway-file-upload__progress">
            <div class="nway-file-upload__progress-bar" :style="{ width: row.progress + '%' }"></div>
          </div>
          <span class="nway-file-upload__percent">{{ row.progress }}%</span>
          <button
            v-if="row.status === 'uploading'"
            type="button"
            class="nway-file-upload__link"
            @click.stop="cancel(row)"
          >
            取消
          </button>
          <button
            v-else
            type="button"
            class="nway-file-upload__link"
            @click.stop="retry(row)"
          >
            重试
          </button>
          <span v-if="row.error" class="nway-file-upload__error">{{ row.error }}</span>
        </template>
        <template v-else>
          <button
            v-if="previewable(row.meta)"
            type="button"
            class="nway-file-upload__link"
            @click.stop="openPreview(row.meta)"
          >
            预览
          </button>
          <button type="button" class="nway-file-upload__link" @click.stop="downloadFile(row.meta)">
            下载
          </button>
          <button
            v-if="!disabled"
            type="button"
            class="nway-file-upload__link nway-file-upload__link--danger"
            @click.stop="removeFile(row.meta)"
          >
            移除
          </button>
        </template>
      </li>
    </ul>

    <FilePreview v-model="previewVisible" :file="previewFile" :files="previewFiles" :client="client" />
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import type { FileClient, FileMeta } from '../types'
import FilePreview from './FilePreview.vue'
import { fileTypeLabel, formatFileSize, resolvePreviewKind } from './fileType'

export interface Props {
  modelValue?: FileMeta[]
  client: FileClient
  bizType?: string
  bizId?: string
  remark?: string
  multiple?: boolean
  limit?: number
  maxSizeMb?: number
  accept?: string
  disabled?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: () => [],
  multiple: true,
  limit: 5,
  maxSizeMb: 200,
  accept: '',
  disabled: false
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: FileMeta[]): void
  (e: 'uploaded', value: FileMeta): void
  (e: 'removed', value: FileMeta): void
  (e: 'error', value: string): void
}>()

interface PendingItem {
  key: string
  file: File
  meta: FileMeta
  status: 'uploading' | 'failed'
  progress: number
  error?: string
  controller?: AbortController
}

interface RowItem {
  key: string
  meta: FileMeta
  pending: boolean
  status: 'uploading' | 'failed' | ''
  progress: number
  error?: string
  controller?: AbortController
}

let seq = 0

const inputRef = ref<HTMLInputElement>()
const dragover = ref(false)
const pending = ref<PendingItem[]>([])
const previewVisible = ref(false)
const previewFile = ref<FileMeta | null>(null)

/** 预览弹窗只在可预览的文件之间前后切换，避免翻到只能下载的文件 */
const previewFiles = computed(() =>
  props.modelValue.filter((meta) => resolvePreviewKind(meta) !== 'other')
)

/** 附件数量达到上限后隐藏上传入口，已上传列表保留 */
const isFull = computed(() => props.modelValue.length >= props.limit)

const rows = computed<RowItem[]>(() => {
  const existing: RowItem[] = props.modelValue.map((meta) => ({
    key: `meta-${meta.id}`,
    meta,
    pending: false,
    status: '',
    progress: 100
  }))
  const uploading: RowItem[] = pending.value.map((item) => ({
    key: item.key,
    meta: item.meta,
    pending: true,
    status: item.status,
    progress: item.progress,
    error: item.error,
    controller: item.controller
  }))
  return [...existing, ...uploading]
})

function pickFiles() {
  if (props.disabled) {
    return
  }
  if (inputRef.value) {
    inputRef.value.value = ''
    inputRef.value.click()
  }
}

function onFilesSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  enqueue(files)
}

function onDrop(event: DragEvent) {
  dragover.value = false
  if (props.disabled) {
    return
  }
  enqueue(Array.from(event.dataTransfer?.files || []))
}

function enqueue(files: File[]) {
  if (!files.length) {
    return
  }
  const accepted: File[] = []
  for (const file of files) {
    const reason = validate(file)
    if (reason) {
      notifyError(reason)
      continue
    }
    if (!props.multiple) {
      if (props.modelValue.length > 0 || pending.value.length > 0 || accepted.length > 0) {
        notifyError('只能上传一个文件')
        break
      }
    }
    accepted.push(file)
  }
  if (accepted.length === 0) {
    return
  }
  if (props.modelValue.length + pending.value.length + accepted.length > props.limit) {
    notifyError(`文件数量不能超过 ${props.limit} 个`)
    accepted.length = Math.max(0, props.limit - props.modelValue.length - pending.value.length)
  }
  accepted.forEach(startUpload)
}

function validate(file: File): string | null {
  if (file.size <= 0) {
    return `${file.name} 是空文件`
  }
  if (file.size > props.maxSizeMb * 1024 * 1024) {
    return `${file.name} 超过 ${props.maxSizeMb}MB 限制`
  }
  if (!matchAccept(file)) {
    return `${file.name} 不在允许的文件类型范围内`
  }
  const duplicated = props.modelValue.some((item) => item.fileName === file.name)
    || pending.value.some((item) => item.file.name === file.name)
  if (duplicated) {
    return `${file.name} 已存在，请勿重复上传`
  }
  return null
}

function matchAccept(file: File): boolean {
  if (!props.accept) {
    return true
  }
  const tokens = props.accept.split(',').map((token) => token.trim().toLowerCase()).filter(Boolean)
  if (!tokens.length) {
    return true
  }
  const name = file.name.toLowerCase()
  const type = (file.type || '').toLowerCase()
  const ext = name.substring(name.lastIndexOf('.') + 1)
  return tokens.some((token) => {
    if (token.startsWith('.')) {
      return name.endsWith(token) || Boolean(ext && token === `.${ext}`)
    }
    if (token.endsWith('/*')) {
      return type.startsWith(token.slice(0, -1))
    }
    return token === type
  })
}

function startUpload(file: File) {
  const key = `upload-${++seq}-${file.name}`
  // reactive 项：进度/状态直接变更即可触发视图更新
  const item = reactive<PendingItem>({
    key,
    file,
    meta: {
      id: key,
      fileName: file.name,
      fileSize: file.size,
      contentType: file.type,
      fileExt: file.name.substring(file.name.lastIndexOf('.') + 1).toLowerCase()
    },
    status: 'uploading',
    progress: 0
  })
  pending.value.push(item)
  runUpload(item)
}

async function runUpload(item: PendingItem) {
  const controller = new AbortController()
  item.status = 'uploading'
  item.error = ''
  item.controller = controller
  item.progress = 0
  try {
    const meta = await props.client.upload(item.file, {
      bizType: props.bizType,
      bizId: props.bizId,
      remark: props.remark,
      signal: controller.signal,
      onProgress: (percent) => {
        item.progress = percent
      }
    })
    pending.value = pending.value.filter((entry) => entry.key !== item.key)
    emit('update:modelValue', [...props.modelValue, meta])
    emit('uploaded', meta)
  } catch (e) {
    const error = e as { code?: number; message?: string }
    if (error?.code === 499) {
      pending.value = pending.value.filter((entry) => entry.key !== item.key)
      return
    }
    item.status = 'failed'
    item.error = error?.message || '上传失败'
  }
}

function cancel(row: RowItem) {
  row.controller?.abort()
}

function retry(row: RowItem) {
  const item = pending.value.find((entry) => entry.key === row.key)
  if (item) {
    runUpload(item)
  }
}

function removeFile(meta: FileMeta) {
  emit('update:modelValue', props.modelValue.filter((item) => item.id !== meta.id))
  emit('removed', meta)
}

function previewable(meta: FileMeta): boolean {
  return resolvePreviewKind(meta) !== 'other'
}

function openPreview(meta: FileMeta) {
  previewFile.value = meta
  previewVisible.value = true
}

async function downloadFile(meta: FileMeta) {
  try {
    await props.client.download(meta.id, meta.fileName)
  } catch (e) {
    notifyError((e as { message?: string })?.message || '下载失败')
  }
}

function notifyError(message: string) {
  emit('error', message)
}
</script>

<script lang="ts">
export default {
  name: 'NwayFileUpload'
}
</script>

<style scoped>
.nway-file-upload {
  width: 100%;
  font-size: 14px;
  color: #303133;
}

.nway-file-upload__drop {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 150px;
  height: 68px;
  border: 1px dashed #dcdfe6;
  border-radius: 8px;
  color: #86909c;
  cursor: pointer;
  transition: border-color 0.2s, color 0.2s;
}

.nway-file-upload__drop:hover,
.nway-file-upload__drop.is-dragover {
  border-color: var(--nway-file-accent, #409eff);
  color: var(--nway-file-accent, #409eff);
}

.nway-file-upload__trigger {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  user-select: none;
}

.nway-file-upload__trigger-icon {
  width: 22px;
  height: 22px;
}

.nway-file-upload__input {
  display: none;
}

.nway-file-upload__list {
  margin: 10px 0 0;
  padding: 0;
  list-style: none;
}

.nway-file-upload__item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 4px;
  border-bottom: 1px solid #f0f0f0;
}

.nway-file-upload__item:last-child {
  border-bottom: none;
}

.nway-file-upload__icon {
  flex: none;
  min-width: 42px;
  padding: 2px 6px;
  border-radius: 4px;
  background: #f0f2f5;
  color: #606266;
  font-size: 11px;
  text-align: center;
}

.nway-file-upload__name {
  flex: 0 1 auto;
  min-width: 0;
  max-width: 60%;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.nway-file-upload__size {
  flex: none;
  color: #909399;
  font-size: 12px;
}

.nway-file-upload__progress {
  flex: none;
  width: 120px;
  height: 6px;
  border-radius: 3px;
  background: #ebeef5;
  overflow: hidden;
}

.nway-file-upload__progress-bar {
  height: 100%;
  background: #409eff;
  transition: width 0.2s;
}

.nway-file-upload__percent {
  flex: none;
  width: 40px;
  color: #606266;
  font-size: 12px;
  text-align: right;
}

.nway-file-upload__link {
  flex: none;
  border: none;
  background: transparent;
  color: #409eff;
  cursor: pointer;
  font-size: 12px;
}

/* 操作按钮之间收紧间距 */
.nway-file-upload__link + .nway-file-upload__link {
  margin-left: -4px;
}

.nway-file-upload__link--danger {
  color: #f56c6c;
}

.nway-file-upload__error {
  flex: 1;
  color: #f56c6c;
  font-size: 12px;
}
</style>
