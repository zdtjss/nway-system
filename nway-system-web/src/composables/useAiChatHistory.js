import { onBeforeUnmount, ref, watch } from 'vue'
import { listConversations } from '@/api/ai'
import { AI_HISTORY_PAGE_SIZE } from '@/components/global-search/constants'

/**
 * AI 面板内的历史会话列表：打开面板内历史视图、关键字防抖搜索与分页。
 */
export function useAiChatHistory() {
    const aiHistoryOpen = ref(false)
    const aiHistoryKeyword = ref('')
    const aiHistoryCurrent = ref(1)
    const aiHistoryTotal = ref(0)
    const aiHistoryLoading = ref(false)
    const aiConversations = ref([])
    let aiHistoryTimer = null
    let aiHistorySeq = 0

    watch(aiHistoryKeyword, () => {
        if (aiHistoryTimer) clearTimeout(aiHistoryTimer)
        aiHistoryTimer = setTimeout(searchAiHistory, 300)
    })

    async function loadAiHistory() {
        const seq = ++aiHistorySeq
        aiHistoryLoading.value = true
        try {
            const res = await listConversations(aiHistoryCurrent.value, AI_HISTORY_PAGE_SIZE,
                aiHistoryKeyword.value.trim() || undefined)
            if (seq !== aiHistorySeq) return
            // 分页数据在 Result.data 里：{ data: 列表, total: 总数 }
            aiConversations.value = res.data?.data || []
            aiHistoryTotal.value = res.data?.total || 0
        } catch (e) {
            // http 拦截器已提示
        } finally {
            if (seq === aiHistorySeq) aiHistoryLoading.value = false
        }
    }

    function searchAiHistory() {
        if (aiHistoryTimer) {
            clearTimeout(aiHistoryTimer)
            aiHistoryTimer = null
        }
        aiHistoryCurrent.value = 1
        loadAiHistory()
    }

    function onAiHistoryPageChange(page) {
        aiHistoryCurrent.value = page
        loadAiHistory()
    }

    function toggleAiHistory() {
        if (aiHistoryOpen.value) {
            aiHistoryOpen.value = false
            return
        }
        aiHistoryOpen.value = true
        loadAiHistory()
    }

    onBeforeUnmount(() => {
        if (aiHistoryTimer) clearTimeout(aiHistoryTimer)
    })

    return {
        aiHistoryOpen,
        aiHistoryKeyword,
        aiHistoryCurrent,
        aiHistoryTotal,
        aiHistoryLoading,
        aiConversations,
        aiHistoryPageSize: AI_HISTORY_PAGE_SIZE,
        loadAiHistory,
        searchAiHistory,
        onAiHistoryPageChange,
        toggleAiHistory
    }
}
