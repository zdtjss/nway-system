/**
 * 授权决策与变更 API（授权运营台）
 */
import { get, post, type Result } from './request'

export interface DataPredicate {
  kind: 'ALL' | 'SELF' | 'DEPT' | 'DEPT_TREE' | 'DEPT_LIST' | 'NONE'
  values: string[]
}

export interface Decision {
  allow: boolean
  reason: string
  decisionId: string
  dataPredicates: DataPredicate[]
  matchedGrantIds: string[]
  version: number
}

export interface EffectiveResource {
  appCode: string
  resourceCode: string
  actions: string[]
  dataPredicates: DataPredicate[]
}

export interface Grant {
  id?: string
  subjectType: string
  subjectId: string
  appCode: string
  resourceCode: string
  actions: string
  effect: 'ALLOW' | 'DENY'
  dataScope: string
  scopeDeptIds?: string | null
  effectiveFrom?: string | null
  expireAt?: string | null
  status?: string
  version?: number
  tenantCode?: string
}

export interface GrantChange {
  id: string
  actorId: string
  actorTenant: string
  reason: string
  targetType: string
  targetId: string
  beforeJson: string
  afterJson: string
  authzVersion: number
  requestId: string
  createTime: string
}

/** 决策诊断 */
export function decide(employeeId: string, appCode: string, resourceCode: string, action: string): Promise<Result<Decision>> {
  return get('/authz/decision', { employeeId, appCode, resourceCode, action })
}

/** 用户有效权限 */
export function effective(employeeId: string): Promise<Result<EffectiveResource[]>> {
  return get('/authz/effective', { employeeId })
}

/** 主体授权（含 DENY/时间窗） */
export function grants(subjectType: string, subjectId: string): Promise<Result<Grant[]>> {
  return get('/authz/grants', { subjectType, subjectId })
}

/** 保存单条授权 */
export function saveGrant(grant: Grant, reason?: string): Promise<Result<string>> {
  return post('/authz/grants/save', grant, { params: { reason } })
}

/** 删除授权 */
export function deleteGrant(id: string, reason?: string): Promise<Result<void>> {
  return post('/authz/grants/delete', null, { params: { id, reason } })
}

/** 变更留痕 */
export function changes(targetType: string, targetId: string, limit = 50): Promise<Result<GrantChange[]>> {
  return get('/authz/changes', { targetType, targetId, limit })
}

/** 回滚某次变更 */
export function rollbackChange(changeId: string, reason?: string): Promise<Result<void>> {
  return post('/authz/changes/rollback', null, { params: { changeId, reason } })
}

/** 影响面预演 */
export function preview(payload: { subjectType: string; subjectId: string; grants: Grant[] }): Promise<Result<PreviewResult>> {
  return post('/authz/preview', payload)
}

/** 批量授权 */
export function batchGrant(payload: { subjectType: string; subjectIds: string[]; grant: Grant; reason?: string }): Promise<Result<number>> {
  return post('/authz/grants/batch', payload)
}

export interface PreviewResult {
  affectedUsers: number
  sampleUsers: string[]
  diffs: { appCode: string; resourceCode: string; added: string[]; removed: string[]; denied: string[] }[]
}

export interface GrantTemplate {
  id?: string
  templateCode: string
  name: string
  description?: string
  appCode: string
  itemsJson: string
  status?: string
}

/** 模板列表 */
export function templates(): Promise<Result<GrantTemplate[]>> {
  return get('/authz/templates')
}

/** 保存模板 */
export function saveTemplate(template: GrantTemplate): Promise<Result<string>> {
  return post('/authz/templates/save', template)
}

/** 应用模板到租户 */
export function applyTemplate(templateId: string, tenantCode: string, reason?: string): Promise<Result<any>> {
  return post('/authz/templates/apply', null, { params: { templateId, tenantCode, reason } })
}
