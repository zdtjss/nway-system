import auth from '../js/auth.js'
import { buildApp, buildCompName } from '../js/utils.js'
import { defineStore } from 'pinia'
import { destroyApp as destroyAppWujie } from "wujie";
import microApp from '@micro-zoe/micro-app'
import { useIframeAppStore } from '@/stores/iframeAppStore'


// 布局偏好 localStorage key
const LAYOUT_POS_KEY = 'nway_layout_position'
const LAYOUT_DOCK_KEY = 'nway_layout_docked'

// 已打开页签持久化 key：与 Token 同为 sessionStorage（关闭标签页即失效），
// 使任意刷新（F5、开发服务器强制刷新、登录跳转）后已打开的菜单仍能恢复
const OPEN_TABS_KEY = 'nway_open_tabs'

/**
 * 读取持久化的页签快照。
 * 仅当快照属于当前登录用户（工号一致）且存在已打开页签时返回，避免换用户登录后恢复他人菜单。
 */
function readOpenTabs() {
  try {
    const snap = JSON.parse(sessionStorage.getItem(OPEN_TABS_KEY))
    if (!snap || !Array.isArray(snap.pageList) || snap.pageList.length === 0) return null
    const user = auth.getUser()
    if (!user || !user.employeeId || snap.employeeId !== user.employeeId) return null
    return snap
  } catch (e) {
    return null
  }
}

/**
 * 清除地址栏中指定 wujie 实例的路由同步参数。
 *
 * wujie 的 sync 会把子应用当前路由写成宿主地址栏的 query（?<appName>=<子应用路由>），
 * 新建沙箱时再据此恢复子应用路由。实例已销毁后该快照不再有意义，但 wujie 的 destroy
 * 先移出实例表再 unmount，内部 clearInactiveAppUrl 对已销毁实例直接跳过，参数会残留在地址栏；
 * 重新打开同一菜单时 syncUrlToIframe 又会用它覆盖菜单 URL 的 hash（表现为回到关闭前的子应用页面）。
 * 这里在销毁页签后补一次清理：只删本实例的 key，保留其它 query 与 hash。
 */
function clearWujieSyncParam(name) {
  if (!name) return
  try {
    const url = new URL(window.location.href)
    if (!url.searchParams.has(name)) return
    url.searchParams.delete(name)
    window.history.replaceState(window.history.state, '', url.href)
  } catch (e) {
    console.warn('清理子应用路由同步参数失败', e)
  }
}

export const useCurrentPageStore = defineStore('currentPage', {
  state: () => {
    const snap = readOpenTabs()
    return {
      lastMenuOnShowMenu: "",
      currentUrl: snap ? snap.currentUrl || "" : "",
      showIndex: snap ? !!snap.showIndex : undefined,
      currentApp: snap && snap.currentApp ? snap.currentApp : { name: "", host: "", defaultPage: "" },
      currentComp: snap ? snap.currentComp || "" : "",
      toolbarVisible: true,
      // 布局位置：'top' | 'left' | 'bottom' | 'right'
      layoutPosition: localStorage.getItem(LAYOUT_POS_KEY) || 'top',
      // 工具栏是否固定（常驻展开）
      toolbarDocked: localStorage.getItem(LAYOUT_DOCK_KEY) === 'true',
      // 工具栏是否处于展开状态（peek 或 docked 时均为 true）
      toolbarExpanded: false,
      favorite: { index: {}, favorite: [] },
      pageList: snap ? snap.pageList : [],
      userMenuFull: {
        "name": "用户所有菜单",
        "url": "/views/system/menu/allMenu",
        "type": "PAGE",
        "renderType": "native"
      }
    }
  },
  actions: {
    // 持久化已打开页签与当前页面：刷新后可原样恢复，避免已打开菜单被清空
    saveOpenTabs() {
      try {
        const user = auth.getUser()
        sessionStorage.setItem(OPEN_TABS_KEY, JSON.stringify({
          employeeId: user && user.employeeId ? user.employeeId : "",
          pageList: this.pageList,
          currentUrl: this.currentUrl,
          currentComp: this.currentComp,
          currentApp: this.currentApp,
          showIndex: !!this.showIndex
        }))
      } catch (e) {
        // 存储异常（如超限）不影响页面功能
        console.warn('页签持久化失败', e)
      }
    },
    showPage(menu, isHomePage = false) {
      // 添加参数检查，这是必要的修改
      if (!menu || !menu.url) {
        console.warn('Invalid menu provided to currentPageStore.showPage');
        return;
      }

      console.log("showPage", menu)

      this.currentUrl = menu.url

      this.currentApp = buildApp(menu) || { name: "", host: "", defaultPage: "" }
      // Token 不拼接到 URL（安全风险），改由 current.vue 通过 :data / :props 注入子应用

      this.currentComp = buildCompName(menu.url) || ""

      if (!this.pageList.find(item => item.url === menu.url) && !isHomePage) {
        this.pageList.push(menu)
      }

      this.saveOpenTabs()
    },
    showUserMenu() {

      this.lastMenuOnShowMenu = this.currentUrl

      this.currentApp = { name: "", host: "", defaultPage: "" }

      this.currentComp = buildCompName(this.userMenuFull.url)

      // 确保 allMenu 页签已加入 pageList：currentUrl 不在列表（如初始首页）时也能插入，且避免重复插入
      const exists = this.pageList.some(m => m.url === this.userMenuFull.url && m.name === this.userMenuFull.name)
      if (!exists) {
        let inserted = false
        for (let i = 0; i < this.pageList.length; i++) {
          if (this.pageList[i].url === this.currentUrl) {
            this.pageList.splice(i, 0, this.userMenuFull)
            inserted = true
            break
          }
        }
        if (!inserted) {
          this.pageList.push(this.userMenuFull)
        }
      }

      this.saveOpenTabs()
    },
    justCloseUserMenu() {
      for (let i = 0; i < this.pageList.length; i++) {
        const m = this.pageList[i]
        if (this.userMenuFull.url === m.url && this.userMenuFull.name == m.name) {
          const tempMenu = this.pageList[this.pageList.length !== (i + 1) ? i + 1 : i - 1]
          if (tempMenu === null) {
            // 如果没有其他页面，重置状态
            this.currentUrl = ""
            this.currentApp = { name: "", host: "", defaultPage: "" }
            this.currentComp = ""
          }
          this.pageList.splice(i, 1)
          break
        }
      }

      this.saveOpenTabs()
    },
    del(menu) {
      // 添加参数检查
      if (!menu || !menu.url) {
        console.warn('Invalid menu provided to currentPageStore.del');
        return;
      }

      if (menu.url.startsWith("http://") || menu.url.startsWith("https://")) {
        const app = buildApp(menu)
        if (app && app.renderType) {
          if (app.renderType.indexOf("wujie") !== -1) {
            // wujie：destroyApp 彻底销毁 iframe + shadowRoot + wujie 实例 + 所有缓存
            destroyAppWujie(app.name)
            // 实例销毁后其路由同步参数立即失效，必须清掉，
            // 否则重开同一菜单时会被旧路由覆盖（如提交成功后子应用内部跳转的页面）
            clearWujieSyncParam(app.name)
          } else if (app.renderType.indexOf("microApp") !== -1) {
            // 延迟到 Vue 完成 v-if 卸载后再销毁，避免与 Vue 对同一 DOM 节点的移除竞争。
            // setTimeout(0) 为宏任务，晚于 Vue 的微任务刷新，此时 <micro-app> 已被移除并触发
            // disconnectedCallback（keep-alive 应用进入 hidden 状态）；destroy:true 会同时
            // 清除 keep-alive 缓存并从 appInstanceMap 移除该应用。
            setTimeout(() => {
              microApp.unmountApp(app.name, { destroy: true }).catch((e) => {
                console.warn('microApp 卸载失败，保持实例以避免渲染异常', e)
              })
            }, 0)
          } else if (app.renderType === "iFrame") {
            // 传 host 而非完整 url：open() 时 iframe.src 即为 buildApp 处理后的 host（已去除 query），
            // 菜单 url 带 query 时按完整 url 前缀匹配将找不到 iframe，导致关闭不销毁、重开复用旧实例
            useIframeAppStore().destroyIframe(app.host)
          }
        }
      }

      for (let i = 0; i < this.pageList.length; i++) {
        const m = this.pageList[i]
        if (menu.url === m.url && menu.name === m.name) {
          let tempMenu = null
          if (this.pageList.length > 1) {
            // 如果不是最后一个元素，选择下一个；否则选择前一个
            if (i < this.pageList.length - 1) {
              tempMenu = this.pageList[i + 1]
            } else {
              tempMenu = this.pageList[i - 1]
            }
          }

          if (tempMenu != null) {
            this.showPage(tempMenu)
          } else {
            // 如果没有其他页面，重置状态
            this.currentUrl = ""
            this.currentApp = { name: "", host: "", defaultPage: "" }
            this.currentComp = ""
          }
          this.pageList.splice(i, 1)
          break
        }
      }

      // 如果删除后页面列表为空，重置状态
      if (this.pageList.length === 0) {
        this.currentUrl = ""
        this.currentApp = { name: "", host: "", defaultPage: "" }
        this.currentComp = ""
      }

      this.saveOpenTabs()
    },
    setShowIndex(show) {
      this.showIndex = show
      this.saveOpenTabs()
    },
    setToolbarVisible(visible) {
      this.toolbarVisible = visible
    },
    // 设置布局位置并持久化
    setLayoutPosition(pos) {
      if (!['top', 'left', 'bottom', 'right'].includes(pos)) return
      this.layoutPosition = pos
      localStorage.setItem(LAYOUT_POS_KEY, pos)
    },
    // 设置工具栏固定状态并持久化
    setToolbarDocked(val) {
      this.toolbarDocked = !!val
      localStorage.setItem(LAYOUT_DOCK_KEY, this.toolbarDocked ? 'true' : 'false')
      if (this.toolbarDocked) {
        this.toolbarExpanded = true
      }
    },
    // 切换固定/非固定
    toggleDock() {
      this.setToolbarDocked(!this.toolbarDocked)
    },
    // 设置展开状态
    setToolbarExpanded(val) {
      this.toolbarExpanded = !!val
    },
    setFavorite(fav) {
      const defaultIndex = { name: "首页", url: "/views/system/home/welcomePage", type: "PAGE", renderType: "native" }
      if (!fav) {
        this.favorite = { index: defaultIndex, favorite: [] }
      } else {
        // 若后端未配置首页，使用默认欢迎页
        if (!fav.index || !fav.index.url) {
          fav.index = defaultIndex
        }
        this.favorite = fav
      }
    }
  }
})