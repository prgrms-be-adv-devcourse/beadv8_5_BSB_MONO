import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { server } from '@/shared/testing'

import { SellerDashboardPage } from './SellerDashboardPage'

function renderPage(queries: { retry: number; retryDelay?: number } = { retry: 0 }) {
  const client = new QueryClient({ defaultOptions: { queries } })
  return render(
    <QueryClientProvider client={client}>
      <SellerDashboardPage />
    </QueryClientProvider>,
  )
}

describe('SellerDashboardPage', () => {
  it('다음 정산 카드에 정산 예정액 · 지급일 · 지급보류와 판매한 참가권을 보여 준다', async () => {
    renderPage()

    const payout = await card('다음 정산')
    expect(await within(payout).findByText('94,500')).toBeInTheDocument()
    expect(within(payout).getByText('10월 20일 지급 예정')).toBeInTheDocument()
    expect(within(payout).getByText('지급보류 1건')).toBeInTheDocument()
    // 명세 예시 대회: 정원 600 중 15장 판매
    expect(await within(payout).findByText('15')).toBeInTheDocument()
    expect(within(payout).getByText('정원의 3%')).toBeInTheDocument()
  })

  it('제목 아래에 크루런 AI 브리핑 카드를 붙인다', async () => {
    renderPage()

    const card = await screen.findByRole('region', { name: '크루런 AI 브리핑' })
    expect(
      within(card).getByText(
        '송장 입력 대기 키트가 4건 있어요. 지급보류 1건의 사유를 확인해 주세요.',
      ),
    ).toBeInTheDocument()
    expect(within(card).getByRole('link', { name: '크루런 AI에 물어보기' })).toHaveAttribute(
      'href',
      '/seller/ai',
    )
  })

  it('지급 예정 정산이 없어 다음 정산일이 null이면 지급 예정 정산이 없다고 보여 준다', async () => {
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

  it('정산 요약과 대회 목록이 실패하면 각각 서버 문구를 보여 준다', async () => {
    server.use(
      http.get('*/api/v1/payout/seller/summary', () =>
        HttpResponse.json(
          { status: 403, message: '정산 요약을 볼 수 없습니다.', data: null },
          { status: 403 },
        ),
      ),
      http.get('*/api/v1/product/races/mine', () =>
        HttpResponse.json(
          { status: 403, message: '승인된 판매자만 볼 수 있습니다.', data: null },
          { status: 403 },
        ),
      ),
    )
    renderPage()

    expect(await screen.findByText('정산 요약을 불러오지 못했어요')).toBeInTheDocument()
    expect(screen.getByText('정산 요약을 볼 수 없습니다.')).toBeInTheDocument()
    expect(await screen.findByText('대회 목록을 불러오지 못했어요')).toBeInTheDocument()
    expect(screen.getByText('승인된 판매자만 볼 수 있습니다.')).toBeInTheDocument()
    // 판매한 참가권은 로딩 칸으로 남지 않고 - 로 바뀐다
    const payout = await card('다음 정산')
    expect(within(payout).getByText('-')).toBeInTheDocument()
  })

  it('처리할 일은 송장 입력 대기와 지급보류 건수를 더하고 각 메뉴로 연결한다', async () => {
    renderPage()

    // 배송 준비 3건 중 환불 기한 전 1건은 아직 보낼 수 없어 2건
    const todo = await card(/처리할 일/)
    const kit = await within(todo).findByRole('link', { name: /송장 입력 대기 2건/ })
    expect(kit).toHaveAttribute('href', '/seller/kits')
    const hold = await within(todo).findByRole('link', { name: /지급보류 1건/ })
    expect(hold).toHaveAttribute('href', '/seller/settlements')
    expect(await screen.findByText('처리할 일 · 3건')).toBeInTheDocument()
  })

  it('키트 건수를 불러오지 못하면 건수 대신 - 를 보여 준다', async () => {
    server.use(kitSummaryUnavailable())
    renderPage()

    expect(await screen.findByRole('link', { name: /송장 입력 대기 -/ })).toBeInTheDocument()
  })

  it('키트 건수 요청이 재시도 중이어도 첫 실패부터 - 를 보여 준다', async () => {
    server.use(kitSummaryUnavailable())
    // 재시도 간격을 길게 줘서, 재시도가 끝나기 전에 - 가 먼저 보이는지 본다
    renderPage({ retry: 3, retryDelay: 60_000 })

    expect(await screen.findByRole('link', { name: /송장 입력 대기 -/ })).toBeInTheDocument()
  })

  it('키트 배송 현황 표는 송장 입력 대기부터 보여 주고 탭으로 배송 중을 볼 수 있다', async () => {
    const user = userEvent.setup()
    renderPage()

    const table = await screen.findByRole('table', { name: '키트 배송 현황' })
    expect(await within(table).findByText('달리는곰')).toBeInTheDocument()
    expect(within(table).getAllByRole('link', { name: '송장 입력' })[0]).toHaveAttribute(
      'href',
      '/seller/kits',
    )

    await user.click(screen.getByRole('tab', { name: '배송 중' }))
    expect(await screen.findByText('한강토끼')).toBeInTheDocument()
    const shipping = screen.getByRole('table', { name: '키트 배송 현황' })
    expect(within(shipping).queryByText('달리는곰')).not.toBeInTheDocument()
    expect(within(shipping).queryByRole('link', { name: '송장 입력' })).not.toBeInTheDocument()
  })

  it('코스별 판매 현황에 판매 · 결제 대기 · 남은 자리와 코스 막대를 보여 준다', async () => {
    server.use(
      http.get('*/api/v1/product/races/mine', () =>
        HttpResponse.json({
          status: 200,
          message: 'ok',
          data: [sellerRace(1, '춘천 호수', 'ON_SALE')],
        }),
      ),
    )
    renderPage()

    expect(await screen.findByText('10장')).toBeInTheDocument()
    expect(screen.getByText('5장')).toBeInTheDocument()
    expect(screen.getByText('85자리')).toBeInTheDocument()
    const bars = screen.getByRole('list', { name: '코스별 판매' })
    expect(within(bars).getByText('10/100')).toBeInTheDocument()
  })
})

/** 제목으로 대시보드 카드(section)를 찾는다 */
async function card(title: string | RegExp) {
  const heading = await screen.findByRole('heading', { name: title })
  return heading.closest('section') as HTMLElement
}

function kitSummaryUnavailable() {
  return http.get('*/api/v1/order/seller/kits/summary', () =>
    HttpResponse.json(
      { status: 503, code: 'ORDER_EXTERNAL_SERVICE_UNAVAILABLE', message: '잠시 뒤', data: null },
      { status: 503 },
    ),
  )
}

function sellerRace(raceId: number, title: string, saleStatus: string) {
  return {
    raceId,
    title,
    raceDate: '2027-01-01T08:00:00',
    saleStatus,
    saleOpenDate: '2026-10-01',
    applicationCloseAt: '2026-12-01T18:00:00',
    courses: [
      {
        raceCourseId: raceId,
        name: '10K',
        price: 45000,
        status: 'OPEN',
        quota: 100,
        reserved: 5,
        sold: 10,
      },
    ],
  }
}
