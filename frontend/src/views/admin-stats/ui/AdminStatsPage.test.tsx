import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { server } from '@/shared/testing'

import { AdminStatsPage } from './AdminStatsPage'

const clarity = vi.hoisted(() => ({ id: 'abc123' as string | undefined }))
vi.mock('@/shared/config', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/shared/config')>()
  return {
    ...actual,
    clientEnv: {
      ...actual.clientEnv,
      get NEXT_PUBLIC_CLARITY_ID() {
        return clarity.id
      },
    },
  }
})

// jsdom에는 그래프 크기를 잴 ResizeObserver가 없어 그래프 부품만 점 개수를 적는 대역으로 바꾼다
vi.mock('./TrendChart', () => ({
  default: ({ points }: { points: unknown[] }) => <div>그래프 점 {points.length}개</div>,
}))

const PERIOD = {
  current: { start: '2026-10-26', end: '2026-11-01' },
  previous: { start: '2026-10-19', end: '2026-10-25' },
}

const funnel = (steps: { event: string; label: string; users: number }[]) => ({
  status: 'ok',
  data: { period: PERIOD, steps: steps.map((s) => ({ ...s, previousUsers: 10 })) },
})

const trend = (total: number, previousTotal: number) => ({
  status: 'ok',
  data: {
    period: PERIOD,
    points: [
      { date: '2026-10-26', value: 5 },
      { date: '2026-10-27', value: 6 },
    ],
    total,
    previousTotal,
  },
})

const BODY = {
  realtime: {
    status: 'ok',
    data: { activeUsers: 23, topScreens: [{ name: '대회 목록 | 크루런', activeUsers: 9 }] },
  },
  buyer: funnel([
    { event: 'view_item_list', label: '대회 목록', users: 1240 },
    { event: 'view_item', label: '대회 상세', users: 980 },
    { event: 'queue_enter', label: '대기열 진입', users: 620 },
    { event: 'queue_pass', label: '대기열 통과', users: 260 },
  ]),
  seller: funnel([
    { event: 'seller_apply_start', label: '판매자 신청 시작', users: 48 },
    { event: 'seller_apply_submit', label: '신청 제출', users: 31 },
  ]),
  formErrors: {
    status: 'ok',
    data: { period: PERIOD, groups: [{ name: '종목 · 참가권', users: 9 }] },
  },
  visitors: trend(1180, 1169),
  purchases: trend(142, 145),
}

type Section = keyof typeof BODY

const requests: string[] = []

function mockStats(override: Partial<Record<Section, () => Response>> = {}) {
  requests.length = 0
  server.use(
    http.get('*/admin/stats/data', ({ request }) => {
      const params = new URL(request.url).searchParams
      requests.push(params.toString())
      const section = params.get('section') as Section
      return override[section]?.() ?? HttpResponse.json(BODY[section])
    }),
  )
}

const failed = () => HttpResponse.json({ status: 502, message: 'x', data: null }, { status: 502 })

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <AdminStatsPage />
    </QueryClientProvider>,
  )
}

const block = (title: string) => screen.getByRole('region', { name: title })
const SLOW = { timeout: 4000 }

describe('AdminStatsPage', () => {
  // 기간 고르기의 날짜(지난 7일)는 오늘로 계산한다. 기대값이 고정 날짜라 오늘을 2026-11-02 정오(한국 시간)로 묶는다.
  // Date만 바꿔서 MSW · react-query · userEvent의 타이머는 그대로 돈다
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-11-02T03:00:00Z'))
  })
  afterEach(() => vi.useRealTimers())

  it('Clarity ID가 있으면 Clarity 대시보드 바로가기를 새 탭 링크로 보여 준다', () => {
    clarity.id = 'abc123'
    renderPage()

    const link = screen.getByRole('link', { name: 'Clarity에서 녹화 보기' })
    expect(link).toHaveAttribute(
      'href',
      'https://clarity.microsoft.com/projects/view/abc123/dashboard',
    )
    expect(link).toHaveAttribute('target', '_blank')
    expect(link).toHaveAttribute('rel', 'noopener noreferrer')
  })

  it('Clarity ID가 없으면 바로가기를 보여 주지 않는다', () => {
    clarity.id = undefined
    renderPage()

    expect(screen.queryByRole('link', { name: 'Clarity에서 녹화 보기' })).not.toBeInTheDocument()
  })

  it('지금 접속 중, 일별 추이, 구매·판매자 단계를 보여 준다', async () => {
    mockStats()
    renderPage()

    const realtime = await screen.findByRole('region', { name: '지금 접속 중' })
    expect(await within(realtime).findByText('23')).toBeInTheDocument()
    expect(within(realtime).getByText('대회 목록 | 크루런')).toBeInTheDocument()

    const trends = block('일별 추이')
    expect(await within(trends).findByText('그래프 점 2개')).toBeInTheDocument()
    expect(within(trends).getByText('1,180')).toBeInTheDocument()
    expect(within(trends).getByText('+11')).toBeInTheDocument()
    expect(
      within(trends).getByRole('heading', { name: '지난 7일 방문한 사람' }),
    ).toBeInTheDocument()
    expect(within(trends).getByText('합계 1,180명, 지난 기간보다 11명 많아요')).toBeInTheDocument()

    const buyer = block('구매 단계')
    expect(await within(buyer).findByText('1,240명')).toBeInTheDocument()
    expect(within(buyer).getByText('시작 단계')).toBeInTheDocument()
    expect(within(buyer).getByText('앞 단계의 42%')).toBeInTheDocument()
    expect(
      within(buyer).getByText(
        '가장 많이 빠지는 단계는 「대기열 진입 → 대기열 통과」예요. 「대기열 진입」까지 온 사람의 58%가 다음 단계로 가지 않았어요.',
      ),
    ).toBeInTheDocument()

    const seller = block('판매자 단계')
    expect(await within(seller).findByText('48명')).toBeInTheDocument()
    expect(await within(seller).findByText('종목 · 참가권')).toBeInTheDocument()
    expect(
      screen.getByText('10/26 (월) ~ 11/1 (일) · 비교 기간 10/19 (월) ~ 10/25 (일)', {
        exact: false,
      }),
    ).toBeInTheDocument()
  })

  it('한 영역이 실패하면 그 영역만 「불러오지 못했어요」이고 나머지는 그대로다', async () => {
    mockStats({ buyer: failed, visitors: failed })
    renderPage()

    await screen.findByRole('region', { name: '구매 단계' })
    expect(
      await within(block('구매 단계')).findByText('구매 단계를 불러오지 못했어요', {}, SLOW),
    ).toBeInTheDocument()
    expect(
      await within(block('일별 추이')).findByText('일별 방문자 수를 불러오지 못했어요', {}, SLOW),
    ).toBeInTheDocument()
    expect(within(block('판매자 단계')).getByText('48명')).toBeInTheDocument()
    expect(within(block('지금 접속 중')).getByText('23')).toBeInTheDocument()
  })

  it('알약 버튼으로 방문자와 구매를 바꿔 보고, 지난 기간보다 줄면 − 배지를 보여 준다', async () => {
    mockStats()
    const user = userEvent.setup()
    renderPage()
    const trends = await screen.findByRole('region', { name: '일별 추이' })
    await within(trends).findByText('1,180')

    await user.click(within(trends).getByRole('button', { name: '구매' }))

    expect(await within(trends).findByText('142')).toBeInTheDocument()
    expect(within(trends).getByText('\u22123')).toBeInTheDocument()
    expect(within(trends).getByRole('button', { name: '구매' })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
    expect(requests).toContain('section=purchases&period=7d')
  })

  it('다시 시도는 실패한 영역만 다시 부른다', async () => {
    let fail = true
    mockStats({ seller: () => (fail ? failed() : HttpResponse.json(BODY.seller)) })
    const user = userEvent.setup()
    renderPage()
    const seller = await screen.findByRole('region', { name: '판매자 단계' })
    const alert = await within(seller).findByRole('alert', {}, SLOW)
    expect(alert).toHaveTextContent('판매자 단계를 불러오지 못했어요')
    expect(alert).toHaveTextContent('잠시 뒤 다시 시도해 주세요. 다른 영역은 그대로 볼 수 있어요.')
    expect(seller.querySelector('img[src*="wordmark-muted"]')).toBeInTheDocument()

    fail = false
    requests.length = 0
    await user.click(within(seller).getByRole('button', { name: '다시 시도' }))

    expect(await within(seller).findByText('48명')).toBeInTheDocument()
    expect(requests.every((r) => r.startsWith('section=seller'))).toBe(true)
  })

  it('키가 없으면 화면 전체에 「GA4 연결 정보가 없어요」만 보이고 기간 고르기를 숨긴다', async () => {
    mockStats({ realtime: () => HttpResponse.json({ status: 'notConfigured' }) })
    renderPage()

    expect(await screen.findByText('GA4 연결 정보가 없어요')).toBeInTheDocument()
    expect(screen.queryByRole('tab', { name: '지난 7일' })).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: '구매 단계' })).not.toBeInTheDocument()
  })

  it('기록이 없으면 0명과 「계산할 수 없어요」, 빈 기간 안내를 보여 준다', async () => {
    mockStats({
      buyer: () =>
        HttpResponse.json(
          funnel([
            { event: 'view_item_list', label: '대회 목록', users: 0 },
            { event: 'view_item', label: '대회 상세', users: 0 },
          ]),
        ),
      formErrors: () => HttpResponse.json({ status: 'ok', data: { period: PERIOD, groups: [] } }),
    })
    renderPage()

    const buyer = await screen.findByRole('region', { name: '구매 단계' })
    expect(
      await within(buyer).findByText('이 기간에는 구매 기록이 없어요. 기간을 넓혀 보세요.'),
    ).toBeInTheDocument()
    expect(within(buyer).getAllByText('0명')).toHaveLength(2)
    expect(within(buyer).getByText('계산할 수 없어요')).toBeInTheDocument()
    expect(await within(block('판매자 단계')).findByText('입력 오류가 없어요')).toBeInTheDocument()
  })

  it('일별 추이에 기록이 없으면 큰 숫자 0과 안내만 보이고 배지는 없다', async () => {
    mockStats({
      visitors: () =>
        HttpResponse.json({
          status: 'ok',
          data: {
            period: PERIOD,
            points: [{ date: '2026-10-26', value: 0 }],
            total: 0,
            previousTotal: 4,
          },
        }),
    })
    renderPage()

    const trends = await screen.findByRole('region', { name: '일별 추이' })
    expect(
      await within(trends).findByText('이 기간에는 방문 기록이 없어요. 기간을 넓혀 보세요.'),
    ).toBeInTheDocument()
    expect(within(trends).getByText('0')).toBeInTheDocument()
    expect(within(trends).queryByText('지난 기간보다')).not.toBeInTheDocument()
  })

  it('로그인이 풀려 401이면 「불러오지 못함」 대신 다시 로그인하라고 알린다', async () => {
    mockStats({
      buyer: () =>
        HttpResponse.json(
          { status: 401, code: 'LOGIN_REQUIRED', message: '로그인이 필요합니다.', data: null },
          { status: 401 },
        ),
    })
    renderPage()

    const buyer = await screen.findByRole('region', { name: '구매 단계' })
    expect(await within(buyer).findByText('로그인이 풀렸어요', {}, SLOW)).toBeInTheDocument()
    expect(within(buyer).queryByText('구매 단계를 불러오지 못했어요')).not.toBeInTheDocument()
  })

  it('관리자가 아니면 숫자 대신 안내를 보여 준다', async () => {
    const denied = () =>
      HttpResponse.json(
        { status: 403, code: 'MEMBER_NOT_ADMIN', message: '관리자만', data: null },
        { status: 403 },
      )
    mockStats({
      realtime: denied,
      buyer: denied,
      seller: denied,
      formErrors: denied,
      visitors: denied,
      purchases: denied,
    })
    renderPage()

    // 방문자 · 구매 중 보이는 하나만 불러오므로 다섯 영역
    await waitFor(() => expect(screen.getAllByText('관리자만 이용할 수 있어요.')).toHaveLength(5))
  })

  it('기간을 바꾸면 기간을 쓰는 영역을 그 기간으로 다시 묻는다', async () => {
    mockStats()
    const user = userEvent.setup()
    renderPage()
    await within(await screen.findByRole('region', { name: '구매 단계' })).findByText('1,240명')

    await user.click(screen.getByRole('tab', { name: '지난 30일' }))

    await waitFor(() =>
      expect(requests).toEqual(
        expect.arrayContaining([
          'section=buyer&period=30d',
          'section=seller&period=30d',
          'section=formErrors&period=30d',
          'section=visitors&period=30d',
        ]),
      ),
    )
  })
})
