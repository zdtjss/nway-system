import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import basicSsl from '@vitejs/plugin-basic-ssl'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import IconsResolver from 'unplugin-icons/resolver'

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => ({
  esbuild: {
    // 生产构建自动移除 console.* 和 debugger
    drop: mode === 'production' ? ['console', 'debugger'] : []
  },
  server: {
    host: "0.0.0.0",
    allowedHosts: ['localhost', 'pf5081z1.shdm.com'],
    port: 5173,
    // 预热入口与全部本地页面：Vite 在启动时即完成这些模块的转换与依赖发现，
    // 避免运行中（首次打开某个菜单页面时）才发现新依赖而重新预构建，进而强制整页刷新
    warmup: {
      clientFiles: ['./src/main.ts', './src/views/**/*.vue']
    },
    proxy: {
      // 带选项写法：http://localhost:5173/api/bar -> http://jsonplaceholder.typicode.com/bar
      '/api': {
        target: 'http://localhost:9081',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      }
    }
  },
  plugins: [vue({
    template: {
      compilerOptions: {
        isCustomElement: tag => /^micro-app/.test(tag)
      }
    }
  })/* , basicSsl() */,
  AutoImport({
    resolvers: [ElementPlusResolver(), IconsResolver({
      prefix: 'Icon',
    }),],
  }),
  Components({
    resolvers: [ElementPlusResolver(),
    IconsResolver({
      enabledCollections: ['ep'],
    }),],
  }),],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
      // 平台自身与外部系统引用同一份 SDK 源码，避免维护两份实现
      '@nway/file/vue': fileURLToPath(new URL('./packages/file/src/vue/index.ts', import.meta.url)),
      '@nway/file': fileURLToPath(new URL('./packages/file/src/index.ts', import.meta.url))
    }
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          const normalizedId = id.replace(/\\/g, '/')
          if (normalizedId.includes('node_modules')) {
            // 将 Vue 相关库打包成一个 chunk
            if (normalizedId.includes('/vue/') || normalizedId.includes('/vue-router/') || normalizedId.includes('/pinia/')) {
              return 'vue'
            }
            // 将 UI 框架相关库打包成一个 chunk
            if (normalizedId.includes('/element-plus/') || normalizedId.includes('/@element-plus/icons-vue/')) {
              return 'elementPlus'
            }
            // 将微前端相关库打包成一个 chunk
            if (normalizedId.includes('/@micro-zoe/micro-app/') || normalizedId.includes('/wujie-vue3/')) {
              return 'microFrontend'
            }
            // 将工具和认证相关库打包成一个 chunk
            if (normalizedId.includes('/axios/') || normalizedId.includes('/camelcase/') || normalizedId.includes('/pinyin-pro/') || normalizedId.includes('/@passwordless-id/webauthn/')) {
              return 'utils'
            }
          }
        }
      }
    },
    chunkSizeWarningLimit: 1000 // 提高警告阈值到 1000 kB
  }
}))