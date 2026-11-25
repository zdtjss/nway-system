/**
 * 统一 HTTP 请求封装。
 *
 * 功能：
 * - 请求拦截：自动注入 Authorization Token
 * - Token 滑动续期：剩余不足阈值（后端下发，缺省 5 分钟）自动调 /login/refresh（无感续期）
 * - 响应拦截：401 跳登录、403 提示无权限、业务错误码统一提示
 */
import axios from 'axios';
import { ElMessage } from 'element-plus'
import auth from './auth.js'

const env = import.meta.env

/** 续期阈值：登录时后端下发（nway.jwt.refresh-threshold-millis），通过 auth.getRefreshThreshold() 读取 */

/** 防止并发续期 */
let isRefreshing = false
/** 续期期间的待重试队列 */
let pendingRequests = []

const http = axios.create({
    timeout: 1000 * 60
})

/**
 * 解析 JWT payload（无需密钥，Base64 解码）。
 */
function parseJwtExp(token) {
    try {
        const payload = token.split('.')[1]
        const decoded = JSON.parse(atob(payload))
        return decoded.exp ? decoded.exp * 1000 : 0
    } catch (e) {
        return 0
    }
}

/**
 * 检查并执行 Token 续期。
 * 剩余不足阈值（后端下发，缺省 5 分钟）且未过期时，自动调用 /login/refresh。
 */
async function refreshTokenIfNeeded() {
    const token = auth.getToken()
    if (!token) return token

    const expMs = parseJwtExp(token)
    if (!expMs) return token

    const remaining = expMs - Date.now()
    // 已过期或剩余超过阈值（后端下发，缺省 5 分钟），不续期
    if (remaining <= 0 || remaining >= auth.getRefreshThreshold()) {
        return token
    }

    // 已有续期在进行中，等待结果
    if (isRefreshing) {
        return new Promise((resolve) => {
            pendingRequests.push((newToken) => resolve(newToken))
        })
    }

    isRefreshing = true
    try {
        const resp = await axios.post(env.VITE_API_URL + '/login/refresh', null, {
            headers: { Authorization: token }
        })
        if (resp.data?.code === 200 && resp.data?.data) {
            const newToken = resp.data.data
            auth.saveToken(newToken)
            // 通知等待中的请求
            pendingRequests.forEach((cb) => cb(newToken))
            pendingRequests = []
            return newToken
        }
        // 续期失败（Token 已过期等），跳登录
        redirectToLogin()
        return null
    } catch (e) {
        redirectToLogin()
        return null
    } finally {
        isRefreshing = false
    }
}

let isRedirecting = false
function redirectToLogin() {
    if (isRedirecting) return
    isRedirecting = true
    ElMessage({ message: '登录失效，请重新登录', type: 'error' })
    setTimeout(() => {
        // 被动退出（超时/失效）保留已打开页签：重新登录后恢复上次打开的页面；
        // 显式登出走 toolbar 的 clearUser()，仍会清空页签并回到首页
        auth.clearUser({ keepTabs: true })
        location.href = '/login'
        isRedirecting = false
    }, 1000)
}

// 请求拦截：注入 Token + 自动续期
http.interceptors.request.use(async (config) => {
    config.url = env.VITE_API_URL + config.url
    // /login/refresh 自身不触发续期检查
    if (!config.url.includes('/login/refresh')) {
        const token = await refreshTokenIfNeeded()
        if (token) {
            config.headers['Authorization'] = token
        }
    } else {
        config.headers['Authorization'] = auth.getToken() || ''
    }
    return config
})

// 响应拦截：401/403 区分 + 业务错误码处理
http.interceptors.response.use((response) => {
    const data = response.data
    // 业务错误码处理（HTTP 200 但 code 非 200）
    if (data && typeof data.code === 'number' && data.code !== 200) {
        ElMessage({ message: data.msg || '操作失败', type: 'error' })
        return Promise.reject(new Error(data.msg || '操作失败'))
    }
    return response
}, (error) => {
    if (error.response) {
        const status = error.response.status
        if (status === 401) {
            // Token 失效或未登录，跳登录页
            redirectToLogin()
        } else if (status === 403) {
            // 有登录但无权限，提示而非跳登录
            ElMessage({ message: '您无此操作权限，请联系管理员分配相应角色', type: 'warning' })
        } else if (status === 500) {
            const msg = error.response.data?.msg || '服务器错误'
            ElMessage({ message: msg, type: 'error' })
        } else if (error.response.data?.msg) {
            ElMessage({ message: error.response.data.msg, type: 'error' })
        }
    } else {
        ElMessage({ message: error.message || '网络异常', type: 'error' })
    }
    return Promise.reject(error)
})

export default http
