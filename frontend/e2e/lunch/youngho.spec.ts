import { expect, test } from '@playwright/test'

import {
  apiLogin,
  balanceOf,
  buyTicket,
  DUMMY_RACES,
  eventually,
  onSaleCourse,
  pullSaleToNow,
  registerRace,
  sql,
  sqlOne,
  topUp,
  uiLogin,
  USERS,
  waitStatus,
} from './support'

// 정산 배치는 개발용 실행 API로 바로 돌린다. 게이트웨이를 거치지 않고 정산 서비스(8082)를 직접 부른다
const PAYOUT = process.env.E2E_PAYOUT_URL ?? 'http://localhost:8082'

test.describe('영호', { tag: '@youngho' }, () => {
  test('41 판매자 정산 내역 보기', async ({ page }) => {
    const course = await onSaleCourse(DUMMY_RACES[2])
    topUp(USERS.user3)
    const buyer = await apiLogin(USERS.user3)
    await buyTicket(buyer, course.courseId)

    // 대회가 어제 열린 것으로 만든다. 상품(대회)과 주문(주문 상품) 양쪽 날짜를 함께 옮긴다
    sql(`update race set race_date = now() - interval 1 day, refund_deadline = curdate() - interval 2 day,
      application_close_at = now() - interval 3 day where race_id = ${course.raceId}`)
    sql(
      `update order_order_item set race_date = now() - interval 1 day where race_id = ${course.raceId}`,
    )

    const res = await fetch(`${PAYOUT}/api/v1/payout/dev/batches/create`, { method: 'POST' })
    expect(res.status, await res.clone().text()).toBe(200)
    await eventually(
      () =>
        Number(sqlOne(`select count(*) from payout_payout_item where race_id = ${course.raceId}`)) >
        0,
    )

    await uiLogin(page, USERS.seller)
    await page.goto('/seller/settlements')
    await expect(page.getByText(course.title).first()).toBeVisible({ timeout: 20_000 })
  })

  test('46 판매자 대회 취소 요청', async ({ page }) => {
    const seller = await apiLogin(USERS.seller)
    const race = await registerRace(seller, `E2E 취소 ${Date.now() % 100_000}`)

    await uiLogin(page, USERS.seller)
    await page.goto(`/seller/races/${race.raceId}/change`)
    await page.getByText('대회 취소', { exact: true }).click()
    await page.getByRole('button', { name: '대회 취소하기' }).click()
    await page.getByRole('dialog').getByRole('button', { name: '대회 취소하기' }).click()
    await eventually(() =>
      /CANCEL/.test(sqlOne(`select sale_status from race where race_id = ${race.raceId}`) ?? ''),
    )

    await page.goto('/seller/races')
    await expect(page.getByText(race.title).first()).toBeVisible({ timeout: 20_000 })
  })

  test('47 관리자 대회 취소 요청 검토', async ({ page }) => {
    // 판매자가 취소한 대회가 관리자 대회 관리 화면에 취소 상태로 보이는지 확인한다
    const seller = await apiLogin(USERS.seller)
    const race = await registerRace(seller, `E2E 관리자확인 ${Date.now() % 100_000}`)
    const canceled = await seller.call('POST', `/api/v1/product/races/${race.raceId}/cancel`)
    expect(canceled.status, JSON.stringify(canceled.raw)).toBe(200)

    await uiLogin(page, USERS.admin)
    await page.goto('/admin/races')
    const row = page.getByRole('row').filter({ hasText: race.title })
    await expect(row).toBeVisible({ timeout: 20_000 })
    await expect(row).toContainText('취소')
  })

  test('37 대회 취소 때 자동 환불', async () => {
    test.setTimeout(10 * 60_000)
    const seller = await apiLogin(USERS.seller)
    const race = await registerRace(seller, `E2E 자동환불 ${Date.now() % 100_000}`)
    pullSaleToNow(race.raceId)
    await waitStatus(race.raceId, 'ON_SALE')

    topUp(USERS.user1)
    const buyer = await apiLogin(USERS.user1)
    const bought = await buyTicket(buyer, race.courseId)
    const afterPay = balanceOf(USERS.user1)

    const canceled = await seller.call('POST', `/api/v1/product/races/${race.raceId}/cancel`)
    expect(canceled.status, JSON.stringify(canceled.raw)).toBe(200)

    // 대회 취소 이벤트 → 주문 취소 → 예치금 환불까지 15초 안에 끝나야 한다
    await expect.poll(() => balanceOf(USERS.user1), { timeout: 15_000 }).toBe(afterPay + 40_000)
    expect(sqlOne(`select status from order_order_item where id = ${bought.itemId}`)).toMatch(
      /CANCEL/,
    )
  })
})
