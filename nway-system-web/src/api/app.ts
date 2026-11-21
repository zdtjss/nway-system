/**
 * 应用管理 API（替代原 business.ts）
 */
import { get, post, postForm, type Result } from './request'

export interface App {
  id?: string
  appCode: string
  appName: string
  host?: string
  apiBaseUrl?: string
  status?: string
  sort?: number
  tenantCode?: string
  tenantName?: string
  /** 前端标记：不属于目标角色租户（存量策略引用的应用），仅用于提示"不会生效" */
  outOfScope?: boolean
}

/** 查询全部应用 */
export function queryAll(): Promise<Result<App[]>> {
  return get('/app/queryAll')
}

/** 查询启用的应用（下拉选择用；tenantCode=目标角色租户，仅返回该租户 + public 共享租户的应用） */
export function queryEnabled(tenantCode?: string): Promise<Result<App[]>> {
  return get('/app/queryByStatus', { status: 'using', tenantCode })
}

/** 按 appCode 获取应用 */
export function getByCode(appCode: string): Promise<Result<App>> {
  return get('/app/getByCode', { appCode })
}

/** 保存应用（新增/编辑） */
export function save(app: App): Promise<Result<void>> {
  return post('/app/save', app)
}

/** 删除应用 */
export function deleteById(id: string): Promise<Result<void>> {
  return postForm('/app/deleteById', { id })
}
