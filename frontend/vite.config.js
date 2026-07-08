import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5180,
    strictPort: true // 如果端口被占用则直接退出，而不是尝试下一个端口
  }
})
