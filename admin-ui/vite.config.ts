import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  build: {
    // Build output to Kotlin resources for serving
    outDir: '../src/main/resources/static/admin',
    emptyOutDir: true,
  },
  server: {
    port: 3000,
    // Proxy API requests to Kotlin backend during development
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
})
