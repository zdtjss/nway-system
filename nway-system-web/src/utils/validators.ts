/**
 * 通用表单校验规则（Element Plus FormItem rules 兼容）。
 *
 * 用法：
 *   import { createRequired, createAsyncValidator } from '@/utils/validators'
 *
 *   <el-form :rules="rules" :model="form">
 *     <el-form-item label="名称" prop="name" :rules="createRequired('请输入名称')">
 *       <el-input v-model="form.name" />
 *     </el-form-item>
 *   </el-form>
 */

export interface FormRule {
  required?: boolean
  pattern?: RegExp
  message?: string
  trigger?: string
  type?: string
  min?: number
  max?: number
  validator?: (rule: any, value: any, callback: (error?: Error) => void) => void | Promise<void>
}

/**
 * 必填规则
 */
export function createRequired(message = '此项必填', trigger = 'blur'): FormRule {
  return { required: true, message, trigger }
}

/**
 * 编码规则：字母+数字+下划线，2-50字符
 */
export function createCodeRule(message = '编码只能包含字母、数字、下划线，2-50字符', trigger = 'blur'): FormRule {
  return {
    pattern: /^[a-zA-Z][a-zA-Z0-9_]{1,49}$/,
    message,
    trigger
  }
}

/**
 * 手机号规则
 */
export function createPhoneRule(message = '请输入正确的手机号', trigger = 'blur'): FormRule {
  return {
    pattern: /^1[3-9]\d{9}$/,
    message,
    trigger
  }
}

/**
 * 邮箱规则
 */
export function createEmailRule(message = '请输入正确的邮箱地址', trigger = 'blur'): FormRule {
  return {
    pattern: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
    message,
    trigger
  }
}

/**
 * URL 规则
 */
export function createUrlRule(message = '请输入正确的 URL', trigger = 'blur'): FormRule {
  return {
    pattern: /^https?:\/\/.+/,
    message,
    trigger
  }
}

/**
 * 数字范围规则
 */
export function createNumberRange(min = 0, max = 999999, message: string | null = null, trigger = 'blur'): FormRule {
  return {
    type: 'number',
    min,
    max,
    message: message || `数值范围 ${min} - ${max}`,
    trigger
  }
}

/**
 * 字符串长度规则
 */
export function createLengthRule(min = 1, max = 100, message: string | null = null, trigger = 'blur'): FormRule {
  return {
    min,
    max,
    message: message || `长度 ${min} - ${max} 字符`,
    trigger
  }
}

/**
 * 创建异步校验器（如编码唯一性检查）。
 *
 * @param asyncCheck 异步检查函数，参数为值，返回 Promise<boolean>，true 表示可用
 * @param message 校验失败提示
 */
export function createAsyncValidator(
  asyncCheck: (value: any) => Promise<boolean>,
  message = '校验失败'
): FormRule {
  return {
    validator: async (_rule: any, value: any, callback: (error?: Error) => void) => {
      if (!value) {
        callback()
        return
      }
      try {
        const ok = await asyncCheck(value)
        if (ok) {
          callback()
        } else {
          callback(new Error(message))
        }
      } catch {
        callback(new Error(message))
      }
    },
    trigger: 'blur'
  }
}

/**
 * 通用规则集合，可直接传给 <el-form :rules="rules">
 */
export const commonRules = {
  required: { required: true, message: '此项必填', trigger: 'blur' },
  code: createCodeRule(),
  phone: createPhoneRule(),
  email: createEmailRule(),
  url: createUrlRule(),
}

export default {
  createRequired,
  createCodeRule,
  createPhoneRule,
  createEmailRule,
  createUrlRule,
  createNumberRange,
  createLengthRule,
  createAsyncValidator,
  commonRules
}
