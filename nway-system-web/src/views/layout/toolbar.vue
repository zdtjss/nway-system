<template>
  <!-- Trigger zone: edge handle when collapsed -->
  <div v-if="!toolbarDocked" class="trigger-zone" :class="['trigger-' + layoutPosition, { 'trigger-collapsed': !toolbarExpanded }]"
    @mouseenter="peekIn" @click="peekIn">
    <div v-if="!toolbarExpanded" class="edge-handle" :class="'edge-handle-' + layoutPosition">
      <span class="edge-chevron" :class="'chev-' + layoutPosition"></span>
    </div>
  </div>

  <!-- Toolbar panel -->
  <div ref="panelRef" class="toolbar-panel"
    :class="[
      'pos-' + layoutPosition,
      (toolbarExpanded || toolbarDocked) ? 'expanded' : 'collapsed'
    ]"
    v-show="toolbarVisible"
    @mouseenter="peekIn"
    @mouseleave="scheduleHide">

    <!-- ====== Horizontal layout (top/bottom) ====== -->
    <div v-if="isHorizontal" class="toolbar-inner horizontal-inner">
      <div class="h-tool-btn" @click="showHome" title="首页"><i class="icon16 icon_home"></i></div>
      <div class="h-tool-btn" @click="showAllMenu" title="所有菜单"><i class="icon16 icon_menu"></i></div>

      <div class="h-favorites" v-if="showIndex">
        <div class="h-fav-item" v-for="menu in favorite.favorite" @click="openMenu(menu)" :key="menu.url">
          <i class="icon websql" :class="menu.icon"></i><span class="title_opened">{{ menu.name }}</span>
        </div>
      </div>

      <el-dropdown v-if="pageList.length > 0" @command="openFav" @visible-change="onDropdownToggle" class="h-fav-dropdown" size="small">
        <span class="el-dropdown-link">我的常用<el-icon class="el-icon--right"><arrow-down /></el-icon></span>
        <template #dropdown>
          <el-dropdown-menu v-if="favorite?.favorite?.length > 0">
            <el-dropdown-item :command="menu.url"
              :disabled="pageList.find(item => item.url === menu.url) ? true : false"
              v-for="menu in favorite.favorite">{{ menu.name }}</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <template v-if="!showIndex">
        <div class="h-tab" :class="{ 'current': !!currentUrl && currentUrl === menu.url }"
          v-for="menu in pageList" :key="menu.url">
          <div @click="changeTab(menu)" class="h-tab-click">
            <i class="icon8" :class="menu.icon" style="top:-4px;position:relative;"></i>
            <span class="h-tab-name">{{ menu.name }}</span>
          </div>
          <div class="h-tab-close" @click.stop="close(menu)"><i class="icon8 icon_close"></i></div>
        </div>
      </template>

      <!-- User area: avatar is the ONLY right-side entry -->
      <div class="h-user-area">
        <el-dropdown placement="bottom-end" @command="userPreference" @visible-change="onDropdownToggle">
          <div class="h-avatar-wrap"><i class="avatar"></i><span class="msg_num" v-if="msgNum > 0">{{ msgNum }}</span></div>
          <template #dropdown>
            <UserPanelContent ref="panelContentRef" show-user-info :user-name="userName" :tenant-name="tenantName" :msg-num="msgNum" :message-list="messageList" :toolbar-docked="toolbarDocked" :layout-position="layoutPosition" :bio-available="client.isAvailable()" @msg-read="msgRead" @register="register" @toggle-dock="toggleDock" @logout="userPreference('logout')" @switch-layout="switchLayout">
              <template #more-msg>
                <el-dropdown-item @click="showAllMsg()" divided>更多消息</el-dropdown-item>
              </template>
            </UserPanelContent>
          </template>
        </el-dropdown>
      </div>
    </div>

    <!-- ====== Vertical layout (left/right sidebar) ====== -->
    <div v-else class="toolbar-inner vertical-inner" :class="'sidebar-' + layoutPosition">
      <!-- Sidebar header -->
      <div class="v-header">
        <div class="v-nav-icons">
          <div class="v-tool-btn" @click="showHome" title="首页"><i class="icon16 icon_home"></i></div>
          <div class="v-tool-btn" @click="showAllMenu" title="所有菜单"><i class="icon16 icon_menu"></i></div>
          <div class="v-tool-btn v-tool-btn-logout" @click="userPreference('logout')" title="退出登录"><el-icon><SwitchButton /></el-icon></div>
        </div>
      </div>

      <div class="v-divider"></div>

      <!-- Sidebar body -->
      <div class="v-body">
        <div class="v-section-title">
          <span>{{ showIndex ? '常用' : '已打开' }}</span>
          <el-dropdown v-if="pageList.length > 0" @command="openFav" @visible-change="onDropdownToggle" size="small" trigger="hover" popper-class="v-toolbar-popper" style="margin-left:auto;">
            <span class="el-dropdown-link" style="font-size:12px;color:#0078d7;cursor:pointer;">常用<el-icon class="el-icon--right"><arrow-down /></el-icon></span>
            <template #dropdown>
              <el-dropdown-menu v-if="favorite?.favorite?.length > 0">
                <el-dropdown-item :command="menu.url"
                  :disabled="pageList.find(item => item.url === menu.url) ? true : false"
                  v-for="menu in favorite.favorite">{{ menu.name }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>

        <template v-if="showIndex">
          <div class="v-fav-item" v-for="menu in favorite.favorite" @click="openMenu(menu)" :key="menu.url">
            <i class="icon websql" :class="menu.icon"></i>
            <span class="v-fav-name">{{ menu.name }}</span>
          </div>
        </template>

        <template v-if="!showIndex">
          <div class="v-tab" :class="{ 'current': !!currentUrl && currentUrl === menu.url }"
            v-for="menu in pageList" :key="menu.url">
            <div @click="changeTab(menu)" class="v-tab-click">
              <i class="icon8" :class="menu.icon"></i>
              <span class="v-tab-name">{{ menu.name }}</span>
            </div>
            <div class="v-tab-close" @click.stop="close(menu)"><i class="icon8 icon_close"></i></div>
          </div>
        </template>
      </div>

      <div class="v-divider"></div>

      <!-- Sidebar footer: user popover (hover trigger: 鼠标移入头像/用户名区域自动显示) -->
      <div class="v-footer">
        <el-popover :placement="layoutPosition === 'right' ? 'left-start' : 'right-start'" :width="240" trigger="hover" :hover-delay="300" popper-class="v-toolbar-popper v-user-popover" :visible="userPopoverVisible" @update:visible="onUserPopoverChange">
          <template #reference>
            <div class="v-user-trigger">
              <i class="avatar"></i>
              <span class="v-user-name">{{ userName }}</span>
              <el-tag v-if="tenantName" size="small" type="info" class="v-tenant-tag">{{ tenantName }}</el-tag>
              <span class="msg_num" v-if="msgNum > 0">{{ msgNum }}</span>
            </div>
          </template>
          <UserPanelContent ref="panelContentRef" :user-name="userName" :tenant-name="tenantName" :msg-num="msgNum" :message-list="messageList" :toolbar-docked="toolbarDocked" :layout-position="layoutPosition" :bio-available="client.isAvailable()" @msg-read="msgRead" @register="register" @toggle-dock="toggleDock" @logout="userPreference('logout')" @switch-layout="switchLayout">
            <template #more-msg>
              <div class="v-popover-link" @click="showAllMsg(); userPopoverVisible = false">更多消息</div>
            </template>
          </UserPanelContent>
        </el-popover>
      </div>
    </div>

    <!-- Message notification popup -->
    <div v-if="showMsg" class="msg-popup" :class="'msg-' + layoutPosition">
      <div class="msg_title">
        <div style="display:inline-block;">通知</div>
        <div class="msg_close" @click="showMsg = false" title="关闭"><i class="icon16 icon_close"></i></div>
      </div>
      <div class="msg_content">
        &nbsp;&nbsp;&nbsp;&nbsp;{{ dialogMessage.content.length > 80 ? dialogMessage.content.substring(0, 80) + '...' : dialogMessage.content }}
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, onActivated, onDeactivated, computed } from 'vue'
import { useRouter } from 'vue-router'
import http from '@/js/httpProxy.js'
import auth from '@/js/auth.js'
import { storeToRefs } from 'pinia'
import { useCurrentPageStore } from '@/stores/menuStore'
import { client, parsers, server } from '@passwordless-id/webauthn'
import { SwitchButton } from '@element-plus/icons-vue'
import UserPanelContent from './UserPanelContent.vue'

const currentPageStore = useCurrentPageStore()
const { pageList, favorite, showIndex, currentUrl, toolbarVisible, toolbarDocked, toolbarExpanded, layoutPosition } = storeToRefs(currentPageStore)

const router = useRouter()
const avatar = ref("")
const userName = ref("")
const tenantName = ref("")
const msgNum = ref(0)
const messageList = ref([])
const showMsg = ref(false)
const dialogMessage = ref({})
const panelRef = ref(null)
const panelContentRef = ref(null)
const dropdownOpen = ref(false)
const userPopoverVisible = ref(false)
const bioLocalStorageKey = "nway_system_bio_credential_id"
let pollingTimer = null
let hideTimer = null

const isHorizontal = computed(() => layoutPosition.value === 'top' || layoutPosition.value === 'bottom')

// --- Show/Hide logic ---
function peekIn() {
  if (toolbarDocked.value) return
  clearHideTimer()
  userPopoverVisible.value = false
  currentPageStore.setToolbarExpanded(true)
}
function scheduleHide() {
  if (toolbarDocked.value) return
  if (dropdownOpen.value) return
  if (userPopoverVisible.value) return
  clearHideTimer()
  hideTimer = setTimeout(() => { currentPageStore.setToolbarExpanded(false) }, 800)
}
function clearHideTimer() {
  if (hideTimer) { clearTimeout(hideTimer); hideTimer = null }
}
// 全局点击：点击非工具栏面板、非弹层区域时，未固定的工具栏立即收起
function handleGlobalClick(e) {
  if (toolbarDocked.value || !toolbarExpanded.value) return
  // 点击在工具栏面板内：不收起
  if (panelRef.value && panelRef.value.contains(e.target)) return
  // el-dropdown / el-popover 弹层挂载在 body 下，点击弹层内容时不立即收起（由弹层关闭逻辑处理）
  if (e.target instanceof Element && e.target.closest('.el-popper')) return
  clearHideTimer()
  currentPageStore.setToolbarExpanded(false)
}
function toggleDock() {
  userPopoverVisible.value = false
  currentPageStore.toggleDock()
  // 切换后保持工具栏可见，让鼠标有机会移回工具栏区域
  clearHideTimer()
  currentPageStore.setToolbarExpanded(true)
}
function switchLayout(pos) {
  // 同时重置两个弹层状态：布局切换会销毁/重建 el-dropdown，其 @visible-change 不再触发，
  // 若不重置 dropdownOpen，scheduleHide 将被永久拦截导致工具栏无法收起
  userPopoverVisible.value = false
  dropdownOpen.value = false
  panelContentRef.value?.resetLayoutSection()
  currentPageStore.setLayoutPosition(pos)
}

// User popover visibility management
function onUserPopoverChange(visible) {
  userPopoverVisible.value = visible
  if (visible) {
    clearHideTimer()
    panelContentRef.value?.resetLayoutSection()
  } else {
    // Popover closed: if mouse is not over toolbar panel, schedule hide
    if (!toolbarDocked.value) {
      // Use rAF to ensure DOM has updated before checking
      requestAnimationFrame(() => {
        if (!panelRef.value) return
        scheduleHide()
      })
    }
  }
}

// Keep toolbar visible when dropdown is open
function onDropdownToggle(visible) {
  dropdownOpen.value = visible
  if (visible) {
    clearHideTimer()
    currentPageStore.setToolbarExpanded(true)
    panelContentRef.value?.resetLayoutSection()
  } else if (!toolbarDocked.value) {
    // Dropdown closed: start hide timer if not docked
    scheduleHide()
  }
}

// --- Keyboard shortcut ---
function handleKeydown(e) {
  if (e.ctrlKey && e.shiftKey && e.key === 'B') { e.preventDefault(); toggleDock() }
}

// --- Lifecycle ---
onMounted(() => {
  const user = auth.getUser()
  if (user) {
    avatar.value = user.avatar
    userName.value = user.realName || user.userName || ''
    tenantName.value = user.tenantName || ''
  }
  // 始终加载：首次进入由 loadMenu 内部跳转首页；刷新后恢复页签时也要刷新常用菜单与按钮权限
  loadMenu()
  // 恢复页签后 showIndex 已有值，loadMenu 不会跳首页，此处兜底把内容区挂到 /main（如刷新时地址停在 /）
  if (typeof showIndex.value !== 'undefined' && router.currentRoute.value.path !== '/main') {
    router.push({ path: "/main" })
  }
  pollingMsg()
  document.addEventListener('keydown', handleKeydown)
  document.addEventListener('click', handleGlobalClick)
})
onActivated(() => {
  if (!pollingTimer) { pollingTimer = setInterval(() => { pollingMsg() }, 60 * 1000) }
})
onDeactivated(() => {
  if (pollingTimer) { clearInterval(pollingTimer); pollingTimer = null }
})
onUnmounted(() => {
  if (pollingTimer) { clearInterval(pollingTimer); pollingTimer = null }
  clearHideTimer()
  document.removeEventListener('keydown', handleKeydown)
  document.removeEventListener('click', handleGlobalClick)
})

// --- Business functions ---
async function pollingMsg() {
  http.get("/message/polling").then(({ data }) => {
    if (data.code === 200) {
      messageList.value = data.data.data
      msgNum.value = data.data.total
      if (messageList.value.length > 0 && messageList.value[0].isNew === true) {
        showMsg.value = true; dialogMessage.value = messageList.value[0]
      }
    }
  })
}
function showAllMsg() { openMenu({ "name": "消息列表", "url": "/views/system/message/messageList", "type": "PAGE", "renderType": "native" }) }
function msgRead(receiveId) { const f = new FormData(); f.append('receiveId', receiveId); http.post("/message/read", f) }
function showAllMenu() { currentPageStore.showUserMenu(); currentPageStore.setShowIndex(false); router.push({ path: "/main" }) }
function openMenu(menu) { currentPageStore.showPage(menu); currentPageStore.setShowIndex(false); router.push({ path: "/main" }) }
function openFav(command) { const menu = favorite.value.favorite.find(item => item.url === command); if (menu) { currentPageStore.showPage(menu) } }
function loadMenu() {
  http.get("/myMenu/userFav").then((resp) => {
    const menuData = resp.data.data
    currentPageStore.setFavorite(menuData)
    // 从菜单树收集按钮权限标识，供 v-permission 指令使用
    if (menuData && menuData.menuList) {
      const powerKeys = collectPowerKeys(menuData.menuList)
      auth.savePowerKeys(powerKeys)
    }
    if (typeof showIndex.value === 'undefined') { showHome() }
  })
}
/**
 * 递归遍历菜单树，收集所有 BTN 节点的 resourceCode:action 格式权限标识。
 */
function collectPowerKeys(menuList) {
  const keys = []
  if (!Array.isArray(menuList)) return keys
  for (const node of menuList) {
    if (node.type === 'BTN' && node.action) {
      // BTN 的 resourceCode 从其父 PAGE 继承，后端已在树中传递
      const resource = node.resourceCode || ''
      if (resource) {
        keys.push(resource + ':' + node.action)
      }
    }
    if (node.children && node.children.length > 0) {
      keys.push(...collectPowerKeys(node.children))
    }
  }
  return keys
}
function changeTab(page) { currentPageStore.showPage(page) }
function showHome() { currentPageStore.showPage(favorite.value.index, true); currentPageStore.setShowIndex(true); router.push({ path: "/main" }) }
function close(menu) { currentPageStore.del(menu); if (currentPageStore.pageList.length === 0) { showHome() } }

async function register() {
  if (!client.isAvailable()) { ElMessage({ showClose: true, message: '您的设备不支持生物识别', type: 'error' }); return }
  try {
    let registration = await client.register({ challenge: server.randomChallenge(), user: { id: auth.getUser().employeeId, name: auth.getUser().realName } })
    const parsed = parsers.parseRegistration(registration)
    window.localStorage.setItem(bioLocalStorageKey, JSON.stringify({ id: parsed.credential.id, transports: parsed.credential.transports }))
    const params = new URLSearchParams(); params.append("bioKey", parsed.credential.id)
    const resp = await http.post("/login/updateBioPc", params, { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } })
    if (resp.data.code == 200) { ElMessage("注册成功") } else { ElMessage(resp.data.msg || "注册失败") }
  } catch (error) { ElMessage(error.message || "注册过程中发生错误") }
}

function userPreference(command) {
  if (command == "msg") {
  } else if (command == "loginSetting") { register() }
  else if (command == "logout") {
    const params = new URLSearchParams(); params.append("clientType", "pc")
    http.post("/login/logout", params, { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } }).then((resp) => {
      auth.clearUser()
      if (resp.data.code == 200) { location.href = '/login' } else { ElMessage(resp.data.msg) }
    }).catch((error) => { ElMessage(error) })
  }
}
</script>

<style scoped lang="scss">
/* ========== Trigger Zone ========== */
.trigger-zone { position: fixed; z-index: 99999998; background: transparent; transition: box-shadow 0.25s; }
.trigger-zone:hover { box-shadow: inset 0 0 8px rgba(0,120,215,0.18); }
.trigger-top    { top: 0; left: 0; width: 100%; height: 8px; }
.trigger-bottom { bottom: 0; left: 0; width: 100%; height: 8px; }
.trigger-left   { top: 0; left: 0; width: 8px; height: 100%; }
.trigger-right  { top: 0; right: 0; width: 8px; height: 100%; }

/* ========== Collapsed: enlarged click area + edge handle ========== */
.trigger-collapsed.trigger-top    { height: 14px; cursor: pointer; }
.trigger-collapsed.trigger-bottom { height: 14px; cursor: pointer; }
.trigger-collapsed.trigger-left   { width: 14px; cursor: pointer; }
.trigger-collapsed.trigger-right  { width: 14px; cursor: pointer; }

.edge-handle {
  position: absolute; pointer-events: none;
  display: flex; align-items: center; justify-content: center;
  background: #d0d0d0;
  border-radius: 3px;
  transition: all 0.3s ease;
  animation: handleBreath 2.5s ease-in-out infinite;
}
@keyframes handleBreath {
  0%, 100% { opacity: 0.55; }
  50% { opacity: 1; }
}

/* top/bottom: horizontal handle at center */
.edge-handle-top, .edge-handle-bottom { left: 50%; transform: translateX(-50%); width: 36px; height: 16px; border-radius: 0 0 5px 5px; }
.edge-handle-top    { top: 0; }
.edge-handle-bottom { bottom: 0; border-radius: 5px 5px 0 0; }
/* left/right: vertical handle at center */
.edge-handle-left, .edge-handle-right { top: 50%; transform: translateY(-50%); width: 16px; height: 36px; border-radius: 0 5px 5px 0; }
.edge-handle-left  { left: 0; }
.edge-handle-right { right: 0; border-radius: 5px 0 0 5px; }

/* CSS chevron arrows */
.edge-chevron {
  display: inline-block; width: 7px; height: 7px;
  border-right: 2px solid #888; border-bottom: 2px solid #888;
}
.chev-top    { transform: rotate(-135deg); margin-bottom: 3px; }
.chev-bottom { transform: rotate(45deg); margin-top: 3px; }
.chev-left   { transform: rotate(-45deg); margin-right: 3px; }
.chev-right  { transform: rotate(135deg); margin-left: 3px; }

/* Hover: show accent color */
.trigger-zone:hover .edge-handle { opacity: 1; animation: none; background: #bbb; }
.trigger-zone:hover .edge-chevron { border-color: #555; }
.trigger-zone:hover .edge-handle-top,
.trigger-zone:hover .edge-handle-bottom { width: 44px; height: 20px; }
.trigger-zone:hover .edge-handle-left,
.trigger-zone:hover .edge-handle-right  { height: 44px; width: 20px; }

/* ========== Toolbar Panel ========== */
.toolbar-panel {
  position: fixed; z-index: 99999999;
  background-color: whitesmoke;
  transition: transform 0.3s cubic-bezier(0.4,0,0.2,1);
  box-shadow: 0 2px 12px rgba(0,0,0,0.15);
  pointer-events: auto;
}
.pos-top    { top:0; left:0; width:100%; height:44px; }
.pos-top.collapsed    { transform: translateY(-100%); }
.pos-top.expanded     { transform: translateY(0); }
.pos-bottom { bottom:0; left:0; width:100%; height:44px; }
.pos-bottom.collapsed { transform: translateY(100%); }
.pos-bottom.expanded  { transform: translateY(0); }
.pos-left   { top:0; left:0; width:220px; height:100%; }
.pos-left.collapsed   { transform: translateX(-100%); }
.pos-left.expanded    { transform: translateX(0); }
.pos-right  { top:0; right:0; width:220px; height:100%; }
.pos-right.collapsed  { transform: translateX(100%); }
.pos-right.expanded   { transform: translateX(0); }

/* ========== Toolbar Inner ========== */
.toolbar-inner { flex: 1; overflow: hidden; }

/* ========== HORIZONTAL ========== */
.horizontal-inner {
  display: flex; flex-direction: row; align-items: center;
  padding: 0 8px; height: 100%;
}
.h-tool-btn {
  display: flex; align-items: center; justify-content: center;
  margin: 0 4px; cursor: pointer; padding: 5px;
  border-radius: 4px; flex-shrink: 0;
}
.h-tool-btn:hover { background-color: #e0e0e0; }
.h-favorites { display: flex; flex-direction: row; align-items: center; flex: 1; overflow: hidden; }
.h-fav-item { cursor: pointer; display: inline-flex; align-items: center; margin: 0 6px; white-space: nowrap; flex-shrink: 0; }
.h-fav-item:hover { color: #0078d7; }
.title_opened { margin: 0 4px; font-size: 13px; }
.h-fav-dropdown { flex-shrink: 0; margin: 0 8px; cursor: pointer; white-space: nowrap; }
.h-tab {
  cursor: pointer; display: flex; align-items: center;
  white-space: nowrap; flex-shrink: 0;
  margin: 0 2px; padding: 3px 6px;
  border-radius: 4px; transition: background-color 0.15s;
  border-bottom: 2px solid transparent;
}
.h-tab:hover { background-color: #e8e8e8; }
.h-tab.current { border-bottom-color: #0078d7; color: #0078d7; font-weight: 600; }
.h-tab-click { display: flex; align-items: center; flex: 1; overflow: hidden; }
.h-tab-name { font-size: 13px; overflow: hidden; text-overflow: ellipsis; }
.h-tab-close { display: flex; align-items: center; margin-left: 4px; opacity: 0; transition: opacity 0.15s; }
.h-tab:hover .h-tab-close { opacity: 1; }
.h-tab-close:hover { color: red; }
.h-user-area { display: flex; align-items: center; margin-left: auto; flex-shrink: 0; }
.h-avatar-wrap { cursor: pointer; outline: none; position: relative; }
/* el-dropdown 触发元素（"我的常用"/"常用"）被注入 tabindex=0，弹层按 ESC 关闭后焦点回落到触发元素，隐藏浏览器默认黑色 outline */
.el-dropdown-link { outline: none; }

/* ========== VERTICAL ========== */
.vertical-inner { display: flex; flex-direction: column; height: 100%; padding: 0; }
.v-header { padding: 8px 8px 4px 8px; flex-shrink: 0; }
.v-nav-icons { display: flex; flex-direction: row; gap: 4px; }
.sidebar-left .v-nav-icons  { justify-content: flex-start; }
.sidebar-right .v-nav-icons { justify-content: flex-end; }
.v-tool-btn {
  display: flex; align-items: center; justify-content: center;
  width: 34px; height: 34px; cursor: pointer;
  border-radius: 6px; transition: background-color 0.15s;
}
.v-tool-btn:hover { background-color: #e0e0e0; }
.v-tool-btn-logout { margin-left: auto; }
.v-tool-btn-logout:hover { background-color: #fef0f0; color: #f56c6c; }
.v-divider { height: 1px; background-color: #e0e0e0; margin: 4px 12px; flex-shrink: 0; }
.v-body { flex: 1; overflow-y: auto; overflow-x: hidden; padding: 4px 8px; }
.v-section-title {
  font-size: 11px; color: #999; letter-spacing: 0.5px;
  padding: 4px 6px 2px 6px; display: flex; align-items: center; user-select: none;
}
.v-fav-item { display: flex; align-items: center; padding: 6px 8px; margin: 1px 0; border-radius: 4px; cursor: pointer; transition: background-color 0.15s; }
.v-fav-item:hover { background-color: #e8e8e8; }
.v-fav-name { margin-left: 8px; font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.v-tab { display: flex; align-items: center; padding: 7px 8px; margin: 1px 0; border-radius: 4px; cursor: pointer; transition: all 0.15s; border-left: 3px solid transparent; }
.v-tab:hover { background-color: #e8e8e8; }
.v-tab.current { border-left-color: #0078d7; background-color: #e3f0ff; color: #0078d7; font-weight: 600; }
.v-tab-click { display: flex; align-items: center; flex: 1; overflow: hidden; }
.v-tab-name { margin-left: 8px; font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.v-tab-close { display: flex; align-items: center; flex-shrink: 0; opacity: 0; transition: opacity 0.15s; }
.v-tab:hover .v-tab-close { opacity: 1; }
.v-tab-close:hover { color: red; }

.v-footer {
  display: flex; flex-direction: row; align-items: center;
  padding: 6px 8px; flex-shrink: 0; border-top: 1px solid #e0e0e0;
}
.sidebar-left .v-footer  { justify-content: flex-start; }
.sidebar-right .v-footer { justify-content: flex-end; }
.v-user-trigger {
  cursor: default; display: flex; align-items: center;
  position: relative; padding: 4px; border-radius: 4px;
  transition: background-color 0.15s; flex: 1; overflow: hidden;
  outline: none;
}
.v-user-trigger:hover { background-color: #e8e8e8; }
.v-user-name { margin-left: 8px; font-size: 13px; color: #333; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.v-popover-link {
  padding: 6px 12px; cursor: pointer; font-size: 13px; color: #333;
  border-top: 1px solid var(--el-border-color-lighter, #ebeef5);
  border-bottom: 1px solid var(--el-border-color-lighter, #ebeef5);
  transition: background 0.15s;
}
.v-popover-link:hover { background-color: #f0f7ff; color: #0078d7; }
.msg_num { position: relative; top: -16px; left: -4px; color: red; font-size: 10px; }

/* 弹层内容（用户信息/消息/快捷操作/布局切换）已抽离至 UserPanelContent.vue */

/* ========== Message Popup ========== */
.msg-popup { position: absolute; border: 2px solid lightblue; border-radius: 5px; width: 300px; background-color: beige; z-index: 999999; }
.msg-top    { top: 48px; right: 8px; }
.msg-bottom { bottom: 48px; right: 8px; }
.msg-left   { top: 8px; left: 224px; }
.msg-right  { top: 8px; right: 224px; }
.msg_title { line-height: 35px; background-color: lightblue; padding-left: 5px; display: flex; justify-content: space-between; align-items: center; }
.msg_close { cursor: pointer; margin-right: 8px; }
.msg_content { padding: 5px 10px; font-size: 14px; line-height: 28px; }

/* 以下样式供弹层 reference 与“更多消息”链接使用，其余弹层样式已移至 UserPanelContent.vue */
.v-tenant-tag {
  margin-left: 6px; flex-shrink: 0;
  font-size: 10px; padding: 0 4px; height: 18px; line-height: 18px;
}
</style>

<style lang="scss">
/* 全局样式：垂直布局下拉弹层 z-index 需高于工具栏(99999999)以避免被内容区遮挡 */
.v-toolbar-popper {
  z-index: 100000000 !important;
}
.v-user-popover {
  padding: 0 !important;
}
</style>
