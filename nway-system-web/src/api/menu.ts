/**
 * 菜单管理 API
 */
import { get, post, postForm, type Result } from './request'

export interface Menu {
  id?: string
  name: string
  parentId?: string
  url?: string
  icon?: string
  appCode: string
  resourceCode?: string
  type: string
  action?: string
  renderType?: string
  sequence?: number
  isEnabled?: boolean
  source?: string
  tenantCode?: string
  children?: Menu[]
}

/** 按应用查询菜单（管理后台树构建；tenantCode=目标角色租户，仅返回该租户 + public 共享租户的菜单） */
export function queryByApp(appCode: string, tenantCode?: string): Promise<Result<Menu[]>> {
  return get('/menu/queryByApp', { appCode, tenantCode })
}

/** 管理后台菜单列表（带筛选） */
export function queryForAdmin(params: { appCode?: string; parentId?: string; name?: string }): Promise<Result<Menu[]>> {
  return get('/menu/queryForAdmin', params)
}

/** 获取单条菜单 */
export function getById(id: string): Promise<Result<Menu>> {
  return get('/menu/getById', { id })
}

/** 保存菜单 */
export function save(menu: Menu): Promise<Result<void>> {
  return post('/menu/save', menu)
}

/** 删除菜单 */
export function deleteById(id: string): Promise<Result<void>> {
  return postForm('/menu/delete', { id })
}
