import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  snapshotPathTemplate: '{testDir}/__snapshots__/{arg}{ext}',
  use: {
    baseURL: 'http://127.0.0.1:5173',
    // 브라우저를 따로 받지 않고 Windows 기본 Edge를 쓴다.
    channel: 'msedge',
    viewport: { width: 402, height: 886 },
    deviceScaleFactor: 1,
  },
  webServer: {
    command: 'npm run dev',
    url: 'http://127.0.0.1:5173',
    reuseExistingServer: true,
  },
})
