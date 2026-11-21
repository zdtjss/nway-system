/**
 * 开放平台管理 API
 */
import { get, post, type Result } from './request'
import { postForm } from './request'

export interface OpenApp {
  id?: string
  appCode: string
  appName: string
  status?: string
  grantedScopes?: string
  dataScopeType?: string
  dataScopeDeptIds?: string
  rateLimit?: number
  dailyLimit?: number
  allowedIps?: string
  ownerEmployeeId?: string
  expireTime?: string
  [key: string]: any
}

export interface OpenWebhook {
  id?: string
  appId: string
  eventType: string
  callbackUrl: string
  secret?: string
  status?: string
  retryCount?: number
  [key: string]: any
}

export function queryAllApps(): Promise<Result<OpenApp[]>> {
  return get('/openApp/queryAll')
}

export function getAppById(id: string): Promise<Result<OpenApp>> {
  return get('/openApp/getById', { id })
}

export function createApp(app: OpenApp): Promise<Result<{ appId: string; appSecret: string }>> {
  return post('/openApp/create', app)
}

export function updateApp(app: OpenApp): Promise<Result<void>> {
  return post('/openApp/update', app)
}

export function resetSecret(appId: string): Promise<Result<{ appSecret: string }>> {
  return postForm('/openApp/resetSecret', { appId })
}

export function grantScopes(appId: string, scopes: string[]): Promise<Result<void>> {
  return post('/openApp/grantScopes', scopes, { params: { appId } })
}

export function deleteApp(appId: string): Promise<Result<void>> {
  return postForm('/openApp/delete', { appId })
}

export function queryWebhooksByApp(appId: string): Promise<Result<OpenWebhook[]>> {
  return get('/openWebhook/queryByApp', { appId })
}

export function createWebhook(webhook: OpenWebhook): Promise<Result<void>> {
  return post('/openWebhook/create', webhook)
}

export function updateWebhook(webhook: OpenWebhook): Promise<Result<void>> {
  return post('/openWebhook/update', webhook)
}

export function deleteWebhook(id: string): Promise<Result<void>> {
  return postForm('/openWebhook/delete', { id })
}

export function testWebhook(webhookId: string): Promise<Result<{ statusCode: number }>> {
  return postForm('/openWebhook/test', { webhookId })
}

export function queryApiLogsByApp(appCode: string): Promise<Result<any[]>> {
  return get('/openApiLog/queryByApp', { appCode })
}

export function queryAllApiLogs(): Promise<Result<any[]>> {
  return get('/openApiLog/queryAll')
}

export function statsByApp(): Promise<Result<any[]>> {
  return get('/openApiLog/statsByApp')
}

export function statsByApi(): Promise<Result<any[]>> {
  return get('/openApiLog/statsByApi')
}
