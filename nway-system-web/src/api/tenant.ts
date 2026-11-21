/**
 * 租户管理 API
 */
import { get, post, type Result, type PageVo } from './request'
import { postForm } from './request'

export interface TenantDto {
  id?: string
  tenantCode: string
  tenantName: string
  status?: string
  expireDate?: string
  remark?: string
  [key: string]: any
}

export function queryPage(params: any): Promise<Result<PageVo<TenantDto>>> {
  return get('/tenant/queryPage', params)
}

export function queryAll(): Promise<Result<TenantDto[]>> {
  return get('/tenant/queryAll')
}

export function save(tenant: TenantDto): Promise<Result<void>> {
  return post('/tenant/save', tenant)
}

export function deleteById(id: string): Promise<Result<void>> {
  return postForm('/tenant/deleteById', { id })
}
