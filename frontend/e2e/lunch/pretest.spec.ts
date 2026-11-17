import { expect, test } from '@playwright/test'

import { uiLogin, USERS } from './support'

// e2e-lunch.sh pretest의 마지막 확인. 화면 → 프론트 → 게이트웨이 → 회원 서비스까지 로그인이 이어지는지 본다
test('프리테스트: 로그인 화면으로 로그인', { tag: '@pretest' }, async ({ page }) => {
  await uiLogin(page, USERS.user1)
  await expect(page).not.toHaveURL(/\/login/)
})
