<template>
  <div class="user-dropdown-content">
    <!-- User info: avatar + name + tenant + message count (horizontal layout only) -->
    <div v-if="showUserInfo" class="dropdown-user-info">
      <i class="avatar"></i>
      <span class="v-user-name">{{ userName }}</span>
      <el-tag v-if="tenantName" size="small" type="info" class="v-tenant-tag">{{ tenantName }}</el-tag>
      <span class="msg_num" v-if="msgNum > 0">{{ msgNum }}</span>
    </div>

    <!-- Messages section -->
    <template v-if="messageList.length > 0">
      <div style="max-height:180px;overflow-y:auto;padding:4px 0;">
        <div v-for="msg in messageList" class="dropdown-msg-item" @click="emit('msg-read', msg.receiverId)">
          <div class="dropdown-msg-text">{{ msg.content.length > 60 ? msg.content.substring(0,60)+'...' : msg.content }}</div>
          <div class="dropdown-msg-meta">{{ msg.sender }} · {{ msg.receiveTime }}</div>
        </div>
      </div>
      <!-- 更多消息：由父组件按弹层容器类型提供（el-dropdown-item 自动关闭 / 自定义链接手动关闭） -->
      <slot name="more-msg" />
    </template>

    <!-- Quick actions row: icons with tooltips -->
    <div class="dropdown-quick-actions">
      <span v-if="bioAvailable" class="dropdown-action-icon" title="注册指纹/面容" @click.stop="emit('register')"><el-icon><Fingerprint /></el-icon></span>
      <span class="dropdown-action-icon" :title="toolbarDocked ? '取消固定工具栏' : '固定工具栏'" @click.stop="emit('toggle-dock')"><el-icon><Lock v-if="toolbarDocked" /><Unlock v-else /></el-icon></span>
      <span class="dropdown-action-icon dropdown-action-logout" title="退出登录" @click.stop="emit('logout')"><el-icon><SwitchButton /></el-icon></span>
    </div>

    <!-- Layout positions -->
    <div class="dropdown-layout-section">
      <div class="dropdown-layout-label" @click.stop="layoutSectionOpen = !layoutSectionOpen">
        <span>工具栏位置</span>
        <el-icon class="layout-toggle-arrow" :class="{ open: layoutSectionOpen }"><ArrowRight /></el-icon>
      </div>
      <transition name="layout-expand">
        <div class="dropdown-layout-row" v-show="layoutSectionOpen">
          <div v-for="pos in layoutPositions" :key="pos.key"
            class="dropdown-layout-opt" :class="{ active: layoutPosition === pos.key }"
            @click.stop="emit('switch-layout', pos.key)">
            <div class="lp"><div class="lp-bar" :class="'lp-' + pos.key"></div></div>
            <span>{{ pos.label }}</span>
          </div>
        </div>
      </transition>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ArrowRight, Lock, Unlock, SwitchButton } from '@element-plus/icons-vue'
import Fingerprint from '@/assets/icon/Fingerprint.vue'

defineProps({
  showUserInfo: { type: Boolean, default: false },
  userName: { type: String, default: '' },
  tenantName: { type: String, default: '' },
  msgNum: { type: Number, default: 0 },
  messageList: { type: Array, default: () => [] },
  toolbarDocked: { type: Boolean, default: false },
  layoutPosition: { type: String, default: 'top' },
  bioAvailable: { type: Boolean, default: false }
})
const emit = defineEmits(['msg-read', 'register', 'toggle-dock', 'logout', 'switch-layout'])

const layoutSectionOpen = ref(false)
const layoutPositions = [
  { key: 'top', label: '顶部' },
  { key: 'left', label: '左侧' },
  { key: 'bottom', label: '底部' },
  { key: 'right', label: '右侧' }
]

// 供父组件在弹层打开/布局切换时重置"工具栏位置"折叠状态
function resetLayoutSection() {
  layoutSectionOpen.value = false
}
defineExpose({ resetLayoutSection })
</script>

<style scoped lang="scss">
/* 弹层内容统一宽度：水平(el-dropdown)/垂直(el-popover)布局保持一致 */
.user-dropdown-content { width: 240px; }

/* ========== User info (horizontal layout) ========== */
.dropdown-user-info {
  display: flex; align-items: center; padding: 10px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter, #ebeef5);
}
.dropdown-user-info .v-user-name { flex: 1; min-width: 0; }
.v-user-name { margin-left: 8px; font-size: 13px; color: #333; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.v-tenant-tag {
  margin-left: 6px; flex-shrink: 0;
  font-size: 10px; padding: 0 4px; height: 18px; line-height: 18px;
}
.msg_num { position: relative; top: -16px; left: -4px; color: red; font-size: 10px; }

/* ========== Messages ========== */
.dropdown-msg-item {
  padding: 6px 12px; cursor: pointer; transition: background 0.15s;
  border-bottom: 1px solid #f5f5f5;
}
.dropdown-msg-item:hover { background-color: #f0f7ff; }
.dropdown-msg-text { font-size: 13px; white-space: pre-wrap; word-wrap: break-word; line-height: 1.4; }
.dropdown-msg-meta { font-size: 11px; color: #999; text-align: right; margin-top: 2px; }

/* ========== Quick actions row ========== */
.dropdown-quick-actions {
  display: flex; align-items: center; padding: 8px 12px;
  border-top: 1px solid var(--el-border-color-lighter, #ebeef5);
  gap: 6px;
}
.dropdown-action-icon {
  display: inline-flex; align-items: center; justify-content: center;
  width: 32px; height: 32px; border-radius: 4px;
  cursor: pointer; font-size: 18px; color: #666;
  transition: all 0.15s;
}
.dropdown-action-icon:hover { background-color: #e8f0fe; color: #0078d7; }
.dropdown-action-logout:hover { background-color: #fef0f0; color: #f56c6c; }

/* ========== Layout positions ========== */
.dropdown-layout-section {
  padding: 10px 8px 6px 8px;
  border-top: 1px solid var(--el-border-color-lighter, #ebeef5);
}
.dropdown-layout-label {
  font-size: 12px;
  color: #666;
  font-weight: 500;
  padding: 4px 6px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  user-select: none;
  border-radius: 4px;
  transition: all 0.15s;
  background-color: #f9f9f9;
}
.dropdown-layout-label:hover {
  color: #333;
  background-color: #f0f0f0;
}
.layout-toggle-arrow {
  font-size: 12px;
  transition: transform 0.25s ease;
  color: #999;
}
.layout-toggle-arrow.open {
  transform: rotate(90deg);
}

/* Expand/collapse animation */
.layout-expand-enter-active,
.layout-expand-leave-active {
  transition: all 0.25s ease;
  overflow: hidden;
}
.layout-expand-enter-from,
.layout-expand-leave-to {
  opacity: 0;
  max-height: 0;
}
.layout-expand-enter-to,
.layout-expand-leave-from {
  opacity: 1;
  max-height: 200px;
}
.dropdown-layout-row { display: flex; gap: 4px; }
.dropdown-layout-opt {
  display: flex; flex-direction: column; align-items: center;
  cursor: pointer; padding: 4px 2px; border-radius: 4px;
  border: 2px solid transparent; transition: all 0.15s; flex: 1; min-width: 0;
}
.dropdown-layout-opt:hover { background-color: #f0f0f0; }
.dropdown-layout-opt.active { border-color: #0078d7; background-color: #e8f0fe; }
.dropdown-layout-opt span { font-size: 10px; margin-top: 2px; }

.lp { width: 100%; max-width: 40px; height: 28px; border: 1px solid #ccc; border-radius: 2px; position: relative; background: #fafafa; }
.lp-bar { position: absolute; background: #0078d7; }
.lp-top    { top: 0; left: 0; width: 100%; height: 4px; border-radius: 2px 2px 0 0; }
.lp-bottom { bottom: 0; left: 0; width: 100%; height: 4px; border-radius: 0 0 2px 2px; }
.lp-left   { top: 0; left: 0; width: 5px; height: 100%; border-radius: 2px 0 0 2px; }
.lp-right  { top: 0; right: 0; width: 5px; height: 100%; border-radius: 0 2px 2px 0; }
</style>
