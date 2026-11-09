import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [
    vue(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    // host: '0.0.0.0',
    // port: 8081,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8000',
        changeOrigin: true,
        // rewrite: (path) => path.replace(/^\/api/, '')
      },
      '/images': {
        target: 'http://127.0.0.1:8000/',
        changeOrigin: true,
        // rewrite: (path) => path.replace(/^\/api/, '')
      }
    }
  },
  // 默认输出到同级目录下后端项目的 docker/dist，方便 docker 打包部署；
  // 可通过环境变量 NAV_DIST 指定其它目录，例如：NAV_DIST=dist yarn build
  build: {
    outDir: process.env.NAV_DIST || fileURLToPath(new URL('../Navigator/docker/dist', import.meta.url)),
    assetsDir: 'assets',
    emptyOutDir: true,
    chunkSizeWarningLimit: 1500,
  }
})
