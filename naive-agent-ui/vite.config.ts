import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import UnoCSS from 'unocss/vite'

export default defineConfig({
  plugins: [vue(), UnoCSS()],
  server: {
    port: 5174,
    proxy: {
      '/agent': {
        target: 'http://localhost:8082',
        changeOrigin: true,
      },
      '/auth': {
        target: 'http://localhost:8082',
        changeOrigin: true,
      },
      // 精确匹配后端 API，避免 /users 等前端页面路由被误代理到后端
      '^/user/(feedback|manage|profile|password)': {
        target: 'http://localhost:8082',
        changeOrigin: true,
      },
    },
  },
})
