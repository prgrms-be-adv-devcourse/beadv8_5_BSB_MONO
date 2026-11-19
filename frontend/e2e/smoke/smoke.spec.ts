import { test as base, expect, type Page } from '@playwright/test'

// 운영 스모크. 읽기만 한다: 화면 열기 · 화면 이동 · 로그인 말고는 아무것도 누르지 않는다.

// 화면 안 오류 안내 문구 (RouteErrorScreen · StatusPages · 각 화면 Alert · AI 브리핑 실패 설명)
const ERROR_TEXT =
  /불러오지 못했어요|찾을 수 없는 페이지예요|인터넷에 연결되어 있지 않아요|로그인이 필요합니다/

// 앱을 열 때 AuthSessionRestorer가 재발급을 보낸다. 로그인 전에는 401이 정상이다.
const ALLOWED_401 = '/api/v1/auth/reissue'

const test = base.extend<{ problems: string[] }>({
  problems: [
    async ({ page }, use) => {
      const problems: string[] = []
      page.on('pageerror', (error) =>
        problems.push(`페이지 예외 (${new URL(page.url()).pathname}): ${error.message}`),
      )
      page.on('console', (message) => {
        // 응답 실패는 아래 response에서 상태 코드로 따진다
        if (message.type() === 'error' && !message.text().startsWith('Failed to load resource'))
          problems.push(`콘솔 에러 (${new URL(page.url()).pathname}): ${message.text()}`)
      })
      page.on('response', (response) => {
        const { pathname } = new URL(response.url())
        const status = response.status()
        if (status >= 500 || (status === 401 && pathname !== ALLOWED_401))
          problems.push(`${status} ${response.request().method()} ${pathname}`)
      })
      // 스모크 방문이 운영 GA4 통계에 섞이지 않게 막는다
      await page.route(/google-analytics\.com|googletagmanager\.com/, (route) => route.abort())
      await use(problems)
      expect(problems, '콘솔 에러 · 5xx · 예상 밖 401').toEqual([])
    },
    { auto: true },
  ],
})

async function expectNoErrorScreen(page: Page) {
  // 화면이 데이터를 다 받을 때까지 기다린다. 주기적으로 부르는 화면은 끝나지 않으니 10초에서 멈춘다
  await page.waitForLoadState('networkidle', { timeout: 10_000 }).catch(() => {})
  await expect(page.getByText(ERROR_TEXT)).toHaveCount(0)
}

async function open(page: Page, path: string) {
  const response = await page.goto(path)
  expect(response?.status(), `${path} 응답`).toBeLessThan(400)
  await expectNoErrorScreen(page)
}

// 로그인은 nginx가 IP당 초당 1건으로 막는다. 계정마다 한 번만 하고, 429면 2초 쉬고 다시 누른다
async function login(page: Page, email: string, password: string, next: string) {
  await page.goto(`/login?next=${encodeURIComponent(next)}`)
  await page.getByLabel('이메일').fill(email)
  await page.getByLabel('비밀번호', { exact: true }).fill(password)
  for (let attempt = 1; ; attempt++) {
    await page.waitForTimeout(1_100)
    const [response] = await Promise.all([
      page.waitForResponse(
        (r) => r.request().method() === 'POST' && r.url().endsWith('/api/v1/auth/login'),
      ),
      page.getByRole('button', { name: '로그인', exact: true }).click(),
    ])
    if (response.status() === 429 && attempt < 3) {
      await page.waitForTimeout(2_000)
      continue
    }
    expect(response.status(), '로그인 응답').toBeLessThan(300)
    break
  }
  await page.waitForURL((url) => url.pathname === next)
}

function account(role: 'BUYER' | 'SELLER' | 'ADMIN') {
  const email = process.env[`SMOKE_${role}_EMAIL`]
  const password = process.env[`SMOKE_${role}_PASSWORD`]
  test.skip(!email || !password, `SMOKE_${role}_EMAIL · SMOKE_${role}_PASSWORD가 없어 건너뜀`)
  return { email: email!, password: password! }
}

test.describe('공개 화면 @smoke', () => {
  test('홈', async ({ page }) => {
    await open(page, '/')
  })

  test('대회 목록 → 첫 대회 상세', async ({ page }) => {
    await open(page, '/races')
    const firstRace = page.locator('a[href^="/races/"]').first()
    test.skip((await firstRace.count()) === 0, '대회 목록이 비어 상세를 볼 수 없음')
    await firstRace.click()
    await page.waitForURL(/\/races\/\d+$/)
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
    await expectNoErrorScreen(page)
  })

  test('로그인 화면', async ({ page }) => {
    await open(page, '/login')
    await expect(page.getByLabel('이메일')).toBeVisible()
    await expect(page.getByLabel('비밀번호', { exact: true })).toBeVisible()
  })
})

test.describe('로그인 화면들 @smoke', () => {
  test('구매자: 내 참가권 · 예치금', async ({ page }) => {
    const { email, password } = account('BUYER')
    await login(page, email, password, '/tickets')
    await expect(page.getByText('내 참가권').first()).toBeVisible()
    await expectNoErrorScreen(page)
    await open(page, '/me/deposit')
    await expect(page.getByText('보유 예치금')).toBeVisible()
  })

  test('판매자: 판매자 센터 첫 화면', async ({ page }) => {
    const { email, password } = account('SELLER')
    await login(page, email, password, '/seller')
    await expect(page.getByText('대시보드').first()).toBeVisible()
    await expectNoErrorScreen(page)
  })

  test('관리자: 관리자 홈 · AI 브리핑', async ({ page }) => {
    const { email, password } = account('ADMIN')
    await login(page, email, password, '/admin')
    await expect(page.getByText('관리자 홈').first()).toBeVisible()
    await expectNoErrorScreen(page)
  })
})
