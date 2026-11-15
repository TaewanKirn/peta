import path from 'node:path'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'
import { debugApiPlugin } from './server/debugApi.ts'
import { openApiPlugin } from './server/openApi.ts'
import { tripApiPlugin } from './server/tripApi.ts'

export default defineConfig({
  plugins: [
    react(),
    tripApiPlugin(path.resolve(import.meta.dirname, '.data/trip.json')),
    // 카카오·관광공사 키는 환경변수 또는 차량 앱의 local.properties에서 읽는다.
    openApiPlugin(path.resolve(import.meta.dirname, '../car/local.properties')),
    // 플로우 점검 페이지 /admin 과 차량 앱 화면 이동 명령 (dev 서버 전용)
    debugApiPlugin(),
  ],
  // 에뮬레이터의 10.0.2.2는 PC의 127.0.0.1로 연결되므로 IPv4로 명시한다.
  // 같은 Wi-Fi의 실제 폰에서 열 때만 `npm run dev -- --host`를 쓴다.
  server: { host: '127.0.0.1', port: 5173, strictPort: true },
  preview: { host: '127.0.0.1', port: 5173, strictPort: true },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    include: ['src/**/*.test.{ts,tsx}', 'server/**/*.test.ts'],
    css: false,
  },
})
