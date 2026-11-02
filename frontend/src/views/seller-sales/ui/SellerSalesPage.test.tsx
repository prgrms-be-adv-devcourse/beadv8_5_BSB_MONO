import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { server } from '@/shared/testing'

import { SellerSalesPage } from './SellerSalesPage'

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <SellerSalesPage />
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

    expect(await screen.findByRole('heading', { name: '2026 춘천 나이트런' })).toBeInTheDocument()
    // 판매 120 + 80 = 200, 정원 500, 남은 자리 500 - 200 - 결제 대기 10 = 290
    expect(screen.getByText('200')).toBeInTheDocument()
    expect(screen.getByText('290')).toBeInTheDocument()
    expect(screen.getAllByText('/ 500명')).toHaveLength(2)
    expect(screen.getByText('참가자 200명')).toBeInTheDocument()
    expect(await screen.findAllByText(/11\/5 \(목\) 23:59/)).not.toHaveLength(0)
  })

  it('예시 참가자 대신 준비 중 안내를 보여 주고 엑셀 내려받기를 막는다', async () => {
    mockRaces([RACE])
    renderPage()

    expect(await screen.findByText('참가자 목록은 준비 중이에요')).toBeInTheDocument()
    expect(screen.queryByText('R-20261014-0012')).not.toBeInTheDocument()
    expect(screen.getByText('엑셀 내려받기는 준비 중이에요')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '엑셀로 내려받기' })).toBeDisabled()
  })

  it('등록한 대회가 없으면 대회 등록으로 안내한다', async () => {
    mockRaces([])
    renderPage()

    expect(await screen.findByText('등록한 대회가 없어요')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '대회 등록' })).toHaveAttribute(
      'href',
      '/seller/races/new?from=sales',
    )
  })
})
