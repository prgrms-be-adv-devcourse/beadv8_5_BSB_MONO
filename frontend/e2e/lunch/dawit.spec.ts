import { expect, test } from '@playwright/test'

import {
  apiLogin,
  balanceOf,
  buyTicket,
  DUMMY_RACES,
  freshBuyer,
  onSaleCourse,
  participant,
  pullSaleToNow,
  registerRace,
  sql,
  sqlOne,
  uiLogin,
  USERS,
  waitStatus,
} from './support'

test.describe('다윗', { tag: '@dawit' }, () => {
  test('22 개인 일반 예매', async ({ page }) => {
    const course = await onSaleCourse(DUMMY_RACES[0])
    const buyer = freshBuyer('22')
    const before = balanceOf(buyer)

    await uiLogin(page, buyer)
    await page.goto(`/races/${course.raceId}`)
    await page.getByRole('link', { name: '신청하기' }).first().click()
    // 대기열을 지나 신청서로 넘어간다(설계 문서의 대기열 최대 대기 30초)
    await expect(page).toHaveURL(/\/apply/, { timeout: 30_000 })

    const p = participant('김러너')
    await page.getByLabel('이름', { exact: true }).fill(p.name)
    await page.getByLabel('생년월일').fill(p.birth)
    await page.getByLabel('휴대폰 번호').fill(p.phone.replaceAll('-', ''))
    await page.getByRole('radio', { name: '여성' }).check()
    await page.getByLabel('비상 연락처 이름').fill(p.emergencyContactName)
    await page.getByLabel('비상 연락처 전화번호').fill(p.emergencyContactPhone.replaceAll('-', ''))
    await page.getByText('[필수] 개인정보 제3자 제공 동의').click()
    await page.getByRole('button', { name: '다음' }).click()

    await expect(page).toHaveURL(/\/checkout\?orderId=\d+/, { timeout: 20_000 })
    await page.getByText('[필수] 환불 기한을 확인했어요').click()
    await page.getByRole('button', { name: /원 결제$/ }).click()
    await expect(page).toHaveURL(/\/checkout\/complete/, { timeout: 30_000 })

    // 예치금이 참가비만큼 줄었는지, 내 참가권에 대회가 보이는지 확인한다
    await expect.poll(() => balanceOf(buyer), { timeout: 15_000 }).toBe(before - 45_000)
    await page.goto('/tickets')
    await expect(page.getByText(course.title).first()).toBeVisible({ timeout: 20_000 })
  })

  test('25 참가 취소와 환불', async ({ page }) => {
    const course = await onSaleCourse(DUMMY_RACES[1])
    const buyer = freshBuyer('25')
    const bought = await buyTicket(await apiLogin(buyer), course)
    const afterPay = balanceOf(buyer)

    await uiLogin(page, buyer)
    await page.goto('/tickets')
    await page.getByText(course.title).first().click()
    // 참가권 ID는 「주문 ID-주문 항목 ID」 형식이다(entities/ticket의 parseTicketId)
    await expect(page).toHaveURL(/\/tickets\/\d+-\d+$/, { timeout: 20_000 })
    await page.getByRole('button', { name: '참가 취소하기' }).click()
    await page.getByRole('button', { name: '취소하기', exact: true }).click()
    await expect(page).toHaveURL(/\/tickets\/\d+-\d+\/canceled/, { timeout: 20_000 })

    // 환불 기한 전이라 전액(60,000원)이 예치금으로 돌아와야 한다
    await expect.poll(() => balanceOf(buyer), { timeout: 15_000 }).toBe(afterPay + 60_000)
    expect(sqlOne(`select status from order_order_item where id = ${bought.itemId}`)).toMatch(
      /CANCEL/,
    )
  })

  test('65 파티 만들기와 파티원 결제', async ({ page }) => {
    test.setTimeout(10 * 60_000)
    // 시드 구매자는 3명뿐이라 크루 최소 인원(4명)을 채우려고 구매자를 더 만든다
    const buyers = ['651', '652', '653', '654', '655'].map(freshBuyer)
    const apis = await Promise.all(buyers.map((email) => apiLogin(email)))
    const [leader, ...members] = apis

    // 크루를 만들고 초대 링크로 모두 가입시킨다
    const crew = await leader.call<{ crewId: number }>('POST', '/api/v1/member/crews', {
      name: `E2E파티${Date.now() % 10_000}`,
    })
    expect(crew.status, JSON.stringify(crew.raw)).toBe(201)
    const crewId = crew.data.crewId
    const invite = await leader.call<{ inviteToken: string }>(
      'POST',
      `/api/v1/member/crews/${crewId}/invites`,
      {},
    )
    expect(invite.status, JSON.stringify(invite.raw)).toBe(201)
    for (const m of members) {
      const join = await m.call(
        'POST',
        `/api/v1/member/crews/invites/${invite.data.inviteToken}/join`,
      )
      expect(join.status, JSON.stringify(join.raw)).toBe(201)
    }
    // 파티 예매 시작 7일 전보다 늦게 가입한 크루원은 초대에서 빠지므로 가입일을 한 달 전으로 옮긴다
    sql(
      `update member_crew_member set joined_at = now() - interval 30 day, created_at = now() - interval 30 day where crew_id = ${crewId}`,
    )

    // 크루 예매를 받는 대회를 등록하고 크루 예매 시작을 지금으로 당긴다
    const seller = await apiLogin(USERS.seller)
    const race = await registerRace(seller, `E2E 파티 ${Date.now() % 100_000}`, { crew: true })
    pullSaleToNow(race.raceId, { crew: true })
    await waitStatus(race.raceId, 'ON_SALE')

    const party = await leader.call<{
      party: { id: number; joinToken: string }
      invitedMemberIds: number[]
    }>('POST', '/api/v1/order/parties', {
      crewId,
      raceCourseId: race.courseId,
      participant: participant('파티장'),
      thirdPartyConsent: true,
      invitedMemberIds: members.map((m) => m.memberId),
    })
    expect(party.status, JSON.stringify(party.raw)).toBe(201)
    // 응답은 { party, participation, invitedMemberIds } 모양이고 참가 링크 토큰도 party에 들어 있다(PartyCreatedDto)
    const partyId = party.data.party.id
    expect(party.data.invitedMemberIds).toHaveLength(members.length)

    for (const m of members) {
      const joined = await m.call('POST', '/api/v1/order/parties/join', {
        joinToken: party.data.party.joinToken,
        participant: participant(`파티원${m.memberId}`),
        thirdPartyConsent: true,
      })
      expect(joined.status, JSON.stringify(joined.raw)).toBe(201)
    }
    const applied = await leader.call('POST', `/api/v1/order/parties/${partyId}/apply`)
    expect(applied.status, JSON.stringify(applied.raw)).toBe(200)

    // 파티원 한 명은 화면에서 결제한다
    await uiLogin(page, buyers[1])
    await page.goto(`/parties/${partyId}`)
    await page.getByRole('button', { name: /원 결제하기$/ }).click()
    await expect(page).toHaveURL(/\/checkout\?orderId=\d+/, { timeout: 20_000 })
    await page.getByText('[필수] 환불 기한을 확인했어요').click()
    await page.getByRole('button', { name: /원 결제$/ }).click()
    await expect(page).toHaveURL(/\/checkout\/complete/, { timeout: 30_000 })

    await page.goto(`/parties/${partyId}`)
    await expect(page.getByText(race.title).first()).toBeVisible({ timeout: 20_000 })
  })
})
