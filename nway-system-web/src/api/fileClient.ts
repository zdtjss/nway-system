/**
 * 文件中心 SDK 实例（平台内部登录态）。
 * 平台页面与外部系统使用同一份 @nway/file 实现。
 */
import { createFileClient } from '@nway/file'
import { getToken } from '@/utils/auth'

const env = import.meta.env

export const fileClient = createFileClient({
  baseUrl: env.VITE_API_URL as string,
  getToken: () => getToken() || ''
})
