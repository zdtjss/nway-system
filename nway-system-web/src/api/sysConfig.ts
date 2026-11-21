/**
 * 系统配置管理 API
 */
import { get, post, postForm, type Result } from './request'

export interface SysConfig {
  id?: string
  configKey: string
  configValue: string
  configName: string
  configGroup: string
  remark?: string
  status?: string
}

export function save(config: SysConfig): Promise<Result<void>> {
  return post('/sysConfig/save', config)
}

export function deleteById(id: string): Promise<Result<void>> {
  return postForm('/sysConfig/delete', { id })
}

export function queryPage(params: { configKey?: string; configName?: string; configGroup?: string; status?: string; page: number; pageSize: number }): Promise<Result<{ data: SysConfig[]; total: number }>> {
  return get('/sysConfig/queryPage', params)
}

export function getByKey(configKey: string): Promise<Result<SysConfig>> {
  return get('/sysConfig/getByKey', { configKey })
}
