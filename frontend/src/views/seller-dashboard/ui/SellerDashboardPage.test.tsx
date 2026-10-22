import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
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
  it('정산 요약과 판매 중 대회를 API로 보여 준다', async () => {
    renderPage()

    expect(await screen.findByText('₩94,500')).toBeInTheDocument()
    expect(screen.getByText('(화)')).toBeInTheDocument()
    expect(await screen.findByText('접수율')).toBeInTheDocument()
    expect(screen.getByText('15/600명')).toBeInTheDocument()
    expect(screen.getByText('판매 중 대회')).toBeInTheDocument()
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
  })

  it('처리할 일의 배송 대기 키트를 키트 건수 API로 채우고 키트 화면으로 연결한다', async () => {
    renderPage()

    // 배송 준비 3건 중 환불 기한 전 1건은 아직 보낼 수 없어 2건
    const link = await screen.findByRole('link', { name: /배송 대기 키트/ })
    expect(link).toHaveAttribute('href', '/seller/kits')
    expect(await within(link).findByText('2건')).toBeInTheDocument()
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

    const link = await screen.findByRole('link', { name: /배송 대기 키트/ })
    expect(await within(link).findByText('-')).toBeInTheDocument()
  })

  it('키트 건수 요청이 재시도 중이어도 첫 실패부터 - 를 보여 준다', async () => {
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
    // 재시도 간격을 길게 줘서, 재시도가 끝나기 전에 - 가 먼저 보이는지 본다
    renderPage({ retry: 3, retryDelay: 60_000 })

    const link = await screen.findByRole('link', { name: /배송 대기 키트/ })
    expect(await within(link).findByText('-')).toBeInTheDocument()
  })

  it('최근 주문은 예시 값 대신 준비 중 안내를 보여 준다', async () => {
    renderPage()

    expect(await screen.findByText('최근 주문 목록은 준비 중이에요')).toBeInTheDocument()
    expect(screen.queryByText('R-20261019-0907')).not.toBeInTheDocument()
    expect(screen.queryByText('주말장거리')).not.toBeInTheDocument()
  })

  it('처리할 일을 키트 건수 · 접수 마감 임박 대회 · 지급보류 건수로 채운다', async () => {
    server.use(
      http.get('*/api/v1/product/races/mine', () =>
        HttpResponse.json({
          status: 200,
          message: 'ok',
          data: [
            sellerRace(1, '마감 임박 대회', 'ON_SALE', kstDateTimeAfterDays(3)),
            sellerRace(2, '아직 먼 대회', 'ON_SALE', kstDateTimeAfterDays(20)),
            sellerRace(3, '판매 중지 대회', 'STOPPED', kstDateTimeAfterDays(2)),
          ],
        }),
      ),
    )
    renderPage()

    const closing = await screen.findByRole('link', { name: /접수 마감 임박/ })
    expect(closing).toHaveAttribute('href', '/seller/races')
    expect(await within(closing).findByText('D-3')).toBeInTheDocument()
    expect(within(closing).getByText(/마감 임박 대회 · .* 접수 마감/)).toBeInTheDocument()

    // 명세 예시: 지급보류 1건 (HD-20261012-0001 · 정산 계좌 미등록)
    const hold = screen.getByRole('link', { name: /지급보류 알림/ })
    expect(hold).toHaveAttribute('href', '/seller/settlements')
    expect(await within(hold).findByText('HD-20261012-0001 · 정산 계좌 미등록')).toBeInTheDocument()
    expect(within(hold).getByText('1건')).toBeInTheDocument()

    // 키트 2 + 마감 임박 1 + 지급보류 1
    expect(await screen.findByText('4')).toBeInTheDocument()
  })

  it('오늘 이미 마감 시각이 지난 대회는 접수 마감 임박에서 뺀다', async () => {
    server.use(
      http.get('*/api/v1/product/races/mine', () =>
        HttpResponse.json({
          status: 200,
          message: 'ok',
          data: [sellerRace(1, '방금 마감한 대회', 'ON_SALE', kstDateTimeFromNow(-60))],
        }),
      ),
    )
    renderPage()

    expect(await screen.findByText('7일 안에 접수가 끝나는 대회가 없어요')).toBeInTheDocument()
    expect(screen.queryByText(/방금 마감한 대회 ·/)).not.toBeInTheDocument()
  })

  it('처리할 일이 없으면 0건과 없다는 문구를 보여 준다', async () => {
    server.use(
      http.get('*/api/v1/product/races/mine', () =>
        HttpResponse.json({ status: 200, message: 'ok', data: [] }),
      ),
      http.get('*/api/v1/payout/seller/summary', () =>
        HttpResponse.json({
          status: 200,
          message: 'ok',
          data: { nextPayDate: '2026-11-10', scheduledPayoutAmount: 0, heldCount: 0 },
        }),
      ),
    )
    renderPage()

    expect(await screen.findByText('7일 안에 접수가 끝나는 대회가 없어요')).toBeInTheDocument()
    expect(await screen.findByText('보류된 정산이 없어요')).toBeInTheDocument()
  })
})

/** 지금부터 days일 뒤 18:00 (한국 시간, 시간대 없는 모양) */
function kstDateTimeAfterDays(days: number): string {
  const kst = new Date(Date.now() + 9 * 60 * 60 * 1000 + days * 24 * 60 * 60 * 1000)
  return `${kst.toISOString().slice(0, 10)}T18:00:00`
}

/** 지금부터 minutes분 뒤의 한국 시간 (시간대 없는 모양) */
function kstDateTimeFromNow(minutes: number): string {
  return new Date(Date.now() + 9 * 60 * 60 * 1000 + minutes * 60 * 1000).toISOString().slice(0, 19)
}

function sellerRace(raceId: number, title: string, saleStatus: string, applicationCloseAt: string) {
  return {
    raceId,
    title,
    raceDate: '2027-01-01T08:00:00',
    saleStatus,
    saleOpenDate: '2026-10-01',
    applicationCloseAt,
    courses: [
      {
        raceCourseId: raceId,
        name: '10K',
        price: 45000,
        status: 'OPEN',
        quota: 100,
        reserved: 0,
        sold: 10,
      },
    ],
  }
}
