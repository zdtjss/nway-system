/**
 * 部门管理 API
 */
import { get, post, type Result } from './request'
import { postForm } from './request'

export interface DeptTreeVO {
  id: string
  deptCode: string
  deptName: string
  parentId: string
  status: string
  sort: number
  level: number
  tenantCode?: string
  tenantName?: string
  children?: DeptTreeVO[]
  [key: string]: any
}

export interface DeptDto {
  id?: string
  deptName: string
  parentId?: string
  sort?: number
  status?: string
  [key: string]: any
}

export function getTree(): Promise<Result<DeptTreeVO[]>> {
  return get('/dept/tree')
}

export function save(dept: DeptDto): Promise<Result<void>> {
  return post('/dept/save', dept)
}

export function deleteById(id: string): Promise<Result<void>> {
  return postForm('/dept/deleteById', { id })
}

export function queryUsersByDept(deptId: string): Promise<Result<any[]>> {
  return get('/dept/users', { deptId })
}
