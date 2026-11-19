import { expect, test } from '@playwright/test'

import { sqlOne, uiLogin, USERS } from './support'

test.describe('지훈', { tag: '@jihoon' }, () => {
  test('02 이메일 로그인', async ({ page }) => {
    await uiLogin(page, USERS.user1)
    // 새로 고쳐도 Refresh Token 쿠키로 로그인이 이어지는지 마이에서 확인한다
    await page.goto('/me')
    await expect(page.getByText('사용자1').first()).toBeVisible({ timeout: 20_000 })
    await expect(page).not.toHaveURL(/\/login/)
  })

  test('02 이메일 로그인 - 비밀번호가 틀리면 로그인되지 않는다', async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel('이메일').fill(USERS.user1)
    await page.getByLabel('비밀번호', { exact: true }).fill('wrong-password-1234')
    await page.getByRole('button', { name: '로그인', exact: true }).click()
    await expect(page).toHaveURL(/\/login/)
    await expect(
      page
        .getByRole('alert')
        .or(page.getByText(/일치하지|올바르지|확인해/))
        .first(),
    ).toBeVisible({
      timeout: 15_000,
    })
  })

  test('51 크루 만들기와 초대', async ({ page, browser }) => {
    const crewName = `E2E${Date.now() % 100_000}`
    await uiLogin(page, USERS.user1)
    await page.goto('/crews/new')
    await page.getByLabel('크루 이름').fill(crewName)
    await page.getByRole('button', { name: '크루 만들기' }).click()
    await expect(page).toHaveURL(/\/crews\/\d+$/, { timeout: 20_000 })
    const crewId = Number(page.url().match(/\/crews\/(\d+)/)![1])
    await expect(page.getByText(crewName).first()).toBeVisible()

    await page.goto(`/crews/${crewId}/invites`)
    await page.getByRole('button', { name: '초대 링크 만들기' }).click()
    const link = await page
      .getByText(/\/invite\//)
      .first()
      .textContent({ timeout: 15_000 })
    const token = link!.split('/invite/')[1].trim()

    // 초대받은 사람(user2)은 다른 브라우저 세션에서 링크를 연다
    const other = await browser.newContext()
    const invitee = await other.newPage()
    await uiLogin(invitee, USERS.user2)
    await invitee.goto(`/invite/${token}`)
    await invitee.getByRole('button', { name: `${crewName}에 가입하기` }).click()
    await expect(invitee).toHaveURL(new RegExp(`/crews/${crewId}`), { timeout: 20_000 })
    await other.close()

    // 크루원 수가 DB에도 2명으로 남았는지 확인한다
    expect(sqlOne(`select count(*) from member_crew_member where crew_id = ${crewId}`)).toBe('2')
  })
})
