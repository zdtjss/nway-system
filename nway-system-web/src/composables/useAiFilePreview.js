import { reactive } from 'vue'
import { assetUrl } from '@/api/ai'

/**
 * AI 消息附件的页内预览：图片走全屏看图，PDF/文本用半透明浮层。
 */
export function useAiFilePreview() {
    const aiPreview = reactive({
        open: false,
        url: '',
        type: '',
        text: '',
        loading: false
    })
    let aiPreviewSeq = 0

    // 历史消息的预览地址由后端重新签发；图片走全屏看图，PDF/文本在当前页浮层展示
    function previewAiFile(file) {
        if (!file?.previewUrl) return
        const kind = String(file.kind || '').toUpperCase()
        aiPreviewSeq += 1
        Object.assign(aiPreview, {
            open: true,
            url: assetUrl(file.previewUrl),
            type: kind === 'IMAGE' ? 'image' : kind === 'PDF' ? 'pdf' : kind === 'TEXT' ? 'text' : 'other',
            text: '',
            loading: kind === 'TEXT'
        })
        if (kind === 'TEXT') loadAiPreviewText(aiPreview.url)
    }

    async function loadAiPreviewText(url) {
        const seq = aiPreviewSeq
        try {
            const res = await fetch(url)
            if (!res.ok) throw new Error(`HTTP ${res.status}`)
            const text = await res.text()
            if (seq !== aiPreviewSeq || !aiPreview.open) return
            aiPreview.text = text || '（文件内容为空）'
        } catch (e) {
            if (seq !== aiPreviewSeq || !aiPreview.open) return
            aiPreview.text = '文件内容加载失败，请稍后重试'
        } finally {
            if (seq === aiPreviewSeq && aiPreview.open) aiPreview.loading = false
        }
    }

    function closeAiPreview() {
        aiPreviewSeq += 1
        aiPreview.open = false
        aiPreview.loading = false
        aiPreview.text = ''
    }

    return { aiPreview, previewAiFile, closeAiPreview }
}
