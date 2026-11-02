// @vitest-environment node
// 서버 코드라 Node 환경에서 돈다. customFetch가 서버 경로(BACKEND_URL)로 관리자 확인을 보내고 MSW가 받는다.
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { mockGa4Reporter, server } from '@/shared/testing'

const ga4 = vi.hoisted(() => ({
  result: { status: 'notConfigured' } as { status: string; reporter?: unknown },
}))
vi.mock('server-only', () => ({}))
vi.mock('@/shared/ga4', () => ({ getGa4Reporter: async () => ga4.result }))

const { GET } = await import('./route')

const ADMIN_CHECK = '*/api/v1/member/admin/sellers'

function get(search: string, token: string | null = 'Bearer admin-token') {
  return GET(
    new Request(`http://localhost/admin/stats/data?${search}`, {
      headers: token ? { Authorization: token } : {},
    }),
  )
}

beforeEach(() => {
  ga4.result = { status: 'ready', reporter: mockGa4Reporter }
  server.use(
    http.get(ADMIN_CHECK, () => HttpResponse.json({ status: 200, message: 'ok', data: [] })),
  )
})

afterEach(() => vi.restoreAllMocks())

describe('GET /admin/stats/data 권한', () => {
  it('토큰이 없으면 GA4를 부르지 않고 401', async () => {
    const usersByEvent = vi.spyOn(mockGa4Reporter, 'usersByEvent')
    const response = await get('section=buyer&period=7d', null)

    expect(response.status).toBe(401)
    expect(usersByEvent).not.toHaveBeenCalled()
  })

  it('관리자가 아니면(회원 서비스 403) 숫자 없이 403', async () => {
    server.use(
      http.get(ADMIN_CHECK, () =>
        HttpResponse.json(
          { status: 403, code: 'MEMBER_NOT_ADMIN', message: '관리자만', data: null },
          { status: 403 },
        ),
      ),
    )
    const response = await get('section=buyer&period=7d')

    expect(response.status).toBe(403)
    expect(await response.json()).toMatchObject({ code: 'MEMBER_NOT_ADMIN', data: null })
  })

  it('관리자 확인에 받은 토큰을 그대로 게이트웨이에 보낸다', async () => {
    let received: string | null = null
    server.use(
      http.get(ADMIN_CHECK, ({ request }) => {
        received = request.headers.get('Authorization')
        return HttpResponse.json({ status: 200, message: 'ok', data: [] })
      }),
    )
    await get('section=realtime')

    expect(received).toBe('Bearer admin-token')
  })

  it('관리자인지 확인하지 못하면 숫자를 주지 않는다', async () => {
    server.use(http.get(ADMIN_CHECK, () => HttpResponse.json({}, { status: 500 })))

    expect((await get('section=realtime')).status).toBe(502)
  })
})

describe('GET /admin/stats/data 숫자', () => {
  it('구매 단계는 shop 기록으로 고른 기간과 앞 기간 사람 수를 준다', async () => {
    const usersByEvent = vi.spyOn(mockGa4Reporter, 'usersByEvent')
    const response = await get('section=buyer&period=custom&start=2026-10-10&end=2026-10-12')
    const body = await response.json()

    expect(response.status).toBe(200)
    expect(usersByEvent).toHaveBeenCalledWith(
      expect.objectContaining({
        siteArea: 'shop',
        dateRanges: [
          { name: 'current', startDate: '2026-10-10', endDate: '2026-10-12' },
          { name: 'previous', startDate: '2026-10-07', endDate: '2026-10-09' },
        ],
      }),
    )
    expect(body.data.steps).toHaveLength(8)
    expect(body.data.steps[0]).toEqual({
      event: 'view_item_list',
      label: '대회 목록',
      users: 1240,
      previousUsers: 1105,
    })
  })

  it('판매자 단계는 seller 기록으로 센다', async () => {
    const usersByEvent = vi.spyOn(mockGa4Reporter, 'usersByEvent')
    const body = await (await get('section=seller&period=30d')).json()

    expect(usersByEvent).toHaveBeenCalledWith(expect.objectContaining({ siteArea: 'seller' }))
    expect(body.data.steps.map((s: { event: string }) => s.event)).toEqual([
      'seller_apply_start',
      'seller_apply_submit',
      'seller_race_form_start',
      'seller_race_register',
    ])
  })

  it('키가 없으면 연결 정보 없음을 200으로 준다', async () => {
    ga4.result = { status: 'notConfigured' }

    expect(await (await get('section=realtime')).json()).toEqual({ status: 'notConfigured' })
  })

  it('GA4 호출이 실패하면 그 영역만 502', async () => {
    vi.spyOn(mockGa4Reporter, 'realtime').mockRejectedValueOnce(new Error('UNAVAILABLE'))
    vi.spyOn(console, 'error').mockImplementation(() => {})

    expect((await get('section=realtime')).status).toBe(502)
    expect((await get('section=buyer&period=7d')).status).toBe(200)
  })

  it('기간이 틀리면 400', async () => {
    expect((await get('section=buyer&period=custom&start=2026-10-12&end=2026-10-10')).status).toBe(
      400,
    )
    expect((await get('section=nope')).status).toBe(400)
  })
})

describe('GET /admin/stats/data 일별 추이 · 입력 오류', () => {
  it('일별 방문자 수는 기간의 날짜마다 이번 기간과 비교 기간 사람 수를 준다', async () => {
    const usersByDate = vi.spyOn(mockGa4Reporter, 'usersByDate')
    const body = await (
      await get('section=visitors&period=custom&start=2026-10-30&end=2026-11-01')
    ).json()

    expect(usersByDate).toHaveBeenCalledWith(expect.objectContaining({ metric: 'activeUsers' }))
    expect(body.data.points).toHaveLength(3)
    expect(body.data.points[0]).toMatchObject({ date: '2026-10-30', previousDate: '2026-10-27' })
  })

  it('일별 구매 수는 purchase 이벤트를 보낸 사람 수로 센다', async () => {
    const usersByDate = vi.spyOn(mockGa4Reporter, 'usersByDate')
    await get('section=purchases&period=7d')

    expect(usersByDate).toHaveBeenCalledWith(
      expect.objectContaining({ metric: 'totalUsers', eventName: 'purchase' }),
    )
  })

  it('GA4가 빈 날짜를 빼고 줘도 0명으로 채운다', async () => {
    vi.spyOn(mockGa4Reporter, 'usersByDate').mockResolvedValueOnce({
      current: { '2026-10-31': 4 },
      previous: {},
    })
    const body = await (
      await get('section=visitors&period=custom&start=2026-10-30&end=2026-11-01')
    ).json()

    expect(body.data.points.map((p: { value: number }) => p.value)).toEqual([0, 4, 0])
  })

  it('입력 오류 묶음은 상위 3개만 준다', async () => {
    const body = await (await get('section=formErrors&period=7d')).json()

    expect(body.data.groups).toEqual([
      { name: '종목 · 참가권', users: 7 },
      { name: '이미지', users: 6 },
      { name: '기본 정보', users: 4 },
    ])
  })
})
