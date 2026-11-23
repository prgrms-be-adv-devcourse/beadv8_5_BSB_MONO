import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'

import { detailPeriods } from '@/entities/payout'
import { payoutMock, server } from '@/shared/testing'
import { ToastProvider } from '@/shared/ui'

import { SellerSettlementsPage, type SettlementTab } from './SellerSettlementsPage'

function renderPage(initialTab?: SettlementTab) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <SellerSettlementsPage initialTab={initialTab} />
      </ToastProvider>
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
    expect(
      within(screen.getByRole('group', { name: '정산 요약' })).getByText('10/20 (화)'),
    ).toBeInTheDocument()
    expect(await screen.findByText('ST-20260926-0002')).toBeInTheDocument()
    expect(months).toMatch(/^\d{4}-\d{2}~\d{4}-\d{2}$/)
  })

  it('월별 정산 탭의 엑셀 버튼은 최근 12개월 정산 내역을 CSV로 받아 저장한다', async () => {
    let body: unknown
    URL.createObjectURL = vi.fn(() => 'blob:payouts')
    URL.revokeObjectURL = vi.fn()
    server.use(
      http.post('*/api/v1/payout/seller/payouts/export', async ({ request }) => {
        body = await request.json()
        return new HttpResponse('정산 ID\nST-1\n', { headers: { 'Content-Type': 'text/csv' } })
      }),
    )
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: '엑셀로 내려받기' }))

    await vi.waitFor(() => expect(URL.createObjectURL).toHaveBeenCalled())
    expect(body).toMatchObject({
      fromMonth: expect.stringMatching(/^\d{4}-\d{2}$/),
      toMonth: expect.stringMatching(/^\d{4}-\d{2}$/),
    })
  })

  it('월별 · 건별 정산 탭이 아니면 엑셀 버튼을 막는다', async () => {
    renderPage('holds')

    expect(await screen.findByRole('button', { name: '엑셀로 내려받기' })).toBeDisabled()
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

    const tile = (await screen.findByText('다음 지급일')).parentElement as HTMLElement
    expect(within(tile).getByText('-')).toBeInTheDocument()
  })

  it('월별 정산: 1차 · 최종을 나눠 보여 주고, 수수료 · 1차 정산금 차감은 최종 줄에만 보인다', async () => {
    renderPage()

    const finalRow = (await screen.findByText('ST-20260926-0002')).closest('tr')!
    expect(within(finalRow).getByText('최종 · 9월')).toBeInTheDocument()
    expect(within(finalRow).getByText('5,500')).toBeInTheDocument()
    expect(within(finalRow).getByText('40,000')).toBeInTheDocument()
    expect(within(finalRow).getByText('54,500')).toBeInTheDocument()
    expect(within(finalRow).getByText('지급 예정')).toBeInTheDocument()

    const firstRow = screen.getByText('ST-20260916-0001').closest('tr')!
    expect(within(firstRow).getByText('1차 · 9월')).toBeInTheDocument()
    expect(within(firstRow).getAllByText('–')).toHaveLength(2)
    expect(within(firstRow).getByText('40,000')).toBeInTheDocument()
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
    expect(screen.getByText('10/12 (월)')).toBeInTheDocument()
  })

  it('대회 취소로 닫힌 보류가 섞여도 지급보류 탭이 깨지지 않고 취소로 보여 준다', async () => {
    const base = payoutMock.getHoldsResponseMock()
    const [hold] = base.data!
    server.use(
      http.get('*/api/v1/payout/seller/holds', () =>
        HttpResponse.json({
          ...base,
          data: [
            hold,
            {
              ...hold,
              id: 2,
              holdCode: 'HD-20261012-0002',
              reasonType: '대회_취소_처리',
              status: '취소',
            },
          ],
        }),
      ),
    )
    renderPage('holds')

    expect(await screen.findByText('HD-20261012-0002')).toBeInTheDocument()
    expect(screen.getByText('취소')).toBeInTheDocument()
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

  it('금액 열 이름에 단위(원)를 쓴다', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(screen.getByRole('tab', { name: '건별 정산' }))
    expect(await screen.findByRole('columnheader', { name: '금액(원)' })).toBeInTheDocument()
  })

  it('지급보류 표의 금액 열 이름에도 단위(원)를 쓴다', async () => {
    renderPage('holds')

    expect(await screen.findByRole('columnheader', { name: '금액(원)' })).toBeInTheDocument()
  })

  it('대회별 정산은 기간 선택이 없으므로 빈 문구에 「이 기간에」를 쓰지 않는다', async () => {
    server.use(
      http.get('*/api/v1/payout/seller/payouts', () =>
        HttpResponse.json({ status: 200, message: '', data: [] }),
      ),
    )
    renderPage()

    expect(await screen.findByText('정산 내역이 없어요')).toBeInTheDocument()
  })

  it('건별 정산은 기간 선택이 있으므로 빈 문구에 「이 기간에」를 쓴다', async () => {
    server.use(
      http.get('*/api/v1/payout/seller/payout-items', () =>
        HttpResponse.json({
          status: 200,
          message: '',
          data: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 },
        }),
      ),
    )
    const user = userEvent.setup()
    renderPage()

    await user.click(screen.getByRole('tab', { name: '건별 정산' }))
    expect(await screen.findByText('이 기간에 건별 정산이 없어요')).toBeInTheDocument()
  })

  it('대회별 정산 줄의 대회 이름을 누르면 정산 상세로 간다', async () => {
    renderPage()

    const row = (await screen.findByText('ST-20260926-0002')).closest('tr')!
    expect(within(row).getByRole('link', { name: '2026 한강 나이트런' })).toHaveAttribute(
      'href',
      '/seller/settlements/ST-20260926-0002',
    )
  })

  it('회수: 청구마다 사유 · 금액 · 입금 기한 · 입금 계좌 · 상태를 응답 값으로 보여 준다', async () => {
    let query = ''
    server.use(
      http.get('*/api/v1/payout/seller/claims', ({ request }) => {
        query = new URL(request.url).search
        return undefined
      }),
    )
    const user = userEvent.setup()
    renderPage()

    await user.click(screen.getByRole('tab', { name: '회수' }))
    const row = (await screen.findByText('RC-20261113-0001')).closest('tr')!
    expect(within(row).getByText('춘천 하프마라톤')).toBeInTheDocument()
    expect(within(row).getByText('일정 변경')).toBeInTheDocument()
    expect(within(row).getByText('60,000')).toBeInTheDocument()
    expect(within(row).getByText('12/13 (일)')).toBeInTheDocument()
    expect(within(row).getByText('국민은행')).toBeInTheDocument()
    expect(within(row).getByText('입금 대기')).toBeInTheDocument()
    expect(query).toMatch(/from=\d{4}-\d{2}-\d{2}&to=\d{4}-\d{2}-\d{2}/)

    const done = screen.getByText('RC-20260930-0002').closest('tr')!
    expect(within(done).getByText('입금 완료')).toBeInTheDocument()

    // 가상계좌를 아직 받지 않은 청구는 계좌 칸을 비워 둔다
    const noAccount = screen.getByText('RC-20260812-0001').closest('tr')!
    expect(within(noAccount).getByText('-')).toBeInTheDocument()
  })

  it('회수: 입금 계좌를 누르면 계좌번호를 복사하고 알려 준다', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(screen.getByRole('tab', { name: '회수' }))
    await user.click(await screen.findByRole('button', { name: /904-2201-3344-12/ }))
    expect(await navigator.clipboard.readText()).toBe('904-2201-3344-12')
    expect(await screen.findByText('계좌번호를 복사했어요')).toBeInTheDocument()
  })

  it('세금계산서: 공급 월마다 금액 · 승인번호 · 발행 일시 · 상태를 보여 주고 상세로 간다', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(screen.getByRole('tab', { name: '세금계산서' }))
    const issued = (await screen.findByRole('link', { name: '2026.10' })).closest('tr')!
    expect(within(issued).getByText('277,200')).toBeInTheDocument()
    expect(within(issued).getByText('252,000')).toBeInTheDocument()
    expect(within(issued).getByText('25,200')).toBeInTheDocument()
    expect(within(issued).getByText('20261101-41000000-01234567')).toBeInTheDocument()
    expect(within(issued).getByText('11/1 (일) 06:10')).toBeInTheDocument()
    expect(within(issued).getByText('발행 완료')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '2026.10' })).toHaveAttribute(
      'href',
      '/seller/settlements/tax-invoices/3',
    )

    const failed = screen.getByRole('link', { name: '2026.09' }).closest('tr')!
    expect(within(failed).getByText('-')).toBeInTheDocument()
    expect(within(failed).getByText('발행 실패')).toBeInTheDocument()
  })

  it('세금계산서는 기간 선택이 없으므로 빈 문구에 「이 기간에」를 쓰지 않는다', async () => {
    server.use(
      http.get('*/api/v1/payout/seller/tax-invoices', () =>
        HttpResponse.json({ status: 200, message: '', data: [] }),
      ),
    )
    const user = userEvent.setup()
    renderPage()

    await user.click(screen.getByRole('tab', { name: '세금계산서' }))
    expect(await screen.findByText('발행한 세금계산서가 없어요')).toBeInTheDocument()
  })

  it('회수: 빈 기간이면 「이 기간에」 문구를 쓴다', async () => {
    server.use(
      http.get('*/api/v1/payout/seller/claims', () =>
        HttpResponse.json({ status: 200, message: '', data: [] }),
      ),
    )
    const user = userEvent.setup()
    renderPage()

    await user.click(screen.getByRole('tab', { name: '회수' }))
    expect(await screen.findByText('이 기간에 회수 내역이 없어요')).toBeInTheDocument()
  })

  it('회수: 불러오지 못하면 서버 문구를 보여 준다', async () => {
    server.use(
      http.get('*/api/v1/payout/seller/claims', () =>
        HttpResponse.json(
          { status: 400, message: '조회 시작이 끝보다 늦습니다.', data: null },
          { status: 400 },
        ),
      ),
    )
    const user = userEvent.setup()
    renderPage()

    await user.click(screen.getByRole('tab', { name: '회수' }))
    expect(await screen.findByText('조회 시작이 끝보다 늦습니다.')).toBeInTheDocument()
  })
})
