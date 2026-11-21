import { createRouter, createWebHistory, type RouteLocationNormalized, type RouteRecordRaw } from 'vue-router'
import { getToken } from '@/utils/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'index',
    component: () => import('@/views/layout/index.vue'),
    children: [
      {
        path: 'main',
        name: 'main',
        component: () => import('@/views/current.vue')
      }
    ]
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/login.vue')
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'notFound',
    // D12：不再静默重定向回首页（用户分不清「无权限」与「页面不存在」）
    component: () => import('@/views/error/notFound.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/**
 * 清理 micro-app 路由同步残留的伪子应用参数。
 *
 * micro-app 的 search 路由模式会把子应用当前路由以 `app_xxx` 查询参数同步到地址栏；
 * iframe 沙箱的沙箱 iframe 曾以宿主页面地址为 src，宿主自身的路径（如 /main）会被误当作
 * 子应用路由写入该参数。这类值与宿主当前路径相同（子应用不可能以宿主路由为自己的页面），
 * 属于脏数据：带着它打开时 micro-app 会强制子应用跳转到该路由，子应用路由表无匹配 →
 * <router-view> 为空 → 内容区白屏（如 http://localhost:5173/main?app_localhost_5174_workItemList=%2Fmain）。
 *
 * 清除后 micro-app 按子应用入口地址重新同步，页面回到菜单对应页面，白屏 URL 可自愈。
 */
function dropStaleMicroAppQuery(to: RouteLocationNormalized) {
  const staleKeys = Object.keys(to.query).filter(
    (key) => key.startsWith('app_') && to.query[key] === to.path
  )
  if (staleKeys.length === 0) return null
  const query = { ...to.query }
  staleKeys.forEach((key) => delete query[key])
  return { path: to.path, query, replace: true }
}

router.beforeEach((to) => {
  const token = getToken()
  if (to.name !== 'login' && !token) {
    return { name: 'login' }
  }
  return dropStaleMicroAppQuery(to) || undefined
})

export default router
