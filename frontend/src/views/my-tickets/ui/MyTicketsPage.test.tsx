import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { clearAccessToken } from '@/shared/api'
import { server } from '@/shared/testing'

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

    expect(await screen.findByText('아직 참가 예정인 대회가 없어요')).toBeInTheDocument()
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

    expect(await screen.findByText('정보를 불러오지 못했어요')).toBeInTheDocument()
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

    await user.click(await screen.findByRole('button', { name: '다시 시도하기' }))

    await vi.waitFor(() => expect(calls).toBe(2))
  })
})
