import { defineConfig, devices } from '@playwright/test'

// E2E_BASE_URL을 주면 이미 떠 있는 서버(dev 서버 등)를 테스트한다.
// 없으면 가짜 응답 모드(API_MOCKING=on)로 로컬 개발 서버를 직접 띄운다.
const PORT = 3177
const baseURL = process.env.E2E_BASE_URL ?? `http://localhost:${PORT}`

export default defineConfig({
  testDir: './e2e',
  // 점심 로컬 E2E(lunch)와 운영 스모크(smoke)는 각자 설정 파일로만 돌린다
  testIgnore: ['lunch/**', 'smoke/**'],
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: 'list',
  use: { baseURL, trace: 'retain-on-failure' },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: process.env.E2E_BASE_URL
    ? undefined
    : {
        command: `pnpm dev --port ${PORT}`,
        url: baseURL,
        env: { API_MOCKING: 'on' },
        reuseExistingServer: !process.env.CI,
        timeout: 120_000,
      },
})
