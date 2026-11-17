import { spawn } from 'node:child_process'
import { expect, test, type Page } from '@playwright/test'

import {
  apiLogin,
  balanceOf,
  buyTicket,
  createOrder,
  DUMMY_RACES,
  eventually,
  freshBuyer,
  memberIdOf,
  onSaleCourse,
  participant,
  pullSaleToNow,
  registerRace,
  sql,
  sqlOne,
  topUp,
  uiLogin,
  USERS,
  waitStatus,
  type Api,
  type Course,
} from './support'

// 크루 예매 대회(65·71·72)와 키트 대회(96)를 한 번에 만들어 판매 상태 배치(5분 주기)를 한 번만 기다린다.
// 크루 정원은 20이라 파티 3개(5명씩)가 함께 쓸 수 있다
let testRaces: Promise<{ crew: Course; kit: Course }> | undefined
function races() {
  testRaces ??= (async () => {
    const seller = await apiLogin(USERS.seller)
    const stamp = Date.now() % 100_000
    const crew = await registerRace(seller, `E2E 파티 ${stamp}`, { crew: true })
    const kit = await registerRace(seller, `E2E 키트 ${stamp}`, { kit: true })
    pullSaleToNow(crew.raceId, { crew: true })
    pullSaleToNow(kit.raceId)
    await Promise.all([waitStatus(crew.raceId, 'ON_SALE'), waitStatus(kit.raceId, 'ON_SALE')])
    return { crew, kit }
  })()
  return testRaces
}

// 새 구매자 5명으로 크루를 만들고, 크루 예매 대회에 파티를 만들어 모두 합류시킨 뒤 신청까지 한다.
// 시드 구매자는 3명뿐이라 크루 최소 인원(4명)을 채우려고 구매자를 더 만든다
async function appliedParty(keys: string[]) {
  const { crew: race } = await races()
  const buyers = keys.map(freshBuyer)
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
  // 신청하면 상품에 크루 자리를 잡고 결제 진행 중이 된다
  await eventually(() => partyStatus(partyId) === 'PAYING')
  return { race, buyers, leader, members, partyId }
}

const partyStatus = (partyId: number) =>
  sqlOne(`select status from order_party where id = ${partyId}`)

const participationStatus = (partyId: number, memberId: number) =>
  sqlOne(
    `select status from order_participation where party_id = ${partyId} and member_id = ${memberId} order by id desc limit 1`,
  )

// 파티원이 [결제하기]로 주문을 만든다(화면의 PartyDetailPage와 같은 API). 파티 주문은 대기열 입장이 필요 없다
async function partyOrder(api: Api, partyId: number) {
  const participationId = Number(
    sqlOne(
      `select id from order_participation where party_id = ${partyId} and member_id = ${api.memberId} and status = 'APPLIED'`,
    ),
  )
  const order = await api.call<{ id: number; totalAmount: number }>(
    'POST',
    '/api/v1/order/orders',
    { participationId },
    { 'Idempotency-Key': crypto.randomUUID() },
  )
  expect(order.status, `주문 ${JSON.stringify(order.raw)}`).toBe(201)
  return order.data
}

// 결제 화면에서 동의하고 결제 버튼까지 누른다(충전이 필요하면 「충전하고 결제」 버튼)
async function agreeAndPay(page: Page, name: RegExp = /원 결제$/) {
  await page.getByText('[필수] 환불 기한을 확인했어요').click()
  await page.getByRole('button', { name }).click()
}

const won = (n: number) => `${n.toLocaleString('ko-KR')}원`

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
    const { race, buyers, partyId } = await appliedParty(['651', '652', '653', '654', '655'])

    // 파티원 한 명은 화면에서 결제한다
    await uiLogin(page, buyers[1])
    await page.goto(`/parties/${partyId}`)
    await page.getByRole('button', { name: /원 결제하기$/ }).click()
    await expect(page).toHaveURL(/\/checkout\?orderId=\d+/, { timeout: 20_000 })
    await agreeAndPay(page)
    await expect(page).toHaveURL(/\/checkout\/complete/, { timeout: 30_000 })

    await page.goto(`/parties/${partyId}`)
    await expect(page.getByText(race.title).first()).toBeVisible({ timeout: 20_000 })
  })

  test('24 결제 중 연결 끊김과 시간 초과', async ({ page }) => {
    test.setTimeout(5 * 60_000)
    const course = await onSaleCourse(DUMMY_RACES[2])
    const buyer = freshBuyer('24')
    const before = balanceOf(buyer)
    const { orderId } = await createOrder(await apiLogin(buyer), course)

    await uiLogin(page, buyer)
    await page.goto(`/checkout?orderId=${orderId}`)
    await expect(page.getByRole('button', { name: /원 결제$/ })).toBeVisible({ timeout: 30_000 })

    // 예치금 응답을 늦춘다: 예치금 서비스가 결제 때 잠그는 지갑 줄(지갑 ID = 회원 ID, CashSupport)을 DB에서 12초 잡아 둔다.
    // 주문은 예치금을 2초(order.clients.timeout)만 기다리고 결과를 몰라 결제 중(202)으로 둔다. 앱 설정은 바꾸지 않는다
    const lock = spawn('docker', [
      'exec',
      'bukang-db',
      'mysql',
      '-ubukang2',
      '-pbukang2',
      'bukang-db',
      '-e',
      `begin; select id from cash_wallet where id = ${memberIdOf(buyer)} for update; do sleep(12); commit;`,
    ])
    const released = new Promise((resolve) => lock.on('exit', resolve))
    // 잠금이 잡혔는지는 같은 줄을 기다리지 않고 잠가 보아 확인한다(테스트 DB 계정은 잠금 목록을 볼 권한이 없다)
    await eventually(() => {
      try {
        sql(`select id from cash_wallet where id = ${memberIdOf(buyer)} for update nowait`)
        return false
      } catch {
        return true
      }
    }, 10_000)

    const paying = page.waitForResponse(
      (res) =>
        res.url().includes(`/orders/${orderId}/payment`) && res.request().method() === 'POST',
    )
    await agreeAndPay(page)
    expect((await paying).status()).toBe(202)
    await expect(page.getByText('결제를 확인하고 있어요')).toBeVisible()
    expect(sqlOne(`select status from order_order where id = ${orderId}`)).toBe('PAYING')

    // 잠금이 풀리면 결제 재확인(결제 중 30초 뒤부터, 15초 주기)이 끝내고, 화면은 주문을 다시 받아 완료로 넘어간다
    await released
    await expect(page).toHaveURL(/\/checkout\/complete/, { timeout: 120_000 })
    // 예치금은 한 번만 빠진다(예치금 결제는 주문 ID로 멱등)
    expect(balanceOf(buyer)).toBe(before - 30_000)
  })

  test('38 결제 중 부족분 충전', async ({ page }) => {
    const course = await onSaleCourse(DUMMY_RACES[0])
    const buyer = freshBuyer('38')
    // 참가비(45,000원)보다 적게 남긴다
    sql(`update cash_wallet set balance = 5000 where holder_id = ${memberIdOf(buyer)}`)
    const { orderId } = await createOrder(await apiLogin(buyer), course)

    await uiLogin(page, buyer)
    // 충전 결제창(토스)은 열지 않는다. SDK를 막아 결제창이 뜨기 전에서 멈춘다
    await page.route(/tosspayments\.com/, (route) => route.abort())
    await page.goto(`/checkout?orderId=${orderId}`)
    await expect(page.getByText(`${won(40_000)} 부족`)).toBeVisible({ timeout: 30_000 })

    const paying = page.waitForResponse(
      (res) =>
        res.url().includes(`/orders/${orderId}/payment`) && res.request().method() === 'POST',
    )
    await agreeAndPay(page, /충전하고 결제$/)
    const res = await paying
    expect(res.status()).toBe(409)
    const body = (await res.json()) as { code?: string; data?: { shortage?: number } }
    expect(body.code).toBe('ORDER_INSUFFICIENT_BALANCE')
    expect(body.data?.shortage).toBe(40_000)

    // 결제가 막혀 예치금은 그대로이고, 주문은 결제 대기로 남아 충전 뒤 같은 주문으로 다시 결제할 수 있다
    expect(balanceOf(buyer)).toBe(5000)
    expect(sqlOne(`select status from order_order where id = ${orderId}`)).toBe('CREATED')
  })

  test('96 키트 옵션 고르며 신청', async ({ page }) => {
    test.setTimeout(10 * 60_000)
    const { kit: course } = await races()
    const buyer = freshBuyer('96')
    const before = balanceOf(buyer)

    await uiLogin(page, buyer)
    // 카카오 주소 검색을 막아 주소를 직접 입력하게 한다(AddressSearch가 불러오기 실패면 칸을 연다)
    await page.route(/postcode\.v2\.js/, (route) => route.abort())
    await page.goto(`/races/${course.raceId}`)
    await page.getByRole('link', { name: '신청하기' }).first().click()
    await expect(page).toHaveURL(/\/apply/, { timeout: 30_000 })

    const p = participant('키트러너')
    await page.getByLabel('이름', { exact: true }).fill(p.name)
    await page.getByLabel('생년월일').fill(p.birth)
    await page.getByLabel('휴대폰 번호').fill(p.phone.replaceAll('-', ''))
    await page.getByRole('radio', { name: '여성' }).check()
    await page.getByLabel('비상 연락처 이름').fill(p.emergencyContactName)
    await page.getByLabel('비상 연락처 전화번호').fill(p.emergencyContactPhone.replaceAll('-', ''))
    await page.getByRole('button', { name: '주소 검색' }).click()
    await expect(page.getByText('주소 검색을 불러오지 못했어요')).toBeVisible()
    await page.getByLabel('키트 배송 우편번호').fill('04524')
    await page.getByLabel('키트 배송 주소').fill('서울특별시 중구 세종대로 110')
    await page.getByLabel('상세 주소').fill('3층')
    await page.getByText('[필수] 개인정보 제3자 제공 동의').click()
    await page.getByRole('button', { name: '다음' }).click()

    // 키트 옵션 단계: 이름(양말)마다 값 하나를 고른다
    await page.getByRole('button', { name: '255-280' }).click()
    await page.getByRole('button', { name: '결제하기' }).click()
    await expect(page).toHaveURL(/\/checkout\?orderId=\d+/, { timeout: 20_000 })
    const orderId = Number(new URL(page.url()).searchParams.get('orderId'))
    await expect(page.getByText('서울특별시 중구 세종대로 110 3층')).toBeVisible({
      timeout: 20_000,
    })
    await agreeAndPay(page)
    await expect(page).toHaveURL(/\/checkout\/complete/, { timeout: 30_000 })

    // 고른 옵션이 참가 신청에 남고, 키트 배송이 넣은 주소로 만들어지고, 예치금이 주문 금액만큼 빠진다
    const total = Number(sqlOne(`select total_amount from order_order where id = ${orderId}`))
    await expect.poll(() => balanceOf(buyer), { timeout: 15_000 }).toBe(before - total)
    expect(
      sqlOne(
        `select k.option_value from order_participation_kit_option k join order_order_item i on i.participation_id = k.participation_id where i.order_id = ${orderId}`,
      ),
    ).toBe('255-280')
    expect(sqlOne(`select address_detail from order_shipment where order_id = ${orderId}`)).toBe(
      '3층',
    )
  })

  test('71 파티 결제 기간 끝', async () => {
    test.setTimeout(10 * 60_000)
    const { leader, members, partyId } = await appliedParty(['711', '712', '713', '714', '715'])
    const payer = members[0]
    const order = await partyOrder(payer, partyId)
    const pay = await payer.call('POST', `/api/v1/order/orders/${order.id}/payment`)
    expect(pay.status, JSON.stringify(pay.raw)).toBe(200)

    // 결제 기한을 지난 것으로 바꾼다. 파티 결제 마감 배치(1분 주기)가 결제하지 않은 파티원을 정리한다
    sql(`update order_party set payment_due_at = now() - interval 1 minute where id = ${partyId}`)
    await eventually(() => partyStatus(partyId) === 'PAID', 150_000)

    // 결제한 사람만 참가 확정으로 남고, 나머지 신청서는 미결제 해제로 끝나 파티에서 빠진다
    expect(participationStatus(partyId, payer.memberId)).toBe('CONFIRMED')
    for (const m of [leader, ...members.slice(1)]) {
      expect(participationStatus(partyId, m.memberId)).toBe('RELEASED')
    }
    expect(
      sql(
        `select member_id from order_party_member where party_id = ${partyId} and left_at is null`,
      ).map((row) => Number(row[0])),
    ).toEqual([payer.memberId])
    // 파티장이 결제하지 않아 빠지면 남은 파티원에게 파티장이 넘어간다
    expect(Number(sqlOne(`select leader_member_id from order_party where id = ${partyId}`))).toBe(
      payer.memberId,
    )
  })

  test('72 파티원 결제와 예치금 부족', async () => {
    test.setTimeout(10 * 60_000)
    const { buyers, members, partyId } = await appliedParty(['721', '722', '723', '724', '725'])
    const payer = members[0]
    const email = buyers[1]
    sql(`update cash_wallet set balance = 1000 where holder_id = ${payer.memberId}`)
    const order = await partyOrder(payer, partyId)

    // 예치금이 모자라면 409와 부족 금액을 받고, 돈·주문·파티는 그대로다
    const short = await payer.call<{ shortage: number }>(
      'POST',
      `/api/v1/order/orders/${order.id}/payment`,
    )
    expect(short.status, JSON.stringify(short.raw)).toBe(409)
    expect((short.raw as { code?: string }).code).toBe('ORDER_INSUFFICIENT_BALANCE')
    expect(short.data.shortage).toBe(order.totalAmount - 1000)
    expect(balanceOf(email)).toBe(1000)
    expect(sqlOne(`select status from order_order where id = ${order.id}`)).toBe('CREATED')
    expect(participationStatus(partyId, payer.memberId)).toBe('APPLIED')
    expect(partyStatus(partyId)).toBe('PAYING')

    // 충전한 뒤 같은 주문으로 다시 결제하면 된다. 다른 파티원이 아직 결제하지 않아 파티는 결제 진행 중으로 남는다
    topUp(email)
    const paid = await payer.call('POST', `/api/v1/order/orders/${order.id}/payment`)
    expect(paid.status, JSON.stringify(paid.raw)).toBe(200)
    expect(balanceOf(email)).toBe(1000 + 1_000_000 - order.totalAmount)
    expect(participationStatus(partyId, payer.memberId)).toBe('CONFIRMED')
    expect(partyStatus(partyId)).toBe('PAYING')
  })
})
