import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// 业主端 H5：开发端口 3001，/api 代理到网关 8080
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    host: '0.0.0.0',
    port: 3001,
    proxy: {
      '/api': {
        // 用 127.0.0.1 而非 localhost：Node 18+ 会把 localhost 优先解析成 ::1，偶发代理 500/超时。
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
        // 手机用热点/局域网 IP 访问时，浏览器给 POST 带 Origin: http://192.168.137.1:3001，
        // 而网关 globalcors 白名单（SP_CORS_ORIGINS）只放了 localhost/127.0.0.1，
        // 网关遂返回 403 且响应体为空 —— 前端 safeMessage 无 message 可读，只能弹「请求失败」。
        // 手机↔vite 是同源，vite→网关是服务端转发，这一跳本不该带 Origin，摘掉即可。
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin'))
        }
      }
    }
  }
})
