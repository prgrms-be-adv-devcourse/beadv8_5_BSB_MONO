import { defineConfig, devices } from '@playwright/test'

// 운영 스모크 (e2e/smoke). 배포된 서버를 읽기만 한다. 서버를 직접 띄우지 않는다.
export default defineConfig({
  testDir: './e2e/smoke',
  // 로그인은 nginx가 IP당 초당 1건으로 막으므로 한 번에 하나씩 돈다
  workers: 1,
  retries: 0,
  timeout: 60_000,
  expect: { timeout: 15_000 },
  reporter: 'list',
  use: {
    baseURL: process.env.SMOKE_BASE_URL ?? 'https://crewrun.duckdns.org',
    screenshot: 'only-on-failure',
    // 트레이스에는 로그인 입력값이 남으므로 끈다
    trace: 'off',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
})
