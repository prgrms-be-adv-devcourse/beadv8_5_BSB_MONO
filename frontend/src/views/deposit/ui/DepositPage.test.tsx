import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { setAccessToken } from '@/shared/api'
import { server } from '@/shared/testing'

import { DepositPage } from './DepositPage'

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), replace: vi.fn() }),
  usePathname: () => '/me/deposit',
}))

beforeEach(() => setAccessToken('access-token'))

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <DepositPage />
    </QueryClientProvider>,
  )
}

describe('DepositPage 잔액', () => {
  it('회원 ID 없이(경로에 ID 없이) 내 지갑 잔액을 불러와 보여 준다', async () => {
    let balanceCalls = 0
    server.use(
      http.get('*/api/v1/member/members/me', () =>
        HttpResponse.json({ status: 200, message: '조회 성공', data: { id: 7, nickname: '러너' } }),
      ),
      http.get('*/api/v1/cash/wallets/balance', () => {
        balanceCalls += 1
        return HttpResponse.json({
          status: 200,
          message: '잔액 조회 성공',
          data: { holderId: 7, balance: 12000 },
        })
      }),
    )
    renderPage()

    expect(await screen.findByText('12,000원')).toBeInTheDocument()
    expect(balanceCalls).toBe(1)
  })

  it('지갑이 없으면(404) 잔액 대신 - 와 안내를 보여 준다', async () => {
    server.use(
      http.get('*/api/v1/cash/wallets/balance', () =>
        HttpResponse.json(
          { status: 404, message: '존재하지 않는 지갑입니다.', data: null },
          { status: 404 },
        ),
      ),
    )
    renderPage()

    expect(await screen.findByText('잔액을 불러오지 못했어요.')).toBeInTheDocument()
    expect(screen.getByText('-')).toBeInTheDocument()
  })
})

type Ledger = {
  category: string
  ledgerType: string
  amount: number
  balanceAfter: number
  createdAt: string
  refId: number
}

const ledgerPage = (content: Ledger[], page = 0, totalPages = 1) =>
  HttpResponse.json({
    status: 200,
    message: '예치금 내역을 조회했습니다.',
    data: { content, page, size: 20, totalElements: content.length, totalPages },
  })

const charge: Ledger = {
  category: 'CHARGE',
  ledgerType: '충전__PG결제_토스페이먼츠',
  amount: 10000,
  balanceAfter: 60000,
  createdAt: '2026-10-05T14:21:03.319058',
  refId: 1,
}
const payment: Ledger = {
  category: 'PAYMENT',
  ledgerType: '사용__주문결제',
  amount: -45000,
  balanceAfter: 15000,
  createdAt: '2026-10-05T14:22:00',
  refId: 12,
}

describe('DepositPage 내역', () => {
  it('충전은 +, 결제는 − 로 금액과 날짜를 시안 모양으로 보여 준다', async () => {
    server.use(http.get('*/api/v1/cash/wallets/ledgers', () => ledgerPage([payment, charge])))
    renderPage()

    const items = await screen.findAllByRole('listitem')
    expect(within(items[0]).getByText('결제 · 주문 #12')).toBeInTheDocument()
    expect(within(items[0]).getByText('− 45,000원')).toBeInTheDocument()
    expect(within(items[0]).getByText('10/5 14:22')).toBeInTheDocument()
    expect(within(items[1]).getByText('충전 · 토스페이먼츠')).toBeInTheDocument()
    expect(within(items[1]).getByText('+ 10,000원')).toBeInTheDocument()
  })

  it('전체 탭은 category 없이, 다른 탭은 그 category로 부른다', async () => {
    const categories: (string | null)[] = []
    server.use(
      http.get('*/api/v1/cash/wallets/ledgers', ({ request }) => {
        categories.push(new URL(request.url).searchParams.get('category'))
        return ledgerPage([charge])
      }),
    )
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('충전 · 토스페이먼츠')

    for (const label of ['충전', '결제', '환불', '환급']) {
      await user.click(screen.getByRole('button', { name: label }))
    }
    await vi.waitFor(() => expect(categories).toHaveLength(5))
    expect(categories).toEqual([null, 'CHARGE', 'PAYMENT', 'REFUND', 'WITHDRAWAL'])
  })

  it('내역이 없으면 빈 목록 안내를 보여 준다', async () => {
    server.use(http.get('*/api/v1/cash/wallets/ledgers', () => ledgerPage([], 0, 0)))
    renderPage()

    expect(await screen.findByText('아직 내역이 없어요')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '더 보기' })).not.toBeInTheDocument()
  })

  it('더 보기를 누르면 다음 페이지를 이어 붙인다', async () => {
    const pages: (string | null)[] = []
    server.use(
      http.get('*/api/v1/cash/wallets/ledgers', ({ request }) => {
        const page = new URL(request.url).searchParams.get('page')
        pages.push(page)
        return page === '1' ? ledgerPage([charge], 1, 2) : ledgerPage([payment], 0, 2)
      }),
    )
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('결제 · 주문 #12')

    await user.click(screen.getByRole('button', { name: '더 보기' }))

    expect(await screen.findByText('충전 · 토스페이먼츠')).toBeInTheDocument()
    expect(screen.getByText('결제 · 주문 #12')).toBeInTheDocument()
    expect(pages).toEqual(['0', '1'])
    expect(screen.queryByRole('button', { name: '더 보기' })).not.toBeInTheDocument()
  })

  it('더 보기 사이 새 내역이 생겨 줄이 밀려도 같은 거래는 한 줄만 보여 준다', async () => {
    const newer: Ledger = {
      ...charge,
      balanceAfter: 70000,
      createdAt: '2026-10-06T09:00:00',
      refId: 2,
    }
    server.use(
      http.get('*/api/v1/cash/wallets/ledgers', ({ request }) => {
        const page = new URL(request.url).searchParams.get('page')
        // 0페이지 뒤 새 내역(newer)이 생겨 0페이지 끝 줄(payment)이 1페이지 첫 줄로 밀렸다
        return page === '1'
          ? ledgerPage([payment, charge], 1, 2)
          : ledgerPage([newer, payment], 0, 2)
      }),
    )
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('결제 · 주문 #12')

    await user.click(screen.getByRole('button', { name: '더 보기' }))

    await screen.findByText('10/5 14:21')
    expect(screen.getAllByText('결제 · 주문 #12')).toHaveLength(1)
    expect(screen.getAllByRole('listitem')).toHaveLength(3)
  })

  it('정산 입금은 + 와 정산 라벨로 보여 준다 (MSW 기본 목)', async () => {
    renderPage()

    expect(await screen.findByText('정산 · 판매 대금 입금')).toBeInTheDocument()
    expect(screen.getByText('+ 40,000원')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '더 보기' })).toBeInTheDocument()
  })

  it('내역을 못 불러오면 다시 불러오기 버튼을 보여 준다', async () => {
    server.use(
      http.get('*/api/v1/cash/wallets/ledgers', () =>
        HttpResponse.json(
          { status: 404, message: '존재하지 않는 지갑입니다.', data: null },
          { status: 404 },
        ),
      ),
    )
    renderPage()

    expect(await screen.findByText('내역을 불러오지 못했어요')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '다시 불러오기' })).toBeInTheDocument()
  })
})
