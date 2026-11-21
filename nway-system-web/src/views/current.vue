<template>
    <div id="currentMainApp" v-if="!!currentApp && !!currentApp.host && currentApp.renderType !== 'iFrame'">

        <micro-app v-if="renderTypePrefix === 'microApp'" :iframe="currentApp.renderType.indexOf('-iframe') > 0"
            :name="currentApp.name" :url="currentApp.host" :default-page="currentApp.defaultPage" keep-alive keep-router-state
            :data="microAppData"
            @error="onMicroAppError"
            @mounted="onSubAppReady"
            @aftershow="onSubAppReady"></micro-app>

        <WujieVue v-if="renderTypePrefix === 'wujie'" :name="currentApp.name" :url="currentApp.host"
            :degrade="currentApp.renderType.indexOf('-iframe') > 0" alive :sync="true"
            :props="wujieProps"
            :loadError="onWujieLoadError"
            :afterMount="onSubAppReady"
            :activated="onWujieActivated"></WujieVue>
    </div>
    <div id="currentMainIframe" v-show="!!currentApp && !!currentApp.host && currentApp.renderType === 'iFrame'">
    </div>
    <div id="currentMainComp" v-show="!!currentComp">
        <KeepAlive :include="keepAliveInclude">
            <component v-if="currentComp" :is="currentComp" :key="currentComp"></component>
        </KeepAlive>
    </div>
    <!-- 默认兜底页：无匹配页面时展示欢迎页 -->
    <SystemHomeWelcomePage v-if="(!currentApp || !currentApp.host) && !currentComp" />
</template>

<script setup>
import { onMounted, onUnmounted, watch, computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useCurrentPageStore } from '@/stores/menuStore'
import { useIframeAppStore } from '@/stores/iframeAppStore'
import auth from '@/js/auth.js'
import microApp from '@micro-zoe/micro-app'
import { bus } from 'wujie'
import { ElMessage } from 'element-plus'
import { RENDERER_CAPS } from '@/microfront/capabilities'
import { notifyPageActivated } from '@/js/subAppBridge'

const currentPageStore = useCurrentPageStore()
const { currentApp, currentComp, currentUrl, showIndex, pageList, favorite } = storeToRefs(currentPageStore)

const iframeAppStore = useIframeAppStore()

const renderTypePrefix = computed(() => currentApp.value.renderType ? currentApp.value.renderType.split('-')[0] : '')

// 本地组件 keep-alive 缓存名单：仅缓存仍处于打开状态的页签组件。
// 关闭页签后其文件名从名单移除，缓存实例被真正销毁，再次打开时重新挂载并重新执行数据接口；
// 未关闭的页签切换仍走缓存（onActivated），保持原有交互。
// 注意：include 匹配的是异步组件解析后的 __name（SFC 文件名），故取 url 最后一段。
const keepAliveInclude = computed(() => {
    const names = new Set()
    const collect = (url) => {
        if (!url || url.startsWith('http://') || url.startsWith('https://')) return
        let base = url.indexOf('?') !== -1 ? url.substring(0, url.indexOf('?')) : url
        if (base.endsWith('.vue')) base = base.substring(0, base.length - 4)
        const name = base.substring(base.lastIndexOf('/') + 1)
        if (name) names.add(name)
    }
    pageList.value.forEach((m) => collect(m.url))
    // 首页无关闭入口，始终保留缓存，避免每次返回首页都重新加载
    collect(favorite.value?.index?.url)
    // “所有菜单”页：不依赖页签插入结果，始终保留缓存（其 onActivated 负责加载数据）
    collect(currentPageStore.userMenuFull.url)
    // 当前显示页面始终保留缓存，防止关闭切换瞬间被误销毁
    collect(currentUrl.value)
    return Array.from(names)
})

// 菜单归属租户作为工作租户下发：空值与公共共享租户 public 不具备业务租户语义（手工菜单可能记应用租户 public），
// 一律视为"不下发"，由子应用清空工作租户，避免把 public 当工作租户导致 403。
function workTenantCodeOf(app) {
    const code = (app && app.tenantCode) || ""
    return code && code !== 'public' ? code : ""
}

// 构造下发给子应用的鉴权数据。withWorkTenant=false 仅用于 token 续期：只推 token+user，不触碰工作租户，
// 避免续期覆盖其它实例的工作租户。
function buildAuthData(app, withWorkTenant = true) {
    const data = { token: auth.getToken(), user: auth.getUser() }
    if (withWorkTenant) data.workTenantCode = workTenantCodeOf(app)
    return data
}

// micro-app 初始数据：通过 :data 注入 token、用户信息与工作租户，运行时变更由 syncAuthToSubApps 同步
const microAppData = computed(() => buildAuthData(currentApp.value))

// wujie 初始 props：仅注入一次（非响应式），运行时变更由 syncAuthToSubApps 按实例名定向推送。
// appName 供子应用订阅"属于自己的"实例事件——alive 下多实例并存，全局广播会把租户串给其它存活实例。
const wujieProps = computed(() => ({
    ...buildAuthData(currentApp.value),
    appName: currentApp.value?.name || ""
}))

// 加载守卫：多渲染器门户的主要排障手段——超时未就绪时给出可操作提示，而不是只留一句"请稍后重试"。
// 就绪时机：micro-app 的 mounted/aftershow（首载与保活重显）、wujie 的 afterMount/activated。
const LOAD_TIMEOUT = 8000
let loadTimer = null
let subAppReady = false

// 按渲染器能力给出可操作建议（能力表见 src/microfront/capabilities.ts）
function rendererHint() {
    const cap = RENDERER_CAPS[currentApp.value?.renderType]
    if (!cap) return ''
    return cap.embedableWhenFrameDenied
        ? '请检查目标站点是否放行跨域（CORS）且静态资源可达'
        : '若目标站点禁止被嵌入（X-Frame-Options / CSP frame-ancestors），请把该菜单渲染类型改为 microApp 或 wujie'
}

function armLoadGuard() {
    clearTimeout(loadTimer)
    subAppReady = false
    const prefix = currentApp.value?.renderType ? currentApp.value.renderType.split('-')[0] : ''
    // 纯 iFrame 通道没有就绪回调，不参与守卫（避免误报）；native 本地页无 host，也不参与
    if (prefix !== 'microApp' && prefix !== 'wujie') {
        subAppReady = true
        return
    }
    loadTimer = setTimeout(() => {
        if (subAppReady) return
        ElMessage({
            type: 'warning',
            showClose: true,
            duration: 30000,
            message: `子应用 ${currentApp.value?.host || ''} 30 秒未完成加载：${rendererHint()}`
        })
    }, LOAD_TIMEOUT)
}

function onSubAppReady() {
    subAppReady = true
    clearTimeout(loadTimer)
    syncAuthToSubApps()
    // 页签重新显示后通知该子应用：保活实例在隐藏期间会被清空数据/事件监听，
    // 必须在 aftershow / activated 之后发送，来源页签（如流程待办列表）才能收到并刷新
    if (currentApp.value && currentApp.value.host && !showIndex.value) {
        notifyPageActivated(currentApp.value)
    }
}

// micro-app 加载失败兜底：给出用户提示，避免白屏无反馈
function onMicroAppError(e) {
    console.error('[micro-app] 应用加载失败:', currentApp.value.name, currentApp.value.host, e)
    const hint = rendererHint()
    ElMessage({ message: hint ? `应用加载失败：${hint}` : '应用加载失败，请稍后重试', type: 'error' })
}

// wujie 加载失败兜底
function onWujieLoadError(url, e) {
    console.error('[wujie] 应用加载失败:', currentApp.value?.name, url, e)
    const hint = rendererHint()
    ElMessage({ message: hint ? `应用加载失败：${hint}` : '应用加载失败，请稍后重试', type: 'error' })
}

// wujie alive 模式重新激活回调（degrade 降级 iframe 需重新校正尺寸）
function onWujieActivated(appWindow) {
    // 重新激活即视为就绪（清除加载守卫计时器，并向当前实例重推最新鉴权信息）
    onSubAppReady()
    const wujieInstance = appWindow && appWindow.__WUJIE
    if (wujieInstance && wujieInstance.degrade) {
        adjustWujieIframe(wujieInstance.id)
    }
}

// 鉴权信息变更时，向当前子应用实时同步（token 滑动续期后子应用仍持有旧 token）
let stopAuthSync = null
// 登出时通知子应用清空自己的登录态（与宿主同源共享存储的 wujie 实例由 clearUser 直接清理）
let stopLogoutSync = null
function syncAuthToSubApps(withWorkTenant = true) {
    const app = currentApp.value
    if (!app || !app.host || !app.renderType) return
    const data = buildAuthData(app, withWorkTenant)
    const prefix = app.renderType.split('-')[0]
    if (prefix === 'microApp') {
        // micro-app 按 app.name 精确投递，天然不会串给其它应用
        microApp.setData(app.name, data)
    } else if (prefix === 'wujie') {
        // 按实例名定向发送：携带工作租户的同步只影响当前实例
        bus.$emit('nway:auth-change:' + app.name, data)
        // 续期只推 token（不含工作租户），额外广播全局事件：
        // 兼容按文档订阅 'nway:auth-change' 的子应用，且不会串改其它存活实例的租户
        if (!withWorkTenant) bus.$emit('nway:auth-change', data)
    }
}

/**
 * 登出广播：token 已清空，显式告知子应用移除自身登录态。
 * wujie 全局广播可覆盖所有存活实例；micro-app 按当前应用投递。
 */
function broadcastLogout() {
    const app = currentApp.value
    if (!app || !app.host || !app.renderType) return
    const payload = { token: '', user: null, logout: true }
    const prefix = app.renderType.split('-')[0]
    if (prefix === 'microApp') {
        microApp.setData(app.name, payload)
    } else if (prefix === 'wujie') {
        bus.$emit('nway:auth-change:' + app.name, payload)
        bus.$emit('nway:auth-change', payload)
    }
}

onMounted(() => {
    // token 续期只推 token+user，不携带工作租户
    stopAuthSync = auth.onTokenChange(() => syncAuthToSubApps(false))
    // 登出/登录失效：告知子应用移除自身 token（同源实例的存储已由 auth.clearUser 清理）
    stopLogoutSync = auth.onLogout(() => broadcastLogout())
    // 首屏已恢复页签时，同样需要计时（否则刷新后直接白屏不会提示）
    armLoadGuard()
    if (currentApp.value && currentApp.value.renderType === 'iFrame') {
        iframeAppStore.open(currentApp.value)
    }
})

onUnmounted(() => {
    clearTimeout(loadTimer)
    if (stopAuthSync) {
        stopAuthSync()
        stopAuthSync = null
    }
    if (stopLogoutSync) {
        stopLogoutSync()
        stopLogoutSync = null
    }
})

watch(() => currentApp.value, (newApp) => {
    if (newApp && newApp.renderType === 'iFrame') {
        iframeAppStore.open(newApp)
    }
    // 渲染目标变化即重新计时：新目标 8s 内未就绪给出可操作提示
    armLoadGuard()
    // 菜单切换时向当前子应用按实例重推：同 url 跨租户会复用同一个 wujie 实例（不触发 activated），
    // 只有此处能刷新其工作租户；切到非子应用页面（native/iFrame）时无接收方，各存活实例保留自身租户，
    // 不做跨实例清空（各实例租户本就应各自独立，强行清空会使其后台请求落到错误租户）。
    syncAuthToSubApps()
})

function adjustWujieIframe(wujieId) {
    const iframe = document.querySelector(`iframe[data-wujie-id="${wujieId}"]`);
    if (iframe) {
        // 工具栏始终浮动覆盖，iframe 始终使用完整视口尺寸
        const w = document.documentElement.clientWidth - 2
        const h = document.documentElement.clientHeight
        iframe.style.cssText = "width:" + w + "px;height: " + h + "px;border:0;";
    } else {
        console.log('未找到指定的 wujie iframe');
    }
}

</script>

