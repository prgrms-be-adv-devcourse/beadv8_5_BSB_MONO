import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { customFetch } from '@/shared/api'

import { answerMockPass } from './identity-verification'
import { MOCK_LOGGED_IN_COOKIE } from './mock-session'

type RaceRow = {
  raceId: number
  title: string
  minPrice: number
  raceDate: string
  applicationCloseAt: string
  saleStatus: string
  regionSido: string
}
type RacePage = {
  data: { races: RaceRow[]; page: number; size: number; totalElements: number; totalPages: number }
}
const searchRaces = (query = '') =>
  customFetch<RacePage>(`http://localhost/api/v1/product/races${query}`)
const idsOf = (body: RacePage) => body.data.races.map((race) => race.raceId)

/** 체험용 PASS 본인인증을 마친 인증 토큰. 가입 목은 이 토큰에 담긴 이름 · 번호 · 생년월일로 가입시킨다 */
function mockPassToken(name: string, phone: string, birthDate: string): string {
  const verified = answerMockPass({ carrier: 'SKT', name, phone, birthDate }).body.data
  return verified?.verificationToken ?? ''
}

// 생성된 목이 등록됐는지 확인한다 (API_MOCKING=on 개발 서버도 같은 handlers를 쓴다)
describe('생성된 MSW 핸들러', () => {
  it('/races/suggestions가 /races/:raceId 핸들러에 가로채이지 않는다', async () => {
    const body = await customFetch<{ message: string }>(
      'http://localhost/api/v1/product/races/suggestions?keyword=서울',
    )
    expect(body.message).toBe('연관검색어를 조회했습니다.')
  })

  it('연관검색어는 검색어가 이름에 든 대회만 돌려준다', async () => {
    const body = await customFetch<{ data: { races: { title: string }[] } }>(
      'http://localhost/api/v1/product/races/suggestions?keyword=제주',
    )
    expect(body.data.races.map((race) => race.title)).toEqual(['제주감귤국제마라톤'])
  })
})

describe('대회 목록 목', () => {
  // 대회 날짜가 "지금"에서 며칠 뒤라 시계를 세워 둔다 (10/16 낮 12시)
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'], now: new Date('2026-10-16T12:00:00+09:00') })
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('대회 8개를 마감 임박순으로 돌려준다 (마감된 대회는 맨 뒤)', async () => {
    const body = await searchRaces()

    expect(body.data.totalElements).toBe(8)
    expect(idsOf(body)).toEqual([4, 2, 5, 1, 3, 7, 6, 10])
  })

  it('접수 중 · 마감 임박 · 마감 · 오픈 예정이 섞여 있다', async () => {
    const { data } = await searchRaces()
    const now = Date.now()
    const closeMs = (race: RaceRow) => new Date(`${race.applicationCloseAt}+09:00`).getTime()
    const closingSoon = data.races.filter(
      (r) => closeMs(r) > now && closeMs(r) - now < 3 * 86_400_000,
    )
    const closed = data.races.filter((r) => closeMs(r) <= now)

    expect(closingSoon.map((r) => r.raceId)).toEqual([4, 2])
    expect(closed.map((r) => r.raceId)).toEqual([10])
    expect(data.races.filter((r) => r.saleStatus === 'SCHEDULED').map((r) => r.raceId)).toEqual([7])
  })

  it('RACE_DATE는 대회일이 빠른 순이다', async () => {
    const body = await searchRaces('?sort=RACE_DATE')

    expect(idsOf(body)).toEqual([10, 2, 1, 5, 3, 7, 4, 6])
  })

  it('LATEST는 최근에 등록한 대회부터 돌려준다', async () => {
    const body = await searchRaces('?sort=LATEST')

    expect(idsOf(body)).toEqual([7, 6, 5, 3, 1, 2, 4, 10])
  })

  it('PRICE는 최저 참가비가 낮은 순이다', async () => {
    const prices = (await searchRaces('?sort=PRICE')).data.races.map((race) => race.minPrice)

    expect(prices).toEqual([...prices].sort((a, b) => a - b))
    expect(prices[0]).toBe(30000)
  })

  it('keyword는 이름이나 지역에 든 대회만 남긴다', async () => {
    expect(idsOf(await searchRaces(`?keyword=${encodeURIComponent('쿠키')}`))).toEqual([1])
    const busan = await searchRaces(`?keyword=${encodeURIComponent('부산')}`)
    expect(idsOf(busan)).toEqual([5])
    expect(busan.data.totalElements).toBe(1)
    expect(idsOf(await searchRaces(`?keyword=${encodeURIComponent('없는대회')}`))).toEqual([])
  })

  it('page · size로 자르고 totalElements · totalPages를 계산한다', async () => {
    const first = await searchRaces('?size=3&page=0')
    const last = await searchRaces('?size=3&page=2')

    expect(idsOf(first)).toEqual([4, 2, 5])
    expect(first.data).toMatchObject({ page: 0, size: 3, totalElements: 8, totalPages: 3 })
    expect(idsOf(last)).toEqual([6, 10])
    expect((await searchRaces('?size=3&page=5')).data.races).toEqual([])
  })

  it('오픈 예정 대회는 오픈 시각을 앞당기지 않는다', async () => {
    const { data } = await searchRaces()
    const upcoming = data.races.find((race) => race.raceId === 7) as RaceRow & {
      individualOpenAt: string
    }

    expect(new Date(`${upcoming.individualOpenAt}+09:00`).getTime()).toBeGreaterThan(Date.now())
  })
})

describe('대회 상세 목', () => {
  it('목록의 모든 대회가 상세로 열리고 종목이 있다', async () => {
    for (const raceId of [1, 2, 3, 4, 5, 6, 7, 10]) {
      const body = await customFetch<{
        data: { raceId: number; courses: { raceCourseId: number }[] }
      }>(`http://localhost/api/v1/product/races/${raceId}`)

      expect(body.data.raceId).toBe(raceId)
      expect(body.data.courses.length).toBeGreaterThan(0)
    }
  })

  it('없는 대회는 404, 숫자가 아니면 400이다', async () => {
    const missing = await fetch('http://localhost/api/v1/product/races/999')
    const invalid = await fetch('http://localhost/api/v1/product/races/abc')

    expect(missing.status).toBe(404)
    expect(invalid.status).toBe(400)
  })

  it('종목 이미지는 대회마다 다른 파일이다', async () => {
    const urlOf = async (courseId: number) =>
      (
        await customFetch<{ data: { url: string }[] }>(
          `http://localhost/api/v1/file/files?refType=RaceCourse&refId=${courseId}`,
        )
      ).data[0]?.url

    expect(await urlOf(1)).toBe('/mock/races/cookierun.webp')
    expect(await urlOf(11)).toBe('/mock/races/youth10k.webp')
  })
})

// 가짜 응답 모드에서 로그인 상태를 진짜처럼(Refresh Token 쿠키로) 흉내 내는지 확인한다.
// Node의 MSW는 응답 쿠키를 저장하지 않으므로, 브라우저처럼 받은 쿠키를 다음 요청에 직접 붙인다.
describe('인증 목', () => {
  const post = (path: string, cookie?: string) =>
    fetch(`http://localhost/api/v1/auth/${path}`, {
      method: 'POST',
      headers: cookie ? { Cookie: cookie } : {},
    })
  /** Set-Cookie에서 "이름=값"만 꺼낸다 (브라우저가 다음 요청에 붙이는 모양) */
  const cookieOf = (response: Response) => response.headers.get('set-cookie')?.split(';')[0] ?? ''
  const accessTokenOf = async (response: Response) =>
    ((await response.json()) as { data: { accessToken: string } }).data.accessToken

  it('쿠키 없이 재발급하면 401이다', async () => {
    expect((await post('reissue')).status).toBe(401)
  })

  it('로그인하면 진짜처럼 httpOnly Refresh Token 쿠키를 심는다', async () => {
    const login = await fetch('http://localhost/api/v1/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: 'runner1@crewrun.com', password: 'password1' }),
    })

    const setCookie = login.headers.get('set-cookie') ?? ''
    expect(setCookie).toMatch(/^mockRefreshToken=mock-refresh-/)
    expect(setCookie).toContain('Path=/api/v1/auth')
    expect(setCookie).toContain('HttpOnly')
    expect(setCookie).toContain('SameSite=Lax')
  })

  it('로그인 쿠키로 재발급하면 매번 새 Access Token과 새 쿠키를 준다', async () => {
    const login = await fetch('http://localhost/api/v1/auth/login', { method: 'POST' })

    const first = await post('reissue', cookieOf(login))
    const second = await post('reissue', cookieOf(first))

    expect(first.status).toBe(200)
    expect(second.status).toBe(200)
    expect(cookieOf(first)).not.toBe(cookieOf(login))
    expect(await accessTokenOf(first)).not.toBe(await accessTokenOf(second))
  })

  it('MOCK_LOGGED_IN_COOKIE를 붙이면 로그인된 채로 재발급할 수 있다', async () => {
    expect((await post('reissue', MOCK_LOGGED_IN_COOKIE)).status).toBe(200)
  })

  it('로그아웃하면 Max-Age=0으로 쿠키를 지운다', async () => {
    const logout = await post('logout', MOCK_LOGGED_IN_COOKIE)

    expect(logout.headers.get('set-cookie')).toMatch(
      /^mockRefreshToken=; Max-Age=0;.*Path=\/api\/v1\/auth/,
    )
  })
})

// 가짜 응답 모드에서 차례 기다리기(M05-Q1) → 내 차례(M05-Q2)를 볼 수 있게 대기열 순서를 흉내 내는지 확인한다.
describe('대기열 입장 목', () => {
  const ask = async () =>
    (
      await customFetch<{ data: { admitted: boolean; remainingSeconds: number } }>(
        'http://localhost/api/v1/order/queue/admission?raceId=1&raceCourseId=1',
      )
    ).data

  it('두 번은 기다리는 중으로 답하고, 세 번째에 입장해 10분 남았다고 답한다', async () => {
    expect((await ask()).admitted).toBe(false)
    expect((await ask()).admitted).toBe(false)
    const admitted = await ask()
    expect(admitted.admitted).toBe(true)
    expect(admitted.remainingSeconds).toBeGreaterThan(590)
    expect(admitted.remainingSeconds).toBeLessThanOrEqual(600)
  })
})

// 대기열 줄서기 목: 등록 → 순번이 줄어듦 → 차례 → 입장 확인 흐름이 한 번에 돌아가는지 확인한다.
describe('대기열 줄서기 목', () => {
  type Status = { data: { myRank: number; ahead: number; canEnter: boolean } }
  const base = 'http://localhost/api/v1/queue'
  const register = () =>
    customFetch<Status>(base, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ raceId: 1, raceCourseId: 1 }),
    })
  const poll = () => customFetch<Status>(`${base}/races/1/racecourses/1`)

  it('등록하면 순번을 주고, 물을 때마다 줄다가 차례가 오면 바로 입장 확인이 통과한다', async () => {
    expect((await register()).data).toMatchObject({ myRank: 3, ahead: 2, canEnter: false })
    expect((await poll()).data).toMatchObject({ ahead: 1, canEnter: false })
    expect((await poll()).data).toMatchObject({ ahead: 0, canEnter: false })
    expect((await poll()).data.canEnter).toBe(true)

    const admission = await customFetch<{ data: { admitted: boolean } }>(
      'http://localhost/api/v1/order/queue/admission?raceId=1&raceCourseId=1',
    )
    expect(admission.data.admitted).toBe(true)
  })

  it('줄을 서지 않고 순번을 물으면 404, 입장 중에 다시 등록하면 409다', async () => {
    await expect(poll()).rejects.toMatchObject({ status: 404 })

    await register()
    await poll()
    await poll()
    await poll()
    await customFetch('http://localhost/api/v1/order/queue/admission?raceId=1&raceCourseId=1')
    await expect(register()).rejects.toMatchObject({ status: 409 })
  })
})

// 서버(proxy.ts)가 화면 요청에서 로그인 여부를 알 수 있게, 가짜 로그인이 Path=/ 표시 쿠키를 같이 심고 지우는지 확인한다.
describe('가짜 로그인 표시 쿠키', () => {
  const call = (path: string, cookie?: string) =>
    fetch(`http://localhost/api/v1/auth/${path}`, {
      method: 'POST',
      headers: cookie ? { cookie } : undefined,
    })
  const cookiesOf = (response: Response) => response.headers.getSetCookie()

  it('로그인하면 Refresh Token 쿠키와 Path=/ 표시 쿠키를 함께 심는다', async () => {
    const cookies = cookiesOf(await call('login'))
    expect(cookies.some((c) => /^mockRefreshToken=.*Path=\/api\/v1\/auth/.test(c))).toBe(true)
    expect(cookies.some((c) => /^mockLoggedIn=1;.*Path=\/;/.test(c))).toBe(true)
  })

  it('로그아웃하면 두 쿠키를 모두 지운다', async () => {
    const cookies = cookiesOf(await call('logout', MOCK_LOGGED_IN_COOKIE))
    expect(cookies.some((c) => /^mockRefreshToken=; Max-Age=0/.test(c))).toBe(true)
    expect(cookies.some((c) => /^mockLoggedIn=; Max-Age=0/.test(c))).toBe(true)
  })
})

// 방금 가입한 회원은 프로필만 있고 참가권 · 예치금 · 알림이 비어 있고, 시연용 기존 회원(runner1)은 예시 데이터를 보는지 확인한다.
// 가짜 API는 서버 메모리가 아니라 쿠키로 둘을 가른다(mock-member.ts).
describe('방금 가입한 회원과 시연용 기존 회원', () => {
  const JOIN = {
    email: 'newbie@example.com',
    password: 'password1234',
    name: '박새내',
    nickname: '새내기',
    phone: '010-2222-3333',
    birthDate: '2000-01-02',
    agreedTerms: ['SERVICE', 'PRIVACY'],
    verificationToken: mockPassToken('박새내', '010-2222-3333', '2000-01-02'),
  }
  const json = (path: string, body: unknown, cookie?: string) =>
    fetch(`http://localhost${path}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...(cookie ? { Cookie: cookie } : {}) },
      body: JSON.stringify(body),
    })
  const get = (path: string, cookie?: string) =>
    fetch(`http://localhost${path}`, { headers: cookie ? { Cookie: cookie } : {} }).then(
      (response) => response.json() as Promise<{ data: Record<string, unknown> & unknown[] }>,
    )
  const pair = (response: Response, name: string) =>
    response.headers
      .getSetCookie()
      .find((c) => c.startsWith(`${name}=`))
      ?.split(';')[0]

  /** 가입 → 같은 이메일로 로그인해서 받은 방금 가입한 회원의 쿠키 */
  async function signUpAndLogin() {
    const joined = await json('/api/v1/auth/join', JOIN)
    const login = await json(
      '/api/v1/auth/login',
      { email: JOIN.email, password: JOIN.password },
      pair(joined, 'mockJoined'),
    )
    return { joined, login, cookie: pair(login, 'mockMember') ?? '' }
  }

  it('가입하면 프로필을 쿠키에 담고, 같은 이메일로 로그인하면 방금 가입한 회원이 된다', async () => {
    const { joined, login, cookie } = await signUpAndLogin()

    expect(pair(joined, 'mockJoined')).toBeDefined()
    expect(cookie).toMatch(/^mockMember=/)
    const body = (await login.json()) as { data: { member: { nickname: string; email: string } } }
    expect(body.data.member).toMatchObject({ nickname: '새내기', email: JOIN.email })
  })

  it('방금 가입한 회원은 가입 때 넣은 정보를 보고, 참가권 · 예치금 · 알림이 비어 있다', async () => {
    const { cookie } = await signUpAndLogin()

    const me = await get('/api/v1/member/members/me', cookie)
    expect(me.data).toMatchObject({
      email: JOIN.email,
      name: '박새내',
      nickname: '새내기',
      phone: '010-2222-3333',
      birthDate: '2000-01-02',
    })
    const orders = await get('/api/v1/order/orders?page=0&size=100', cookie)
    expect(orders.data.orders).toEqual([])
    const balance = await get('/api/v1/cash/wallets/balance', cookie)
    expect(balance.data.balance).toBe(0)
    const notices = await get('/api/v1/notification/notifications', cookie)
    expect(notices.data).toEqual([])
  })

  it('시연용 기존 회원(쿠키 없음, runner1 로그인)은 예시 데이터를 본다', async () => {
    const me = await get('/api/v1/member/members/me')
    expect(me.data).toMatchObject({ email: 'runner1@crewrun.com', nickname: '러너1' })
    const orders = await get('/api/v1/order/orders?page=0&size=100')
    expect((orders.data.orders as unknown[]).length).toBeGreaterThan(0)
    const balance = await get('/api/v1/cash/wallets/balance')
    expect(balance.data.balance).toBe(5000)
    const notices = await get('/api/v1/notification/notifications')
    expect(notices.data).toHaveLength(4)

    // 방금 가입한 회원의 쿠키가 남아 있어도 runner1로 로그인하면 지운다
    const { joined } = await signUpAndLogin()
    const login = await json(
      '/api/v1/auth/login',
      { email: 'runner1@crewrun.com', password: 'password1' },
      pair(joined, 'mockJoined'),
    )
    expect(login.headers.getSetCookie().some((c) => /^mockMember=; Max-Age=0/.test(c))).toBe(true)
  })

  it('로그아웃하면 방금 가입한 회원 쿠키도 지운다', async () => {
    const logout = await json('/api/v1/auth/logout', {})
    expect(logout.headers.getSetCookie().some((c) => /^mockMember=; Max-Age=0/.test(c))).toBe(true)
  })

  it('방금 가입한 회원은 정보를 고치면 다음 조회부터 고친 값이 보인다', async () => {
    const { cookie } = await signUpAndLogin()
    const modified = await fetch('http://localhost/api/v1/member/members/me', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', Cookie: cookie },
      body: JSON.stringify({ nickname: '새내기2' }),
    })
    const next = pair(modified, 'mockMember') ?? ''
    expect(((await modified.json()) as { data: { nickname: string } }).data.nickname).toBe(
      '새내기2',
    )
    expect((await get('/api/v1/member/members/me', next)).data.nickname).toBe('새내기2')
  })
})

// 코드리뷰 재현: 새 회원끼리 잔액을 함께 쓰거나, 정보를 고친 뒤 다시 로그인하면 고친 값이 사라지던 경우.
// 브라우저 쿠키 저장소를 흉내 내는 jar를 브라우저마다 하나씩 둔다.
describe('새 회원 쿠키 저장소가 둘일 때', () => {
  function browser() {
    const jar = new Map<string, string>()
    const cookie = () => [...jar].map(([name, value]) => `${name}=${value}`).join('; ')
    const absorb = (response: Response) => {
      for (const line of response.headers.getSetCookie()) {
        const [pair, ...attributes] = line.split(';')
        const [name, value] = pair.split('=')
        if (attributes.some((a) => a.trim() === 'Max-Age=0')) jar.delete(name)
        else jar.set(name, value)
      }
      return response
    }
    const call = async (
      method: string,
      path: string,
      body?: unknown,
      headers: Record<string, string> = {},
    ) =>
      absorb(
        await fetch(`http://localhost${path}`, {
          method,
          headers: { 'Content-Type': 'application/json', Cookie: cookie(), ...headers },
          body: body === undefined ? undefined : JSON.stringify(body),
        }),
      )
    const data = async (
      method: string,
      path: string,
      body?: unknown,
      headers?: Record<string, string>,
    ) =>
      (
        (await call(method, path, body, headers)).json() as Promise<{
          data: Record<string, unknown>
        }>
      ).then((json) => json.data)
    return { call, data }
  }
  const join = (email: string, nickname: string) => ({
    email,
    password: 'password1234',
    name: '박새내',
    nickname,
    phone: '010-2222-3333',
    birthDate: '2000-01-02',
    agreedTerms: ['SERVICE', 'PRIVACY'],
    verificationToken: mockPassToken('박새내', '010-2222-3333', '2000-01-02'),
  })
  async function signUp(b: ReturnType<typeof browser>, email: string, nickname: string) {
    await b.call('POST', '/api/v1/auth/join', join(email, nickname))
    await b.call('POST', '/api/v1/auth/login', { email, password: 'password1234' })
  }

  it('한 새 회원이 충전해도 다른 새 회원의 잔액은 0원이다', async () => {
    const a = browser()
    const b = browser()
    await signUp(a, 'a@example.com', '에이')
    expect((await a.data('GET', '/api/v1/cash/wallets/balance')).balance).toBe(0)

    const charge = await a.data('POST', '/api/v1/cash/charges/confirm', {
      paymentKey: 'pk',
      orderId: 'order-a',
      amount: 50_000,
    })
    expect(charge.balance).toBe(50_000)
    expect((await a.data('GET', '/api/v1/cash/wallets/balance')).balance).toBe(50_000)

    await signUp(b, 'b@example.com', '비')
    expect((await b.data('GET', '/api/v1/cash/wallets/balance')).balance).toBe(0)
    // 시연용 기존 회원(쿠키 없음)의 잔액에도 영향이 없다
    expect((await browser().data('GET', '/api/v1/cash/wallets/balance')).balance).toBe(5000)
  })

  it('새 회원의 결제와 환급은 자기 잔액만 줄인다', async () => {
    const a = browser()
    await signUp(a, 'c@example.com', '씨')
    await a.call('POST', '/api/v1/cash/charges/confirm', {
      paymentKey: 'pk',
      orderId: 'order-c',
      amount: 60_000,
    })
    const pay = await a.call('POST', '/api/v1/order/orders/1/payment')
    expect(pay.status).toBe(200)
    expect((await a.data('GET', '/api/v1/cash/wallets/balance')).balance).toBe(10_000)

    const refund = await a.data(
      'POST',
      '/api/v1/cash/withdrawals',
      {},
      { 'Idempotency-Key': 'k-c' },
    )
    expect(refund.withdrawnAmount).toBe(10_000)
    expect((await a.data('GET', '/api/v1/cash/wallets/balance')).balance).toBe(0)
  })

  it('정보를 고친 뒤 로그아웃하고 다시 로그인해도 고친 값이 남는다', async () => {
    const a = browser()
    await signUp(a, 'd@example.com', 'original')
    const edited = await a.data('PUT', '/api/v1/member/members/me', { nickname: 'edited' })
    expect(edited.nickname).toBe('edited')

    await a.call('POST', '/api/v1/auth/logout')
    const login = await a.data('POST', '/api/v1/auth/login', {
      email: 'd@example.com',
      password: 'password1234',
    })
    expect((login.member as { nickname: string }).nickname).toBe('edited')
    expect((await a.data('GET', '/api/v1/member/members/me')).nickname).toBe('edited')
  })
})

describe('체험용 PASS 본인인증 목', () => {
  const post = (path: string, body: unknown) =>
    fetch(`http://localhost${path}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })

  it('입력한 이름 · 생년월일 · 휴대폰 번호를 확인된 정보로 돌려준다', async () => {
    const res = await post('/api/v1/auth/identity-verifications/pass', {
      carrier: 'KT',
      name: '김러너',
      birthDate: '1995-04-12',
      phone: '010-1234-5678',
    })
    expect(res.status).toBe(200)
    const body = (await res.json()) as { data: Record<string, unknown> }
    expect(body.data).toMatchObject({
      name: '김러너',
      birthDate: '1995-04-12',
      phone: '010-1234-5678',
      verificationToken: expect.stringMatching(/^mock-verification\./),
    })
  })

  it('통신사를 고르지 않았으면 400이다', async () => {
    const res = await post('/api/v1/auth/identity-verifications/pass', {
      name: '김러너',
      birthDate: '1995-04-12',
      phone: '010-1234-5678',
    })
    expect(res.status).toBe(400)
  })

  it('가입 요청에 인증 토큰이 없으면 400 AUTH_VERIFICATION_REQUIRED, 있으면 그 정보로 가입한다', async () => {
    const join = {
      email: 'verify@example.com',
      password: 'password1234',
      nickname: '새내기',
      agreedTerms: ['SERVICE', 'PRIVACY'],
    }
    const without = await post('/api/v1/auth/join', join)
    expect(without.status).toBe(400)
    expect(((await without.json()) as { code: string }).code).toBe('AUTH_VERIFICATION_REQUIRED')

    const ok = await post('/api/v1/auth/join', {
      ...join,
      verificationToken: mockPassToken('박새내', '010-2222-3333', '2000-01-02'),
    })
    expect(ok.status).toBe(201)
  })
})
