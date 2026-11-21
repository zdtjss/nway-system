/**
 * wujie-vue3 收尾钩子补齐（三方缺陷兜底）。
 *
 * 问题：wujie-vue3@2.1.0 用 Vue 2 的 beforeDestroy 做收尾，而 Vue 3 只认 beforeUnmount
 * （runtime-core 仅注入 bum/um），该钩子永不执行，导致：
 *   1. bus.$onAll(handleEmit) 每次挂载注册、永不注销（EventBus.$emit 会遍历全局
 *      _wujie_all_event 队列，切换次数越多扇出越大）；
 *   2. window.__WUJIE_QUEUE[name] 常驻，长期持有已卸载实例的组件引用。
 *
 * 做法：wujie-vue3 导出的是 defineComponent(选项对象)，而 Vue 3 的 defineComponent 对
 * 对象入参原样返回同一个对象（runtime-core: `isFunction(options) ? ... : options`），
 * 且该对象未冻结。因此在首次渲染前补一个 beforeUnmount 即可被正常注入执行。
 *
 * 只做两处"真实泄漏"的清理，不置 isUnmounted：上游那条 isUnmounted 会激活
 * "异步 startApp 续体在卸载后 destroy 沙箱"，而同名应用快速切回时该 destroy 可能
 * 作用在新实例刚复用的同一个 sandbox 上（偶发白屏），收益小于风险。
 *
 * 上游修复后（自带 beforeUnmount）本补丁由 if 守卫自动失效。
 */
import WujieVue from 'wujie-vue3'
import { bus } from 'wujie'

const def = WujieVue as unknown as Record<string, any>

if (typeof def.beforeUnmount !== 'function' && typeof def.beforeDestroy === 'function') {
    def.beforeUnmount = function beforeUnmount(this: any) {
        // 注销全局总线监听：handleEmit 是组件实例上的方法，与本组件注册的是同一引用
        bus.$offAll(this.handleEmit)
        // 清理该实例的串行队列：仅当全局队列仍指向本实例的链尾时删除，
        // 避免误删被同名新实例接管的队列（与上游 clearStartAppQueue 同义）
        const queueMap = (window as any).__WUJIE_QUEUE
        if (this.name && queueMap && queueMap[this.name] === this.startAppQueue) {
            delete queueMap[this.name]
        }
    }
}

// 开发环境探针：切换页签前后读取该值，恒定（≤2）说明收尾生效；线性增长说明钩子未执行。
// 用法：在 wujie 菜单与本地页面之间来回切换 N 次，然后看 window.__NWAY_WUJIE_LISTENERS__
if (import.meta.env.DEV) {
    Object.defineProperty(window, '__NWAY_WUJIE_LISTENERS__', {
        configurable: true,
        get() {
            const eventObjMap = (window as any).__WUJIE_INJECT?.appEventObjMap
            let total = 0
            eventObjMap?.forEach((eventObj: Record<string, any[]>) => {
                total += (eventObj['_wujie_all_event'] || []).length
            })
            return total
        }
    })
}
