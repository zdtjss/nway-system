/**
 * 用户管理 API
 */
import { get, post, postForm, type Result, type PageVo } from './request'

export interface UserDto {
  id?: string
  employeeId: string
  realName: string
  status?: string
  deptId?: string
  deptName?: string
  tenantCode?: string
  [key: string]: any
}

export interface UserQuery {
  employeeId?: string
  realName?: string
  status?: string
  deptId?: string
  current?: number
  size?: number
  [key: string]: any
}

export function getById(id: string): Promise<Result<UserDto>> {
  return get('/user/getById', { id })
}

export function queryPage(params: UserQuery): Promise<Result<PageVo<UserDto>>> {
  return get('/user/queryPage', params)
}

export function save(user: UserDto): Promise<Result<void>> {
  return post('/user/save', user)
}

export function disable(employeeId: string): Promise<Result<void>> {
  return postForm('/user/disable', { employeeId })
}

export function enable(employeeId: string): Promise<Result<void>> {
  return postForm('/user/enable', { employeeId })
}
