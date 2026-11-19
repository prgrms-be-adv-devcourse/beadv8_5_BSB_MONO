import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { setAccessToken } from '@/shared/api'
import { server } from '@/shared/testing'

import { DepositChargePage } from './DepositChargePage'

const router = vi.hoisted(() => ({ push: vi.fn(), back: vi.fn(), replace: vi.fn() }))
vi.mock('next/navigation', () => ({
  useRouter: () => router,
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
    await user.click(screen.getByRole('button', { name: '60,000원 충전하기' }))

    expect(requestCharge).toHaveBeenCalledWith({ amount: 60_000 })
    // 결제창으로 떠나는 동안 다시 누르지 못한다
    expect(screen.getByRole('button', { name: '60,000원 충전하기' })).toBeDisabled()
  })

  it('1만 원보다 적으면 충전 버튼을 막는다', async () => {
    const user = userEvent.setup()
    renderPage()

    const input = screen.getByLabelText('충전할 금액')
    await user.clear(input)
    await user.type(input, '9000')

    expect(screen.getByRole('button', { name: '9,000원 충전하기' })).toBeDisabled()
  })

  it('숫자가 아닌 글자를 치면 값은 그대로이고 숫자만 입력할 수 있다는 안내가 보인다', async () => {
    const user = userEvent.setup()
    renderPage()

    const input = screen.getByLabelText('충전할 금액')
    expect(screen.queryByText('숫자만 입력할 수 있어요')).not.toBeInTheDocument()
    await user.type(input, 'a')

    expect(input).toHaveValue('10,000')
    expect(screen.getByText('숫자만 입력할 수 있어요')).toBeInTheDocument()
    await user.type(input, '0')
    expect(screen.queryByText('숫자만 입력할 수 있어요')).not.toBeInTheDocument()
  })

  it('결제창을 띄우지 못하면 결제 실패 문구를 보이고 다시 누를 수 있다', async () => {
    requestCharge.mockRejectedValue(new Error('no client key'))
    const user = userEvent.setup()
    renderPage()

    await user.click(screen.getByRole('button', { name: '10,000원 충전하기' }))

    expect(await screen.findByText('결제하지 못했어요')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '10,000원 충전하기' })).toBeEnabled()
  })

  it('앞선 충전 결과를 아직 모르면 결제창을 띄우지 않고 기다리라고 안내한다', async () => {
    requestCharge.mockResolvedValue('held')
    const user = userEvent.setup()
    renderPage()

    await user.click(screen.getByRole('button', { name: '10,000원 충전하기' }))

    expect(await screen.findByText('앞선 충전 결과를 확인하고 있어요')).toBeInTheDocument()
    // 예치금 결제와 헷갈리지 않게 카드 결제라고 쓴다
    expect(screen.getByText(/카드 결제가 됐다면/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '10,000원 충전하기' })).toBeEnabled()
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
    expect(await screen.findByText('5,000원')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '10,000원 충전하기' }))

    expect(await screen.findByText('17,000원')).toBeInTheDocument()
  })

  it('1회 한도(30만 원)를 넘는 금액은 충전 버튼을 막고 알려 준다', async () => {
    const user = userEvent.setup()
    renderPage()

    const input = screen.getByLabelText('충전할 금액')
    await user.clear(input)
    await user.type(input, '310000')

    expect(screen.getByText('한 번에 300,000원까지 충전할 수 있어요.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '310,000원 충전하기' })).toBeDisabled()
  })

  it('오늘 충전한 금액과 더해 하루 한도(100만 원)를 넘으면 충전 버튼을 막는다', async () => {
    setAccessToken('access-token')
    // 서버 createdAt은 한국 시간이다
    const todayKst = new Date(Date.now() + 9 * 3_600_000).toISOString().slice(0, 19)
    const row = (amount: number) => ({
      category: 'CHARGE',
      ledgerType: '충전__PG결제_토스페이먼츠',
      amount,
      balanceAfter: amount,
      createdAt: todayKst,
      refId: 1,
      charge: null,
    })
    server.use(
      http.get('*/api/v1/cash/wallets/ledgers', () =>
        HttpResponse.json({
          status: 200,
          message: '예치금 내역을 조회했습니다.',
          data: {
            content: [row(300_000), row(300_000), row(300_000)],
            page: 0,
            size: 100,
            totalElements: 3,
            totalPages: 1,
          },
        }),
      ),
    )
    const user = userEvent.setup()
    renderPage()

    await user.click(screen.getByRole('button', { name: '+10만' }))

    expect(await screen.findByText('오늘은 100,000원까지 더 충전할 수 있어요.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '110,000원 충전하기' })).toBeDisabled()
  })

  it('결제창에서 돌아와 거절됐으면 서버 문구를 모달로 보여 준다', async () => {
    sessionStorage.setItem(
      'crewrun:charge-notice',
      JSON.stringify({
        kind: 'failed',
        message: '일일 충전 한도는 1,000,000원입니다.',
        savedAt: Date.now(),
      }),
    )
    const user = userEvent.setup()
    renderPage()

    const dialog = await screen.findByRole('alertdialog')
    expect(dialog).toHaveTextContent('충전하지 못했어요')
    expect(dialog).toHaveTextContent('일일 충전 한도는 1,000,000원입니다.')
    expect(dialog).toHaveTextContent('결제된 금액은 없어요.')
    await user.click(screen.getByRole('button', { name: '확인' }))
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
    // 한 번만 보여 준다
    expect(sessionStorage.getItem('crewrun:charge-notice')).toBeNull()
  })

  it('결제창에서 돌아와 충전됐으면 충전 전 화면으로 한 번 더 돌아간다', async () => {
    router.replace.mockClear()
    sessionStorage.setItem(
      'crewrun:charge-notice',
      JSON.stringify({ kind: 'completed', savedAt: Date.now() }),
    )
    renderPage()

    // 테스트에서는 앱 안 기록이 없어 예치금으로 바꿔 간다 (기록이 있으면 뒤로 간다)
    await vi.waitFor(() => expect(router.replace).toHaveBeenCalledWith('/me/deposit'))
  })
})
