import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { TaxInvoiceDetailPage } from './TaxInvoiceDetailPage'

function renderPage(invoiceId: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <TaxInvoiceDetailPage invoiceId={invoiceId} />
    </QueryClientProvider>,
  )
}

describe('세금계산서 상세 (시안 2070:70160 · 2070:70207)', () => {
  it('발행 완료: 금액 · 공급받는 자 · 승인번호를 응답 값 그대로 보여 준다', async () => {
    renderPage('3')

    expect(await screen.findByText('2026년 10월 공급분')).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 1, name: '세금계산서' })).toBeInTheDocument()
    expect(screen.getByText('발행 완료')).toBeInTheDocument()

    const amount = screen.getByRole('region', { name: '금액' })
    expect(within(amount).getByText('277,200원')).toBeInTheDocument()
    expect(within(amount).getByText('252,000원')).toBeInTheDocument()
    expect(within(amount).getByText('25,200원')).toBeInTheDocument()

    const buyer = screen.getByRole('region', { name: '공급받는 자' })
    expect(within(buyer).getByText('나이트런컴퍼니')).toBeInTheDocument()
    expect(within(buyer).getByText('123-45-67890')).toBeInTheDocument()
    expect(within(buyer).getByText('김러너')).toBeInTheDocument()

    const issue = screen.getByRole('region', { name: '발행 정보' })
    expect(within(issue).getByText('20261101-41000000-01234567')).toBeInTheDocument()
    expect(within(issue).getByText('2026-11-01 (일) 06:10')).toBeInTheDocument()

    expect(screen.getByRole('link', { name: '세금계산서' })).toHaveAttribute(
      'href',
      '/seller/settlements?tab=taxInvoices',
    )
  })

  it('발행 실패: 실패 사유를 보여 주고 승인번호 줄은 없다', async () => {
    renderPage('2')

    expect(await screen.findByText('발행 실패')).toBeInTheDocument()
    expect(screen.getByText('다음 정산 때 다시 발행돼요')).toBeInTheDocument()
    const issue = screen.getByRole('region', { name: '발행 정보' })
    expect(
      within(issue).getByText('공급받는 자의 사업자등록번호가 휴업 상태예요'),
    ).toBeInTheDocument()
    expect(within(issue).queryByText('승인번호')).not.toBeInTheDocument()
  })

  it('목록에 없는 세금계산서면 찾을 수 없다고 알려 준다', async () => {
    renderPage('999')

    expect(await screen.findByText('세금계산서를 찾을 수 없어요')).toBeInTheDocument()
  })
})
