import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { clearAccessToken } from '@/shared/api'
import { orderMock, server } from '@/shared/testing'

import { MyTicketsPage } from './MyTicketsPage'

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), replace: vi.fn() }),
  usePathname: () => '/tickets',
}))

beforeEach(() => {
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
  it('결제한 주문의 주문 상품을 대회 이름 · 참가자 · 환불 안내와 함께 참가 예정에 보여 준다', async () => {
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
    expect(screen.getByRole('heading', { name: '참가 예정 1' })).toBeInTheDocument()
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

  it('결제 확인 중인 주문은 참가 예정에 「결제 확인 중」 배지로 보여 주고 환불 안내는 붙이지 않는다', async () => {
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
    expect(screen.getByRole('heading', { name: '참가 예정 1' })).toBeInTheDocument()
    expect(screen.getByText('결제 확인 중')).toBeInTheDocument()
    expect(screen.queryByText(/전액 환불/)).not.toBeInTheDocument()
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
})
