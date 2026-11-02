import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { server } from '@/shared/testing'

import { AdminStatsPage } from './AdminStatsPage'

const PERIOD = {
  current: { start: '2026-10-26', end: '2026-11-01' },
  previous: { start: '2026-10-19', end: '2026-10-25' },
}

const BODY = {
  buyer: {
    status: 'ok',
    data: {
      period: PERIOD,
      steps: [
        { event: 'view_item_list', label: '대회 목록', users: 100, previousUsers: 80 },
        { event: 'view_item', label: '대회 상세', users: 40, previousUsers: 30 },
        { event: 'purchase', label: '결제 완료', users: 30, previousUsers: 20 },
      ],
    },
  },
  seller: {
    status: 'ok',
    data: {
      period: PERIOD,
      steps: [
        { event: 'seller_apply_start', label: '판매자 신청 시작', users: 5, previousUsers: 3 },
      ],
    },
  },
  realtime: {
    status: 'ok',
    data: { activeUsers: 23, topScreens: [{ name: '대회 목록 | 크루런', activeUsers: 9 }] },
  },
}

const requests: string[] = []

function mockStats(override: Partial<Record<keyof typeof BODY, () => Response>> = {}) {
  requests.length = 0
  server.use(
    http.get('*/admin/stats/data', ({ request }) => {
      const params = new URL(request.url).searchParams
      requests.push(params.toString())
      const section = params.get('section') as keyof typeof BODY
      return override[section]?.() ?? HttpResponse.json(BODY[section])
    }),
  )
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <AdminStatsPage />
    </QueryClientProvider>,
  )
}

const card = (title: string) => screen.getByRole('heading', { name: title }).closest('section')!

describe('AdminStatsPage', () => {
  it('세 영역의 사람 수와 가장 많이 빠진 단계를 보여 준다', async () => {
    mockStats()
    renderPage()

    expect(await screen.findByText('23')).toBeInTheDocument()
    expect(screen.getByText('대회 목록 | 크루런')).toBeInTheDocument()
    const buyer = card('구매 단계별 남는 사람 수')
    expect(await within(buyer).findByText('40명')).toBeInTheDocument()
    expect(within(buyer).getByText('40%')).toBeInTheDocument()
    expect(within(buyer).getByText('가장 많이 빠짐')).toBeInTheDocument()
    expect(within(buyer).getByText('10/26 ~ 11/1 (앞 기간 10/19 ~ 10/25)')).toBeInTheDocument()
    expect(await within(card('판매자 단계별 남는 사람 수')).findByText('5명')).toBeInTheDocument()
  })

  it('한 영역이 실패하면 그 영역만 「불러오지 못함」이고 나머지는 그대로다', async () => {
    mockStats({
      buyer: () => HttpResponse.json({ status: 502, message: 'x', data: null }, { status: 502 }),
    })
    renderPage()

    const buyer = card('구매 단계별 남는 사람 수')
    expect(
      await within(buyer).findByText('불러오지 못함', {}, { timeout: 4000 }),
    ).toBeInTheDocument()
    expect(within(card('판매자 단계별 남는 사람 수')).getByText('5명')).toBeInTheDocument()
    expect(within(card('지금 접속 중')).getByText('23')).toBeInTheDocument()
  })

  it('키가 없으면 「GA4 연결 정보가 없습니다」를 보여 준다', async () => {
    const notConfigured = () => HttpResponse.json({ status: 'notConfigured' })
    mockStats({ buyer: notConfigured, seller: notConfigured, realtime: notConfigured })
    renderPage()

    await waitFor(() => expect(screen.getAllByText('GA4 연결 정보가 없습니다')).toHaveLength(3))
  })

  it('관리자가 아니면 숫자 대신 안내를 보여 준다', async () => {
    const denied = () =>
      HttpResponse.json(
        { status: 403, code: 'MEMBER_NOT_ADMIN', message: '관리자만', data: null },
        { status: 403 },
      )
    mockStats({ buyer: denied, seller: denied, realtime: denied })
    renderPage()

    await waitFor(() => expect(screen.getAllByText('관리자만 이용할 수 있어요.')).toHaveLength(3))
  })

  it('기간을 바꾸면 그 기간으로 다시 묻는다', async () => {
    mockStats()
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('23')

    await user.click(screen.getByRole('tab', { name: '지난 30일' }))
    await within(card('구매 단계별 남는 사람 수')).findByText('40명')

    expect(requests).toContain('section=buyer&period=30d')
    expect(requests).toContain('section=seller&period=30d')
  })
})
