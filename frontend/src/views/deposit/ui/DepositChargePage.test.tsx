import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { setAccessToken } from '@/shared/api'
import { server } from '@/shared/testing'

import { DepositChargePage } from './DepositChargePage'

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), replace: vi.fn() }),
  usePathname: () => '/me/deposit/charge',
}))

const requestCharge = vi.fn()
vi.mock('@/features/deposit-charge', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/features/deposit-charge')>()),
  requestCharge: (...args: unknown[]) => requestCharge(...args),
}))

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <DepositChargePage />
    </QueryClientProvider>,
  )
}

describe('예치금 충전 (M21)', () => {
  beforeEach(() => {
    requestCharge.mockReset()
    requestCharge.mockResolvedValue('redirecting')
  })

  it('고른 금액으로 결제창을 띄운다. 결제 중이 아니라 돌아갈 주문은 없다', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(screen.getByRole('button', { name: '+5만' }))
    await user.click(screen.getByRole('button', { name: '₩60,000 충전하기' }))

    expect(requestCharge).toHaveBeenCalledWith({ amount: 60_000 })
    // 결제창으로 떠나는 동안 다시 누르지 못한다
    expect(screen.getByRole('button', { name: '₩60,000 충전하기' })).toBeDisabled()
  })

  it('1만 원보다 적으면 충전 버튼을 막는다', async () => {
    const user = userEvent.setup()
    renderPage()

    const input = screen.getByLabelText('충전할 금액')
    await user.clear(input)
    await user.type(input, '9000')

    expect(screen.getByRole('button', { name: '₩9,000 충전하기' })).toBeDisabled()
  })

  it('결제창을 띄우지 못하면 결제 실패 문구를 보이고 다시 누를 수 있다', async () => {
    requestCharge.mockRejectedValue(new Error('no client key'))
    const user = userEvent.setup()
    renderPage()

    await user.click(screen.getByRole('button', { name: '₩10,000 충전하기' }))

    expect(await screen.findByText('결제하지 못했어요')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '₩10,000 충전하기' })).toBeEnabled()
  })

  it('앞선 충전 결과를 아직 모르면 결제창을 띄우지 않고 기다리라고 안내한다', async () => {
    requestCharge.mockResolvedValue('held')
    const user = userEvent.setup()
    renderPage()

    await user.click(screen.getByRole('button', { name: '₩10,000 충전하기' }))

    expect(await screen.findByText('앞선 충전 결과를 확인하고 있어요')).toBeInTheDocument()
    // 예치금 결제와 헷갈리지 않게 카드 결제라고 쓴다
    expect(screen.getByText(/카드 결제가 됐다면/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '₩10,000 충전하기' })).toBeEnabled()
  })

  it('충전이 막히면 잔액을 다시 불러, 앞선 충전이 이미 들어갔으면 늘어난 잔액을 보여 준다', async () => {
    setAccessToken('access-token')
    let balance = 5_000
    server.use(
      http.get('*/api/v1/cash/wallets/balance', () =>
        HttpResponse.json({
          status: 200,
          message: '잔액 조회 성공',
          data: { holderId: 7, balance },
        }),
      ),
    )
    requestCharge.mockImplementation(async () => {
      // 서버의 재조회가 앞선 충전을 이미 적립했다
      balance = 17_000
      return 'held'
    })
    const user = userEvent.setup()
    renderPage()
    expect(await screen.findByText('₩5,000')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '₩10,000 충전하기' }))

    expect(await screen.findByText('₩17,000')).toBeInTheDocument()
  })
})
