import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { orderMock, server } from '@/shared/testing'

import { CheckoutCompletePage } from './CheckoutCompletePage'

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), replace: vi.fn() }),
  usePathname: () => '/checkout/complete',
}))

describe('결제 완료', () => {
  it('주문 조회 결과로 주문번호 · 결제 금액 · 대회를 보여 준다', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={client}>
        <CheckoutCompletePage orderId={1} />
      </QueryClientProvider>,
    )

    // 주문 API 명세 예시: OD-20261012-0001, 50,000원, 종목 1 · 받는 사람 김러너
    expect(await screen.findByText('OD-20261012-0001')).toBeInTheDocument()
    expect(screen.getByText('50,000원 (예치금)')).toBeInTheDocument()
    expect(await screen.findByText('2026 용감한 쿠키RUN in 서울 · 10K')).toBeInTheDocument()
    expect(screen.getByText('김러너 · 10K')).toBeInTheDocument()
    // Clarity 화면 녹화에서 참가자 이름을 가린다
    expect(screen.getByText('김러너 · 10K').closest('[data-clarity-mask="true"]')).not.toBeNull()
    expect(screen.getByText('김러너').closest('[data-clarity-mask="true"]')).not.toBeNull()
  })

  it('참가자 이름은 배송지 받는 사람이 아니라 참가 신청에서 가져온다 (새로고침해도 같다)', async () => {
    const example = orderMock.getFindByIdResponseMock()
    server.use(
      orderMock.getFindByIdMockHandler({
        ...example,
        data: { ...example.data, participant: { ...example.data!.participant, name: '이달림' } },
      }),
    )
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={client}>
        <CheckoutCompletePage orderId={1} />
      </QueryClientProvider>,
    )

    expect(await screen.findByText('이달림 · 10K')).toBeInTheDocument()
  })
})
