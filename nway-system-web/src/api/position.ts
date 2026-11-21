/**
 * 岗位管理 API
 */
import { get, post, postForm, type Result, type PageVo } from './request'

export interface PositionDto {
  id?: string
  positionCode: string
  positionName: string
  deptId?: string
  deptName?: string
  positionLevel?: string
  status?: string
  remark?: string
  createTime?: string
  tenantCode?: string
  tenantName?: string
  [key: string]: any
}

export interface UserPositionDto {
  id?: string
  employeeId: string
  positionId: string
  deptId?: string | null
  deptName?: string
  isPrimary?: boolean
  realName?: string
  [key: string]: any
}

export interface PositionQuery {
  status?: string
  positionCode?: string
  positionName?: string
  current?: number
  size?: number
}

export function queryPage(params: PositionQuery): Promise<Result<PageVo<PositionDto>>> {
  return get('/position/queryPage', params)
}

export function queryAll(): Promise<Result<PositionDto[]>> {
  return get('/position/queryAll')
}

export function queryByStatus(status: string): Promise<Result<PositionDto[]>> {
  return get('/position/queryByStatus', { status })
}

export function getById(id: string): Promise<Result<PositionDto>> {
  return get('/position/getById', { id })
}

export function save(position: PositionDto): Promise<Result<void>> {
  return post('/position/save', position)
}

export function deleteById(id: string): Promise<Result<void>> {
  return postForm('/position/deleteById', { id })
}

export function userPositions(employeeId: string): Promise<Result<UserPositionDto[]>> {
  return get('/position/userPositions', { employeeId })
}

export function saveUserPositions(employeeId: string, positions: UserPositionDto[]): Promise<Result<void>> {
  return post('/position/saveUserPositions', positions, { params: { employeeId } })
}

export function usersByPosition(positionId: string): Promise<Result<UserPositionDto[]>> {
  return get('/position/usersByPosition', { positionId })
}

export function saveUserPosition(position: UserPositionDto): Promise<Result<void>> {
  return post('/position/saveUserPosition', position)
}

export function removeUserPosition(employeeId: string, positionId: string): Promise<Result<void>> {
  return postForm('/position/removeUserPosition', { employeeId, positionId })
}
