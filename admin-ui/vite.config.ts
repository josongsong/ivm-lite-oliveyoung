import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  // 분리 배포: base는 항상 /
  base: '/',
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  build: {
    // 독립 배포: dist 폴더로 출력
    outDir: 'dist',
    emptyOutDir: true,
    // 메모리 최적화
    chunkSizeWarningLimit: 1000,
    rollupOptions: {
      output: {
        manualChunks: {
          'react-vendor': ['react', 'react-dom', 'react-router-dom'],
          'query-vendor': ['@tanstack/react-query'],
          'ui-vendor': ['framer-motion', 'lucide-react'],
        },
      },
    },
  },
  server: {
    port: 3000,
    host: true, // 외부 접근 허용
    hmr: {
      port: 3000,
      host: 'localhost',
    },
    watch: {
      // 파일 변경 감지 설정
      usePolling: false,
      interval: 100,
      // 메모리 최적화: 큰 파일 무시
      ignored: ['**/node_modules/**', '**/.git/**', '**/dist/**'],
    },
    // API 프록시 비활성화 - 프론트엔드에서 직접 API 호출
    // proxy: {
    //   '/api': {
    //     target: 'http://localhost:8081',
    //     changeOrigin: true,
    //     timeout: 30000,
    //   },
    // },
    // 메모리 최적화
    fs: {
      strict: false,
      allow: ['..'],
    },
  },
  // 메모리 최적화
  optimizeDeps: {
    include: ['react', 'react-dom', 'react-router-dom', '@tanstack/react-query'],
    exclude: [],
  },
  // 로그 레벨 조정
  logLevel: 'warn',
})
