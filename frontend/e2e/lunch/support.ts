import { execFileSync } from 'node:child_process'
import { expect, type Page } from '@playwright/test'

// 점심 로컬 E2E 공용 도우미. 노트북의 로컬 백엔드(게이트웨이 8000, bukang-db 컨테이너)만 부른다.
// 시드 계정·비밀번호는 member-service의 MemberDataInit(dev 프로필)과 같다.
export const GATEWAY = process.env.E2E_API_URL ?? 'http://localhost:8000'
export const PASSWORD = '12345678910'
export const USERS = {
  admin: 'admin@crewrun.com',
  seller: 'seller1@crewrun.com',
  user1: 'user1@crewrun.com',
  user2: 'user2@crewrun.com',
  user3: 'user3@crewrun.com',
} as const
// ProductDataInit이 만드는 더미 대회. e2e-lunch.sh pretest가 판매 시작일을 지금으로 당겨 둔다
export const DUMMY_RACES = [
  '[더미] 서울숲 10K 런',
  '[더미] 해운대 하프 마라톤',
  '[더미] 갑천 5K 런',
] as const

// 로컬 DB에 SQL을 실행하고 탭으로 나뉜 줄을 돌려준다. 값은 테스트가 만든 ID·고정 문자열만 넣는다
export function sql(query: string): string[][] {
  const out = execFileSync(
    'docker',
    [
      'exec',
      'bukang-db',
      'mysql',
      '--default-character-set=utf8mb4',
      '-ubukang2',
      '-pbukang2',
      'bukang-db',
      '-N',
      '-B',
      '-e',
      query,
    ],
    { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] },
  )
  return out
    .trim()
    .split('\n')
    .filter(Boolean)
    .map((line) => line.split('\t'))
}

export const sqlOne = (query: string) => sql(query)[0]?.[0]

export type Api = {
  token: string
  memberId: number
  call: <T = unknown>(
    method: string,
    path: string,
    body?: unknown,
    headers?: Record<string, string>,
  ) => Promise<{ status: number; data: T; raw: unknown }>
}

async function request(
  method: string,
  url: string,
  token: string | null,
  body?: unknown,
  headers: Record<string, string> = {},
) {
  const res = await fetch(url, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const text = await res.text()
  let raw: unknown = text
  try {
    raw = JSON.parse(text)
  } catch {
    // 본문이 JSON이 아니면 글자 그대로 둔다
  }
  const data = (raw as { data?: unknown } | null)?.data
  return { status: res.status, data, raw }
}

// 게이트웨이로 로그인한다. 테스트마다 새로 로그인한다(같은 토큰을 여러 테스트가 쓰면 재사용 탐지에 걸린다)
export async function apiLogin(email: string, password = PASSWORD): Promise<Api> {
  const res = await request('POST', `${GATEWAY}/api/v1/auth/login`, null, { email, password })
  const data = res.data as { accessToken?: string; member?: { id?: number } } | undefined
  if (res.status !== 200 || !data?.accessToken) {
    throw new Error(`로그인 실패 ${email}: ${res.status} ${JSON.stringify(res.raw)}`)
  }
  const token = data.accessToken
  return {
    token,
    memberId: Number(data.member?.id),
    call: async <T>(
      method: string,
      path: string,
      body?: unknown,
      headers?: Record<string, string>,
    ) => {
      const r = await request(
        method,
        path.startsWith('http') ? path : `${GATEWAY}${path}`,
        token,
        body,
        headers,
      )
      return r as { status: number; data: T; raw: unknown }
    },
  }
}

// 화면으로 로그인한다. 로그인 화면을 벗어나면 성공으로 본다
export async function uiLogin(page: Page, email: string, password = PASSWORD) {
  await page.goto('/login')
  await page.getByLabel('이메일').fill(email)
  await page.getByLabel('비밀번호', { exact: true }).fill(password)
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page).not.toHaveURL(/\/login/, { timeout: 30_000 })
}

export const memberIdOf = (email: string) =>
  Number(sqlOne(`select member_id from member_member where email = '${email}'`))

// 결제에 쓸 예치금을 넣는다. 토스 결제창을 거치지 않는 로컬 전용 지름길이라 충전 기록은 생기지 않는다.
// shortcut: 잔액 칸만 바꾼다. 충전 기록이 필요한 돌려받기(35)는 이 함수를 쓰지 않는다
export function topUp(email: string, amount = 1_000_000) {
  sql(`update cash_wallet set balance = balance + ${amount} where holder_id = ${memberIdOf(email)}`)
}

export const balanceOf = (email: string) =>
  Number(sqlOne(`select balance from cash_wallet where holder_id = ${memberIdOf(email)}`))

export type Course = { raceId: number; courseId: number; title: string }

// 판매 중인 더미 대회의 종목 하나. pretest가 당겨 둔 판매 상태 배치(5분 주기)를 최대 6분 기다린다
export async function onSaleCourse(title: string = DUMMY_RACES[0]): Promise<Course> {
  const deadline = Date.now() + 6 * 60_000
  for (;;) {
    const row = sql(
      `select r.race_id, c.race_course_id, r.sale_status from race r join race_course c on c.race_id = r.race_id where r.title = '${title}' limit 1`,
    )[0]
    if (row && row[2] === 'ON_SALE')
      return { raceId: Number(row[0]), courseId: Number(row[1]), title }
    if (Date.now() > deadline)
      throw new Error(`${title}이(가) 6분 안에 판매 중이 되지 않음: ${JSON.stringify(row)}`)
    await new Promise((r) => setTimeout(r, 10_000))
  }
}

let phoneSeq = Date.now() % 10_000
export const participant = (name: string) => ({
  name,
  birth: '1995-01-01',
  gender: 'FEMALE',
  phone: `010-2222-${String(phoneSeq++ % 10_000).padStart(4, '0')}`,
  emergencyContactName: '보호자',
  emergencyContactPhone: '010-9999-0000',
  tshirtSize: 'S',
  // 키트가 없는 종목도 주문 서비스가 배송 주소를 요구한다
  shippingAddress: {
    recipientName: name,
    recipientPhone: '010-2222-0000',
    zipcode: '04524',
    address: '서울특별시 중구 세종대로 110',
    addressDetail: '1층',
  },
})

// API로 참가 신청 → 주문 → 예치금 결제까지 한다. 화면을 확인하려는 시나리오의 준비 단계에서 쓴다
export async function buyTicket(api: Api, { raceId, courseId }: Omit<Course, 'title'>) {
  const apply = await api.call<{ id: number }>('POST', '/api/v1/order/participations', {
    raceCourseId: courseId,
    thirdPartyConsent: true,
    participant: participant(`E2E${api.memberId}`),
  })
  expect(apply.status, `참가 신청 ${JSON.stringify(apply.raw)}`).toBe(201)
  // 주문은 저장된 대기열 입장 끝 시각이 있어야 한다. 신청서 화면처럼 입장 상태 API를 불러 저장시킨다(가짜 대기열은 이때 입장시킨다)
  const admit = await api.call<{ admitted: boolean }>(
    'GET',
    `/api/v1/order/queue/admission?raceId=${raceId}&raceCourseId=${courseId}`,
  )
  expect(admit.data?.admitted, `대기열 입장 ${JSON.stringify(admit.raw)}`).toBe(true)
  const order = await api.call<{ id: number; items: { id: number }[] }>(
    'POST',
    '/api/v1/order/orders',
    { participationId: apply.data.id },
    { 'Idempotency-Key': crypto.randomUUID() },
  )
  expect(order.status, `주문 ${JSON.stringify(order.raw)}`).toBe(201)
  const pay = await api.call('POST', `/api/v1/order/orders/${order.data.id}/payment`)
  expect(pay.status, `결제 ${JSON.stringify(pay.raw)}`).toBe(200)
  return { participationId: apply.data.id, orderId: order.data.id, itemId: order.data.items[0].id }
}

// 서비스 사이 이벤트(1초 주기)를 기다린다. 설계 문서의 최대 대기 시간 15초
export async function eventually<T>(check: () => T | Promise<T>, timeoutMs = 15_000): Promise<T> {
  const deadline = Date.now() + timeoutMs
  let last: unknown
  for (;;) {
    try {
      const v = await check()
      if (v) return v
    } catch (e) {
      last = e
    }
    if (Date.now() > deadline) throw new Error(`기다린 결과가 오지 않음 ${String(last ?? '')}`)
    await new Promise((r) => setTimeout(r, 1_000))
  }
}

const day = (n: number, time = '10:00:00') => {
  const d = new Date(Date.now() + 9 * 3600e3 + n * 86_400_000)
  return `${d.toISOString().slice(0, 10)}T${time}`
}

// 판매자 API로 대회를 등록한다. 판매 시작일은 이틀 뒤부터만 정할 수 있어 등록 직후에는 판매 예정이다.
// crew: true면 크루 예매(파티)를 받는 종목으로 만든다
export async function registerRace(seller: Api, title: string, { crew = false } = {}) {
  const body = {
    title,
    description: '점심 E2E 테스트 대회',
    location: '여의도 한강공원',
    regionSido: '서울특별시',
    regionSigungu: '영등포구',
    raceDate: day(30, '08:00:00'),
    crewSaleStartAt: crew ? day(2) : null,
    crewSaleEndAt: crew ? day(9, '18:00:00') : null,
    individualOpenAt: crew ? day(10) : day(2),
    applicationCloseAt: day(20, '18:00:00'),
    refundDeadline: day(15).slice(0, 10),
    refundPolicy: '환불 기한까지 취소하면 전액 환불됩니다.',
    crewMinCount: 4,
    crewMaxCount: crew ? 10 : null,
    courses: [
      {
        courseId: null,
        name: '10K',
        courseType: 'KM_10',
        surface: 'ROAD',
        distanceKm: 10,
        timeLimitMinutes: 90,
        price: 40_000,
        kitIncluded: false,
        kitPrice: 0,
        minAge: 14,
        maxAge: null,
        crewQuota: crew ? 20 : 0,
        individualQuota: 100,
        // ProductDataInit과 같은 가짜 대표 이미지 ID. 등록 검사만 통과하고 파일 연결은 실패한다
        images: [{ fileId: 999_999_999, fileType: 'THUMBNAIL', sortNo: 0 }],
      },
    ],
  }
  const res = await seller.call<{ race?: { raceId: number }; raceId?: number }>(
    'POST',
    '/api/v1/product/races',
    body,
    {
      'Idempotency-Key': crypto.randomUUID(),
    },
  )
  expect(res.status, `대회 등록 ${JSON.stringify(res.raw)}`).toBe(201)
  const raceId = Number(res.data.race?.raceId ?? res.data.raceId)
  const courseId = Number(
    sqlOne(`select race_course_id from race_course where race_id = ${raceId} limit 1`),
  )
  return { raceId, courseId, title }
}

// 판매 시작일을 지금으로 당긴다. 판매 상태 배치(5분 주기)가 판매 중으로 바꾸며 대기열 쪽 복사본도 맞춘다
export function pullSaleToNow(raceId: number, { crew = false } = {}) {
  sql(
    crew
      ? `update race set sale_open_date = curdate(), crew_sale_start_at = now() - interval 1 minute where race_id = ${raceId}`
      : `update race set sale_open_date = curdate(), individual_open_at = now() - interval 1 minute where race_id = ${raceId}`,
  )
}

export async function waitStatus(raceId: number, status: string, timeoutMs = 6 * 60_000) {
  return eventually(
    () => sqlOne(`select sale_status from race where race_id = ${raceId}`) === status,
    timeoutMs,
  )
}

// 본인인증 없이 구매자를 더 만든다. user3 행을 복사하고 이메일·휴대폰 해시만 바꾼다(휴대폰 암호문은 그대로라 복호화된다).
// 예치금 지갑도 user3 지갑을 복사한다. 이미 있으면 그대로 쓴다.
// shortcut: 가입 이벤트가 나가지 않아 다른 서비스의 회원 복제본은 없다. 회원 생성 테스트 API가 생기면 바꾼다
export function cloneBuyer(email: string) {
  if (!sqlOne(`select member_id from member_member where email = '${email}'`)) {
    const nick = `e2e${email.split('@')[0].replace(/\D/g, '')}`
    sql(`create temporary table t as select * from member_member where email = '${USERS.user3}';
      update t set member_id = (select max(member_id) + 1 from member_member), email = '${email}', nickname = '${nick}',
        phone_hash = sha2('${email}', 256), identity_hash = sha2('id-${email}', 256);
      insert into member_member select * from t;`)
  }
  const id = memberIdOf(email)
  // 지갑이 캐시 회원 복제본을 외래 키로 가리키므로 복제본도 user3 행을 복사해 먼저 만든다
  if (!sqlOne(`select id from cash_member where id = ${id}`)) {
    sql(`create temporary table cm as select * from cash_member where id = ${memberIdOf(USERS.user3)};
      update cm set id = ${id}, nickname = 'e2e${id}';
      insert into cash_member select * from cm;`)
  }
  if (!sqlOne(`select holder_id from cash_wallet where holder_id = ${id}`)) {
    sql(`create temporary table w as select * from cash_wallet where holder_id = ${memberIdOf(USERS.user3)};
      update w set id = (select max(id) + 1 from cash_wallet), holder_id = ${id}, balance = 0;
      insert into cash_wallet select * from w;`)
  }
  return id
}
