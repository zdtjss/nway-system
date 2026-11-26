<template>
    <!-- 历史会话：在面板内切换查看，选中后继续该会话 -->
    <div class="gs-ai-history">
        <div class="gs-ai-history-head">
            <div class="gs-ai-history-title">历史记录</div>
            <button type="button" class="gs-ai-icon-btn" title="返回对话" @click="emit('close')">
                <el-icon><Close /></el-icon>
            </button>
        </div>
        <el-input :model-value="keyword" class="gs-ai-history-search" size="small" clearable placeholder="搜索会话标题"
            :prefix-icon="Search" @update:model-value="emit('update:keyword', $event)" />
        <div v-loading="loading" class="gs-ai-history-list">
            <div v-for="conv in conversations" :key="conv.id" class="gs-ai-history-item"
                :class="{ active: conv.id === activeId }" @click="emit('open', conv)">
                <span class="gs-ai-history-item-title" :title="conv.title">{{ conv.title || '新对话' }}</span>
                <span class="gs-ai-history-item-time">{{ conversationTime(conv) }}</span>
            </div>
            <div v-if="!loading && conversations.length === 0" class="gs-ai-history-empty">
                {{ keyword ? '没有匹配的会话' : '还没有历史会话' }}
            </div>
        </div>
        <div v-if="total > AI_HISTORY_PAGE_SIZE" class="gs-ai-history-pagination">
            <el-pagination size="small" background layout="prev, pager, next" :pager-count="5"
                :total="total" :page-size="AI_HISTORY_PAGE_SIZE" :current-page="current"
                @current-change="emit('page-change', $event)" />
        </div>
    </div>
</template>

<script setup>
import { Close, Search } from '@element-plus/icons-vue'
import { AI_HISTORY_PAGE_SIZE } from '@/components/global-search/constants'
import { conversationTime } from '@/utils/aiTool'

defineProps({
    keyword: { type: String, default: '' },
    loading: { type: Boolean, default: false },
    conversations: { type: Array, default: () => [] },
    total: { type: Number, default: 0 },
    current: { type: Number, default: 1 },
    activeId: { type: String, default: null }
})

const emit = defineEmits(['update:keyword', 'open', 'page-change', 'close'])
</script>

<style scoped>
/* ---------- 历史会话 ---------- */
.gs-ai-history {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
    padding: 14px 14px 12px;
    background:
        radial-gradient(120% 50% at 50% 0%, rgba(79, 124, 255, 0.05) 0%, rgba(79, 124, 255, 0) 70%),
        #f6f7fb;
}

.gs-ai-history-head {
    flex: 0 0 auto;
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-bottom: 10px;
}

.gs-ai-history-title {
    font-size: 14px;
    font-weight: 600;
    color: #232a38;
}

.gs-ai-icon-btn {
    width: 28px;
    height: 28px;
    padding: 0;
    border: none;
    border-radius: 9px;
    background: transparent;
    color: #6f7890;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    transition: background-color 0.15s, color 0.15s;
}

.gs-ai-icon-btn .el-icon {
    font-size: 16px;
}

.gs-ai-icon-btn:hover {
    background: rgba(79, 124, 255, 0.09);
    color: #3b5bdb;
}

.gs-ai-history-search {
    flex: 0 0 auto;
    margin-bottom: 10px;
}

.gs-ai-history-search :deep(.el-input__wrapper) {
    border-radius: 10px;
    background: #fff;
    box-shadow: 0 0 0 1px #e8ecf5 inset;
}

.gs-ai-history-list {
    position: relative;
    flex: 1;
    min-height: 60px;
    overflow-y: auto;
    overscroll-behavior: contain;
}

.gs-ai-history-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 9px 10px;
    margin-bottom: 2px;
    border-radius: 10px;
    color: #4e5969;
    font-size: 12.5px;
    cursor: pointer;
    transition: background-color 0.15s, color 0.15s, box-shadow 0.15s;
}

.gs-ai-history-item:hover {
    background: #eef3ff;
}

.gs-ai-history-item.active {
    background: #fff;
    color: #3b5bdb;
    box-shadow: 0 2px 10px -4px rgba(20, 30, 60, 0.14);
}

.gs-ai-history-item-title {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.gs-ai-history-item-time {
    flex: 0 0 auto;
    font-size: 10.5px;
    color: #98a1b3;
}

.gs-ai-history-empty {
    padding: 24px 0;
    text-align: center;
    font-size: 12px;
    color: #98a1b3;
}

.gs-ai-history-pagination {
    flex: 0 0 auto;
    display: flex;
    justify-content: center;
    padding-top: 6px;
}

.gs-ai-history-list::-webkit-scrollbar {
    width: 6px;
}

.gs-ai-history-list::-webkit-scrollbar-thumb {
    background-color: rgba(148, 163, 184, 0.24);
    border-radius: 3px;
}

.gs-ai-history-list::-webkit-scrollbar-thumb:hover {
    background-color: rgba(148, 163, 184, 0.45);
}

.gs-ai-history-list::-webkit-scrollbar-track {
    background: transparent;
}
</style>
