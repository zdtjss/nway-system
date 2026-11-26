<template>
  <el-popover v-model:visible="visible" placement="top-end" width="min(380px, 90vw)"
              trigger="click" popper-class="history-popover" @before-enter="emit('open')">
    <template #reference>
      <button class="tool-btn" type="button" title="历史记录">
        <el-icon><Clock /></el-icon>
        <span>历史记录</span>
      </button>
    </template>
    <div class="history-panel">
      <div class="history-panel-head">
        <div>
          <div class="history-panel-title">历史记录</div>
        </div>
      </div>
      <el-input :model-value="keyword" class="history-search" size="small" clearable
                placeholder="搜索会话标题" :prefix-icon="Search"
                @update:model-value="emit('update:keyword', $event)"
                @keyup.enter="emit('search')" />
      <div v-loading="loading" class="history-list">
        <div v-for="conv in conversations" :key="conv.id" class="history-item"
             :class="{ active: conv.id === activeId }" @click="emit('open-conversation', conv)">
          <span class="history-title" :title="conv.title">{{ conv.title || '新对话' }}</span>
          <span class="history-time">{{ conversationTime(conv) }}</span>
          <el-dropdown trigger="click" @command="(cmd: string) => onConvCommand(cmd, conv)">
            <el-icon class="history-more" @click.stop><MoreFilled /></el-icon>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="rename">重命名</el-dropdown-item>
                <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
        <div v-if="!loading && conversations.length === 0" class="history-empty">
          {{ keyword ? '没有匹配的会话' : '还没有历史会话' }}
        </div>
      </div>
      <div v-if="total > pageSize" class="history-pagination">
        <el-pagination size="small" background layout="prev, pager, next"
                       :total="total" :page-size="pageSize"
                       :current-page="current"
                       @current-change="emit('page-change', $event)" />
      </div>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { Clock, MoreFilled, Search } from '@element-plus/icons-vue'
import type { AiConversationVo } from '@/api/ai'
import { conversationTime } from '@/utils/aiTool'

defineProps({
  keyword: { type: String, default: '' },
  loading: { type: Boolean, default: false },
  conversations: { type: Array as () => AiConversationVo[], default: () => [] },
  total: { type: Number, default: 0 },
  current: { type: Number, default: 1 },
  pageSize: { type: Number, default: 10 },
  activeId: { type: String as () => string | null, default: null }
})

const visible = defineModel<boolean>('visible', { default: false })

const emit = defineEmits<{
  (e: 'update:keyword', value: string): void
  (e: 'search'): void
  (e: 'open-conversation', conv: AiConversationVo): void
  (e: 'rename', conv: AiConversationVo): void
  (e: 'delete', conv: AiConversationVo): void
  (e: 'page-change', page: number): void
  (e: 'open'): void
}>()

function onConvCommand(command: string, conv: AiConversationVo) {
  if (command === 'rename') emit('rename', conv)
  else if (command === 'delete') emit('delete', conv)
}
</script>

<style scoped>
.tool-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  height: 30px;
  padding: 0 12px;
  border: 1px solid #e6ebf5;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.8);
  color: #5f6b80;
  font-size: 12.5px;
  font-family: inherit;
  cursor: pointer;
  box-shadow: 0 1px 2px rgba(17, 24, 51, 0.04);
  transition: border-color 0.15s, color 0.15s, background-color 0.15s, box-shadow 0.15s;
}

.tool-btn:hover {
  border-color: #b9c8f2;
  background: #fff;
  color: #3b5bdb;
  box-shadow: 0 4px 12px -8px rgba(91, 92, 255, 0.4);
}

.history-panel-head {
  padding-bottom: 10px;
  border-bottom: 1px solid #eef1f8;
}

.history-panel-title {
  font-size: 15px;
  font-weight: 600;
  color: #232a38;
}

.history-search {
  margin-top: 10px;
}

.history-search :deep(.el-input__wrapper) {
  border-radius: 10px;
  box-shadow: 0 0 0 1px #e8ecf5 inset;
}

.history-list {
  position: relative;
  min-height: 60px;
  max-height: 320px;
  overflow-y: auto;
  padding-top: 8px;
}

.history-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 11px;
  margin-bottom: 2px;
  border-radius: 10px;
  cursor: pointer;
  color: #4e5969;
  font-size: 13px;
  transition: background 0.15s, color 0.15s, box-shadow 0.15s;
}

.history-item:hover {
  background: #f2f5fb;
}

.history-item.active {
  background: #fff;
  color: #3b5bdb;
  box-shadow: 0 4px 14px -8px rgba(20, 30, 60, 0.25);
}

.history-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-time {
  flex: 0 0 auto;
  color: #a4adbe;
  font-size: 10.5px;
}

.history-more {
  flex: 0 0 auto;
  color: #a4adbe;
  opacity: 0;
}

.history-item:hover .history-more {
  opacity: 1;
}

.history-empty {
  color: #a4adbe;
  font-size: 12.5px;
  text-align: center;
  padding: 24px 0;
}

.history-pagination {
  display: flex;
  justify-content: center;
  padding-top: 10px;
  border-top: 1px solid #eef1f8;
}

.history-list::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.history-list::-webkit-scrollbar-thumb {
  background-color: rgba(148, 163, 184, 0.24);
  border: 2px solid transparent;
  background-clip: padding-box;
  border-radius: 8px;
}

.history-list::-webkit-scrollbar-thumb:hover {
  background-color: rgba(148, 163, 184, 0.45);
}

.history-list::-webkit-scrollbar-track {
  background: transparent;
}
</style>
