import { expect, test } from '@playwright/test'

import { apiLogin, DUMMY_RACES, registerRace, sqlOne, uiLogin, USERS } from './support'

test.describe('호빈', { tag: '@hobin' }, () => {
  test('77 판매자 대회 등록', async ({ page }) => {
    await uiLogin(page, USERS.seller)
    await page.goto('/seller/races/new')
    await expect(page.getByRole('button', { name: /등록하기|대회 등록/ }).first()).toBeVisible({
      timeout: 20_000,
    })

    // 등록 양식은 대표 이미지 업로드(파일 서비스)까지 있어 저장은 판매자 API로 하고, 결과를 화면에서 확인한다
    test
      .info()
      .annotations.push({ type: 'skip-step', description: '등록 양식 입력 대신 판매자 API로 등록' })
    const seller = await apiLogin(USERS.seller)
    const race = await registerRace(seller, `E2E 등록 ${Date.now() % 100_000}`)
    expect(sqlOne(`select sale_status from race where race_id = ${race.raceId}`)).toBeTruthy()

    await page.goto('/seller/races')
    await expect(page.getByText(race.title).first()).toBeVisible({ timeout: 20_000 })
  })

  test('19 대회 검색', async ({ page }) => {
    await page.goto('/races')
    const search = page.getByRole('textbox', { name: '대회 찾기' })
    if (!(await search.isVisible())) await page.getByRole('link', { name: '대회 찾기' }).click()
    await search.fill('서울숲')
    await search.press('Enter')
    // 대회 검색은 Elasticsearch 색인을 거친다(설계 문서의 반영 최대 15초)
    await expect(page.getByText(DUMMY_RACES[0]).first()).toBeVisible({ timeout: 15_000 })
  })
})
