import { defineConfig, devices } from '@playwright/test'

// 기록증 심사 E2E (BUK-274~277). 미니 PC에 띄운 실제 백엔드 · 프론트(API_MOCKING=off)를 쓴다. 서버를 직접 띄우지 않는다.
if (!process.env.E2E_BASE_URL)
  throw new Error('E2E_BASE_URL이 없어요. 띄워 둔 프론트 주소를 넘겨 주세요.')

export default defineConfig({
  testDir: './e2e/record-cert',
  // 같은 DB · 시드 판매자를 함께 쓰므로 한 번에 하나씩 돈다
  workers: 1,
  fullyParallel: false,
  retries: 0,
  // 판매 상태 배치(5분 주기)와 브라우저 OCR 모델 내려받기를 기다린다
  timeout: 12 * 60_000,
  expect: { timeout: 15_000 },
  reporter: [['list'], ['json', { outputFile: process.env.E2E_JSON ?? 'e2e-record-cert.json' }]],
  use: {
    baseURL: process.env.E2E_BASE_URL,
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    navigationTimeout: 60_000,
    actionTimeout: 20_000,
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
})
