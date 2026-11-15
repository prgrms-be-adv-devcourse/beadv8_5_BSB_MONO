import { expect, test } from '@playwright/test'

test('홈 화면이 열린다', async ({ page }) => {
  const response = await page.goto('/')
  expect(response?.ok()).toBe(true)
})

test('로그인 화면에 이메일·비밀번호 입력칸이 보인다', async ({ page }) => {
  await page.goto('/login')
  await expect(page.getByLabel('이메일')).toBeVisible()
  await expect(page.getByLabel('비밀번호', { exact: true })).toBeVisible()
})
