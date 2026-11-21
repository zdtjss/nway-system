/**
 * 统一权限策略 API（替代原 dataPermission.ts）
 */
import { get, post, postForm, type Result } from './request'

export interface PermissionPolicy {
  id?: string
  subjectType: string
  subjectId: string
  appCode: string
  resourceCode: string
  /** JSON 数组字符串，null=全部操作 */
  actions?: string | null
  dataScope: string
  scopeDeptIds?: string | null
  scopeCondition?: string | null
  priority?: number
  tenantCode?: string
}

export interface MergedPolicy {
  appCode: string
  resourceCode: string
  /** null=全部操作 */
  actions: string[] | null
  dataScope: string
  deptIds?: string[]
  deptCodes?: string[]
  sourceDescriptions?: string[]
}

/** 查询角色的全部策略 */
export function queryByRole(roleId: string): Promise<Result<PermissionPolicy[]>> {
  return get('/policy/queryByRole', { roleId })
}

/** 查询主体的全部策略 */
export function queryBySubject(subjectType: string, subjectId: string): Promise<Result<PermissionPolicy[]>> {
  return get('/policy/queryBySubject', { subjectType, subjectId })
}

/** 保存单条策略 */
export function save(policy: PermissionPolicy): Promise<Result<void>> {
  return post('/policy/save', policy)
}

/** 批量保存角色策略（替换式） */
export function saveRolePolicies(roleId: string, policies: PermissionPolicy[]): Promise<Result<void>> {
  return post('/policy/saveRolePolicies', { roleId, policies })
}

/** 删除策略 */
export function deletePolicy(id: string): Promise<Result<void>> {
  return postForm('/policy/delete', { id })
}

/** 获取用户合并后的策略（权限总览用） */
export function getMergedPolicies(employeeId: string): Promise<Result<MergedPolicy[]>> {
  return get('/policy/merged', { employeeId })
}

/** 获取用户在指定应用的合并策略 */
export function getMergedPoliciesByApp(employeeId: string, appCode: string): Promise<Result<MergedPolicy[]>> {
  return get('/policy/merged', { employeeId, appCode })
}
