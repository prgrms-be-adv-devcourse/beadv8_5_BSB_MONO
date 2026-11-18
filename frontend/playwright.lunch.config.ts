import { defineConfig, devices } from '@playwright/test'

// 점심 로컬 E2E (scripts/e2e-lunch.sh run). 노트북에 띄운 실제 백엔드와 프론트를 쓴다.
// 프론트는 스크립트가 API_MOCKING=off로 띄워 두고 E2E_BASE_URL로 넘긴다. 서버를 직접 띄우지 않는다.
if (!process.env.E2E_BASE_URL)
  throw new Error('E2E_BASE_URL이 없어요. scripts/e2e-lunch.sh run <이름>으로 실행해 주세요.')

// E2E_DEADLINE(유닉스 초)이 지나면 남은 시나리오를 건너뛰고 끝낸다. 그때까지의 결과는 JSON에 남는다
const deadline = Number(process.env.E2E_DEADLINE ?? 0)
const globalTimeout = deadline ? Math.max(60_000, deadline * 1000 - Date.now()) : 0

export default defineConfig({
  testDir: './e2e/lunch',
  // 같은 시드 계정·DB를 함께 쓰므로 한 번에 하나씩 돈다
  workers: 1,
  fullyParallel: false,
  retries: 0,
  timeout: 5 * 60_000,
  expect: { timeout: 15_000 },
  globalTimeout,
  reporter: [['list'], ['json', { outputFile: process.env.E2E_JSON ?? 'e2e-lunch.json' }]],
  use: {
    baseURL: process.env.E2E_BASE_URL,
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    // 개발 서버는 화면을 처음 열 때 컴파일하느라 느리다
    navigationTimeout: 60_000,
    actionTimeout: 20_000,
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
})
