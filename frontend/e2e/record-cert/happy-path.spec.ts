import { expect, test } from '@playwright/test'

import {
  apiLogin,
  buyTicket,
  eventually,
  freshBuyer,
  pullSaleToNow,
  registerRace,
  uiLogin,
  USERS,
  waitStatus,
} from '../lunch/support'
import { addCertRule, CERT, RECORD_CERTS, SELLER, type MySubmission } from './support'

test('시나리오 1 · 기록증 정상 흐름: 화면 업로드 → 브라우저 OCR → 제출 → 서버 확인 → 승인', async ({
  page,
}) => {
  const seller = await apiLogin(USERS.seller)
  const race = await registerRace(seller, `[E2E] 기록증 ${Date.now()}`)
  addCertRule(race.courseId)
  pullSaleToNow(race.raceId)
  await waitStatus(race.raceId, 'ON_SALE')

  const email = freshBuyer('91')
  const buyer = await apiLogin(email)
  const { orderId, itemId } = await buyTicket(buyer, race)

  await uiLogin(page, email)
  // 읽기 화면은 OCR 실행 파일까지 받아야 load가 끝나 미니 PC에서는 1분을 넘긴다. 화면 뼈대만 기다린다
  const logs: string[] = []
  page.on('console', (m) => ['error', 'warning'].includes(m.type()) && logs.push(m.text()))
  page.on('pageerror', (e) => logs.push(String(e)))
  page.on('requestfailed', (r) =>
    logs.push(`실패 ${r.method()} ${r.url().split('?')[0]} ${r.failure()?.errorText}`),
  )
  page.on('response', (r) => r.status() >= 400 && logs.push(`${r.status()} ${r.url()}`))
  // 원인 추적용: OCR worker가 보내는 답의 종류 · 오류 문구 · 걸린 시간을 콘솔에 남긴다
  // shortcut: file-service의 dev 전용 CORS(DevCorsConfig)는 8085만 허용해, 프론트 프록시가 넘긴 Origin(3177)을 403으로 막는다.
  // prod에는 이 설정이 없다. dev 설정이 프론트 주소를 허용하게 되면 지운다
  await page.route('**/api/v1/file/**', async (route) => {
    const headers = { ...route.request().headers() }
    delete headers.origin
    // 브라우저는 Origin을 다시 붙이므로 요청을 테스트 쪽(Node)에서 대신 보낸다
    await route.fulfill({ response: await route.fetch({ headers }) })
  })
  await page.addInitScript(() => {
    const Real = window.Worker
    window.Worker = class extends Real {
      constructor(url: string | URL, options?: WorkerOptions) {
        super(url, options)
        const born = performance.now()
        this.addEventListener('message', (e) => {
          const d = e.data as { type?: string; message?: string; readMs?: number }
          console.warn(
            `[ocr] ${d?.type} ${d?.message ?? d?.readMs ?? ''} +${Math.round(performance.now() - born)}ms`,
          )
        })
        this.addEventListener('error', (e) => console.warn(`[ocr] worker error ${e.message}`))
      }
    }
  })
  await page.goto(`/tickets/${orderId}-${itemId}/record-cert`, { waitUntil: 'domcontentloaded' })

  // 브라우저 OCR이 칸을 채운다. 읽기는 10초를 넘기면 직접 입력으로 넘어간다(READ_TIMEOUT_MS).
  // 처음에는 worker 컴파일 · 모델 내려받기로 느려, 직접 입력이 뜨면 「다른 사진」으로 같은 사진을 다시 읽힌다
  const netTime = page.getByLabel(/넷타임/)
  for (let attempt = 1; ; attempt++) {
    await page.getByLabel('기록증 사진 고르기').setInputFiles(CERT, { timeout: 3 * 60_000 })
    await page.getByRole('button', { name: '사진 전체' }).click()
    // 10초는 누른 때부터 세는데 worker가 모델을 다 받기 전이면 그 시간이 먼저 쓰인다. 시도마다 새 worker가 준비될 때까지 기다린다
    await expect
      .poll(() => logs.filter((l) => l.startsWith('[ocr] ready')).length, { timeout: 2 * 60_000 })
      .toBeGreaterThanOrEqual(attempt)
    const clicked = Date.now()
    await page.getByRole('button', { name: '이 부분 읽기' }).click()
    await expect(page.getByRole('heading', { name: '기록증 확인' })).toBeVisible({
      timeout: 4 * 60_000,
    })
    logs.push(
      `[읽기 ${attempt}] ${Date.now() - clicked}ms, 격리=${await page.evaluate(() => crossOriginIsolated)}, cpu=${await page.evaluate(() => navigator.hardwareConcurrency)}`,
    )
    if (await netTime.inputValue()) break
    test.info().annotations.push({ type: `읽기 ${attempt}회 실패`, description: logs.join(' | ') })
    expect(attempt, `OCR이 3번 안에 읽지 못함: ${logs.join(' | ')}`).toBeLessThan(3)
    await page.getByRole('button', { name: '다른 사진' }).click()
  }
  expect(await netTime.inputValue(), 'OCR이 넷타임을 읽음').toMatch(/^0?1:00:01$/)
  await page.getByRole('button', { name: '10K', exact: true }).click()
  // 막 뜬 file-service는 첫 업로드 확인이 20초쯤 걸려 게이트웨이가 504로 끊는다. 참가자처럼 다시 누른다(최대 3번)
  const sent = logs.length
  const done = page.getByRole('heading', { name: '기록증을 확인하고 있어요' })
  const failed = page.getByText(/사진을 올리지 못했어요|제출하지 못했어요/)
  for (let attempt = 1; ; attempt++) {
    await page.getByRole('button', { name: '제출하기' }).click()
    await expect(done.or(failed).first()).toBeVisible({ timeout: 90_000 })
    if (await done.isVisible()) break
    expect(attempt, `제출이 3번 안에 끝나지 않음: ${logs.slice(sent).join(' | ')}`).toBeLessThan(3)
    await expect(page.getByRole('button', { name: '제출하기' })).toBeEnabled()
  }

  // 서버 확인(ai-service, Kafka)이 끝나면 확인 중을 벗어난다
  const mine = async () => {
    const res = await buyer.call<MySubmission[]>('GET', RECORD_CERTS)
    return res.data?.find((s) => s.orderItemId === itemId)
  }
  const checked = (await eventually(async () => {
    const s = await mine()
    return s && s.status !== 'CHECKING' ? s : null
  }, 2 * 60_000))!
  test.info().annotations.push({ type: '서버 확인 결과', description: JSON.stringify(checked) })
  expect(['AUTO_APPROVED', 'NEEDS_REVIEW']).toContain(checked.status)

  // 심사 필요면 판매자가 승인한다
  if (checked.status === 'NEEDS_REVIEW') {
    const list = await seller.call<unknown[]>('GET', `${SELLER}/races/${race.raceId}/record-certs`)
    expect(list.status, JSON.stringify(list.raw)).toBe(200)
    const approve = await seller.call(
      'POST',
      `${SELLER}/record-certs/${checked.submissionId}/approve`,
    )
    expect(approve.status, JSON.stringify(approve.raw)).toBe(200)
    expect((await mine())?.status).toBe('APPROVED')
  }
})
