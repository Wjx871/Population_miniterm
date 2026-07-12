import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    // 端口由启动脚本通过 VITE_DEV_PORT 传入，默认 5180
    port: Number(process.env.VITE_DEV_PORT) || 5180,
    // strictPort: true —— 端口被占用时直接退出，不自动切换到其他端口
    // 确保启动脚本显示的地址与实际监听端口始终一致
    strictPort: true,
    proxy: {
      '/api': {
        // 代理目标由启动脚本通过 VITE_BACKEND_TARGET 传入，默认 8080
        target: process.env.VITE_BACKEND_TARGET || 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  }
})
