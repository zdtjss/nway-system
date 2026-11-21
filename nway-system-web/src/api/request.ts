/**
 * 统一请求封装。
 * 基于 httpProxy 的 axios 实例，提供类型化的 API 调用方法。
 */
import http from '@/js/httpProxy.js'

/** 通用响应结构 */
export interface Result<T = any> {
  code: number
  msg: string
  data: T
}

/** 分页响应结构 */
export interface PageVo<T = any> {
  data: T[]
  total: number
  current: number
  size: number
}

/** GET 请求 */
export async function get<T = any>(url: string, params?: Record<string, any>): Promise<Result<T>> {
  const { data } = await http.get(url, { params })
  return data
}

/** POST 请求（JSON body） */
export async function post<T = any>(url: string, body?: any, config?: Record<string, any>): Promise<Result<T>> {
  const { data } = await http.post(url, body, config)
  return data
}

/** POST 请求（form params） */
export async function postForm<T = any>(url: string, params: Record<string, any>): Promise<Result<T>> {
  const { data } = await http.post(url, null, { params })
  return data
}

export default http
