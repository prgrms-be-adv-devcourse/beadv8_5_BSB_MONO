import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { server } from '@/shared/testing'
import { ToastProvider } from '@/shared/ui'

import { SellerRaceDetailPage } from './SellerRaceDetailPage'

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <SellerRaceDetailPage raceId={1} />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

describe('SellerRaceDetailPage', () => {
  it('상태 글자와 종목별 판매 표를 보여 준다', async () => {
    renderPage()
    expect(await screen.findByRole('table', { name: '종목별 판매' })).toBeInTheDocument()
    expect(screen.getAllByText('판매 중').length).toBeGreaterThan(0)
    expect(screen.getByRole('button', { name: '판매 멈추기' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '판매 중 변경' })).toHaveAttribute(
      'href',
      '/seller/races/1/edit',
    )
  })

  it('판매 요약 · 기본 정보 · 종목 · 키트 구역을 보여 주고, 바로 가기에 대회 번호를 넘긴다', async () => {
    renderPage()
    expect(await screen.findByText('결제 대기')).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: '기본 정보' })).toBeInTheDocument()
    expect(screen.getByRole('table', { name: '종목 · 참가권' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /^판매 현황/ })).toHaveAttribute(
      'href',
      '/seller/sales?race=1',
    )
    expect(screen.getByRole('link', { name: '키트 배송' })).toHaveAttribute(
      'href',
      '/seller/kits?race=1',
    )
  })

  it('남은 자리는 정원 - 판매 - 결제 대기로 센다 (요약과 종목별 판매 모두)', async () => {
    server.use(
      http.get('*/api/v1/product/races/mine', () =>
        HttpResponse.json({
          status: 200,
          message: 'ok',
          data: [
            {
              raceId: 1,
              title: '테스트 대회',
              saleStatus: 'ON_SALE',
              applicationCloseAt: '2099-01-01T00:00:00',
              courses: [{ raceCourseId: 1, name: '10K', quota: 10, sold: 3, reserved: 2 }],
            },
          ],
        }),
      ),
    )
    renderPage()
    const table = await screen.findByRole('table', { name: '종목별 판매' })
    expect(within(table).getByRole('cell', { name: '5' })).toBeInTheDocument()
    expect(screen.getAllByText('5')).toHaveLength(2)
  })

  it('접수 마감이 지나면 마감으로 보여 준다', async () => {
    server.use(
      http.get('*/api/v1/product/races/mine', () =>
        HttpResponse.json({
          status: 200,
          message: 'ok',
          data: [
            {
              raceId: 1,
              title: '테스트 대회',
              saleStatus: 'ON_SALE',
              applicationCloseAt: '2000-01-01T00:00:00',
              courses: [],
            },
          ],
        }),
      ),
    )
    renderPage()
    expect(await screen.findByText('마감')).toBeInTheDocument()
  })

  it('키트를 불러오지 못하면 오류를 보여 준다', async () => {
    server.use(
      http.get('*/api/v1/product/races/:raceId/kit-options', () =>
        HttpResponse.json({ status: 500, message: 'x', data: null }, { status: 500 }),
      ),
    )
    renderPage()
    expect(await screen.findByText('키트를 불러오지 못했어요')).toBeInTheDocument()
  })
})
