/**
 * 兼容入口：认证逻辑唯一实现在 src/utils/auth.ts。
 *
 * 保留此文件是为了兼容既有 `import auth from '@/js/auth.js'` 写法；
 * 新代码请直接从 '@/utils/auth' 导入，不要再在此处新增实现。
 */
export { default } from '@/utils/auth'
