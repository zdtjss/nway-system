
import camelCase from 'camelcase'

function buildCompName(newUrl) {
    // 当由本地页面切换至远程页面时  远程页面不被显示  因为上一个compName持续有值  所以默认返回null
    let compName = null
    // 本地组件不通过http或https访问
    if (!newUrl.startsWith('http://') && !newUrl.startsWith('https://') && !!newUrl) {
        if (newUrl.endsWith(".vue")) {
            // 删除 /views/ 
            compName = camelCase(newUrl.substring(7, newUrl.length - 4).replaceAll('/', '-'), { pascalCase: true })
        } else {
            // 删除 /views/ 
            compName = camelCase(newUrl.substring(7).replaceAll('/', '-'), { pascalCase: true })
        }
    }
    return compName
}

function buildApp(menu) {
    const url = menu.url || ""
    const renderType = menu.renderType || ""
    // 外部子应用通过 http/https 访问，其余情况忽略处理
    if (!url.startsWith("http")) {
        return
    }
    // host：子应用入口地址（保留 query；hash 路由保留完整地址）。
    // 详情类地址的 query（如流程 detailsUrl 的 ?id=xxx）是子应用页面的一部分，
    // 剥掉会让子应用打开成新建页，故只对 .html 入口做目录归一。
    let host = url
    const idx = url.indexOf("?", 8)
    if (idx !== -1 && url.indexOf("/#/") === -1) {
        let base = url.substring(0, idx)
        if (base.endsWith(".html")) {
            base = base.substring(0, base.lastIndexOf("/"))
        }
        host = base + url.substring(idx)
    }
    // name：基于完整 url 生成，保证同一 host 下不同子页面不重名，且仅含安全字符。
    // 以 app_ 前缀保证以字母开头（满足 micro-app formatAppName 的命名校验）。
    const name = "app_" + url.replace(/^https?:\/\//, "").replace(/[^a-zA-Z0-9_-]/g, "_")
    // defaultPage：micro-app 解析入口 URL 时会剥离 hash（formatAppURL 只保留 origin+pathname+search），
    // hash 路由子应用（如 app-web 的 /digital/#/流程详情）会因此落到默认首页。
    // 仅当 hash 是路由（以 #/ 开头）时，把它作为 micro-app 的 default-page 下发；
    // wujie / iFrame 的入口地址（host）本身保留 hash，由各自容器按 URL 加载，不依赖该字段。
    const hashIndex = url.indexOf("#/")
    const defaultPage = hashIndex !== -1 ? "/" + url.substring(hashIndex) : ""
    // tenantCode：菜单归属租户，作为打开子应用时的工作租户下发（native 菜单无此字段，取空）
    return { name, host, defaultPage, url, renderType, tenantCode: menu.tenantCode || "" }
}

export { buildCompName, buildApp }