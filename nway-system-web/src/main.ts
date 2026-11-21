import { createApp, defineAsyncComponent } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import WujieVue from "wujie-vue3";
// 补齐 wujie-vue3 的卸载收尾（上游用 Vue 2 的 beforeDestroy，Vue 3 不会调用）——须早于首次渲染
import '@/microfront/wujie-lifecycle-fix'
import microApp from '@micro-zoe/micro-app'
import camelCase from 'camelcase';

import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'

import './assets/base.css'
import { permissionDirective } from '@/directives/permission'
import PageLoading from '@/components/PageLoading.vue'

const app = createApp(App)

// 注册按钮级权限指令
app.directive('permission', permissionDirective)

const modules = import.meta.glob(["@/views/system/**/*.vue"])

for (const path in modules) {
    const modulesConent: any = modules[path]
    // 去除 /src/views/   如果修改组件命名规则，需要调整current.vue中KeepAlive排除的组件名称
    const componentName: string = camelCase(path.substring(11, path.length - 4).replaceAll("/", "-"), { pascalCase: true })
    // delay=200：200ms 内加载完成则不显示 loading，避免快速加载时闪烁；
    // 超过 200ms 显示 loadingComponent，避免首次打开时 chunk 下载期间出现白屏
    app.component(componentName, defineAsyncComponent({
        loader: modulesConent,
        delay: 200,
        loadingComponent: PageLoading,
    }))
}

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
    app.component(key, component)
}

app.use(createPinia())
    .use(router)
    .use(ElementPlus, {
        locale: zhCn,
    })
    .use(WujieVue);

// micro-app 启动：不配置 preFetchApps 且不调用 preFetch() 即等于禁用预加载
// 子应用加载失败兜底在 current.vue 的 @error 中处理
//
// router-mode='state'：子应用虚拟路由写入 history.state.__MICRO_APP_STATE__，
// 刷新后可恢复、且不再向宿主地址栏写 `?app_xxx=...`（rc.32 默认是 search 模式）。
// 这消除了两个副作用：① 脏参数导致子应用被强制跳转到不存在路由而白屏；
// ② 与 wujie 的 sync 参数共用同一 query key（都取 name）导致的串路由隐患。
//
// iframeSrc：iframe 沙箱默认以「宿主页面地址」作为沙箱 iframe 的 src，初始化时会加载并执行宿主应用本身
// （表现为 sandbox iframe 内出现宿主 /src/main.ts 等请求被取消），宿主应用在沙箱里一碰 history.pushState
// 就会被 micro-app 当成子应用路由同步进地址栏（如 ?app_localhost_5174_workItemList=%2Fmain），
// 下次带该参数打开时子应用被强制跳转到不存在的路由，<router-view> 为空 → 内容区白屏。
// 指向同源空白页后沙箱不再加载宿主资源，从根源上杜绝该伪路由参数（micro-app 官方常见问题 4 的方案一）。
microApp.start({
    'router-mode': 'state',
    iframeSrc: new URL('empty.html', location.origin + import.meta.env.BASE_URL).href,
})

app.mount('#app')
