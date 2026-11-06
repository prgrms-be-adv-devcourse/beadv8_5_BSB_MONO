import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'

import { detailPeriods } from '@/entities/payout'
import { payoutMock, server } from '@/shared/testing'

import { SellerSettlementsPage, type SettlementTab } from './SellerSettlementsPage'

function renderPage(initialTab?: SettlementTab) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <SellerSettlementsPage initialTab={initialTab} />
    </QueryClientProvider>,
  )
}

describe('SellerSettlementsPage', () => {
  it('요약 카드와 월별 정산을 정산 API로 보여 준다', async () => {
    let months: string | null = null
    server.use(
      http.get('*/api/v1/payout/seller/payouts', ({ request }) => {
        const url = new URL(request.url)
        months = `${url.searchParams.get('fromMonth')}~${url.searchParams.get('toMonth')}`
        return HttpResponse.json(payoutMock.getPayoutsResponseMock())
      }),
    )
    renderPage()

    expect(await screen.findByText('94,500')).toBeInTheDocument()
    expect(screen.getByText('(화)')).toBeInTheDocument()
    expect(await screen.findByText('ST-20260926-0002')).toBeInTheDocument()
    expect(months).toMatch(/^\d{4}-\d{2}~\d{4}-\d{2}$/)
  })

  it('지급 예정 정산이 없어 다음 정산일이 null이면 - 를 보여 준다', async () => {
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

    const tile = (await screen.findByText('다음 정산일')).parentElement as HTMLElement
    expect(within(tile).getByText('-')).toBeInTheDocument()
  })

  it('월별 정산: 1차 · 최종을 나눠 보여 주고, 수수료 · 1차 정산금 차감은 최종 줄에만 보인다', async () => {
    renderPage()

    const finalRow = (await screen.findByText('ST-20260926-0002')).closest('tr')!
    expect(within(finalRow).getByText('최종 · 9월')).toBeInTheDocument()
    expect(within(finalRow).getByText('₩5,500')).toBeInTheDocument()
    expect(within(finalRow).getByText('₩40,000')).toBeInTheDocument()
    expect(within(finalRow).getByText('₩54,500')).toBeInTheDocument()
    expect(within(finalRow).getByText('지급 예정')).toBeInTheDocument()

    const firstRow = screen.getByText('ST-20260916-0001').closest('tr')!
    expect(within(firstRow).getByText('1차 · 9월')).toBeInTheDocument()
    expect(within(firstRow).getAllByText('–')).toHaveLength(2)
    expect(within(firstRow).getByText('₩40,000')).toBeInTheDocument()
    expect(within(firstRow).getByText('지급 예정')).toBeInTheDocument()
  })

  it('월별 정산: 1차와 최종이 다른 달이면 구분 칸에 각자의 정산 월을 보여 준다', async () => {
    const mock = payoutMock.getPayoutsResponseMock()
    const [final, first] = mock.data ?? []
    if (!final || !first) throw new Error('월별 정산 목 데이터가 비었어요')
    server.use(
      http.get('*/api/v1/payout/seller/payouts', () =>
        HttpResponse.json({ ...mock, data: [{ ...final, payoutMonth: '2026-10' }, first] }),
      ),
    )
    renderPage()

    const finalRow = (await screen.findByText('ST-20260926-0002')).closest('tr')!
    expect(within(finalRow).getByText('최종 · 10월')).toBeInTheDocument()
    const firstRow = screen.getByText('ST-20260916-0001').closest('tr')!
    expect(within(firstRow).getByText('1차 · 9월')).toBeInTheDocument()
  })

  it('월별 정산: 취소된 정산은 취소로 보인다', async () => {
    const mock = payoutMock.getPayoutsResponseMock()
    server.use(
      http.get('*/api/v1/payout/seller/payouts', () =>
        HttpResponse.json({ ...mock, data: [{ ...mock.data![0], status: '취소' }] }),
      ),
    )
    renderPage()

    const row = (await screen.findByText('ST-20260926-0002')).closest('tr')!
    expect(within(row).getByText('취소')).toBeInTheDocument()
  })

  it('월별 정산: 지급 중과 모르는 상태 값을 깨지지 않고 보여 준다', async () => {
    const mock = payoutMock.getPayoutsResponseMock()
    const base = mock.data?.[0]
    if (!base) throw new Error('월별 정산 목 데이터가 비었어요')
    server.use(
      http.get('*/api/v1/payout/seller/payouts', () =>
        HttpResponse.json({
          ...mock,
          data: [
            { ...base, id: 1, payoutCode: 'ST-1', status: '지급_중' },
            { ...base, id: 2, payoutCode: 'ST-2', status: 'SOMETHING_NEW' },
          ],
        }),
      ),
    )
    renderPage()

    expect(await screen.findByText('지급 중')).toBeInTheDocument()
    expect(screen.getByText('SOMETHING_NEW')).toBeInTheDocument()
  })

  it('건별 정산: 지급 중 상태를 보여 준다', async () => {
    const mock = payoutMock.getItemsResponseMock()
    const page = mock.data
    if (!page) throw new Error('건별 정산 목 데이터가 비었어요')
    server.use(
      http.get('*/api/v1/payout/seller/payout-items', () =>
        HttpResponse.json({
          ...mock,
          data: {
            ...page,
            content: page.content.map((item) => ({ ...item, payoutStatus: '지급_중' })),
          },
        }),
      ),
    )
    const user = userEvent.setup()
    renderPage()

    await user.click(screen.getByRole('tab', { name: '건별 정산' }))
    expect(await screen.findByText('지급 중')).toBeInTheDocument()
  })

  it('건별 정산: 기간을 바꾸면 그 기간으로 다시 조회한다', async () => {
    const requests: string[] = []
    server.use(
      http.get('*/api/v1/payout/seller/payout-items', ({ request }) => {
        const url = new URL(request.url)
        requests.push(
          `${url.searchParams.get('from')}~${url.searchParams.get('to')}:${url.searchParams.get('page')}`,
        )
        return HttpResponse.json(payoutMock.getItemsResponseMock())
      }),
    )
    const periods = detailPeriods(new Date())
    const user = userEvent.setup()
    renderPage()

    await user.click(screen.getByRole('tab', { name: '건별 정산' }))
    expect(await screen.findByText('판매')).toBeInTheDocument()
    expect(requests.at(-1)).toBe(`${periods[0].value}:0`)

    await user.click(screen.getByRole('combobox', { name: '조회 기간' }))
    await user.click(await screen.findByRole('option', { name: periods[1].label }))

    await vi.waitFor(() => expect(requests.at(-1)).toBe(`${periods[1].value}:0`))
  })

  it('지급보류 내역을 보여 준다', async () => {
    renderPage('holds')

    expect(await screen.findByText('HD-20261012-0001')).toBeInTheDocument()
    expect(screen.getByText('정산 계좌 미등록')).toBeInTheDocument()
    expect(screen.getByText('10/12')).toBeInTheDocument()
  })

  it('조회가 400이면 서버 문구를 보여 준다', async () => {
    server.use(
      http.get('*/api/v1/payout/seller/holds', () =>
        HttpResponse.json(
          {
            status: 400,
            message: '건별 정산·지급보류 내역은 최대 1개월까지 조회할 수 있습니다.',
            data: null,
          },
          { status: 400 },
        ),
      ),
    )
    renderPage('holds')

    expect(
      await screen.findByText('건별 정산·지급보류 내역은 최대 1개월까지 조회할 수 있습니다.'),
    ).toBeInTheDocument()
  })
})
