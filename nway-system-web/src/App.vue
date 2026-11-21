<template>
  <router-view v-slot="{ Component }">
    <transition>
      <component :is="Component" />
    </transition>
  </router-view>
  <!-- 全局浮动菜单搜索：任意页面右上角可用，可拖动并记忆位置 -->
  <GlobalSearch />
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useCurrentPageStore } from '@/stores/menuStore'
import { installSubAppBridge, notifyPageActivated } from '@/js/subAppBridge'
import GlobalSearch from '@/components/GlobalSearch.vue'

// 子应用（微前端承载的业务系统）点击业务数据时，在平台内以页签方式打开目标页面，
// 与平台内部点击菜单行为一致（showPage + 隐藏首页 + 跳转 /main）
const router = useRouter()
const currentPageStore = useCurrentPageStore()

installSubAppBridge((menu) => {
  // 记录来源页签：关闭该页签时回到打开它的页面（如流程待办列表）并通知其刷新数据
  const openedFrom = currentPageStore.currentUrl || ''
  const exist = currentPageStore.pageList.find((m) => m.url === menu.url)
  if (exist) exist.openedFrom = openedFrom

  currentPageStore.showPage({ ...menu, openedFrom })
  currentPageStore.setShowIndex(false)
  router.push({ path: '/main' })
}, (menu) => {
  // 子应用提交后关闭自己所在的页签：按页面地址定位；
  // 子应用内部发生过路由跳转时地址会变化，此时仅当请求来自当前子应用才关闭当前页签，避免误关其它页签
  const url = menu && menu.url
  const pages = currentPageStore.pageList
  const target = findPage(pages, url)
    || (sameOrigin(url, currentPageStore.currentApp.host)
      ? pages.find((m) => m.url === currentPageStore.currentUrl)
      : null)
  if (!target) return

  // 打开该页签时的来源页签（见上方打开回调）：关闭后优先回到来源页签，并通知其刷新数据
  const returnMenu = target.openedFrom
    ? pages.find((m) => m.url === target.openedFrom)
    : null

  currentPageStore.del(target)

  if (returnMenu && currentPageStore.pageList.includes(returnMenu)) {
    currentPageStore.showPage(returnMenu)
    // 立即通知一次：来源页签已是当前页签（关页时用户已切回来源页签）或 iFrame 承载时不会再有激活回调；
    // 保活实例隐藏期间监听被清空时这次会丢，由 current.vue 的 aftershow / activated 再补发一次
    notifyPageActivated(returnMenu)
    return
  }

  if (currentPageStore.pageList.length === 0) {
    const home = currentPageStore.favorite.index
    if (home && home.url) currentPageStore.showPage(home, true)
    currentPageStore.setShowIndex(true)
    router.push({ path: '/main' })
  }
})

/** 按地址查找已打开页签：忽略 hash 与末尾斜杠，保留 query */
function findPage(pages, url) {
  if (!url) return null
  const target = normalizeUrl(url)
  return pages.find((m) => normalizeUrl(m.url) === target) || null
}

function normalizeUrl(value) {
  try {
    const url = new URL(value)
    url.hash = ''
    return url.href.replace(/\/$/, '')
  } catch (e) {
    return (value || '').replace(/\/$/, '')
  }
}

function sameOrigin(a, b) {
  try {
    return !!a && !!b && new URL(a).origin === new URL(b).origin
  } catch (e) {
    return false
  }
}
</script>