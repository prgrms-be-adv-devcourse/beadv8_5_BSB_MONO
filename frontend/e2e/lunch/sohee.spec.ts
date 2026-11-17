import { expect, test } from '@playwright/test'

import { balanceOf, uiLogin, USERS } from './support'

test.describe('소희', { tag: '@sohee' }, () => {
  test('35 예치금 충전과 돌려받기 - 충전', async ({ page }) => {
    await uiLogin(page, USERS.user1)
    await page.goto('/me/deposit/charge')
    await page.getByRole('button', { name: '+1만' }).click()
    await page.getByRole('button', { name: /원 충전하기$/ }).click()

    // 백엔드가 충전 주문을 만들면 토스 테스트 결제창(외부 화면)이 열린다
    const toss = page.locator('iframe[src*="tosspayments"], iframe[title*="토스"]')
    await expect
      .poll(async () => page.url().includes('tosspayments') || (await toss.count()) > 0, {
        timeout: 30_000,
      })
      .toBe(true)

    // 결제창 안의 카드 인증은 카드사·휴대폰 앱 단계가 있어 자동으로 끝까지 누를 수 없다.
    // 여기서 멈추고, 결제 완료와 잔액 반영은 사람이 결제창을 직접 눌러 확인한다
    test.info().annotations.push({
      type: 'skip-step',
      description:
        '토스 테스트 결제창의 카드 인증 단계는 자동화하지 않음. 결제창까지 열리는 것만 확인',
    })
  })

  test('35 예치금 충전과 돌려받기 - 돌려받기 화면', async ({ page }) => {
    const balance = balanceOf(USERS.user1)
    await uiLogin(page, USERS.user1)
    await page.goto('/me/deposit')
    await expect(page.getByText(`${balance.toLocaleString('ko-KR')}`).first()).toBeVisible({
      timeout: 20_000,
    })
    await page.goto('/me/deposit/refund')
    // 충전 기록이 없으면 돌려받을 금액이 없다. 화면이 오류 없이 열리고 계좌 입력이나 빈 상태가 보이면 통과로 본다
    await expect(
      page
        .getByLabel('계좌번호')
        .or(page.getByText(/돌려받을|충전 내역이 없/))
        .first(),
    ).toBeVisible({
      timeout: 20_000,
    })
  })
})
