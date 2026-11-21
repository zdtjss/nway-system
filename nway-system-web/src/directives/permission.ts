/**
 * 按钮级权限指令 v-permission
 *
 * 用法：
 *   <el-button v-permission="'user:save'">保存</el-button>
 *   <el-button v-permission="['user:save', 'user:disable']">操作</el-button>
 *
 * 无权限的元素不渲染（从 DOM 移除），减少界面噪音。
 */
import type { Directive, DirectiveBinding } from 'vue'
import auth from '@/js/auth.js'

function checkPermission(value: string | string[]): boolean {
  if (!value) return false
  if (auth.isSuperAdmin()) return true
  if (Array.isArray(value)) {
    return value.some((perm) => auth.hasPermission(perm))
  }
  return auth.hasPermission(value)
}

export const permissionDirective: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding) {
    if (!checkPermission(binding.value)) {
      el.parentNode?.removeChild(el)
    }
  },
}

export default permissionDirective
