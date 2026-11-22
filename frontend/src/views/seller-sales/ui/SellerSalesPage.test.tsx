import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { currentSearch, resetNavigationMock, server } from '@/shared/testing'
import { ToastProvider } from '@/shared/ui'

import { SellerSalesPage } from './SellerSalesPage'

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <SellerSalesPage />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

const RACE = {
  raceId: 7,
  title: '2026 춘천 나이트런',
  raceDate: '2026-12-06T19:00:00',
  saleStatus: 'ON_SALE',
  saleOpenDate: '2026-10-05',
  applicationCloseAt: '2026-11-05T18:00:00',
  courses: [
    {
      raceCourseId: 71,
      name: '10K',
      price: 45000,
      status: 'OPEN',
      quota: 300,
      reserved: 10,
      sold: 120,
    },
    {
      raceCourseId: 72,
      name: '5K',
      price: 35000,
      status: 'OPEN',
      quota: 200,
      reserved: 0,
      sold: 80,
    },
  ],
}

const ORDERS = [
  {
    orderItemId: 1,
    orderNo: 'OD-20261012-0231',
    paidAt: '2026-10-12T10:17:30',
    raceId: 7,
    raceCourseId: 71,
    courseName: '하프',
    status: 'PAID',
    participantName: '김러너',
    kitOptions: [
      { kitOptionId: 1, name: '티셔츠', value: 'M' },
      { kitOptionId: 2, name: '양말', value: '255-280' },
    ],
    partyId: null,
  },
  {
    orderItemId: 2,
    orderNo: 'OD-20261015-0512',
    paidAt: null,
    raceId: 7,
    raceCourseId: 72,
    courseName: '10K',
    status: 'ORDERED',
    participantName: '이페이스',
    kitOptions: [],
    partyId: 1023,
  },
]

beforeEach(() => resetNavigationMock())

function mockRaces(races: unknown[]) {
  server.use(
    http.get('*/api/v1/product/races/mine', () =>
      HttpResponse.json({ status: 200, message: 'ok', data: races }),
    ),
    http.get('*/api/v1/product/races/7', () =>
      HttpResponse.json({
        status: 200,
        message: 'ok',
        data: { raceId: 7, title: RACE.title, refundDeadline: '2026-11-05T23:59:00' },
      }),
    ),
  )
}

describe('SellerSalesPage', () => {
  it('요약 칸을 내 대회의 판매 · 정원 · 결제 대기와 대회 상세의 환불 기한으로 채운다', async () => {
    mockRaces([RACE])
    renderPage()

    // 판매 120 + 80 = 200, 정원 500, 남은 자리 500 - 200 - 결제 대기 10 = 290
    const figures = within(await screen.findByRole('group', { name: '판매 숫자' }))
    expect(figures.getByText('200')).toBeInTheDocument()
    expect(figures.getByText('/ 500명')).toBeInTheDocument()
    expect(figures.getByText('290')).toBeInTheDocument()
    // 결제 대기 10명도 숫자로 보인다
    expect(figures.getByText('결제 대기')).toBeInTheDocument()
    expect(figures.getByText('10')).toBeInTheDocument()
    // 종목별 판매 표
    expect(screen.getByRole('table', { name: '종목별 판매' })).toBeInTheDocument()
    expect(await screen.findByText('11/5 (목)')).toBeInTheDocument()
  })

  it('참가자 표에 주문번호 · 참가자 · 종목 · 키트 옵션 · 신청 방식 · 결제일 · 상태를 보여 준다', async () => {
    mockRaces([RACE])
    let query: URLSearchParams | undefined
    server.use(
      http.get('*/api/v1/order/seller/orders', ({ request }) => {
        query = new URL(request.url).searchParams
        return HttpResponse.json({
          status: 200,
          message: 'ok',
          data: { orders: ORDERS, page: 0, size: 50, totalElements: 1780, totalPages: 36 },
        })
      }),
    )
    renderPage()

    const table = await screen.findByRole('table', { name: '참가자' })
    const rows = within(table).getAllByRole('row')
    expect(
      within(rows[1])
        .getAllByRole('cell')
        .map((c) => c.textContent),
    ).toEqual([
      'OD-20261012-0231',
      '김러너',
      '하프',
      '티셔츠 M · 양말 255-280',
      '개인',
      '10/12 (월)',
      '참가 확정',
    ])
    expect(within(rows[2]).getByText('파티 P-1023')).toBeInTheDocument()
    expect(within(rows[2]).getByText('결제 대기')).toBeInTheDocument()
    // 키트 옵션 · 결제일이 없으면 - 로 보인다
    expect(within(rows[2]).getAllByText('-')).toHaveLength(2)
    expect(screen.getByText('전체 1,780명')).toBeInTheDocument()
    expect(screen.getByText('1 / 36')).toBeInTheDocument()
    expect(query?.get('raceId')).toBe('7')
    expect(query?.get('page')).toBe('0')
    expect(query?.get('size')).toBe('50')
    // 준비 중 안내와 화면 아래 내려받기 카드는 없다
    expect(screen.queryByText('참가자 목록은 준비 중이에요')).not.toBeInTheDocument()
    expect(screen.queryByText('엑셀 내려받기는 준비 중이에요')).not.toBeInTheDocument()
  })

  it('다음 쪽을 누르면 주소에 쪽을 남기고 그 쪽을 받는다', async () => {
    mockRaces([RACE])
    const pages: (string | null)[] = []
    server.use(
      http.get('*/api/v1/order/seller/orders', ({ request }) => {
        pages.push(new URL(request.url).searchParams.get('page'))
        return HttpResponse.json({
          status: 200,
          message: 'ok',
          data: { orders: ORDERS, page: 0, size: 50, totalElements: 120, totalPages: 3 },
        })
      }),
    )
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: '다음 쪽' }))

    expect(currentSearch()).toContain('page=2')
    await screen.findByText('2 / 3')
    expect(pages).toContain('1')
  })

  it('이름 · 주문번호로 찾고, 맞는 참가자가 없으면 찾기 지우기를 보여 준다', async () => {
    mockRaces([RACE])
    const keywords: (string | null)[] = []
    server.use(
      http.get('*/api/v1/order/seller/orders', ({ request }) => {
        const keyword = new URL(request.url).searchParams.get('keyword')
        keywords.push(keyword)
        const orders = keyword ? [] : ORDERS
        return HttpResponse.json({
          status: 200,
          message: 'ok',
          data: {
            orders,
            page: 0,
            size: 50,
            totalElements: orders.length ? 1780 : 0,
            totalPages: 1,
          },
        })
      }),
    )
    renderPage()

    await screen.findByRole('table', { name: '참가자' })
    await userEvent.type(screen.getByLabelText('이름 · 주문번호로 찾기'), ' 김철수 {Enter}')

    expect(await screen.findByText('「김철수」와 맞는 참가자가 없어요')).toBeInTheDocument()
    expect(keywords).toContain('김철수')
    // 참가자 이름은 주소에 남기지 않는다
    expect(decodeURIComponent(currentSearch())).not.toContain('김철수')
    // 전체 수는 찾기와 상관없이 대회의 전체 참가자 수다
    expect(screen.getByText('전체 1,780명')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '찾기 지우기' }))
    expect(await screen.findByText('OD-20261012-0231')).toBeInTheDocument()
    expect(screen.getByLabelText('이름 · 주문번호로 찾기')).toHaveValue('')
  })

  it('주소의 쪽 번호가 숫자가 아니면 첫 쪽을 받는다', async () => {
    resetNavigationMock('page=1e21')
    mockRaces([RACE])
    const pages: (string | null)[] = []
    server.use(
      http.get('*/api/v1/order/seller/orders', ({ request }) => {
        pages.push(new URL(request.url).searchParams.get('page'))
        return HttpResponse.json({
          status: 200,
          message: 'ok',
          data: { orders: ORDERS, page: 0, size: 50, totalElements: 2, totalPages: 1 },
        })
      }),
    )
    renderPage()

    await screen.findByRole('table', { name: '참가자' })
    expect(pages.every((p) => p === '0')).toBe(true)
  })

  it('쪽 번호가 전체 쪽 수를 넘으면 빈 문구 대신 쪽 넘기기를 보여 준다', async () => {
    resetNavigationMock('page=9')
    mockRaces([RACE])
    server.use(
      http.get('*/api/v1/order/seller/orders', ({ request }) => {
        const page = Number(new URL(request.url).searchParams.get('page'))
        const orders = page === 0 ? ORDERS : []
        return HttpResponse.json({
          status: 200,
          message: 'ok',
          data: { orders, page, size: 50, totalElements: 120, totalPages: 3 },
        })
      }),
    )
    renderPage()

    expect(await screen.findByText('9 / 3')).toBeInTheDocument()
    expect(screen.queryByText('아직 참가자가 없어요')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '이전 쪽' })).toBeEnabled()
  })

  it('파티별 탭은 파티 번호 · 인원 · 결제 · 상태를 보여 주고 파티 번호로 찾는다', async () => {
    mockRaces([RACE])
    const asked: URLSearchParams[] = []
    server.use(
      http.get('*/api/v1/order/seller/parties', ({ request }) => {
        asked.push(new URL(request.url).searchParams)
        return HttpResponse.json({
          status: 200,
          message: 'ok',
          data: {
            parties: [
              {
                partyId: 1023,
                crewId: 2,
                raceCourseId: 71,
                status: 'PAYING',
                appliedCount: 4,
                paidCount: 3,
                unpaidCount: 1,
              },
            ],
            page: 0,
            size: 50,
            totalElements: 412,
            totalPages: 9,
          },
        })
      }),
    )
    renderPage()

    await userEvent.click(await screen.findByRole('tab', { name: '파티별' }))

    const table = await screen.findByRole('table', { name: '파티별' })
    const cells = within(within(table).getAllByRole('row')[1]).getAllByRole('cell')
    expect(cells.map((c) => c.textContent)).toEqual([
      'P-1023',
      '-',
      '-',
      '4명',
      '3 / 4명 완료',
      '결제 중',
    ])
    expect(screen.getByText('전체 412개 파티')).toBeInTheDocument()
    expect(asked[0].get('raceId')).toBe('7')

    await userEvent.type(screen.getByLabelText('파티 번호로 찾기'), 'P-1023{Enter}')
    await vi.waitFor(() => expect(asked.at(-1)?.get('partyId')).toBe('1023'))
  })

  it('참가자를 불러오지 못하면 명단 구역에 알려 준다', async () => {
    mockRaces([RACE])
    server.use(
      http.get('*/api/v1/order/seller/orders', () =>
        HttpResponse.json(
          { status: 403, message: '승인된 판매자만 쓸 수 있습니다.', data: null },
          { status: 403 },
        ),
      ),
    )
    renderPage()

    expect(await screen.findByText('참가자를 불러오지 못했어요')).toBeInTheDocument()
    expect(screen.getAllByText('승인된 판매자만 쓸 수 있습니다.').length).toBeGreaterThan(0)
  })

  it('등록한 대회가 없으면 대회 등록으로 안내한다', async () => {
    mockRaces([])
    renderPage()

    expect(await screen.findByText('등록한 대회가 없어요')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '대회 등록하기' })).toHaveAttribute(
      'href',
      '/seller/races/new?from=sales',
    )
  })

  it('주소의 ?race= 로 고른 대회를 연다', async () => {
    resetNavigationMock('race=8')
    mockRaces([RACE, { ...RACE, raceId: 8, title: '2026 부산 트레일' }])
    server.use(
      http.get('*/api/v1/product/races/8', () =>
        HttpResponse.json({
          status: 200,
          message: 'ok',
          data: { raceId: 8, title: '2026 부산 트레일' },
        }),
      ),
    )
    renderPage()

    expect(await screen.findByRole('combobox', { name: '대회' })).toHaveTextContent(
      '2026 부산 트레일',
    )
  })
})
