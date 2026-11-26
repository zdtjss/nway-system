import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { deleteChatFile, uploadChatFile } from '@/api/ai'
import { AI_MAX_FILES, AI_MAX_FILE_SIZE } from '@/components/global-search/constants'

/**
 * AI 输入区附件：选择/粘贴/拖放上传、进度、失败重试与移除。
 * attachments 由调用方持有，便于发送时读取文件 id、新对话时统一清空。
 */
export function useAiAttachments(attachments, options = {}) {
    const maxFiles = options.maxFiles ?? AI_MAX_FILES
    const maxSize = options.maxSize ?? AI_MAX_FILE_SIZE
    const uidPrefix = options.uidPrefix ?? 'gs-ai-file'
    // 输入框拖放高亮：仅文件拖到聊天内容输入框区域时展示
    const aiInputDragActive = ref(false)
    // dragenter/dragleave 在子元素间移动时会反复触发，用计数避免高亮闪烁
    let aiInputDragDepth = 0
    let fileSeq = 0

    function aiFileUid() {
        fileSeq += 1
        return `${uidPrefix}-${fileSeq}-${Date.now()}`
    }

    async function addAiFiles(files) {
        const remain = maxFiles - attachments.value.length
        if (remain <= 0) {
            ElMessage.warning(`单次最多 ${maxFiles} 个文件`)
            return
        }
        for (const file of files.slice(0, remain)) {
            if (file.size > maxSize) {
                ElMessage.warning(`${file.name} 超过 ${Math.round(maxSize / 1024 / 1024)}MB`)
                continue
            }
            const item = reactive({
                localId: aiFileUid(),
                fileId: '',
                name: file.name,
                size: file.size,
                mime: file.type,
                uploading: true,
                progress: 0,
                rawFile: file
            })
            attachments.value = [...attachments.value, item]
            try {
                const res = await uploadChatFile(file, (percent) => { item.progress = percent })
                Object.assign(item, res.data, { uploading: false, progress: 100 })
            } catch (error) {
                item.uploading = false
                item.error = error?.response?.data?.msg || error?.message || '上传失败'
            }
        }
    }

    async function retryAiUpload(item) {
        if (!item.rawFile) return
        item.error = undefined
        item.uploading = true
        item.progress = 0
        try {
            const res = await uploadChatFile(item.rawFile, (percent) => { item.progress = percent })
            Object.assign(item, res.data, { uploading: false, progress: 100 })
            item.error = undefined
        } catch (error) {
            item.uploading = false
            item.error = error?.response?.data?.msg || '上传失败'
        }
    }

    async function removeAiAttachment(item) {
        attachments.value = attachments.value.filter((file) => file.localId !== item.localId)
        if (item.fileId) {
            try {
                await deleteChatFile(item.fileId)
            } catch (e) {
                // 已发送或已删除，忽略
            }
        }
    }

    /** 仅接管文件拖拽，文本拖拽保持浏览器/输入框默认行为。 */
    function isFileDrag(event) {
        const types = event.dataTransfer?.types
        return !!types && Array.from(types).includes('Files')
    }

    // 清理输入框拖放高亮（拖放结束或面板收起时调用）
    function resetAiDragState() {
        aiInputDragDepth = 0
        aiInputDragActive.value = false
    }

    // 聊天内容输入框：文件拖放上传
    function onAiInputDragEnter(event) {
        if (!isFileDrag(event)) return
        event.preventDefault()
        aiInputDragDepth += 1
        aiInputDragActive.value = true
    }

    function onAiInputDragOver(event) {
        if (!isFileDrag(event)) return
        event.preventDefault()
        if (event.dataTransfer) event.dataTransfer.dropEffect = 'copy'
        aiInputDragActive.value = true
    }

    function onAiInputDragLeave(event) {
        if (!isFileDrag(event)) return
        aiInputDragDepth = Math.max(0, aiInputDragDepth - 1)
        if (aiInputDragDepth === 0) aiInputDragActive.value = false
    }

    async function onAiInputDrop(event) {
        const files = Array.from(event.dataTransfer?.files || [])
        // 先结束拖放的视觉状态，再走上传流程，虚线框不依赖上传结果
        resetAiDragState()
        if (!files.length) return
        event.preventDefault()
        await addAiFiles(files)
    }

    async function onAiPaste(event) {
        const files = Array.from(event.clipboardData?.files || [])
        if (files.length) {
            event.preventDefault()
            await addAiFiles(files)
        }
    }

    return {
        aiInputDragActive,
        addAiFiles,
        retryAiUpload,
        removeAiAttachment,
        resetAiDragState,
        onAiInputDragEnter,
        onAiInputDragOver,
        onAiInputDragLeave,
        onAiInputDrop,
        onAiPaste
    }
}
