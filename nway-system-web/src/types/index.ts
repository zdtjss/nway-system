/**
 * 公共类型定义
 */

/** 通用响应结构 */
export interface Result<T = any> {
  code: number
  msg: string
  data: T
}

/** 分页响应结构 */
export interface PageVo<T = any> {
  data: T[]
  total: number
  current: number
  size: number
}

/** 登录用户信息 */
export interface LoginUser {
  employeeId: string
  realName: string
  avatar?: string
  tenantCode: string
  tenantName?: string
  superAdmin: boolean
  roleList?: string[]
  token: string
  /** 后端下发的 Token 续期阈值（毫秒），前端据此触发无感续期 */
  refreshThresholdMillis?: number
}

/** 菜单项 */
export interface MenuItem {
  id: string
  name: string
  url?: string
  icon?: string
  parentId?: string
  renderType?: 'microApp' | 'microApp-iframe' | 'wujie' | 'wujie-iframe' | 'iFrame' | 'native' | ''
  appCode?: string
  resourceCode?: string
  action?: string
  type?: string
  /** 菜单归属租户：打开子应用时作为工作租户下发 */
  tenantCode?: string
  children?: MenuItem[]
}

/** 微前端应用信息 */
export interface MicroApp {
  name: string
  host: string
  defaultPage: string
  url: string
  renderType: string
  /** 菜单归属租户：打开子应用时作为工作租户下发 */
  tenantCode?: string
}
