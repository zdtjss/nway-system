/**
 * 角色管理 API
 */
import { get, post, postForm, type Result, type PageVo } from './request'
import type { TenantDto } from './tenant'
import type { Menu } from './menu'
import type { ResourceVo } from './resource'

export interface RoleDto {
  id?: string
  roleCode: string
  roleName: string
  appCode?: string
  appName?: string
  description?: string
  tenantCode?: string
  tenantName?: string
  modifiedTime?: string
}

export function queryPage(params: { roleName?: string; appCode?: string; current?: number; size?: number }): Promise<Result<PageVo<RoleDto>>> {
  return get('/role/queryPage', params)
}

export function queryById(id: string): Promise<Result<RoleDto>> {
  return get('/role/queryById', { id })
}

export function save(role: RoleDto): Promise<Result<void>> {
  return post('/role/save', role)
}

export function deleteById(id: string): Promise<Result<void>> {
  return postForm('/role/deleteById', { id })
}

/** 查询可分配给指定用户的角色 */
export function queryAvailableForUser(employeeId: string): Promise<Result<RoleDto[]>> {
  return get('/role/queryAvailableForUser', { employeeId })
}

/** 角色归属租户/复制目标租户下拉（后端按当前用户可管理范围收敛，仅需 role 权限） */
export function queryTenantOptions(): Promise<Result<TenantDto[]>> {
  return get('/role/tenantOptions')
}

/** 策略配置：授权候选菜单（仅需 role 权限，后端校验目标租户在可读范围内） */
export function queryGrantableMenus(appCode: string, tenantCode?: string): Promise<Result<Menu[]>> {
  return get('/role/grantableMenus', { appCode, tenantCode })
}

/** 策略配置：资源动作目录（仅需 role 权限） */
export function queryGrantableResources(appCode: string): Promise<Result<ResourceVo[]>> {
  return get('/role/grantableResources', { appCode })
}

/** 复制角色到目标租户（新租户开通支撑：模板角色连同其策略克隆到目标租户） */
export function copyToTenant(sourceRoleId: string, targetTenantCode: string): Promise<Result<RoleDto>> {
  return postForm('/role/copyToTenant', { sourceRoleId, targetTenantCode })
}
