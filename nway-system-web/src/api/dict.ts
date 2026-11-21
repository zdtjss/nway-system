/**
 * 字典管理 API
 */
import { get, post, type Result } from './request'
import { postForm } from './request'

export interface DictTypeDto {
  id?: string
  dictCode: string
  dictName: string
  status?: string
  [key: string]: any
}

export interface DictItemDto {
  id?: string
  dictTypeId: string
  itemValue?: Record<string, any>
  status?: string
  sort?: number
  [key: string]: any
}

export function queryType(keyword?: string, tenantCode?: string): Promise<Result<DictTypeDto[]>> {
  return get('/dict/queryType', { keyword, tenantCode })
}

export function queryItems(dictTypeId: string): Promise<Result<DictItemDto[]>> {
  return get('/dict/queryItem', { dictTypeId })
}

/** 按字典编码查询启用字典项（通用，供业务页面使用） */
export function queryByCode(dictCode: string): Promise<Result<Record<string, any>[]>> {
  return get('/dict/queryByCode', { dictCode })
}

export function saveType(dto: DictTypeDto): Promise<Result<void>> {
  return post('/dict/saveType', dto)
}

export function saveItem(dto: DictItemDto): Promise<Result<void>> {
  return post('/dict/saveItem', dto)
}

export function deleteType(id: string): Promise<Result<void>> {
  return postForm('/dict/deleteType', { id })
}

export function deleteItem(id: string): Promise<Result<void>> {
  return postForm('/dict/deleteItem', { id })
}
