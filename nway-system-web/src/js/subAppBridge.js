import { bus } from 'wujie'
import microApp from '@micro-zoe/micro-app'
import { buildApp } from './utils.js'
import auth from './auth.js'
import { useIframeAppStore } from '@/stores/iframeAppStore'

/**
 * 子应用 → 宿主「打开 / 关闭页签」通道。
 *
 * 宿主以三种方式承载子应用（wujie / micro-app / iFrame，含 -iframe 降级），
 * 子应用统一按下列约定调用（见各子应用内的 openInHost / closePage 实现），宿主在此统一收口：
 *  - wujie：window.$wujie.bus.$emit('nway:open-page' | 'nway:close-page', { type, menu })
 *  - micro-app：window.microApp.setGlobalData({ type: 'nway:open-page' | 'nway:close-page', menu })
 *  - iFrame：window.parent.$nway.openPage/closePage(menu)（同源）或 postMessage({ type, menu })（跨域）
 *
 * 与反向通道（宿主 → 子应用，current.vue 的 'nway:auth-change'）保持一致的事件命名。
 *
 * 另含一个宿主 → 子应用的「页签重新激活」通知（notifyPageActivated）：关闭由某页签打开的页面后，
 * 宿主回到来源页签并通知其刷新数据（如流程待办列表在申请单办理关页后刷新）。
 */
export const OPEN_PAGE_EVENT = 'nway:open-page'

/**
 * 打开页签回执：跨域 iframe 子应用据此确认宿主已接入该通道（未收到回执则由子应用降级为页内 iframe 页签）。
 */
export const OPEN_PAGE_ACK_EVENT = 'nway:open-page-ack'

/**
 * 子应用提交后关闭自己所在的页签：三种承载方式的通道与打开一致，
 * 子应用只上报当前页面地址，宿主按地址定位页签并沿用各自 renderType 的销毁逻辑。
 */
export const CLOSE_PAGE_EVENT = 'nway:close-page'

/** 关闭页签回执：子应用据此确认宿主已受理，不再执行自身兜底 */
export const CLOSE_PAGE_ACK_EVENT = 'nway:close-page-ack'

/**
 * 页签重新激活通知（宿主 → 子应用）：关闭由某页签打开的页面后，宿主回到来源页签并通知其刷新，
 * 子应用据此重新拉取列表等数据（如流程待办列表在申请单办理关页后刷新）。
 */
export const PAGE_ACTIVATED_EVENT = 'nway:page-activated'

/**
 * 登录态握手（宿主 → 纯 iframe 子应用）：
 * 子应用启动时发送 'nway:auth-request'，宿主仅向自己承载的 iframe 回发
 * 'nway:auth' + 当前 Token，避免把 Token 拼在 URL 上（历史/日志/Referer 泄露）。
 */
const AUTH_REQUEST_EVENT = 'nway:auth-request'
const AUTH_EVENT = 'nway:auth'

/** 是否已安装（幂等标志） */
let installed = false

/**
 * 安装子应用打开 / 关闭页签通道（只安装一次，建议在 App.vue 中调用）。
 *
 * @param {(menu: { name: string, url: string, type?: string, renderType?: string }) => void} openPage
 *        宿主打开页签的实现，等价于平台内部页面点击菜单：
 *        currentPageStore.showPage(menu) + setShowIndex(false) + router.push('/main')
 * @param {(menu: { name?: string, url?: string }) => void} closePage
 *        宿主关闭页签的实现，等价于点击页签上的关闭按钮：
 *        currentPageStore.del(menu)，页签清空后回到首页
 */
export function installSubAppBridge(openPage, closePage) {
  // 幂等：开发环境 HMR 下 App.vue 会重新执行，避免重复注册监听
  if (installed) return
  installed = true

  const handleOpen = (payload) => {
    // 兼容两种载荷：{ type, menu } 与直接传 menu 对象
    const menu = payload && payload.menu ? payload.menu : payload
    if (!menu || !menu.url) return
    openPage({
      name: menu.name || '详情',
      url: menu.url,
      type: menu.type || 'PAGE',
      // 子应用未指定时按平台默认（低代码菜单同步的缺省渲染方式）
      renderType: menu.renderType || 'microApp',
    })
  }

  const handleClose = (payload) => {
    if (typeof closePage !== 'function') return
    // 兼容 { type, menu } 与直接传 menu 对象
    const menu = payload && payload.menu ? payload.menu : payload
    closePage(menu || {})
  }

  // wujie：主应用与所有子应用共享的 EventBus
  bus.$on(OPEN_PAGE_EVENT, (payload) => {
    handleOpen(payload)
    sendAck(payload, OPEN_PAGE_ACK_EVENT)
  })

  bus.$on(CLOSE_PAGE_EVENT, (payload) => {
    sendAck(payload, CLOSE_PAGE_ACK_EVENT)
    handleClose(payload)
  })

  // micro-app：子应用 window.microApp.setGlobalData({ type, menu })
  // （全局数据通道：宿主无需预先知道各子应用的 appName）
  microApp.addGlobalDataListener((data) => {
    if (!data) return
    if (data.type === OPEN_PAGE_EVENT) {
      handleOpen(data)
      sendAck(data, OPEN_PAGE_ACK_EVENT)
    } else if (data.type === CLOSE_PAGE_EVENT) {
      sendAck(data, CLOSE_PAGE_ACK_EVENT)
      handleClose(data)
    }
  })

  // iFrame / 降级模式：同源子应用直接调用全局入口
  window.$nway = Object.assign(window.$nway || {}, {
    openPage: handleOpen,
    closePage: handleClose,
  })

  // iFrame / 降级模式：跨域子应用 postMessage（仅做页签导航，不读取任何数据），处理后回执
  window.addEventListener('message', (event) => {
    const data = event && event.data
    if (!data) return
    // 仅接受平台承载窗口的请求：micro-app 同沙箱（source 为本窗口）或页面内嵌的 iframe
    if (!isHostedFrame(event.source)) return
    // 纯 iframe 子应用领取登录态：只回发给自己的 iframe，不向任意来源暴露 Token
    if (data.type === AUTH_REQUEST_EVENT) {
      replyAuth(event)
      return
    }
    if (data.type === OPEN_PAGE_EVENT) {
      handleOpen(data)
      sendAckToSource(event.source, data, OPEN_PAGE_ACK_EVENT)
    } else if (data.type === CLOSE_PAGE_EVENT) {
      sendAckToSource(event.source, data, CLOSE_PAGE_ACK_EVENT)
      handleClose(data)
    }
  })
}

/**
 * 回发当前登录 Token 给发起请求的 iframe。
 * 目标源优先取消息来源 origin；sandbox 等 origin 为 "null" 时退化为 '*'（内容仅宿主 Token，
 * 且仅回发给本页面实际承载的 iframe）。
 */
function replyAuth(event) {
  const token = auth.getToken()
  if (!token || !event.source) return
  const targetOrigin = event.origin && event.origin !== 'null' ? event.origin : '*'
  try {
    event.source.postMessage({ type: AUTH_EVENT, token }, targetOrigin)
  } catch (e) {
    // 回发失败时子应用保持自身既有鉴权，不影响页面加载
  }
}

/**
 * 回执：由宿主通过 wujie / micro-app 通道广播。
 * 子应用据此确认宿主已接入该通道；未收到回执（宿主未升级 / 非平台宿主）时走自身兜底。
 */
function sendAck(payload, type) {
  if (!payload || !payload.requestId) return
  const ack = { type, requestId: payload.requestId }
  try {
    bus.$emit(type, ack)
  } catch (e) {
    // 忽略：无 wujie 子应用时同样安全
  }
  try {
    microApp.setGlobalData(ack)
  } catch (e) {
    // 忽略：micro-app 未启动时同样安全
  }
}

/** 回执：跨域 iframe 需定向回发给请求窗口 */
function sendAckToSource(source, payload, type) {
  if (!payload || !payload.requestId || !source) return
  try {
    source.postMessage({ type, requestId: payload.requestId }, '*')
  } catch (e) {
    // 回执失败不影响页签操作
  }
}

/**
 * 消息是否来自平台承载的窗口：微前端沙箱自身（source 即本窗口）或本页面内的 iframe。
 * 用于过滤无关窗口（如其它标签页/弹窗）发来的同类型消息。
 */
function isHostedFrame(source) {
  if (!source) return false
  if (source === window) return true
  const frames = document.querySelectorAll('iframe')
  for (const frame of frames) {
    try {
      if (frame.contentWindow === source) return true
    } catch (e) {
      // 忽略：极端场景下读取 contentWindow 受限
    }
  }
  return false
}

/**
 * 通知指定页签「已被重新激活」：宿主关闭由该页签打开的页面后调用。
 *
 * 通道与打开 / 关闭一致：
 *  - wujie：按 appName 定向发送 'nway:page-activated:<appName>'（alive 下多实例并存，避免广播串扰）
 *  - micro-app：全局数据广播，子应用按 appName 过滤
 *  - iFrame：向对应 iframe 窗口 postMessage
 *
 * @param {{ url: string, renderType?: string }} menu 需要通知刷新的页签（来源页签）
 */
export function notifyPageActivated(menu) {
  if (!menu || !menu.url) return
  const app = buildApp(menu)
  if (!app || !app.renderType) return

  const payload = { type: PAGE_ACTIVATED_EVENT, appName: app.name, url: menu.url }
  const prefix = app.renderType.split('-')[0]

  if (prefix === 'wujie') {
    try {
      bus.$emit(PAGE_ACTIVATED_EVENT + ':' + app.name, payload)
    } catch (e) {
      // 忽略：无 wujie 子应用时同样安全
    }
    return
  }

  // micro-app 不走全局数据通道：global 数据按批合并，多个消息同时发送会互相覆盖 type，
  // 可能冲掉打开/关闭回执；子应用改为监听 micro-app 保活重显时派发的 appstate-change 自行刷新
  if (prefix === 'microApp') {
    return
  }

  if (app.renderType === 'iFrame') {
    const iframe = useIframeAppStore().iframeList.find((item) => item.src.startsWith(app.host))
    if (iframe && iframe.contentWindow) {
      try {
        iframe.contentWindow.postMessage(payload, '*')
      } catch (e) {
        // 发送失败不影响关闭流程
      }
    }
  }
}