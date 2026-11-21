/**
 * 渲染器能力声明。
 *
 * 多渲染器是平台的产品契约：不同来源的页面可能只有某一种渲染器可用。
 * 这里把"每个渲染器能做什么"显式声明出来，供加载失败诊断与选型参考使用，
 * 避免排障时只能靠"白屏 + 请稍后重试"。
 *
 * 能力依据（源码核对）：
 * - micro-app / wujie 均为「fetch 入口 HTML + 注入宿主 DOM」，因此不受 X-Frame-Options /
 *   CSP frame-ancestors 限制；*-iframe / iFrame 走真实 iframe，目标站点禁止被嵌入时不可用。
 * - wujie 在无 Proxy 环境自动降级为 degrade（sandbox.js: degrade = degrade || !wujieSupport）；
 *   micro-app 需显式 disable-sandbox 才能在不支持 Proxy 的环境运行。
 * - 保活语义：micro-app 隐藏时释放全局副作用（recordAndReleaseEffect），wujie 后台继续运行。
 */
export type RenderType = 'native' | 'microApp' | 'microApp-iframe' | 'wujie' | 'wujie-iframe' | 'iFrame'

export interface RendererCapability {
    /** inject=注入宿主 DOM；isolated=独立浏览上下文 */
    mode: 'inject' | 'isolated'
    /** 目标站点用 X-Frame-Options / CSP frame-ancestors 禁止被嵌入时，该渲染器是否仍可用 */
    embedableWhenFrameDenied: boolean
    /** 宿主能否主动推送鉴权载荷（含 token 滑动续期热更新） */
    authChannel: 'push' | 'none'
    /** 后台保活时子应用副作用（定时器 / 全局事件）是否被挂起 */
    background: 'suspend' | 'running'
    /** 无 Proxy 环境 */
    noProxy: 'ok' | 'need-disable-sandbox' | 'auto-degrade'
}

export const RENDERER_CAPS: Record<Exclude<RenderType, 'native'>, RendererCapability> = {
    microApp: { mode: 'inject', embedableWhenFrameDenied: true, authChannel: 'push', background: 'suspend', noProxy: 'need-disable-sandbox' },
    'microApp-iframe': { mode: 'isolated', embedableWhenFrameDenied: false, authChannel: 'push', background: 'suspend', noProxy: 'ok' },
    wujie: { mode: 'inject', embedableWhenFrameDenied: true, authChannel: 'push', background: 'running', noProxy: 'auto-degrade' },
    'wujie-iframe': { mode: 'isolated', embedableWhenFrameDenied: false, authChannel: 'push', background: 'running', noProxy: 'ok' },
    iFrame: { mode: 'isolated', embedableWhenFrameDenied: false, authChannel: 'none', background: 'running', noProxy: 'ok' }
}

/** 目标站点禁止被嵌入时的替代渲染器（仅注入族可用） */
export const FALLBACK_WHEN_FRAME_DENIED = 'wujie'
