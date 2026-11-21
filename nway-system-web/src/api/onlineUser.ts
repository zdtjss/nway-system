/**
 * 在线用户管理 API
 */
import { get, postForm, type Result } from './request'

export interface OnlineUser {
  employeeId: string
  realName: string
  tenantName: string
  ip: string
  userAgent: string
  loginTime: string
  expireTime: string
}

/** 查询在线用户列表（可选关键字搜索工号/姓名） */
export function list(keyword?: string): Promise<Result<OnlineUser[]>> {
  return get('/onlineUser/list', { keyword })
}

/** 强制下线 */
export function forceLogout(employeeId: string): Promise<Result<void>> {
  return postForm('/onlineUser/forceLogout', { employeeId })
}
