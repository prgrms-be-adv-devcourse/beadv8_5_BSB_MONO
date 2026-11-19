import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { server } from '@/shared/testing'

import { SellerDashboardPage } from './SellerDashboardPage'

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: 0 } } })
  return render(
    <QueryClientProvider client={client}>
      <SellerDashboardPage />
    </QueryClientProvider>,
  )
}

describe('SellerDashboardPage', () => {
  it('다음 정산 구역에 지급 예정액 · 지급일을 보여 준다', async () => {
    renderPage()

    const payout = await card('다음 정산')
    expect(await within(payout).findByText('94,500')).toBeInTheDocument()
    expect(within(payout).getByText('10월 20일 (화) 지급 예정')).toBeInTheDocument()
  })

  it('지급 예정 정산이 없으면(다음 정산일 null) 없다고 보여 주고 지급보류는 숨긴다', async () => {
    server.use(
      http.get('*/api/v1/payout/seller/summary', () =>
        HttpResponse.json({
          status: 200,
          message: 'ok',
          data: { nextPayDate: null, scheduledPayoutAmount: 0, heldCount: 0 },
        }),
      ),
    )
    renderPage()

    const payout = await card('다음 정산')
    expect(await within(payout).findByText('지급 예정 정산 없음')).toBeInTheDocument()
    expect(within(payout).queryByText(/지급보류/)).not.toBeInTheDocument()
  })

  it('처리할 일은 송장 입력 대기 · 지급보류를 한 줄씩 링크로 보여 준다', async () => {
    renderPage()

    const todo = await card('처리할 일')
    // 배송 준비 3건 중 환불 기한 전 1건은 아직 보낼 수 없어 2건
    const kit = await within(todo).findByRole('link', { name: /송장 입력 대기 2건/ })
    expect(kit).toHaveAttribute('href', '/seller/kits')
    const hold = await within(todo).findByRole('link', { name: /지급보류 1건/ })
    expect(hold).toHaveAttribute('href', '/seller/settlements')
  })

  it('0건인 줄은 숨긴다', async () => {
    server.use(
      http.get('*/api/v1/payout/seller/summary', () =>
        HttpResponse.json({
          status: 200,
          message: 'ok',
          data: { nextPayDate: null, scheduledPayoutAmount: 0, heldCount: 0 },
        }),
      ),
    )
    renderPage()

    const todo = await card('처리할 일')
    expect(await within(todo).findByRole('link', { name: /송장 입력 대기/ })).toBeInTheDocument()
    expect(within(todo).queryByRole('link', { name: /지급보류/ })).not.toBeInTheDocument()
  })

  it('키트 건수를 불러오지 못하면 건수 대신 - 를 보여 준다', async () => {
    server.use(
      http.get('*/api/v1/order/seller/kits/summary', () =>
        HttpResponse.json(
          {
            status: 503,
            code: 'ORDER_EXTERNAL_SERVICE_UNAVAILABLE',
            message: '잠시 뒤',
            data: null,
          },
          { status: 503 },
        ),
      ),
    )
    renderPage()

    expect(await screen.findByRole('link', { name: /송장 입력 대기 -/ })).toBeInTheDocument()
  })

  it('대회별 판매 표에 대회 · 판매 / 정원 · 상태를 글자로 보여 주고 줄이 상세로 간다', async () => {
    renderPage()

    const table = await screen.findByRole('table', { name: '대회별 판매' })
    // 명세 예시 대회: 정원 600 중 15장 판매
    expect(await within(table).findByRole('cell', { name: '15 / 600' })).toBeInTheDocument()
    expect(within(table).getByText('판매 중')).toBeInTheDocument()
    expect(within(table).getByRole('link', { name: /쿠키RUN/ })).toHaveAttribute(
      'href',
      '/seller/races/1',
    )
  })

  it('대회 목록이 실패하면 서버 문구를 보여 준다', async () => {
    server.use(
      http.get('*/api/v1/product/races/mine', () =>
        HttpResponse.json(
          { status: 403, message: '승인된 판매자만 볼 수 있습니다.', data: null },
          { status: 403 },
        ),
      ),
    )
    renderPage()

    expect(await screen.findByText('대회 목록을 불러오지 못했어요')).toBeInTheDocument()
    expect(screen.getByText('승인된 판매자만 볼 수 있습니다.')).toBeInTheDocument()
  })

  it('처리할 일 제목 옆에 합계를 보여 주고, 줄마다 이동할 메뉴 이름을 보여 준다', async () => {
    renderPage()

    const todo = await card('처리할 일')
    expect(
      await within(todo).findByRole('link', { name: /송장 입력 대기\s*2건\s*키트 배송/ }),
    ).toBeInTheDocument()
    expect(within(todo).getByRole('link', { name: /지급보류\s*1건\s*정산/ })).toBeInTheDocument()
    expect(todo).toHaveTextContent(/처리할 일\s*\d+/)
  })

  it('제목 아래 기준일과 대회별 판매의 대회 관리 버튼은 두지 않는다', async () => {
    renderPage()

    await card('다음 정산')
    expect(screen.queryByText(/기준$/)).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '대회 관리' })).not.toBeInTheDocument()
  })

  it('크루런 AI 브리핑 카드와 키트 배송 표는 두지 않는다', async () => {
    renderPage()

    await card('다음 정산')
    expect(screen.queryByRole('region', { name: '크루런 AI 브리핑' })).not.toBeInTheDocument()
    expect(screen.queryByRole('table', { name: '키트 배송 현황' })).not.toBeInTheDocument()
  })
})

/** 제목으로 대시보드 구역(section)을 찾는다 */
async function card(title: string | RegExp) {
  const heading = await screen.findByRole('heading', { name: title })
  return heading.closest('section') as HTMLElement
}
