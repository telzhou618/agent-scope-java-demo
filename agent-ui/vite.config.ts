import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/agent': {
        target: 'http://localhost:8082',
        changeOrigin: true,
      },
      '/auth': {
        target: 'http://localhost:8082',
        changeOrigin: true,
      },
      // 精确匹配后端 API,避免 /users 等前端页面路由被误代理到后端
      '^/user/(feedback|manage)': {
        target: 'http://localhost:8082',
        changeOrigin: true,
      },
    },
  },
})
