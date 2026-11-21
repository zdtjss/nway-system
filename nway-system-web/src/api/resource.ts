/**
 * 资源目录 API（资源登记台 / 覆盖报表）
 */
import { get, post, postForm, type Result } from './request'

export interface ResourceActionVo {
  action: string
  label: string
  riskLevel: string
}

export interface ResourceVo {
  id: string
  appCode: string
  resourceCode: string
  resourceType: string
  name: string
  description?: string
  tenantScope: string
  ownerTenantCode: string
  status: string
  actions: ResourceActionVo[]
}

export interface CoverageItem {
  pattern: string
  appCode: string
  resourceCode: string | null
  actions: string[]
  source: 'ANNOTATION' | 'LEGACY' | 'PUBLIC' | 'UNCOVERED'
  registered: boolean
  missingActions: string[]
}

export interface CoverageReport {
  totalEndpoints: number
  annotated: number
  legacy: number
  uncovered: number
  publicApi: number
  annotationCoverage: number
  registeredResources: number
  missingResources: number
  items: CoverageItem[]
}

/** 资源列表（含动作） */
export function listResources(appCode?: string, tenantScope?: string): Promise<Result<ResourceVo[]>> {
  return get('/resource/list', { appCode, tenantScope })
}

/** 保存资源并替换动作 */
export function saveResource(payload: Record<string, any>): Promise<Result<string>> {
  return post('/resource/save', payload)
}

/** 资源下线 */
export function deprecateResource(id: string): Promise<Result<void>> {
  return postForm('/resource/deprecate', { id })
}

/** 接口覆盖报表 */
export function resourceCoverage(): Promise<Result<CoverageReport>> {
  return get('/resource/coverage')
}
