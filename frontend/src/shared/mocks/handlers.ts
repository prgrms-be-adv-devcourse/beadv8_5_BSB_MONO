import { http, HttpResponse, type RequestHandler } from 'msw'

import { getBukangCashAPIMock } from '@/shared/api/generated/cash/cash.msw'
import {
  getBukangFileAPIMock,
  getFindByRefMockHandler,
  getFindByRefResponseMock,
} from '@/shared/api/generated/file/file.msw'
import {
  getBukangMemberAPIMock,
  getLoginResponseMock,
  getReissueResponseMock,
} from '@/shared/api/generated/member/member.msw'
import {
  getBukangOrderAPIMock,
  getCheckoutMockHandler,
  getCheckoutResponseMock,
  getCreateMockHandler,
  getCreateResponseMock,
  getFindAdmissionMockHandler,
  getFindById1MockHandler,
  getFindById1ResponseMock,
  getFindCartMockHandler,
  getFindCartResponseMock,
  getFindMineMockHandler,
  getFindMineResponseMock,
  getPayResponseMock,
  getRemoveItemMockHandler,
  getRemoveItemResponseMock,
} from '@/shared/api/generated/order/order.msw'
import { getBukangPayoutAPIMock } from '@/shared/api/generated/payout/payout.msw'
import {
  getBukangProductAPIMock,
  getFindMineMockHandler as getFindMyRacesMockHandler,
  getSearchMockHandler,
  getSuggestMockHandler,
} from '@/shared/api/generated/product/product.msw'
import type { SearchParams } from '@/shared/api/generated/product/product.schemas'

import { crewHandlers } from './crews'
import { fileUploadHandlers } from './file-upload'
import {
  clearMockBalanceCookie,
  clearMockMemberCookie,
  DEMO_MEMBER_EMAIL,
  freshMemberOf,
  issueMockJoinedCookie,
  issueMockMemberCookie,
  joinedProfileOf,
  profileFromJoinBody,
  type MockMemberProfile,
} from './mock-member'
import {
  clearMockLoginMarkCookie,
  clearMockRefreshCookie,
  issueMockLoginMarkCookie,
  issueMockRefreshCookie,
  MOCK_REFRESH_COOKIE_NAME,
} from './mock-session'
import { notificationHandlers } from './notifications'
import { findMockRace, getMockRaces, mockImageOfCourse, searchMockRaces } from './races'
import { resetMockKitsForTesting, sellerKitHandlers } from './seller-kits'
import { balanceCookieHeaders, payFromMockWallet, walletHandlers } from './wallet'

/** 경로 변수(:raceId) 수. 적은 것부터 둬야 /races/suggestions가 /races/:raceId에 먼저 걸리지 않는다. */
function pathParamCount(handler: RequestHandler): number {
  const path = (handler.info as { path?: unknown }).path
  return typeof path === 'string' ? (path.match(/\/:/g) ?? []).length : 0
}

/**
 * 명세 예시의 이미지 주소는 실제로 없는 S3 주소라(next/image 허용 호스트도 아니다) 목에서는 public/mock 이미지로 바꾼다.
 * 생성된 핸들러보다 앞에 둬서 먼저 걸리게 한다.
 */
const fileImageHandler = getFindByRefMockHandler(({ request }) => {
  const example = getFindByRefResponseMock()
  const refId = Number(new URL(request.url).searchParams.get('refId'))
  const image = mockImageOfCourse(refId) ?? '/mock/races/cookierun.webp'
  return {
    ...example,
    data: example.data?.map((file) => ({ ...file, url: image })),
  }
})

/**
 * 로그인 상태를 진짜처럼 흉내 내는 인증 목. 생성된 핸들러보다 앞에 둬서 먼저 걸리게 한다.
 * 진짜 서버처럼 로그인하면 Refresh Token 쿠키(가짜 이름)를 심고, 로그아웃하면 지운다.
 * 재발급은 요청 쿠키에 그 값이 있을 때만 성공하고(쿠키도 새로 바꿔 준다) 없으면 401.
 * 그래서 로그인하지 않고 새로고침하면 로그아웃 상태로 보인다.
 */
/** 내 정보 응답 칸. 방금 가입한 회원의 프로필로 만든다 (시연용 기존 회원은 id 1) */
function toMemberDto(profile: MockMemberProfile) {
  const now = toKstDateTime(Date.now())
  return { id: 2, createDate: now, modifyDate: now, ...profile }
}

/** 로그인 · 재발급 · 로그아웃이 심고 지우는 쿠키 묶음. Refresh Token 쿠키를 먼저 둔다. */
function withCookies(...values: string[]) {
  const headers = new Headers()
  for (const value of values) headers.append('Set-Cookie', value)
  return headers
}

const authHandlers = [
  // 가입 때 심은 프로필과 같은 이메일로 로그인하면 방금 가입한 회원이다(내 정보는 그 프로필, 참가권 · 예치금 · 알림은 비어 있음).
  // 그 밖에는(runner1 · 이메일 없음 · 모르는 이메일) 시연용 기존 회원이다.
  http.post('*/api/v1/auth/login', async ({ request, cookies }) => {
    const body = (await request.json().catch(() => ({}))) as { email?: unknown }
    const joined = joinedProfileOf(cookies)
    const fresh =
      joined && body.email === joined.email && body.email !== DEMO_MEMBER_EMAIL ? joined : null
    const example = getLoginResponseMock()
    return HttpResponse.json(
      fresh ? { ...example, data: { ...example.data, member: toMemberDto(fresh) } } : example,
      {
        headers: withCookies(
          issueMockRefreshCookie(),
          issueMockLoginMarkCookie(),
          fresh ? issueMockMemberCookie(fresh) : clearMockMemberCookie(),
        ),
      },
    )
  }),
  http.post('*/api/v1/auth/reissue', ({ cookies }) => {
    if (!cookies[MOCK_REFRESH_COOKIE_NAME]) {
      return HttpResponse.json(
        { status: 401, message: 'Refresh Token이 없습니다. 다시 로그인해주세요.', data: null },
        { status: 401 },
      )
    }
    const example = getReissueResponseMock()
    return HttpResponse.json(
      {
        ...example,
        data: {
          ...example.data,
          accessToken: `${example.data?.accessToken}.${crypto.randomUUID()}`,
        },
      },
      { headers: withCookies(issueMockRefreshCookie(), issueMockLoginMarkCookie()) },
    )
  }),
  http.post(
    '*/api/v1/auth/logout',
    () =>
      new HttpResponse(null, {
        headers: withCookies(
          clearMockRefreshCookie(),
          clearMockLoginMarkCookie(),
          clearMockMemberCookie(),
        ),
      }),
  ),
]

/**
 * 내 정보 목. 방금 가입한 회원이면 가입 때 넣은 프로필을, 아니면 명세 예시(시연용 기존 회원 runner1)를 돌려준다.
 * 방금 가입한 회원이 정보를 고치면 쿠키의 프로필을 고쳐 다음 조회부터 그 값이 보인다.
 */
const meHandlers = [
  http.get('*/api/v1/member/members/me', ({ cookies }) => {
    const profile = freshMemberOf(cookies)
    if (!profile) return undefined
    return HttpResponse.json({
      status: 200,
      message: '내 정보를 조회했습니다.',
      data: toMemberDto(profile),
    })
  }),
  http.put('*/api/v1/member/members/me', async ({ request, cookies }) => {
    const profile = freshMemberOf(cookies)
    if (!profile) return undefined
    const body = (await request.json()) as Partial<MockMemberProfile>
    const next: MockMemberProfile = {
      ...profile,
      name: body.name ?? profile.name,
      nickname: body.nickname ?? profile.nickname,
      phone: body.phone ?? profile.phone,
      birthDate: body.birthDate ?? profile.birthDate,
    }
    // 로그아웃 뒤 다시 로그인해도 고친 값이 남도록 가입 때 프로필(mockJoined)도 같이 고쳐 덮어쓴다.
    // 이 요청 경로에는 안 실리는 Path=/api/v1/auth 쿠키지만, 응답의 Set-Cookie는 그 경로로 저장된다.
    return HttpResponse.json(
      { status: 200, message: '내 정보를 수정했습니다.', data: toMemberDto(next) },
      { headers: withCookies(issueMockMemberCookie(next), issueMockJoinedCookie(next)) },
    )
  }),
]

/**
 * 탈퇴 목. 성공(200)이 기본이다. 실패(예치금·참가권이 남음, 확인 실패)는 테스트에서 server.use로 바꿔 끼운다.
 * 생성된 핸들러보다 앞에 둬서 먼저 걸리게 한다.
 */
const withdrawHandlers = [
  http.delete('*/api/v1/member/members/me', () =>
    HttpResponse.json({ status: 200, message: '탈퇴했습니다.', data: null }),
  ),
]

/**
 * 가입 목. 서버처럼 필수 약관(SERVICE, PRIVACY)이 빠지면 400 MEMBER_REQUIRED_TERMS_NOT_AGREED로 답한다.
 * 이메일 중복 확인은 이메일에 "taken"이 들어 있으면 이미 가입된 것으로 본다 (그 밖에는 사용 가능).
 * 생성된 핸들러보다 앞에 둬서 먼저 걸리게 한다.
 */
const signupHandlers = [
  http.post('*/api/v1/auth/join', async ({ request }) => {
    const body = (await request.json()) as Record<string, unknown> & { agreedTerms?: unknown }
    const agreed = Array.isArray(body.agreedTerms) ? body.agreedTerms : null
    if (!agreed) {
      return HttpResponse.json(
        {
          status: 400,
          code: 'INVALID_REQUEST',
          message: '동의한 약관 목록을 작성해주세요.',
          data: null,
        },
        { status: 400 },
      )
    }
    if (!agreed.includes('SERVICE') || !agreed.includes('PRIVACY')) {
      return HttpResponse.json(
        {
          status: 400,
          code: 'MEMBER_REQUIRED_TERMS_NOT_AGREED',
          message: '필수 약관에 동의해야 가입할 수 있습니다.',
          data: null,
        },
        { status: 400 },
      )
    }
    // 가입한 프로필을 쿠키에 담아 두면 같은 이메일로 로그인할 때 그 정보로 시작한다 (mock-member.ts)
    const profile = profileFromJoinBody(body)
    return HttpResponse.json(
      { status: 201, message: 'Member Joined Successfully', data: null },
      {
        status: 201,
        // 새 계정이라 잔액은 0원에서 시작한다
        headers: profile
          ? withCookies(issueMockJoinedCookie(profile), clearMockBalanceCookie())
          : undefined,
      },
    )
  }),
  http.get('*/api/v1/member/members/email-availability', ({ request }) => {
    const email = new URL(request.url).searchParams.get('email') ?? ''
    if (!email.includes('@')) {
      return HttpResponse.json(
        {
          status: 400,
          code: 'INVALID_REQUEST',
          message: '이메일 형식에 맞게 작성해주세요.',
          data: null,
        },
        { status: 400 },
      )
    }
    return HttpResponse.json({
      status: 200,
      message: '이메일 사용 가능 여부를 확인했습니다.',
      data: { available: !email.includes('taken') },
    })
  }),
]

/**
 * 내 참가권 목. 생성된 주문 목록 예시는 결제 대기 · 기한 만료 주문뿐이라 참가권이 하나도 안 보인다.
 * 결제한 주문(주문 상세 예시와 같은 1번)을 맨 앞에 더한다. 생성된 핸들러보다 앞에 둔다.
 */
const myOrdersHandler = getFindMineMockHandler(({ cookies }) => {
  const example = getFindMineResponseMock()
  // 방금 가입한 회원은 주문이 없다
  if (freshMemberOf(cookies)) {
    return {
      ...example,
      data: { ...example.data, orders: [], page: 0, totalElements: 0, totalPages: 0 },
    }
  }
  const paid = {
    id: 1,
    createDate: '2026-10-12T10:00:00',
    orderNo: 'OD-20261012-0001',
    status: 'PAID' as const,
    totalAmount: 50000,
    paymentDueAt: '2026-10-12T10:10:00',
    itemName: '2026 서울마라톤 10K',
    itemCount: 1,
  }
  return { ...example, data: { ...example.data, orders: [paid, ...(example.data?.orders ?? [])] } }
})
/** 그 시각(ms)의 한국 시간 (백엔드처럼 시간대 없는 2026-10-12T10:10:00 모양) */
function toKstDateTime(ms: number): string {
  return new Date(ms + 9 * 60 * 60 * 1000).toISOString().slice(0, 19)
}

/** 지금부터 minutes분 뒤의 한국 시간 */
function kstMinutesFromNow(minutes: number): string {
  return toKstDateTime(Date.now() + minutes * 60 * 1000)
}

/** 대기열 목: 입장하기 전에 기다리는 중이라고 답하는 횟수, 입장 시간(정책 4장 2-4의 10분) */
const MOCK_QUEUE_WAITING_ANSWERS = 2
const MOCK_ADMISSION_MS = 10 * 60 * 1000

/** (대회, 종목)마다 입장 여부를 물은 횟수와 입장 끝 시각 */
const mockQueue = new Map<string, { asked: number; endsAt?: number }>()

/** 대기열 줄서기 목: 등록한 뒤 순번을 묻는 횟수 (처음 순번의 앞 인원만큼 줄어들고, 그다음에 입장 가능) */
const MOCK_QUEUE_AHEAD = 2
const MOCK_QUEUE_TOTAL = 10

/** (대회, 종목)마다 등록한 줄. polls는 순번을 물은 횟수, entered는 입장 가능이 된 뒤 */
const mockQueuePosition = new Map<string, { polls: number; entered: boolean }>()

/** 테스트 사이에 키트 발송 · 배송 완료 상태가 넘어가지 않게 처음으로 돌린다. */
export { resetMockKitsForTesting }

/** 테스트 사이에 대기열 순서가 넘어가지 않게 처음(아무도 줄 서지 않음)으로 돌린다. */
export function resetMockQueueForTesting() {
  mockQueue.clear()
  mockQueuePosition.clear()
}

/**
 * 대기열 입장 상태 목. 차례 기다리기(M05-Q1)를 볼 수 있게 처음 두 번은 기다리는 중으로 답하고,
 * 세 번째에 입장해 10분 뒤 끝난다. 그동안은 같은 끝 시각을 돌려준다. 끝난 뒤 다시 물으면 처음부터 다시 줄을 선다.
 */
const queueAdmissionHandler = getFindAdmissionMockHandler(({ request }) => {
  const url = new URL(request.url)
  const key = `${url.searchParams.get('raceId')}:${url.searchParams.get('raceCourseId')}`
  const now = Date.now()
  const saved = mockQueue.get(key)
  const expired = saved?.endsAt !== undefined && saved.endsAt <= now
  const current = saved && !expired ? saved : { asked: 0 }
  mockQueue.set(key, current)
  if (current.endsAt === undefined) {
    // 대기열에서 차례가 온 사람은 기다리지 않고 바로 입장한다. 줄을 서지 않고 물으면 처음 두 번은 기다리는 중이다.
    const myTurn = mockQueuePosition.get(key)?.entered === true
    if (!myTurn && current.asked < MOCK_QUEUE_WAITING_ANSWERS) {
      current.asked += 1
      return {
        status: 200,
        message: '대기열에 입장 중이 아닙니다.',
        data: { admitted: false, admissionEndsAt: null, remainingSeconds: 0 },
      }
    }
    current.endsAt = now + MOCK_ADMISSION_MS
  }
  return {
    status: 200,
    message: '대기열에 입장 중입니다.',
    data: {
      admitted: true,
      admissionEndsAt: toKstDateTime(current.endsAt),
      remainingSeconds: Math.ceil((current.endsAt - now) / 1000),
    },
  }
})

const queueKey = (raceId: unknown, raceCourseId: unknown) => `${raceId}:${raceCourseId}`

const queueStatus = (ahead: number, canEnter: boolean) => ({
  myRank: canEnter ? 0 : ahead + 1,
  ahead: canEnter ? 0 : ahead,
  total: canEnter ? 0 : MOCK_QUEUE_TOTAL,
  canEnter,
})

const queueError = (status: number, message: string) =>
  HttpResponse.json({ status, message, data: null }, { status })

/**
 * 대기열(queue-service) 줄서기 목. 등록하면 앞에 MOCK_QUEUE_AHEAD명이 있고, 순번을 물을 때마다 한 명씩 줄다가
 * 그다음 물을 때 입장 가능(canEnter)이 된다. 입장 시간이 남아 있을 때 다시 등록하면 진짜처럼 409(이미 입장)다.
 * 줄을 서지 않고 순번을 물으면 404다.
 */
const queueHandlers = [
  http.post('*/api/v1/queue', async ({ request }) => {
    const body = (await request.json()) as { raceId?: number; raceCourseId?: number }
    const key = queueKey(body.raceId, body.raceCourseId)
    const admission = mockQueue.get(key)
    const admitted = admission?.endsAt !== undefined && admission.endsAt > Date.now()
    if (admitted && mockQueuePosition.get(key)?.entered) {
      return queueError(409, '이미 입장한 회원입니다.')
    }
    mockQueuePosition.set(key, { polls: 0, entered: false })
    mockQueue.delete(key)
    return HttpResponse.json({
      status: 200,
      message: '대기열에 등록했습니다.',
      data: queueStatus(MOCK_QUEUE_AHEAD, false),
    })
  }),
  http.get('*/api/v1/queue/races/:raceId/racecourses/:raceCourseId', ({ params }) => {
    const key = queueKey(params.raceId, params.raceCourseId)
    const position = mockQueuePosition.get(key)
    if (!position) return queueError(404, '대기열에 없는 회원입니다.')
    position.polls += 1
    const ahead = MOCK_QUEUE_AHEAD - position.polls
    position.entered = position.entered || ahead < 0
    return HttpResponse.json({
      status: 200,
      message: '대기열 상태를 조회했습니다.',
      data: queueStatus(Math.max(ahead, 0), position.entered),
    })
  }),
]

/**
 * 명세 예시는 주문 생성 · 장바구니 주문 · 결제 · 조회가 모두 주문 1이다. 그런데 결제 화면은 주문 1이 결제 대기여야 하고,
 * 내 참가권(위 myOrdersHandler)은 주문 1이 결제한 주문이어야 한다.
 * 그래서 진짜 서버처럼 주문을 만들면 결제 대기로, 결제하면 결제 완료로 바꿔 두고 조회는 그 상태대로 돌려준다.
 * 처음에는 결제 완료(내 참가권에 보이는 주문)다.
 */
let mockOrderAwaitingPayment = false

/** 테스트 사이에 주문 1의 상태가 넘어가지 않게 처음(결제 완료)으로 돌린다. */
export function resetMockOrderForTesting() {
  mockOrderAwaitingPayment = false
}

/**
 * 명세 예시의 주문은 결제 기한이 이미 지난 시각이라 결제 화면이 바로 시간 초과가 된다.
 * 가짜 응답 모드에서 결제 화면을 볼 수 있게 주문 생성 · 결제 대기 주문 조회는 결제 대기 · 기한 10분 뒤로 돌려준다.
 * 생성된 핸들러보다 앞에 둬서 먼저 걸리게 한다.
 */
/**
 * 명세 예시의 대회는 개인 예매 오픈이 2026-10-20이라, 그 전에는 가짜 응답 모드에서 신청 · 대기열 흐름을 볼 수 없다.
 * 오픈이 아직 안 왔으면 어제 연 것으로 돌려준다(접수 마감 · 대회 날은 예시 그대로). 생성된 핸들러보다 앞에 둔다.
 */
function openedForMock<T extends { individualOpenAt?: string | null; saleStatus?: string }>(
  race: T,
): T {
  // 판매 예정 대회(오픈 예정 카드를 보여 주려고 일부러 둔 것)는 그대로 둔다
  if (race.saleStatus === 'SCHEDULED') return race
  const openAt = race.individualOpenAt
  if (!openAt || new Date(`${openAt}+09:00`).getTime() <= Date.now()) return race
  return { ...race, individualOpenAt: kstMinutesFromNow(-24 * 60) }
}

const raceHandlers = [
  // /races/:raceId가 /races/suggestions · /races/mine을 가로채지 않게 고정 경로를 먼저 둔다
  getSuggestMockHandler(({ request }) => {
    const keyword = (new URL(request.url).searchParams.get('keyword') ?? '').trim().toLowerCase()
    const races = getMockRaces().filter((race) =>
      (race.detail.title ?? '').toLowerCase().includes(keyword),
    )
    return {
      status: 200,
      message: '연관검색어를 조회했습니다.',
      data: {
        races: races
          .slice(0, 5)
          .map(({ detail }) => ({ raceId: detail.raceId, title: detail.title })),
        courses: races
          .flatMap(({ detail }) =>
            (detail.courses ?? []).map((course) => ({
              raceCourseId: course.raceCourseId,
              raceId: detail.raceId,
              label: `${detail.title} ${course.name}`,
            })),
          )
          .slice(0, 5),
      },
    }
  }),
  getFindMyRacesMockHandler(),
  // 가짜 대회 목록(races.ts)에 있는 대회만 열린다. 없는 번호는 진짜 서버처럼 404, 숫자가 아니면 400
  http.get('*/api/v1/product/races/:raceId', ({ params }) => {
    const raceId = Number(params.raceId)
    if (!Number.isInteger(raceId) || raceId <= 0) {
      return HttpResponse.json(
        { status: 400, code: 'INVALID_REQUEST', message: '잘못된 요청입니다.', data: null },
        { status: 400 },
      )
    }
    const race = findMockRace(raceId)
    if (!race) {
      return HttpResponse.json(
        { status: 404, code: 'RACE_NOT_FOUND', message: '대회를 찾을 수 없습니다.', data: null },
        { status: 404 },
      )
    }
    return HttpResponse.json({
      status: 200,
      message: '대회를 조회했습니다.',
      data: openedForMock(race.detail),
    })
  }),
  getSearchMockHandler(({ request }) => {
    const query = new URL(request.url).searchParams
    const sort = query.get('sort') as SearchParams['sort']
    const result = searchMockRaces({
      keyword: query.get('keyword') ?? undefined,
      sort: sort ?? undefined,
      page: query.has('page') ? Number(query.get('page')) : undefined,
      size: query.has('size') ? Number(query.get('size')) : undefined,
    })
    return {
      status: 200,
      message: '대회 목록을 조회했습니다.',
      data: { ...result, races: result.races?.map(openedForMock) },
    }
  }),
]

const orderHandlers = [
  queueAdmissionHandler,
  getCreateMockHandler(() => {
    mockOrderAwaitingPayment = true
    const example = getCreateResponseMock()
    return { ...example, data: { ...example.data, paymentDueAt: kstMinutesFromNow(10) } }
  }),
  getCheckoutMockHandler(() => {
    mockOrderAwaitingPayment = true
    return getCheckoutResponseMock()
  }),
  // 장바구니에는 한 종목만 담긴다. 명세 예시는 두 항목이라, 대회 1의 10K(종목 1) 하나만 남겨 돌려준다.
  getFindCartMockHandler(() => {
    const example = getFindCartResponseMock()
    const items = (example.data?.items ?? []).filter((item) => item.raceCourseId === 1)
    return { ...example, data: { ...example.data, items } }
  }),
  getRemoveItemMockHandler(() => {
    const example = getRemoveItemResponseMock()
    return { ...example, data: { ...example.data, items: [] } }
  }),
  // 진짜 서버처럼 예치금(wallet.ts)에서 주문 금액을 뺀다. 모자라면 409 ORDER_INSUFFICIENT_BALANCE + 부족 금액
  http.post('*/api/v1/order/orders/:orderId/payment', ({ cookies }) => {
    const paid = getPayResponseMock()
    const { shortage, next } = payFromMockWallet(paid.data?.totalAmount ?? 0, cookies)
    if (shortage > 0) {
      return HttpResponse.json(
        {
          status: 409,
          code: 'ORDER_INSUFFICIENT_BALANCE',
          message: `예치금이 ${shortage.toLocaleString('ko-KR')}원 부족합니다. 충전한 뒤 결제 기한 안에 다시 결제해주세요.`,
          data: { shortage },
        },
        { status: 409 },
      )
    }
    mockOrderAwaitingPayment = false
    return HttpResponse.json(paid, { headers: balanceCookieHeaders(cookies, next) })
  }),
  getFindById1MockHandler(() => {
    if (!mockOrderAwaitingPayment) return getFindById1ResponseMock()
    const example = getCreateResponseMock()
    return { ...example, data: { ...example.data, paymentDueAt: kstMinutesFromNow(10) } }
  }),
]

/** Idempotency-Key 헤더를 꼭 받는 요청. 백엔드가 이 헤더 없이 오면 400으로 거절한다. */
const IDEMPOTENT_POSTS = [
  '*/api/v1/product/races',
  '*/api/v1/order/orders',
  '*/api/v1/order/cart/checkout',
  '*/api/v1/cash/withdrawals',
]
const IDEMPOTENCY_KEY_MAX = 64

/**
 * 진짜 서버처럼 Idempotency-Key 헤더가 없거나 1~64자가 아니면 400으로 답한다.
 * 헤더가 맞으면 아무것도 돌려주지 않아 다음 핸들러(실제 응답 목)로 넘어간다. 맨 앞에 둔다.
 */
const idempotencyKeyHandlers = IDEMPOTENT_POSTS.map((path) =>
  http.post(path, ({ request }) => {
    const key = request.headers.get('Idempotency-Key')
    if (key === null) {
      return badIdempotencyKey('필수 요청 헤더(Idempotency-Key)가 없습니다.')
    }
    if (key.length < 1 || key.length > IDEMPOTENCY_KEY_MAX) {
      return badIdempotencyKey('Idempotency-Key는 1~64자로 보내주세요.')
    }
  }),
)

function badIdempotencyKey(message: string) {
  return HttpResponse.json(
    { status: 400, code: 'INVALID_REQUEST', message, data: null },
    { status: 400 },
  )
}

/** orval이 서비스별 명세(openapi/*.json)의 응답 예시로 만든 목 */
const generatedHandlers = [
  ...getBukangMemberAPIMock(),
  ...getBukangCashAPIMock(),
  ...getBukangPayoutAPIMock(),
  ...getBukangFileAPIMock(),
  ...getBukangOrderAPIMock(),
  ...getBukangProductAPIMock(),
].sort((a, b) => pathParamCount(a) - pathParamCount(b))

/**
 * 모든 MSW 핸들러 목록. 개발 서버(/api 라우트)와 테스트가 같이 쓴다.
 * 테스트에서 응답을 바꿀 때는 server.use(getXxxMockHandler(응답))로 덮어쓴다.
 */
export const handlers = [
  // 목 파이프라인이 살아있는지 확인하는 용도
  http.get('*/api/health', () => HttpResponse.json({ status: 'ok', mocked: true })),
  ...idempotencyKeyHandlers,
  fileImageHandler,
  ...authHandlers,
  ...meHandlers,
  ...crewHandlers,
  ...notificationHandlers,
  ...withdrawHandlers,
  ...signupHandlers,
  ...fileUploadHandlers,
  myOrdersHandler,
  ...raceHandlers,
  ...queueHandlers,
  ...orderHandlers,
  ...walletHandlers,
  ...sellerKitHandlers,
  ...generatedHandlers,
]
