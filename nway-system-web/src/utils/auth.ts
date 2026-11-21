/**
 * 前端认证工具（唯一实现）。
 *
 * 管理 Token、用户信息、按钮权限标识（powerKeys）与页签快照的存储与读取。
 * 其他模块一律从本文件导入；src/js/auth.js 仅做兼容转发，避免两套实现漂移。
 *
 * 存储约定（sessionStorage，关标签页即失效）：
 *  - nway-token             宿主登录 Token（裸 token，不含 Bearer 前缀）
 *  - currentUser            登录用户信息（后端 LoginUser 原样保存）
 *  - avatar                 头像缓存（登录页展示用，可由 currentUser.avatar 派生）
 *  - nway-power-keys        按钮权限标识集合
 *  - nway-refresh-threshold 后端登录时下发的续期阈值（毫秒）
 *  - nway_open_tabs         已打开页签快照（与登录态同生命周期）
 *  - ds-token               子应用（workflow / app-oa）鉴权 Token
 *  - ds-work-tenant         子应用工作租户（app-oa）
 */
import type { LoginUser } from '@/types'

const TOKEN_KEY = 'nway-token'
const USER_KEY = 'currentUser'
const AVATAR_KEY = 'avatar'
const POWER_KEYS_KEY = 'nway-power-keys'
const REFRESH_THRESHOLD_KEY = 'nway-refresh-threshold'
const OPEN_TABS_KEY = 'nway_open_tabs'
/** 子应用鉴权键：与 workflow / app-oa 的 utils/hostAuth.js 约定一致 */
const SUB_APP_TOKEN_KEY = 'ds-token'
const SUB_APP_TENANT_KEY = 'ds-work-tenant'
// 后端未下发时的回退阈值，与 nway.jwt.refresh-threshold-millis 默认值保持一致
const DEFAULT_REFRESH_THRESHOLD_MS = 5 * 60 * 1000

let _roleList: string[] | null = null
let _powerKeys: Set<string> | null = null

// 鉴权信息变更订阅（用于向微前端子应用实时同步 token / 用户信息）
const tokenChangeListeners = new Set<() => void>()
// 登出订阅（显式登出 / 登录失效）：用于通知子应用清理自己的登录态
const logoutListeners = new Set<() => void>()

function notifyTokenChange(): void {
  tokenChangeListeners.forEach((cb) => cb())
}

function notifyLogout(): void {
  logoutListeners.forEach((cb) => cb())
}

export function saveToken(token: string): void {
  if (sessionStorage.getItem(TOKEN_KEY) === token) return
  sessionStorage.setItem(TOKEN_KEY, token)
  notifyTokenChange()
}

export function getToken(): string | null {
  return sessionStorage.getItem(TOKEN_KEY)
}

/**
 * 获取 Token 续期阈值（毫秒）。
 * 优先取后端登录时下发的配置；未下发时回退到与后端默认值一致的 5 分钟。
 */
export function getRefreshThreshold(): number {
  const raw = sessionStorage.getItem(REFRESH_THRESHOLD_KEY)
  const val = raw ? parseInt(raw, 10) : NaN
  return Number.isFinite(val) && val > 0 ? val : DEFAULT_REFRESH_THRESHOLD_MS
}

export function saveUser(user: LoginUser): void {
  sessionStorage.setItem(AVATAR_KEY, user.avatar || '')
  sessionStorage.setItem(USER_KEY, JSON.stringify(user))
  // 保存后端下发的续期阈值（毫秒），登录后前端按该阈值触发无感续期
  if (user.refreshThresholdMillis) {
    sessionStorage.setItem(REFRESH_THRESHOLD_KEY, String(user.refreshThresholdMillis))
  }
  // 保存角色列表，供 hasRole 使用
  if (user.roleList) {
    _roleList = user.roleList
  }
}

export function getUser(): LoginUser | null {
  try {
    return JSON.parse(sessionStorage.getItem(USER_KEY) || 'null')
  } catch {
    return null
  }
}

export function getAvatar(): string {
  return sessionStorage.getItem(AVATAR_KEY) || ''
}

export function getTenantName(): string {
  return getUser()?.tenantName || ''
}

export function getTenantCode(): string {
  return getUser()?.tenantCode || ''
}

export function isSuperAdmin(): boolean {
  return getUser()?.superAdmin === true
}

/**
 * 清除登录态。
 *
 * @param options.keepTabs 是否保留已打开页签快照（menuStore 的 nway_open_tabs）。
 *   登录超时被强制退回登录页属于被动退出，传 true 保留页签，重新登录（同一用户）后可恢复
 *   已打开页面；显式登出（默认 false）仍清空页签，重新登录回到首页。
 *   F5 刷新（不清登录态）不受影响，仍能恢复页签。
 */
export function clearUser(options: { keepTabs?: boolean } = {}): void {
  sessionStorage.removeItem(AVATAR_KEY)
  sessionStorage.removeItem(USER_KEY)
  sessionStorage.removeItem(TOKEN_KEY)
  sessionStorage.removeItem(POWER_KEYS_KEY)
  sessionStorage.removeItem(REFRESH_THRESHOLD_KEY)
  // 子应用与宿主同源共享 sessionStorage：一并清理，避免显式登出后仍残留可用 Token
  sessionStorage.removeItem(SUB_APP_TOKEN_KEY)
  sessionStorage.removeItem(SUB_APP_TENANT_KEY)
  if (!options.keepTabs) {
    sessionStorage.removeItem(OPEN_TABS_KEY)
  }
  _roleList = null
  _powerKeys = null
  // 先通知登出（子应用据此清空自己的登录态），再通知 token 变更
  notifyLogout()
  notifyTokenChange()
}

/**
 * 订阅鉴权信息变更（token 续期 / 登录），返回取消订阅函数。
 */
export function onTokenChange(cb: () => void): () => void {
  tokenChangeListeners.add(cb)
  return () => tokenChangeListeners.delete(cb)
}

/**
 * 订阅登出（显式登出 / 登录失效），返回取消订阅函数。
 */
export function onLogout(cb: () => void): () => void {
  logoutListeners.add(cb)
  return () => logoutListeners.delete(cb)
}

/**
 * 保存按钮权限标识集合（从菜单树 BTN 节点收集）。
 */
export function savePowerKeys(powerKeys: string[]): void {
  const set = new Set(powerKeys || [])
  sessionStorage.setItem(POWER_KEYS_KEY, JSON.stringify([...set]))
  _powerKeys = set
}

/**
 * 获取按钮权限标识集合。
 */
export function getPowerKeys(): Set<string> {
  if (_powerKeys) return _powerKeys
  try {
    const arr = JSON.parse(sessionStorage.getItem(POWER_KEYS_KEY) || '[]')
    _powerKeys = new Set(arr || [])
    return _powerKeys
  } catch {
    _powerKeys = new Set()
    return _powerKeys
  }
}

/**
 * 检查是否拥有指定按钮权限。
 * 超管直接通过；否则检查 powerKeys 集合。
 */
export function hasPermission(permission: string): boolean {
  if (!permission) return false
  if (isSuperAdmin()) return true
  return getPowerKeys().has(permission)
}

/**
 * 检查是否拥有指定角色。
 */
export function hasRole(role: string): boolean {
  if (!role) return false
  if (isSuperAdmin()) return true
  const roles = _roleList || getUser()?.roleList || []
  return roles.includes(role)
}

/**
 * 默认导出：保留 `import auth from '@/js/auth.js'` 的对象式调用。
 */
export default {
  saveToken,
  getToken,
  getRefreshThreshold,
  saveUser,
  getUser,
  getAvatar,
  getTenantName,
  getTenantCode,
  isSuperAdmin,
  clearUser,
  onTokenChange,
  onLogout,
  savePowerKeys,
  getPowerKeys,
  hasPermission,
  hasRole,
}
