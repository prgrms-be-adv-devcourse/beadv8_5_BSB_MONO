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
  PASSWORD,
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

// 크루 예매 대회(파티 시나리오)와 키트 대회(96·81)를 한 번에 만들어 판매 상태 배치(5분 주기)를 한 번만 기다린다.
// 크루 정원은 등록 값(20)에서 100으로 늘려 신청하는 파티 여럿(65·71·72·53·73)이 함께 쓴다.
// 정원·접수 마감을 바꾸는 시나리오(마지막 자리·30)는 더미 대회를 건드리지 않게 따로 만든 대회(limited)를 쓴다
let testRaces: Promise<{ crew: Course; kit: Course; limited: Course }> | undefined
function races() {
  testRaces ??= (async () => {
    const seller = await apiLogin(USERS.seller)
    const stamp = Date.now() % 100_000
    const crew = await registerRace(seller, `E2E 파티 ${stamp}`, { crew: true })
    const kit = await registerRace(seller, `E2E 키트 ${stamp}`, { kit: true })
    const limited = await registerRace(seller, `E2E 제한 ${stamp}`)
    sql(`update race_course set crew_quota = 100 where race_id = ${crew.raceId}`)
    pullSaleToNow(crew.raceId, { crew: true })
    pullSaleToNow(kit.raceId)
    pullSaleToNow(limited.raceId)
    await Promise.all([
      waitStatus(crew.raceId, 'ON_SALE'),
      waitStatus(kit.raceId, 'ON_SALE'),
      waitStatus(limited.raceId, 'ON_SALE'),
    ])
    return { crew, kit, limited }
  })()
  return testRaces
}

// 새 구매자들로 크루를 만들고 초대 링크로 모두 가입시킨다. 첫 사람이 크루장이다.
// 시드 구매자는 3명뿐이라 크루 최소 인원(4명)을 채우려고 구매자를 더 만든다
async function crewOf(keys: string[]) {
  const buyers = keys.map(freshBuyer)
  const apis = await Promise.all(buyers.map((email) => apiLogin(email)))
  const [leader, ...members] = apis
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
  return { crewId, buyers, leader, members }
}

// 크루장이 크루 예매 대회에 파티를 만든다(201을 기대하지 않고 응답을 그대로 돌려준다)
async function createParty(leader: Api, crewId: number, raceCourseId: number, invited: Api[]) {
  return leader.call<{
    party: { id: number; joinToken: string }
    invitedMemberIds: number[]
  }>('POST', '/api/v1/order/parties', {
    crewId,
    raceCourseId,
    participant: participant('파티장'),
    thirdPartyConsent: true,
    invitedMemberIds: invited.map((m) => m.memberId),
  })
}

// 크루를 만들고 파티를 만든 뒤 앞에서 joined명까지 참가 링크로 합류시킨다(모집 중). 나머지는 크루원으로만 남는다
async function recruitingParty(keys: string[], joined = keys.length - 1) {
  const { crew: race } = await races()
  const { crewId, buyers, leader, members } = await crewOf(keys)
  const party = await createParty(leader, crewId, race.courseId, members)
  expect(party.status, JSON.stringify(party.raw)).toBe(201)
  // 응답은 { party, participation, invitedMemberIds } 모양이고 참가 링크 토큰도 party에 들어 있다(PartyCreatedDto)
  const partyId = party.data.party.id
  const joinToken = party.data.party.joinToken
  expect(party.data.invitedMemberIds).toHaveLength(members.length)
  for (const m of members.slice(0, joined)) {
    const join = await m.call('POST', '/api/v1/order/parties/join', {
      joinToken,
      participant: participant(`파티원${m.memberId}`),
      thirdPartyConsent: true,
    })
    expect(join.status, JSON.stringify(join.raw)).toBe(201)
  }
  return { race, crewId, buyers, leader, members, partyId, joinToken }
}

// 모두 합류한 파티를 파티장이 신청까지 한다. 신청하면 상품에 크루 자리를 잡고 결제 진행 중이 된다
async function appliedParty(keys: string[]) {
  const party = await recruitingParty(keys)
  const applied = await party.leader.call('POST', `/api/v1/order/parties/${party.partyId}/apply`)
  expect(applied.status, JSON.stringify(applied.raw)).toBe(200)
  await eventually(() => partyStatus(party.partyId) === 'PAYING')
  return party
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

// 결제 화면에서 동의하고 결제 버튼까지 누른다(충전이 필요하면 「충전하고 결제하기」 버튼)
async function agreeAndPay(page: Page, name: RegExp = /원 결제하기$/) {
  await page.getByText('주문 내용 확인 및 결제 동의').click()
  await page.getByRole('button', { name }).click()
}

const won = (n: number) => `${n.toLocaleString('ko-KR')}원`

// 오늘에서 n일 뒤의 한국 날짜(YYYY-MM-DD). 주문은 환불 기한·일정 변경을 한국 날짜로 센다
const kstDate = (n: number) =>
  new Date(Date.now() + 9 * 3600e3 + n * 86_400_000).toISOString().slice(0, 10)

// 참가 신청서를 저장하고 대기열 입장을 기록한다(주문 전). 장바구니 결제처럼 신청서만 필요할 때 쓴다
async function applyAdmitted(api: Api, { raceId, courseId }: Omit<Course, 'title'>, name: string) {
  const apply = await api.call<{ id: number }>('POST', '/api/v1/order/participations', {
    raceCourseId: courseId,
    thirdPartyConsent: true,
    participant: participant(name),
  })
  expect(apply.status, `참가 신청 ${JSON.stringify(apply.raw)}`).toBe(201)
  const admit = await api.call<{ admitted: boolean }>(
    'GET',
    `/api/v1/order/queue/admission?raceId=${raceId}&raceCourseId=${courseId}`,
  )
  expect(admit.data?.admitted, `대기열 입장 ${JSON.stringify(admit.raw)}`).toBe(true)
  return apply.data.id
}

// 이미 열린 로그인 화면의 폼을 채워 로그인한다
async function fillLogin(page: Page, email: string) {
  await page.getByLabel('이메일').fill(email)
  await page.getByLabel('비밀번호', { exact: true }).fill(PASSWORD)
  await page.getByRole('button', { name: '로그인', exact: true }).click()
}

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
    await page.getByText('주문 내용 확인 및 결제 동의').click()
    await page.getByRole('button', { name: /원 결제하기$/ }).click()
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
    await expect(page.getByRole('button', { name: /원 결제하기$/ })).toBeVisible({
      timeout: 30_000,
    })

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
    await agreeAndPay(page, /충전하고 결제하기$/)
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

  test('27 대회 일정 변경 때 환불', async () => {
    const course = await onSaleCourse(DUMMY_RACES[2])
    const buyer = freshBuyer('27')
    const api = await apiLogin(buyer)
    const before = balanceOf(buyer)
    const bought = await buyTicket(api, course)
    const afterPay = balanceOf(buyer)
    const cancel = () =>
      api.call('POST', `/api/v1/order/orders/${bought.orderId}/items/${bought.itemId}/cancel`)
    const [deadline, changedAt] = sql(
      `select refund_deadline, ifnull(schedule_changed_at, '') from race where race_id = ${course.raceId}`,
    )[0]
    try {
      // 환불 기한이 어제로 지났고, 일정 변경도 알린 날을 1일째로 세어 8일째라 취소할 수 없다
      sql(
        `update race set refund_deadline = '${kstDate(-1)}', schedule_changed_at = '${kstDate(-7)} 23:59:00' where race_id = ${course.raceId}`,
      )
      const late = await cancel()
      expect(late.status, JSON.stringify(late.raw)).toBe(409)
      expect((late.raw as { code?: string }).code).toBe('ORDER_REFUND_PERIOD_PASSED')
      expect(balanceOf(buyer)).toBe(afterPay)

      // 일정 변경 7일째(오늘)까지는 환불 기한이 지나도 수수료 없이 결제액 전부를 돌려준다
      sql(
        `update race set schedule_changed_at = '${kstDate(-6)} 00:00:00' where race_id = ${course.raceId}`,
      )
      const ok = await cancel()
      expect(ok.status, JSON.stringify(ok.raw)).toBe(200)
      await expect.poll(() => balanceOf(buyer), { timeout: 15_000 }).toBe(before)
      expect(
        sql(`select status, cancel_reason from order_order_item where id = ${bought.itemId}`)[0],
      ).toEqual(['CANCELED', 'SCHEDULE_CHANGED'])
    } finally {
      // 더미 대회는 다른 시나리오도 쓰므로 되돌린다
      sql(
        `update race set refund_deadline = '${deadline}', schedule_changed_at = ${changedAt ? `'${changedAt}'` : 'null'} where race_id = ${course.raceId}`,
      )
    }
  })

  test('31 장바구니 담기', async () => {
    const course = await onSaleCourse(DUMMY_RACES[0])
    const other = await onSaleCourse(DUMMY_RACES[1])
    const api = await apiLogin(freshBuyer('31'))
    const reserved = () =>
      sqlOne(
        `select individual_reserved from race_course where race_course_id = ${course.courseId}`,
      )
    const held = reserved()
    const add = (raceCourseId: number) =>
      api.call('POST', '/api/v1/order/cart/items', { raceCourseId })

    const first = await add(course.courseId)
    expect(first.status, JSON.stringify(first.raw)).toBe(201)
    // 수량은 1로 고정이라 같은 종목을 다시 담으면 담긴 항목을 그대로 돌려준다
    expect((await add(course.courseId)).status).toBe(200)
    // 장바구니에는 한 종목만 담긴다(PRO-192). 다른 대회의 종목은 409로 막힌다
    const second = await add(other.courseId)
    expect(second.status, JSON.stringify(second.raw)).toBe(409)
    expect((second.raw as { code?: string }).code).toBe('ORDER_CART_ONE_COURSE_ONLY')

    const cart = await api.call<{ items: { raceCourseId: number }[] }>('GET', '/api/v1/order/cart')
    expect(cart.data.items.map((item) => item.raceCourseId)).toEqual([course.courseId])
    // 담기만으로는 자리를 잡지 않는다
    expect(reserved()).toBe(held)
  })

  test('32 장바구니 결제', async ({ page }) => {
    const course = await onSaleCourse(DUMMY_RACES[1])
    const buyer = freshBuyer('32')
    const api = await apiLogin(buyer)
    const before = balanceOf(buyer)
    // 신청서를 저장하고 대기열에 입장한 뒤 담는다. 장바구니는 그 종목의 결제 전 신청서를 붙여 보여 준다
    const participationId = await applyAdmitted(api, course, '장바구니')
    const added = await api.call('POST', '/api/v1/order/cart/items', {
      raceCourseId: course.courseId,
    })
    expect(added.status, JSON.stringify(added.raw)).toBe(201)

    await uiLogin(page, buyer)
    await page.goto('/cart')
    await expect(page.getByText(/참가자 장바구니/)).toBeVisible({ timeout: 20_000 })
    await page.getByRole('button', { name: '결제하기' }).click()
    await expect(page).toHaveURL(/\/checkout\?orderId=\d+/, { timeout: 20_000 })
    const orderId = Number(new URL(page.url()).searchParams.get('orderId'))
    await agreeAndPay(page)
    await expect(page).toHaveURL(/\/checkout\/complete/, { timeout: 30_000 })

    // 그 신청서로 주문이 만들어져 결제되고, 주문한 종목은 장바구니에서 빠진다
    const total = Number(sqlOne(`select total_amount from order_order where id = ${orderId}`))
    await expect.poll(() => balanceOf(buyer), { timeout: 15_000 }).toBe(before - total)
    expect(sqlOne(`select status from order_participation where id = ${participationId}`)).toBe(
      'CONFIRMED',
    )
    const cart = await api.call<{ items: unknown[] }>('GET', '/api/v1/order/cart')
    expect(cart.data.items).toEqual([])
  })

  test('67 파티 합류', async ({ page }) => {
    test.setTimeout(10 * 60_000)
    // 마지막 크루원은 아직 합류하지 않은 채로 둔다
    const { partyId, joinToken, members, buyers } = await recruitingParty(
      ['671', '672', '673', '674'],
      2,
    )
    const joiner = members[2]

    await uiLogin(page, buyers[3])
    await page.goto(`/parties/join/${joinToken}`)
    const p = participant('합류러너')
    await page.getByText('개인정보 제3자 제공에 동의해요 (필수)').click()
    await page.getByLabel('참가자 이름').fill(p.name)
    await page.getByLabel('생년월일').fill(p.birth)
    await page.getByRole('radio', { name: '여성' }).check()
    await page.getByLabel('휴대폰 번호').fill(p.phone.replaceAll('-', ''))
    await page.getByLabel('비상 연락처 이름').fill(p.emergencyContactName)
    await page.getByLabel('비상 연락처 번호').fill(p.emergencyContactPhone.replaceAll('-', ''))
    await page.getByRole('button', { name: '합류하기' }).click()
    await expect(page).toHaveURL(new RegExp(`/parties/${partyId}$`), { timeout: 20_000 })

    // 파티 종목으로 결제 전 신청서가 생기고 파티원이 4명이 된다
    expect(participationStatus(partyId, joiner.memberId)).toBe('PENDING')
    expect(
      sqlOne(
        `select count(*) from order_party_member where party_id = ${partyId} and left_at is null`,
      ),
    ).toBe('4')
  })

  test('66 파티 나가기', async ({ page }) => {
    test.setTimeout(10 * 60_000)
    const { partyId, crewId, leader, members, buyers } = await recruitingParty([
      '661',
      '662',
      '663',
      '664',
    ])

    // 파티원은 화면에서 나간다. 나가면 크루 화면으로 가고 내 신청서는 취소된다
    await uiLogin(page, buyers[3])
    await page.goto(`/parties/${partyId}`)
    await page.getByRole('button', { name: '더보기' }).click()
    await page.getByRole('menuitem', { name: '파티 나가기' }).click()
    await page.getByRole('button', { name: '나가기', exact: true }).click()
    await expect(page).toHaveURL(new RegExp(`/crews/${crewId}$`), { timeout: 20_000 })
    expect(participationStatus(partyId, members[2].memberId)).toBe('CANCELED')

    // 파티장이 나가면 가장 먼저 합류한 파티원이 파티장이 된다
    const left = await leader.call('POST', `/api/v1/order/parties/${partyId}/leave`)
    expect(left.status, JSON.stringify(left.raw)).toBe(200)
    expect(Number(sqlOne(`select leader_member_id from order_party where id = ${partyId}`))).toBe(
      members[0].memberId,
    )
    expect(
      sql(
        `select member_id from order_party_member where party_id = ${partyId} and left_at is null order by member_id`,
      ).map((row) => Number(row[0])),
    ).toEqual([members[0].memberId, members[1].memberId].sort((a, b) => a - b))
    expect(partyStatus(partyId)).toBe('RECRUITING')
  })

  test('69 파티 만들기가 막힐 때', async () => {
    const { crew: race } = await races()
    const { crewId, leader, members } = await crewOf(['691', '692'])
    // 결제율 미달로 크루가 파티 만들기 제한 중이다(회원 서비스의 크루 제한 기록)
    sql(
      `insert into member_crew_penalty (crew_id, penalty_month, low_payment_count, start_at, end_at, created_at, updated_at)
       values (${crewId}, '${kstDate(-31).slice(0, 7)}', 2, now() - interval 1 day, now() + interval 29 day, now(), now())`,
    )
    const blocked = await createParty(leader, crewId, race.courseId, members)
    expect(blocked.status, JSON.stringify(blocked.raw)).toBe(403)
    expect((blocked.raw as { code?: string }).code).toBe('ORDER_CREW_PARTY_RESTRICTED')
    expect((blocked.raw as { data?: { endAt?: string } }).data?.endAt).toBeTruthy()
    expect(
      sqlOne(`select count(*) from order_party where leader_member_id = ${leader.memberId}`),
    ).toBe('0')

    // 제한이 끝나면 같은 크루로 만들 수 있다
    sql(
      `update member_crew_penalty set end_at = now() - interval 1 minute where crew_id = ${crewId}`,
    )
    const created = await createParty(leader, crewId, race.courseId, members)
    expect(created.status, JSON.stringify(created.raw)).toBe(201)
  })

  test('70 파티 신청과 크루 몫 부족', async () => {
    test.setTimeout(10 * 60_000)
    const { race, leader, partyId } = await recruitingParty(['701', '702', '703', '704'])
    const where = `where race_course_id = ${race.courseId}`
    const quota = sqlOne(`select crew_quota from race_course ${where}`)
    const reserved = sqlOne(`select crew_reserved from race_course ${where}`)
    const apply = () =>
      leader.call<{ needed: number; remaining: number }>(
        'POST',
        `/api/v1/order/parties/${partyId}/apply`,
      )
    try {
      // 남은 크루 자리를 2석만 남긴다. 파티원은 4명이다
      sql(
        `update race_course set crew_quota = moved_quota + crew_reserved + crew_sold + 2 ${where}`,
      )
      const short = await apply()
      expect(short.status, JSON.stringify(short.raw)).toBe(409)
      expect((short.raw as { code?: string }).code).toBe('ORDER_PARTY_CREW_SEAT_SHORTAGE')
      expect(short.data).toMatchObject({ needed: 4, remaining: 2 })
      // 한 석도 잡지 않고 파티는 모집 중으로 남는다
      expect(sqlOne(`select crew_reserved from race_course ${where}`)).toBe(reserved)
      expect(partyStatus(partyId)).toBe('RECRUITING')
    } finally {
      sql(`update race_course set crew_quota = ${quota} ${where}`)
    }
    // 자리가 생기면 같은 파티로 다시 신청할 수 있다
    const again = await apply()
    expect(again.status, JSON.stringify(again.raw)).toBe(200)
    await eventually(() => partyStatus(partyId) === 'PAYING')
  })

  test('53 크루 신청 참가권 취소', async ({ page }) => {
    test.setTimeout(10 * 60_000)
    const { race, buyers, members, partyId } = await appliedParty(['531', '532', '533', '534'])
    const payer = members[0]
    const email = buyers[1]
    const order = await partyOrder(payer, partyId)
    const pay = await payer.call('POST', `/api/v1/order/orders/${order.id}/payment`)
    expect(pay.status, JSON.stringify(pay.raw)).toBe(200)
    const afterPay = balanceOf(email)

    // 파티원은 파티 전체가 아니라 자기 참가권만 취소한다(정책 5장 2차 2-1)
    await uiLogin(page, email)
    await page.goto('/tickets')
    await page.getByText(race.title).first().click()
    await expect(page).toHaveURL(/\/tickets\/\d+-\d+$/, { timeout: 20_000 })
    await page.getByRole('button', { name: '참가 취소하기' }).click()
    await page.getByRole('button', { name: '취소하기', exact: true }).click()
    await expect(page).toHaveURL(/\/tickets\/\d+-\d+\/canceled/, { timeout: 20_000 })

    // 환불 기한 전이라 결제액 전부가 돌아오고, 파티와 다른 파티원은 그대로다
    await expect
      .poll(() => balanceOf(email), { timeout: 15_000 })
      .toBe(afterPay + order.totalAmount)
    expect(participationStatus(partyId, payer.memberId)).toBe('CANCELED')
    expect(partyStatus(partyId)).toBe('PAYING')
    for (const m of members.slice(1)) {
      expect(participationStatus(partyId, m.memberId)).toBe('APPLIED')
    }
  })

  test('73 파티 결제 알림', async ({ page }) => {
    test.setTimeout(10 * 60_000)
    const { leader, members, buyers } = await appliedParty(['731', '732', '733', '734'])

    // 신청하면 파티원 모두에게 결제 알림이 웹 알림으로 쌓인다(주문 → Kafka → 알림 서비스)
    for (const m of [leader, ...members]) {
      await eventually(
        () =>
          sqlOne(
            `select count(*) from notification_notification where member_id = ${m.memberId} and kind = 'PARTY_PAYMENT_REQUESTED'`,
          ) === '1',
      )
    }
    await uiLogin(page, buyers[1])
    await page.goto('/notifications')
    await expect(page.getByText(/파티 신청이 끝났어요/).first()).toBeVisible({ timeout: 20_000 })
  })

  test('23 대기열 나가기', async ({ page }) => {
    const course = await onSaleCourse(DUMMY_RACES[2])
    await uiLogin(page, freshBuyer('23'))
    // 대기 화면에 머물도록 대기열 응답의 입장 가능만 끈다. 줄은 실제 대기열에 선다
    const queue = /\/api\/v1\/queue(\/races\/\d+\/racecourses\/\d+)?$/
    await page.route(queue, async (route) => {
      const res = await route.fetch()
      const body = (await res.json()) as { data?: { canEnter?: boolean; myRank?: number } }
      if (body.data) Object.assign(body.data, { canEnter: false, myRank: 3 })
      await route.fulfill({ response: res, json: body })
    })
    await page.goto(`/races/${course.raceId}`)
    await page.getByRole('link', { name: '신청하기' }).first().click()
    await expect(page.getByRole('heading', { name: /잠시 기다리고 있어요/ })).toBeVisible({
      timeout: 30_000,
    })

    // 닫기 → 나가기 확인 → 대회 상세로 돌아간다
    await page.getByRole('button', { name: '닫기' }).click()
    await expect(page.getByText('대기열에서 나갈까요?')).toBeVisible()
    await page.getByRole('button', { name: '나가기', exact: true }).click()
    await expect(page).toHaveURL(new RegExp(`/races/${course.raceId}$`), { timeout: 20_000 })

    // 다시 신청하면 처음부터 줄을 서고, 차례가 오면 신청서로 간다
    await page.unroute(queue)
    await page.getByRole('link', { name: '신청하기' }).first().click()
    await expect(page).toHaveURL(/\/apply/, { timeout: 30_000 })
  })

  test('97 로그인 전 신청하기', async ({ page }) => {
    const course = await onSaleCourse(DUMMY_RACES[2])
    const buyer = freshBuyer('97')
    await page.goto(`/races/${course.raceId}`)
    await page.getByRole('link', { name: '신청하기' }).first().click()
    // 로그인 화면으로 가고, 로그인하면 대기열을 지나 신청서로 이어진다
    await expect(page).toHaveURL(/\/login\?next=/, { timeout: 20_000 })
    await fillLogin(page, buyer)
    await expect(page).toHaveURL(new RegExp(`/races/${course.raceId}/apply`), { timeout: 30_000 })
  })

  test('26 참가자 정보와 키트 옵션 수정', async ({ page }) => {
    test.setTimeout(10 * 60_000)
    const { kit: course } = await races()
    const buyer = freshBuyer('26')
    const api = await apiLogin(buyer)
    const { orderId, participationId } = await createOrder(api, course)
    const optionId = sqlOne(
      `select kit_option_id from product_kit_option where race_id = ${course.raceId} and value = '230-250'`,
    )
    const pay = await api.call('POST', `/api/v1/order/orders/${orderId}/payment`, {
      kitOptionIds: [Number(optionId)],
    })
    expect(pay.status, JSON.stringify(pay.raw)).toBe(200)
    const itemId = sqlOne(`select id from order_order_item where order_id = ${orderId}`)

    // 결제 뒤에도 참가자 정보(비상 연락처)는 화면에서 고칠 수 있다
    await uiLogin(page, buyer)
    await page.route(/postcode\.v2\.js/, (route) => route.abort())
    // 수정 화면은 저장 뒤 뒤로 가므로 상세에서 「참가자 정보 수정하기」로 들어간다
    await page.goto(`/tickets/${orderId}-${itemId}`)
    await page.getByRole('link', { name: '참가자 정보 수정하기' }).click()
    await expect(page).toHaveURL(/\/edit$/, { timeout: 20_000 })
    await page.getByRole('textbox', { name: '이름', exact: true }).fill('새보호자')
    await page.getByRole('button', { name: '저장하기' }).click()
    await expect(page).toHaveURL(new RegExp(`/tickets/${orderId}-${itemId}$`), {
      timeout: 20_000,
    })
    expect(
      sqlOne(
        `select emergency_contact_name from order_participation where id = ${participationId}`,
      ),
    ).toBe('새보호자')

    // 키트 옵션 바꾸기 API는 없어졌고(#573), 참가자 정보를 다시 저장해도 고른 옵션은 그대로다.
    // 티셔츠 사이즈 잠금은 사이즈 옵션이 있는 종목에서만 보인다. registerRace가 사이즈 옵션을 넣지 않아 여기서는 보지 않는다
    const kitChange = await api.call(
      'PUT',
      `/api/v1/order/participations/${participationId}/kit-options`,
      { kitOptionIds: [Number(optionId) + 1] },
    )
    expect(kitChange.status, JSON.stringify(kitChange.raw)).toBeGreaterThanOrEqual(400)
    const again = await api.call(
      'PUT',
      `/api/v1/order/participations/${participationId}/participant`,
      participant(`E2E${api.memberId}`),
    )
    expect(again.status, JSON.stringify(again.raw)).toBe(200)
    expect(
      sql(
        `select option_value from order_participation_kit_option where participation_id = ${participationId}`,
      ),
    ).toEqual([['230-250']])
  })

  test('30 신청할 수 없는 대회', async () => {
    test.setTimeout(10 * 60_000)
    const { limited: course } = await races()
    const buyer = freshBuyer('30')
    const api = await apiLogin(buyer)
    const apply = (birth = '1995-01-01') =>
      api.call('POST', '/api/v1/order/participations', {
        raceCourseId: course.courseId,
        thirdPartyConsent: true,
        participant: { ...participant(`E2E${api.memberId}`), birth },
      })
    const code = (r: { raw: unknown }) => (r.raw as { code?: string }).code

    // 나이 제한(만 14세 이상) 밖
    const young = await apply('2020-01-01')
    expect(young.status, JSON.stringify(young.raw)).toBe(400)
    expect(code(young)).toBe('ORDER_AGE_RESTRICTED')

    // 접수 마감 뒤
    const closeAt = sqlOne(`select application_close_at from race where race_id = ${course.raceId}`)
    try {
      sql(
        `update race set application_close_at = now() - interval 1 minute where race_id = ${course.raceId}`,
      )
      const closed = await apply()
      expect(closed.status, JSON.stringify(closed.raw)).toBe(409)
      expect(code(closed)).toBe('ORDER_APPLICATION_CLOSED')
    } finally {
      sql(`update race set application_close_at = '${closeAt}' where race_id = ${course.raceId}`)
    }

    // 매진: 남은 자리를 0으로 만들면 신청은 되지만 주문(자리 확보)에서 막히고 돈은 그대로다
    sql(
      `update race_course set individual_quota = individual_reserved + individual_sold where race_course_id = ${course.courseId}`,
    )
    const before = balanceOf(buyer)
    const ok = await apply()
    expect(ok.status, JSON.stringify(ok.raw)).toBe(201)
    await api.call(
      'GET',
      `/api/v1/order/queue/admission?raceId=${course.raceId}&raceCourseId=${course.courseId}`,
    )
    const order = await api.call(
      'POST',
      '/api/v1/order/orders',
      { participationId: (ok.data as { id: number }).id },
      { 'Idempotency-Key': crypto.randomUUID() },
    )
    expect(order.status, JSON.stringify(order.raw)).toBe(409)
    expect(code(order)).toBe('ORDER_SOLD_OUT')
    expect(balanceOf(buyer)).toBe(before)
  })

  test('33 내 참가권 불러오기', async ({ page }) => {
    const course = await onSaleCourse(DUMMY_RACES[0])
    const buyer = freshBuyer('33')
    const api = await apiLogin(buyer)
    const { orderId, itemId } = await buyTicket(api, course)
    const [orderNo, total] = sql(
      `select order_no, total_amount from order_order where id = ${orderId}`,
    )[0]

    await uiLogin(page, buyer)
    await page.goto('/tickets')
    await page.getByText(course.title).first().click()
    await expect(page).toHaveURL(new RegExp(`/tickets/${orderId}-${itemId}$`), {
      timeout: 20_000,
    })
    // 상세의 주문번호·참가자·결제액이 결제한 주문과 같다
    await expect(page.getByText(`주문번호 ${orderNo}`)).toBeVisible({ timeout: 20_000 })
    await expect(page.getByText(`E2E${api.memberId}`).first()).toBeVisible()
    await expect(page.getByText(`${Number(total).toLocaleString('ko-KR')}원`).first()).toBeVisible()
  })

  // 판매자 주문·참가자 화면(seller/sales)은 「참가자 목록은 준비 중이에요」만 보여 주고 주문 상세 창이 아직 없다
  test.fixme('82 판매자 주문 상세 창', async () => {})

  test('결제 두 번 눌러도 예치금은 한 번만 빠짐', async () => {
    const course = await onSaleCourse(DUMMY_RACES[1])
    const buyer = freshBuyer('41')
    const api = await apiLogin(buyer)
    const before = balanceOf(buyer)
    const { orderId } = await createOrder(api, course)
    const total = Number(sqlOne(`select total_amount from order_order where id = ${orderId}`))

    const pays = await Promise.all(
      [1, 2].map(() => api.call('POST', `/api/v1/order/orders/${orderId}/payment`)),
    )
    const statuses = pays.map((p) => p.status)
    expect(
      statuses.some((s) => s === 200 || s === 202),
      JSON.stringify(statuses),
    ).toBe(true)
    await expect
      .poll(() => sqlOne(`select status from order_order where id = ${orderId}`), {
        timeout: 30_000,
      })
      .toBe('PAID')
    // 결제 재확인이 끝난 뒤에도 한 번만 빠져 있어야 한다
    await new Promise((r) => setTimeout(r, 3_000))
    expect(balanceOf(buyer)).toBe(before - total)
  })

  test('마지막 자리 동시 결제', async () => {
    test.setTimeout(10 * 60_000)
    const { limited: course } = await races()
    sql(
      `update race_course set individual_quota = individual_reserved + individual_sold + 1 where race_course_id = ${course.courseId}`,
    )
    const buyers = ['421', '422'].map(freshBuyer)
    const before = buyers.map(balanceOf)
    const apis = await Promise.all(buyers.map((b) => apiLogin(b)))
    const results = await Promise.allSettled(apis.map((a) => buyTicket(a, course)))

    // 한 명만 자리를 얻고, 판매 수가 정원을 넘지 않으며, 진 사람의 돈은 그대로다
    expect(
      results.filter((r) => r.status === 'fulfilled').length,
      JSON.stringify(results.map((r) => (r.status === 'rejected' ? String(r.reason) : 'ok'))),
    ).toBe(1)
    const [quota, sold, reserved] = sql(
      `select individual_quota, individual_sold, individual_reserved from race_course where race_course_id = ${course.courseId}`,
    )[0].map(Number)
    expect(sold + reserved).toBeLessThanOrEqual(quota)
    const loser = results.findIndex((r) => r.status === 'rejected')
    expect(balanceOf(buyers[loser])).toBe(before[loser])
  })

  test('결제 기한 지난 주문 결제', async () => {
    const course = await onSaleCourse(DUMMY_RACES[2])
    const buyer = freshBuyer('43')
    const api = await apiLogin(buyer)
    const before = balanceOf(buyer)
    const { orderId, participationId } = await createOrder(api, course)
    sql(`update order_order set payment_due_at = now() - interval 1 minute where id = ${orderId};
      update order_participation set payment_due_at = now() - interval 1 minute where id = ${participationId}`)

    const pay = await api.call('POST', `/api/v1/order/orders/${orderId}/payment`)
    expect(pay.status, JSON.stringify(pay.raw)).toBe(409)
    expect((pay.raw as { code?: string }).code).toBe('ORDER_PAYMENT_DEADLINE_PASSED')
    expect(balanceOf(buyer)).toBe(before)
  })

  test('환불 기한 지난 취소', async () => {
    // 정책 5장 환불 기준은 「(회의 후 채움)」이라 코드 기준으로 본다: 기한이 지나면 수수료를 떼고 돌려주지 않고 취소를 막는다
    const course = await onSaleCourse(DUMMY_RACES[2])
    const buyer = freshBuyer('44')
    const api = await apiLogin(buyer)
    const bought = await buyTicket(api, course)
    const afterPay = balanceOf(buyer)
    const deadline = sqlOne(`select refund_deadline from race where race_id = ${course.raceId}`)
    try {
      sql(`update race set refund_deadline = '${kstDate(-1)}' where race_id = ${course.raceId}`)
      const late = await api.call(
        'POST',
        `/api/v1/order/orders/${bought.orderId}/items/${bought.itemId}/cancel`,
      )
      expect(late.status, JSON.stringify(late.raw)).toBe(409)
      expect((late.raw as { code?: string }).code).toBe('ORDER_REFUND_PERIOD_PASSED')
      expect(balanceOf(buyer)).toBe(afterPay)
      expect(sqlOne(`select status from order_order_item where id = ${bought.itemId}`)).toBe('PAID')
    } finally {
      sql(`update race set refund_deadline = '${deadline}' where race_id = ${course.raceId}`)
    }
  })

  test('81 판매자 발송 처리 → 29 구매자 배송 확인 → 34 수령 확인', async ({ page }) => {
    test.setTimeout(10 * 60_000)
    const { kit: course } = await races()
    const api = await apiLogin(freshBuyer('81'))
    const { orderId, itemId } = await createOrder(api, course)
    const optionId = sqlOne(
      `select kit_option_id from product_kit_option where race_id = ${course.raceId} and value = '230-250'`,
    )
    const pay = await api.call('POST', `/api/v1/order/orders/${orderId}/payment`, {
      kitOptionIds: [Number(optionId)],
    })
    expect(pay.status, JSON.stringify(pay.raw)).toBe(200)
    // 키트는 환불할 수 있는 동안 보내지 않는다. 환불 기한을 어제로 당긴다(이 대회를 쓰는 마지막 시나리오)
    sql(`update race set refund_deadline = '${kstDate(-1)}' where race_id = ${course.raceId}`)
    const item = async () =>
      (
        await api.call<{
          items: { kitDeliveryStatus?: string; kitTracking?: { status?: string } | null }[]
        }>('GET', `/api/v1/order/orders/${orderId}`)
      ).data.items[0]

    await test.step('81 판매자가 키트 배송 관리에서 송장을 넣어 발송한다', async () => {
      await uiLogin(page, USERS.seller)
      await page.goto(`/seller/kits?race=${course.raceId}&status=PREPARING`)
      const row = page.getByRole('row').filter({ hasText: `E2E${api.memberId}` })
      await row.getByRole('checkbox').click()
      await page.getByLabel('송장 번호').fill('123456789012')
      await page.getByRole('button', { name: '배송 중으로 바꾸기' }).click()
      await expect(page.getByText('1건을 배송 중으로 바꿨어요')).toBeVisible()
      expect((await item()).kitDeliveryStatus).toBe('SHIPPING')
    })

    await test.step('29 조회 배치가 가짜 택배사 배달 완료를 받아 구매자에게 배송 완료로 보인다', async () => {
      // 가짜 택배사(dev.track.dummy)는 운송장 번호가 지난 시각이면 배달 완료로 답한다
      const seller = await apiLogin(USERS.seller)
      const changed = await seller.call('PUT', `/api/v1/order/seller/kits/${itemId}/shipment`, {
        carrierId: 'dev.track.dummy',
        trackingNumber: new Date(Date.now() - 3600e3).toISOString().replace(/\.\d+Z$/, 'Z'),
      })
      expect(changed.status, JSON.stringify(changed.raw)).toBe(200)
      // 조회 배치는 e2e-lunch.sh가 15초 주기로 띄운다(앱이 뜬 2분 뒤부터)
      await eventually(async () => {
        const got = await item()
        return got.kitDeliveryStatus === 'DELIVERED' && got.kitTracking?.status === 'DELIVERED'
      }, 180_000)
    })

    await test.step('34 구매자가 수령 확인을 누른다(두 번 눌러도 처음 시각)', async () => {
      const receipt = `/api/v1/order/orders/${orderId}/items/${itemId}/kit-receipt`
      const first = await api.call('POST', receipt)
      expect(first.status, JSON.stringify(first.raw)).toBe(200)
      const receivedAt = sqlOne(
        `select received_at from order_shipment where order_id = ${orderId}`,
      )
      expect(receivedAt).toBeTruthy()
      expect((await api.call('POST', receipt)).status).toBe(200)
      expect(sqlOne(`select received_at from order_shipment where order_id = ${orderId}`)).toBe(
        receivedAt,
      )
    })
  })
})
