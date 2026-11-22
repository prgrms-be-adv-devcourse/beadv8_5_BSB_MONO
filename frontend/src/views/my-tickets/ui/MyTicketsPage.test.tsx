import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { clearAccessToken } from '@/shared/api'
import { orderMock, resetNavigationMock, server } from '@/shared/testing'

import { MyTicketsPage } from './MyTicketsPage'

vi.mock('next/navigation', async () =>
  (await import('@/shared/testing')).navigationMock('/tickets'),
)

beforeEach(() => {
  resetNavigationMock()
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date('2026-10-15T12:00:00+09:00'))
})
afterEach(() => {
  vi.useRealTimers()
  clearAccessToken()
})

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MyTicketsPage />
    </QueryClientProvider>,
  )
}

describe('MyTicketsPage', () => {
  it('결제한 주문의 주문 상품을 대회 이름 · 참가자 · 환불 안내와 함께 다가오는 대회 탭에 보여 준다', async () => {
    const detailIds: string[] = []
    server.use(
      http.get('*/api/v1/order/orders/:orderId', ({ params }) => {
        detailIds.push(String(params.orderId))
        return undefined
      }),
    )
    renderPage()

    expect(await screen.findByText('김러너 · 10K')).toBeInTheDocument()
    expect(screen.getByText('2026 용감한 쿠키RUN in 서울')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /^다가오는 대회\s*1$/ })).toBeInTheDocument()
    expect(
      screen.getByText('11/1 (일) · 10/31 (토)까지 취소하면 50,000원 전액 환불'),
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /김러너 · 10K/ })).toHaveAttribute(
      'href',
      '/tickets/1-1',
    )
    // 결제 대기 · 기한 만료 주문은 상세를 부르지 않는다
    expect(detailIds).toEqual(['1'])
  })

  it('결제 확인 중인 주문은 다가오는 대회 탭에 「결제 확인 중」 배지로 보여 주고 환불 안내는 붙이지 않는다', async () => {
    server.use(
      http.get('*/api/v1/order/orders', () =>
        HttpResponse.json({
          status: 200,
          message: '주문 내역을 조회했습니다.',
          data: {
            orders: [{ id: 1, status: 'PAYING' }],
            page: 0,
            size: 100,
            totalElements: 1,
            totalPages: 1,
          },
        }),
      ),
      http.get('*/api/v1/order/orders/1', () => {
        const example = orderMock.getFindById1ResponseMock()
        return HttpResponse.json({
          ...example,
          data: {
            ...example.data,
            status: 'PAYING',
            items: example.data?.items?.map((item) => ({ ...item, status: 'ORDERED' })),
          },
        })
      }),
    )
    renderPage()

    expect(await screen.findByText('김러너 · 10K')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /^다가오는 대회\s*1$/ })).toBeInTheDocument()
    expect(screen.getByText('결제 확인 중')).toBeInTheDocument()
    expect(screen.queryByText(/전액 환불/)).not.toBeInTheDocument()
  })

  it('결제 기한 전인 결제 대기 주문은 다가오는 대회 탭에 기한과 결제하기 버튼을 달고 보이고, 기한이 지난 주문은 숨긴다', async () => {
    const detailIds: string[] = []
    server.use(
      http.get('*/api/v1/order/orders', () =>
        HttpResponse.json({
          status: 200,
          message: '주문 내역을 조회했습니다.',
          data: {
            orders: [
              { id: 3, status: 'CREATED', paymentDueAt: '2026-10-15T12:30:00' },
              { id: 4, status: 'CREATED', paymentDueAt: '2026-10-15T11:00:00' },
              { id: 1, status: 'PAID' },
            ],
            page: 0,
            size: 100,
            totalElements: 3,
            totalPages: 1,
          },
        }),
      ),
      http.get('*/api/v1/order/orders/:orderId', ({ params }) => {
        detailIds.push(String(params.orderId))
        const example = orderMock.getFindById1ResponseMock()
        if (params.orderId !== '3') return HttpResponse.json(example)
        return HttpResponse.json({
          ...example,
          data: {
            ...example.data,
            id: 3,
            orderNo: 'OD-20261015-0003',
            status: 'CREATED',
            paymentDueAt: '2026-10-15T12:30:00',
            items: example.data?.items?.map((item) => ({ ...item, id: 5, status: 'ORDERED' })),
          },
        })
      }),
    )
    renderPage()

    const pay = await screen.findByRole('link', { name: '결제하기' })
    expect(pay).toHaveAttribute('href', '/checkout?orderId=3')
    expect(screen.getByText('결제 대기')).toBeInTheDocument()
    expect(screen.getByText('결제 기한 10/15 (목) 12:30')).toBeInTheDocument()
    // 결제 대기 카드 + 결제 완료 카드
    expect(screen.getByRole('tab', { name: /^다가오는 대회\s*2$/ })).toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: '결제하기' })).toHaveLength(1)
    // 기한이 지난 주문은 상세도 부르지 않는다
    expect(detailIds.sort()).toEqual(['1', '3'])
  })

  it('결제 확인 중 참가권이 있으면 다시 조회해 결과가 나오면 참가 확정으로 바꾼다', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    vi.setSystemTime(new Date('2026-10-15T12:00:00+09:00'))
    let paid = false
    server.use(
      http.get('*/api/v1/order/orders', () =>
        HttpResponse.json({
          status: 200,
          message: '주문 내역을 조회했습니다.',
          data: {
            orders: [{ id: 1, status: paid ? 'PAID' : 'PAYING' }],
            page: 0,
            size: 100,
            totalElements: 1,
            totalPages: 1,
          },
        }),
      ),
      http.get('*/api/v1/order/orders/1', () => {
        const example = orderMock.getFindById1ResponseMock()
        if (paid) return HttpResponse.json(example)
        return HttpResponse.json({
          ...example,
          data: {
            ...example.data,
            status: 'PAYING',
            items: example.data?.items?.map((item) => ({ ...item, status: 'ORDERED' })),
          },
        })
      }),
    )
    renderPage()
    expect(await screen.findByText('결제 확인 중')).toBeInTheDocument()

    paid = true
    // 목록은 주문 수만큼 상세를 다시 부르므로 3초가 아니라 15초마다 다시 받는다
    await act(() => vi.advanceTimersByTimeAsync(3000))
    expect(screen.getByText('결제 확인 중')).toBeInTheDocument()
    await act(() => vi.advanceTimersByTimeAsync(12_000))

    // v3 시안: 참가 확정은 배지 없이 카드만 보여 준다
    expect(await screen.findByText('김러너 · 10K')).toBeInTheDocument()
    await vi.waitFor(() => expect(screen.queryByText('결제 확인 중')).not.toBeInTheDocument())
    expect(screen.queryByText('참가 확정')).not.toBeInTheDocument()
  })

  it('v3 시안: 머리말에는 종만 두고 검색 아이콘은 없다 · 참가 확정 카드에는 배지가 없다', async () => {
    renderPage()

    expect(await screen.findByText('김러너 · 10K')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '대회 찾기' })).not.toBeInTheDocument()
    expect(screen.queryByText('참가 확정')).not.toBeInTheDocument()
  })

  it('결제한 주문이 없으면 빈 화면을 보여 준다', async () => {
    server.use(
      http.get('*/api/v1/order/orders', () =>
        HttpResponse.json({
          status: 200,
          message: '주문 내역을 조회했습니다.',
          data: { orders: [], page: 0, size: 100, totalElements: 0, totalPages: 0 },
        }),
      ),
    )
    renderPage()

    expect(await screen.findByText('신청한 대회가 없어요')).toBeInTheDocument()
  })

  it('주문 목록을 못 불러오면(400) 안내를 보여 준다', async () => {
    server.use(
      http.get('*/api/v1/order/orders', () =>
        HttpResponse.json(
          { status: 400, message: '요청 값의 형식이 올바르지 않습니다.', data: null },
          { status: 400 },
        ),
      ),
    )
    renderPage()

    expect(await screen.findByText('참가권을 불러오지 못했어요')).toBeInTheDocument()
  })

  it('불러오지 못했을 때 다시 시도하기를 누르면 다시 불러온다', async () => {
    let calls = 0
    server.use(
      http.get('*/api/v1/order/orders', () => {
        calls += 1
        return HttpResponse.json({ status: 500, message: '오류', data: null }, { status: 500 })
      }),
    )
    const user = userEvent.setup({ advanceTimers: () => {} })
    renderPage()

    await user.click(await screen.findByRole('button', { name: '다시 불러오기' }))

    await vi.waitFor(() => expect(calls).toBe(2))
  })

  // 대회일이 남았어도 취소한 참가권은 지난 대회 쪽이다(ticketStatus가 canceled면 past)
  function canceledOrder() {
    return [
      http.get('*/api/v1/order/orders/1', () => {
        const example = orderMock.getFindById1ResponseMock()
        return HttpResponse.json({
          ...example,
          data: {
            ...example.data,
            items: example.data?.items?.map((item) => ({ ...item, status: 'CANCELED' })),
          },
        })
      }),
    ]
  }

  it('처음에는 다가오는 대회 탭이 선택되고 탭마다 개수가 붙는다', async () => {
    renderPage()

    expect(await screen.findByRole('tab', { name: /^다가오는 대회\s*1$/ })).toHaveAttribute(
      'aria-selected',
      'true',
    )
    expect(screen.getByRole('tab', { name: /^지난 대회\s*0$/ })).toHaveAttribute(
      'aria-selected',
      'false',
    )
  })

  it('지난 대회 탭을 누르면 취소한 참가권만 보이고 다가오는 대회 참가권은 가려진다', async () => {
    server.use(...canceledOrder())
    const user = userEvent.setup({ advanceTimers: () => {} })
    renderPage()

    // 취소한 참가권은 대회일이 남았어도 지난 대회에 들어간다
    expect(await screen.findByRole('tab', { name: /^지난 대회\s*1$/ })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /^다가오는 대회\s*0$/ })).toBeInTheDocument()
    expect(screen.queryByText('김러너 · 10K')).not.toBeInTheDocument()

    await user.click(screen.getByRole('tab', { name: /^지난 대회\s*1$/ }))

    expect(await screen.findByText('김러너 · 10K')).toBeInTheDocument()
    expect(screen.getByText(/참가 취소/)).toBeInTheDocument()
  })

  it('다가오는 대회가 없으면 그 탭 안에 빈 화면을 보여 주고 탭은 그대로 둔다', async () => {
    server.use(...canceledOrder())
    renderPage()

    expect(await screen.findByText('다가오는 대회가 없어요')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /^지난 대회\s*1$/ })).toBeInTheDocument()
  })

  it('지난 대회가 없는 탭을 열면 그 탭 안에 빈 화면을 보여 준다', async () => {
    const user = userEvent.setup({ advanceTimers: () => {} })
    renderPage()

    await user.click(await screen.findByRole('tab', { name: /^지난 대회\s*0$/ }))

    expect(await screen.findByText('지난 대회가 없어요')).toBeInTheDocument()
    expect(screen.queryByText('김러너 · 10K')).not.toBeInTheDocument()
  })
})
