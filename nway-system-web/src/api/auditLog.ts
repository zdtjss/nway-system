/**
 * 审计日志 API
 */
import { post, type Result } from './request'

export interface AuditLog {
  id: string
  operatorId: string
  operatorName: string
  operationType: string
  apiPath: string
  httpMethod: string
  requestParams: string
  responseStatus: number
  durationMs: number
  ip: string
  userAgent: string
  tenantCode?: string
  tenantName?: string
  createTime: string
}

export function queryPage(params: {
  operatorId?: string
  operationType?: string
  apiPath?: string
  startTime?: string
  endTime?: string
  page: number
  pageSize: number
}): Promise<Result<{ data: AuditLog[]; total: number }>> {
  return post('/auditLog/queryPage', params)
}
