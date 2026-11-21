import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { server } from '@/shared/testing'

import { SettlementDetailPage } from './SettlementDetailPage'

function renderPage(payoutCode: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <SettlementDetailPage payoutCode={payoutCode} />
    </QueryClientProvider>,
  )
}

const noAccount = http.get('*/api/v1/member/sellers/me/payout-account', () =>
  HttpResponse.json({ status: 200, message: 'ok', data: { registered: false } }),
)

describe('정산 상세 (시안 2070:69311 · 2070:70381)', () => {
  it('최종 정산: 응답의 금액을 그대로 보여 주고, 빼는 금액에는 − 기호만 붙인다', async () => {
    renderPage('ST-20261019-B204')

    expect(await screen.findByText('7,390,200원')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '정산 상세' })).toBeInTheDocument()
    expect(screen.getByText('춘천 하프마라톤 · 최종 정산 · ST-20261019-B204')).toBeInTheDocument()

    const calc = screen.getByRole('region', { name: '지급액 계산' })
    expect(within(calc).getByText('14,000,000원')).toBeInTheDocument()
    expect(within(calc).getByText('−440,000원')).toBeInTheDocument()
    expect(within(calc).getByText('−745,800원')).toBeInTheDocument()
    expect(within(calc).getByText('−5,424,000원')).toBeInTheDocument()

    const pay = screen.getByRole('region', { name: '지급' })
    expect(within(pay).getByText('지급 예정')).toBeInTheDocument()
    expect(within(pay).getByText('11/20 (금)')).toBeInTheDocument()
    expect(await within(pay).findByText('국민은행 ****-0678')).toBeInTheDocument()

    expect(screen.getByRole('link', { name: '정산' })).toHaveAttribute(
      'href',
      '/seller/settlements',
    )
  })

  it('1차 정산은 수수료 · 1차 정산금 줄이 없다', async () => {
    renderPage('ST-20261019-A118')

    expect(await screen.findByText('5,424,000원')).toBeInTheDocument()
    expect(screen.getByText('춘천 하프마라톤 · 1차 정산 · ST-20261019-A118')).toBeInTheDocument()
    expect(screen.queryByText('수수료')).not.toBeInTheDocument()
    expect(screen.queryByText('1차 정산금')).not.toBeInTheDocument()
  })

  it('지급보류인데 정산 계좌가 없으면 이유와 계좌 등록 링크를 보여 준다', async () => {
    server.use(noAccount)
    renderPage('ST-20261019-A118')

    const pay = await screen.findByRole('region', { name: '지급' })
    expect(await within(pay).findByText('없음')).toBeInTheDocument()
    expect(within(pay).getByText('지급보류')).toBeInTheDocument()
    expect(within(pay).getByText('정산 계좌가 없어 지급을 멈췄어요')).toBeInTheDocument()
    expect(within(pay).getByRole('link', { name: '계좌 등록하기' })).toHaveAttribute(
      'href',
      '/seller/settings',
    )
  })

  it('지급보류여도 계좌가 있으면 계좌 이유를 지어내지 않는다', async () => {
    renderPage('ST-20261019-A118')

    expect(await screen.findByText('국민은행 ****-0678')).toBeInTheDocument()
    expect(screen.queryByText('정산 계좌가 없어 지급을 멈췄어요')).not.toBeInTheDocument()
  })

  it('지급 예정이면 계좌가 없어도 보류 이유를 보여 주지 않는다', async () => {
    server.use(noAccount)
    renderPage('ST-20261019-B204')

    expect(await screen.findByText('없음')).toBeInTheDocument()
    expect(screen.queryByText('정산 계좌가 없어 지급을 멈췄어요')).not.toBeInTheDocument()
  })

  it('내 정산이 아니거나 없는 정산 ID면 서버 문구를 보여 준다', async () => {
    renderPage('ST-00000000-0000')

    expect(await screen.findByText('정산 내역을 찾을 수 없습니다.')).toBeInTheDocument()
  })

  it('1차 정산은 지급액이 판매액에서 바로 나오지 않으므로 계산 기준을 알려 준다', async () => {
    renderPage('ST-20261019-A118')

    expect(
      await screen.findByText('1차 정산금은 참가권 가격의 40%예요. 수수료는 최종 정산에서 빼요.'),
    ).toBeInTheDocument()
  })

  it('정산 ID로 찾은 결과가 비어 있으면 찾을 수 없다고 알려 준다', async () => {
    server.use(
      http.get('*/api/v1/payout/seller/payouts', () =>
        HttpResponse.json({ status: 200, message: 'ok', data: [] }),
      ),
    )
    renderPage('ST-20261019-B204')

    expect(await screen.findByText('정산 내역을 찾을 수 없어요')).toBeInTheDocument()
  })

  it('정산 계좌를 불러오지 못하면 받는 계좌를 「-」로 둔다', async () => {
    server.use(
      http.get('*/api/v1/member/sellers/me/payout-account', () =>
        HttpResponse.json({ status: 503, message: '잠시 뒤', data: null }, { status: 503 }),
      ),
    )
    renderPage('ST-20261019-B204')

    const pay = await screen.findByRole('region', { name: '지급' })
    expect(await within(pay).findByText('-')).toBeInTheDocument()
  })
})
