import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

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
})
