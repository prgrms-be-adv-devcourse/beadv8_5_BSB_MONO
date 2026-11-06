import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { setAccessToken } from '@/shared/api'
import { server } from '@/shared/testing'

import { refundPlanSchema } from '../api/refund-contract'
import { maskAccount } from '../lib/refund'
import { DepositRefundDonePage, DepositRefundPage } from './DepositRefundPages'

const push = vi.fn()
const replace = vi.fn()
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push, back: vi.fn(), replace }),
  usePathname: () => '/me/deposit/refund',
}))

function renderWithClient(ui: React.ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return { client, ...render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>) }
}

type Plan = Record<string, unknown>

const planHandler = (data: Plan) =>
  http.get('*/api/v1/cash/wallets/refundable-charges', () =>
    HttpResponse.json({ status: 200, message: '아무 문구', data }),
  )

// 응답 모양은 백엔드 BUK-21 환급 화면용 충전 내역 조회(Swagger 예시)와 같다. 충전 건은 최근 충전부터 온다
const OLD_CHARGE = {
  chargeId: 101,
  chargedAt: '2025-09-01T18:40:00',
  amount: 85000,
  lastCancelableDate: '2026-08-31',
  expired: true,
}
const NEW_CHARGE = {
  chargeId: 214,
  chargedAt: '2026-10-13T14:21:00',
  amount: 60000,
  remainingAmount: 60000,
  lastCancelableDate: '2027-10-12',
  expired: false,
}

const MIXED: Plan = {
  balance: 65000,
  earliestLastCancelableDate: '2027-10-12',
  charges: [NEW_CHARGE, { ...OLD_CHARGE, remainingAmount: 5000 }],
}

const CARD_ONLY: Plan = {
  balance: 65000,
  earliestLastCancelableDate: '2027-10-09',
  charges: [
    NEW_CHARGE,
    {
      chargeId: 210,
      chargedAt: '2026-10-10T09:00:00',
      amount: 10000,
      remainingAmount: 5000,
      lastCancelableDate: '2027-10-09',
      expired: false,
    },
  ],
}

const ACCOUNT_ONLY: Plan = {
  balance: 65000,
  earliestLastCancelableDate: null,
  charges: [{ ...OLD_CHARGE, remainingAmount: 65000 }],
}

const EMPTY: Plan = { balance: 0, earliestLastCancelableDate: null, charges: [] }

type WithdrawalBody = {
  bankCode?: string
  accountNumber?: string
  accountHolder?: string
}

type WithdrawalResult = {
  withdrawnAmount: number
  balance: number
  cardAmount: number
  accountAmount: number
  processingAmount: number
  revertedAmount: number
  accountRequired: boolean
}

// 응답 모양은 백엔드 POST /withdrawals Swagger 예시와 같다. withdrawnAmount는 네 금액의 합이다
const withdrawalResult = (data: Partial<WithdrawalResult> = {}): WithdrawalResult => ({
  withdrawnAmount: 65000,
  balance: 0,
  cardAmount: 60000,
  accountAmount: 5000,
  processingAmount: 0,
  revertedAmount: 0,
  accountRequired: false,
  ...data,
})

const okWithdrawal = (data?: Partial<WithdrawalResult>) =>
  HttpResponse.json({ status: 200, message: '아무 문구', data: withdrawalResult(data) })

async function fillAccount(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('계좌번호'), '123456-01-234567')
  await user.type(screen.getByLabelText('예금주'), '김러너')
}

const submit = (amount = '65,000원') =>
  screen.findByRole('button', { name: `${amount} 돌려받기 신청` })

describe('예치금 돌려받기 (M22, 환급 변경안 11/5)', () => {
  beforeEach(() => {
    push.mockClear()
    replace.mockClear()
    sessionStorage.clear()
    setAccessToken('access-token')
    server.use(planHandler(MIXED))
  })

  it('카드 + 계좌: 금액 · 기한 문장을 서버 값 그대로 보여 주고, 계좌와 함께 신청한다', async () => {
    const bodies: WithdrawalBody[] = []
    const keys: (string | null)[] = []
    server.use(
      http.post('*/api/v1/cash/withdrawals', async ({ request }) => {
        keys.push(request.headers.get('Idempotency-Key'))
        bodies.push((await request.json()) as WithdrawalBody)
        return okWithdrawal()
      }),
    )
    const user = userEvent.setup()
    renderWithClient(<DepositRefundPage />)

    expect(await screen.findByText('65,000원')).toBeInTheDocument()
    expect(
      screen.getByText('2027-10-12까지 신청하면 60,000원은 카드로, 5,000원은 계좌로 돌아가요.'),
    ).toBeInTheDocument()
    const button = await submit()
    expect(button).toBeDisabled()
    await fillAccount(user)
    await user.click(button)

    await vi.waitFor(() => expect(push).toHaveBeenCalledWith('/me/deposit/refund/done'))
    expect(bodies).toHaveLength(1)
    expect(bodies[0]).toEqual({
      bankCode: '004',
      accountNumber: '12345601234567',
      accountHolder: '김러너',
    })
    expect(keys[0]).toMatch(/^[0-9a-f-]{36}$/)
  })

  it('카드만: 계좌 입력 없이 바로 신청할 수 있고, 요청에 계좌 정보를 넣지 않는다', async () => {
    const bodies: WithdrawalBody[] = []
    server.use(
      planHandler(CARD_ONLY),
      http.post('*/api/v1/cash/withdrawals', async ({ request }) => {
        bodies.push((await request.json()) as WithdrawalBody)
        return okWithdrawal({ cardAmount: 65000, accountAmount: 0 })
      }),
    )
    const user = userEvent.setup()
    renderWithClient(<DepositRefundPage />)

    expect(
      await screen.findByText('2027-10-09까지 신청하면 65,000원 전부 결제한 카드로 돌아가요.'),
    ).toBeInTheDocument()
    expect(screen.queryByLabelText('계좌번호')).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: '환급 계좌' })).not.toBeInTheDocument()
    const button = await submit()
    expect(button).toBeEnabled()
    await user.click(button)

    await vi.waitFor(() => expect(push).toHaveBeenCalledWith('/me/deposit/refund/done'))
    expect(bodies[0]).not.toHaveProperty('bankCode')
    expect(bodies[0]).not.toHaveProperty('accountNumber')
    expect(bodies[0]).not.toHaveProperty('accountHolder')
    const saved = JSON.parse(sessionStorage.getItem('crewrun:last-withdrawal') ?? '{}') as object
    expect(saved).not.toHaveProperty('maskedAccount')
    expect(saved).toMatchObject({ cardAmount: 65000, accountAmount: 0 })
  })

  it('계좌만: 임시 문장을 보여 주고 계좌를 받는다', async () => {
    server.use(planHandler(ACCOUNT_ONLY))
    renderWithClient(<DepositRefundPage />)

    expect(await screen.findByText('65,000원 전부 계좌로 돌아가요.')).toBeInTheDocument()
    expect(screen.getByLabelText('계좌번호')).toBeInTheDocument()
    expect(await submit()).toBeDisabled()
  })

  it('충전별 내역 상세보기는 접혀 있다가 누르면 충전 건마다 돌려받는 곳 · 기한 · 남은 금액을 보여 준다', async () => {
    const user = userEvent.setup()
    renderWithClient(<DepositRefundPage />)

    const toggle = await screen.findByRole('button', { name: '충전별 내역 상세보기' })
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByText('2025-09-01 충전 · 85,000원')).not.toBeInTheDocument()

    await user.click(toggle)

    expect(toggle).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByText('2025-09-01 충전 · 85,000원')).toBeInTheDocument()
    expect(screen.getByText('계좌로 · 카드 취소 기한(2026-08-31)이 지났어요')).toBeInTheDocument()
    expect(screen.getByText('5,000원')).toBeInTheDocument()
    expect(screen.getByText('2026-10-13 충전 · 60,000원')).toBeInTheDocument()
    expect(screen.getByText('결제한 카드로 · 카드 취소 2027-10-12까지')).toBeInTheDocument()
    expect(screen.getByText('60,000원')).toBeInTheDocument()

    await user.click(toggle)
    expect(screen.queryByText('2025-09-01 충전 · 85,000원')).not.toBeInTheDocument()
  })

  it('카드 몫이 있는데 기한이 비어 오면 계약과 다른 응답으로 보고 신청을 막는다', async () => {
    server.use(planHandler({ ...CARD_ONLY, earliestLastCancelableDate: null }))
    renderWithClient(<DepositRefundPage />)

    expect(await screen.findByText('돌려받을 금액을 불러오지 못했어요.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /돌려받기 신청/ })).toBeDisabled()
  })

  it('응답 모양이 계약과 다르면 안내를 보여 주고 신청을 막는다', async () => {
    server.use(planHandler({ ...MIXED, charges: [{ chargeId: 1, refundTo: 'CASH' }] }))
    renderWithClient(<DepositRefundPage />)

    expect(await screen.findByText('돌려받을 금액을 불러오지 못했어요.')).toBeInTheDocument()
    expect(screen.queryByText('65,000원')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /돌려받기 신청/ })).toBeDisabled()
  })

  it('카드 몫이 잔액보다 크게 오면 계약과 다른 응답으로 보고 신청을 막는다', async () => {
    server.use(planHandler({ ...MIXED, balance: 50000 }))
    renderWithClient(<DepositRefundPage />)

    expect(await screen.findByText('돌려받을 금액을 불러오지 못했어요.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /돌려받기 신청/ })).toBeDisabled()
  })

  it('충전 건에 없는 잔액(판매자 정산 대금)도 계좌 몫에 넣는다: 계좌 몫 = 잔액 − 기한 안 충전의 남은 금액', async () => {
    // 기한 지난 충전 5,000원 + 충전 건이 없는 정산 대금 20,000원이 계좌로 간다
    server.use(planHandler({ ...MIXED, balance: 85000 }))
    renderWithClient(<DepositRefundPage />)

    expect(
      await screen.findByText(
        '2027-10-12까지 신청하면 60,000원은 카드로, 25,000원은 계좌로 돌아가요.',
      ),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('계좌번호')).toBeInTheDocument()
  })

  it('충전 건이 하나도 없는 판매자 회원은 잔액 전부를 계좌로 받는다', async () => {
    server.use(planHandler({ balance: 30000, earliestLastCancelableDate: null, charges: [] }))
    renderWithClient(<DepositRefundPage />)

    expect(await screen.findByText('30,000원 전부 계좌로 돌아가요.')).toBeInTheDocument()
    expect(screen.getByLabelText('계좌번호')).toBeInTheDocument()
  })

  it('남은 금액이 0원인 충전 건은 상세 내역에 보여 주지 않는다', async () => {
    server.use(
      planHandler({
        ...MIXED,
        charges: [
          ...(MIXED.charges as object[]),
          { ...OLD_CHARGE, chargeId: 1, remainingAmount: 0, chargedAt: '2025-01-13T09:00:00' },
        ],
      }),
    )
    const user = userEvent.setup()
    renderWithClient(<DepositRefundPage />)

    await user.click(await screen.findByRole('button', { name: '충전별 내역 상세보기' }))
    expect(screen.queryByText(/^2025-01-13 충전/)).not.toBeInTheDocument()
    expect(screen.getAllByRole('listitem')).toHaveLength(2)
  })

  it('응답 모양 검사 실패는 다시 받아도 같으니 다시 요청하지 않는다', async () => {
    let calls = 0
    server.use(
      http.get('*/api/v1/cash/wallets/refundable-charges', () => {
        calls += 1
        return HttpResponse.json({ status: 200, message: '아무 문구', data: { balance: 'x' } })
      }),
    )
    const client = new QueryClient()
    render(
      <QueryClientProvider client={client}>
        <DepositRefundPage />
      </QueryClientProvider>,
    )

    expect(await screen.findByText('돌려받을 금액을 불러오지 못했어요.')).toBeInTheDocument()
    expect(calls).toBe(1)
  })

  it('펼치기 버튼이 가리키는 목록은 접혀 있어도 화면에 있다(숨김)', async () => {
    renderWithClient(<DepositRefundPage />)

    const toggle = await screen.findByRole('button', { name: '충전별 내역 상세보기' })
    const id = toggle.getAttribute('aria-controls') ?? ''
    expect(document.getElementById(id)).toHaveAttribute('hidden')
  })

  it('돌려받을 금액이 0원이면 신청 버튼을 막는다', async () => {
    server.use(planHandler(EMPTY))
    renderWithClient(<DepositRefundPage />)

    expect(await submit('0원')).toBeDisabled()
  })

  it('두 번 눌러도 요청은 한 번만 간다', async () => {
    const keys: string[] = []
    let release: () => void = () => {}
    server.use(
      http.post('*/api/v1/cash/withdrawals', async ({ request }) => {
        keys.push(request.headers.get('Idempotency-Key') ?? '')
        await new Promise<void>((resolve) => (release = resolve))
        return okWithdrawal()
      }),
    )
    const user = userEvent.setup()
    renderWithClient(<DepositRefundPage />)

    const button = await submit()
    await fillAccount(user)
    await user.dblClick(button)
    await vi.waitFor(() => expect(keys).toHaveLength(1))
    expect(button).toBeDisabled()
    release()

    await vi.waitFor(() => expect(push).toHaveBeenCalledWith('/me/deposit/refund/done'))
    expect(keys).toHaveLength(1)
  })

  it('네트워크 오류 뒤 다시 누르면 처음과 같은 키로 보낸다', async () => {
    const keys: string[] = []
    server.use(
      http.post('*/api/v1/cash/withdrawals', async ({ request }) => {
        keys.push(request.headers.get('Idempotency-Key') ?? '')
        if (keys.length === 1) return HttpResponse.error()
        return okWithdrawal()
      }),
    )
    const user = userEvent.setup()
    renderWithClient(<DepositRefundPage />)

    const button = await submit()
    await fillAccount(user)
    await user.click(button)
    expect(await screen.findByText('잠시 뒤 다시 시도해 주세요.')).toBeInTheDocument()
    await user.click(button)

    await vi.waitFor(() => expect(push).toHaveBeenCalledWith('/me/deposit/refund/done'))
    expect(keys).toHaveLength(2)
    expect(keys[1]).toBe(keys[0])
  })

  it('결과를 모르는 실패 뒤에는 입력을 잠그고, 다시 보낼 때 처음 값을 보내 완료 화면도 그 계좌로 보인다', async () => {
    const bodies: WithdrawalBody[] = []
    server.use(
      http.post('*/api/v1/cash/withdrawals', async ({ request }) => {
        bodies.push((await request.json()) as WithdrawalBody)
        if (bodies.length === 1) return HttpResponse.error()
        return okWithdrawal()
      }),
    )
    const user = userEvent.setup()
    renderWithClient(<DepositRefundPage />)

    const button = await submit()
    await fillAccount(user)
    await user.click(button)
    await screen.findByText('잠시 뒤 다시 시도해 주세요.')

    // 서버가 처음 계좌로 처리했을 수 있어 고치지 못하게 잠근다
    const account = screen.getByLabelText('계좌번호')
    expect(account).toHaveAttribute('readonly')
    await user.type(account, '999')
    expect(account).toHaveValue('123456-01-234567')

    await user.click(button)
    await vi.waitFor(() => expect(push).toHaveBeenCalledWith('/me/deposit/refund/done'))
    expect(bodies[1]).toEqual(bodies[0])
    expect(JSON.parse(sessionStorage.getItem('crewrun:last-withdrawal') ?? '{}')).toMatchObject({
      maskedAccount: '123456-**-***567',
      accountHolder: '김러너',
    })
  })

  it('결과를 모르는 실패 뒤 다시 불러온 금액이 0원이어도 같은 키로 다시 보낼 수 있다', async () => {
    const keys: string[] = []
    server.use(
      http.post('*/api/v1/cash/withdrawals', ({ request }) => {
        keys.push(request.headers.get('Idempotency-Key') ?? '')
        if (keys.length === 1) return HttpResponse.error()
        return okWithdrawal()
      }),
    )
    const user = userEvent.setup()
    const { client } = renderWithClient(<DepositRefundPage />)

    const button = await submit()
    await fillAccount(user)
    await user.click(button)
    await screen.findByText('잠시 뒤 다시 시도해 주세요.')

    // 서버는 처음 요청을 처리해 잔액이 0원이 됐다
    server.use(planHandler(EMPTY))
    await client.invalidateQueries()
    const again = await submit('0원')
    expect(again).toBeEnabled()
    await user.click(again)

    await vi.waitFor(() => expect(push).toHaveBeenCalledWith('/me/deposit/refund/done'))
    expect(keys[1]).toBe(keys[0])
  })

  it('서버가 거절(4xx)하면 입력 잠금을 풀어 고칠 수 있다', async () => {
    server.use(
      http.post('*/api/v1/cash/withdrawals', () =>
        HttpResponse.json({ status: 400, message: '아무 문구', data: null }, { status: 400 }),
      ),
    )
    const user = userEvent.setup()
    renderWithClient(<DepositRefundPage />)

    const button = await submit()
    await fillAccount(user)
    await user.click(button)
    await screen.findByText('잠시 뒤 다시 시도해 주세요.')

    expect(screen.getByLabelText('계좌번호')).not.toHaveAttribute('readonly')
  })

  it('서버가 5xx로 답해도 다음 시도에 같은 키를 쓰고, 4xx로 거절하면 새 키를 쓴다', async () => {
    const keys: string[] = []
    const answers = [500, 400, 200]
    server.use(
      http.post('*/api/v1/cash/withdrawals', async ({ request }) => {
        keys.push(request.headers.get('Idempotency-Key') ?? '')
        const status = answers[keys.length - 1]
        if (status !== 200) {
          return HttpResponse.json({ status, message: '아무 문구', data: null }, { status })
        }
        return okWithdrawal()
      }),
    )
    const user = userEvent.setup()
    renderWithClient(<DepositRefundPage />)

    const button = await submit()
    await fillAccount(user)
    await user.click(button)
    await screen.findByText('잠시 뒤 다시 시도해 주세요.')
    await user.click(button)
    await vi.waitFor(() => expect(keys).toHaveLength(2))
    await vi.waitFor(() => expect(button).toBeEnabled())
    await user.click(button)

    await vi.waitFor(() => expect(push).toHaveBeenCalledWith('/me/deposit/refund/done'))
    expect(keys[1]).toBe(keys[0])
    expect(keys[2]).not.toBe(keys[1])
  })

  it('카드로 돌려줄 수 없는 몫이 계좌 정보가 없어 되돌아오면(accountRequired) 계좌를 받고 다시 신청하게 한다', async () => {
    let planCalls = 0
    const bodies: WithdrawalBody[] = []
    const keys: (string | null)[] = []
    server.use(
      // 카드사가 부분 취소를 거절한 충전 건은 기한 안이라 다시 불러와도 카드로 나올 수 있다
      http.get('*/api/v1/cash/wallets/refundable-charges', () => {
        planCalls += 1
        return HttpResponse.json({ status: 200, message: '아무 문구', data: CARD_ONLY })
      }),
      http.post('*/api/v1/cash/withdrawals', async ({ request }) => {
        keys.push(request.headers.get('Idempotency-Key'))
        bodies.push((await request.json()) as WithdrawalBody)
        return bodies.length === 1
          ? okWithdrawal({
              withdrawnAmount: 65000,
              balance: 5000,
              cardAmount: 60000,
              accountAmount: 0,
              revertedAmount: 5000,
              accountRequired: true,
            })
          : okWithdrawal({ withdrawnAmount: 5000, cardAmount: 0, accountAmount: 5000 })
      }),
    )
    const user = userEvent.setup()
    renderWithClient(<DepositRefundPage />)

    await user.click(await submit())

    expect(
      await screen.findByText('카드 결제취소 60,000원은 요청했어요.', { exact: false }),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('계좌번호')).toBeInTheDocument()
    expect(push).not.toHaveBeenCalled()
    await vi.waitFor(() => expect(planCalls).toBe(2))
    // 알림과 어긋나게 「전부 결제한 카드로 돌아가요」를 보여 주지 않는다
    expect(screen.queryByText('결제한 카드로 돌아가요', { exact: false })).not.toBeInTheDocument()
    await fillAccount(user)
    await user.click(await submit())

    await vi.waitFor(() => expect(push).toHaveBeenCalledWith('/me/deposit/refund/done'))
    expect(bodies[1]).toEqual({
      bankCode: '004',
      accountNumber: '12345601234567',
      accountHolder: '김러너',
    })
    // 첫 신청은 끝난 요청이라 다시 신청은 새 키로 보낸다
    expect(keys[1]).not.toBe(keys[0])
  })

  it('accountRequired와 함께 확인 중인 금액이 있으면 그 금액이 정해진 뒤 다시 신청하라고 알린다', async () => {
    server.use(
      planHandler(CARD_ONLY),
      http.post('*/api/v1/cash/withdrawals', () =>
        okWithdrawal({
          balance: 5000,
          cardAmount: 40000,
          accountAmount: 0,
          processingAmount: 20000,
          revertedAmount: 5000,
          accountRequired: true,
        }),
      ),
    )
    const user = userEvent.setup()
    renderWithClient(<DepositRefundPage />)

    await user.click(await submit())

    expect(
      await screen.findByText('확인 중인 20,000원이 정해진 뒤 다시 신청할 수 있어요.', {
        exact: false,
      }),
    ).toBeInTheDocument()
  })

  it('400 WITHDRAWAL_ACCOUNT_REQUIRED면 충전 내역을 다시 불러오고 계좌 입력을 보여 준다', async () => {
    let planCalls = 0
    server.use(
      http.get('*/api/v1/cash/wallets/refundable-charges', () => {
        planCalls += 1
        // 화면을 연 뒤 기한이 지나 서버는 계좌 몫이 있다고 본다. 다시 불러와도 카드만으로 오는 경우까지 계좌를 받는다
        return HttpResponse.json({ status: 200, message: '아무 문구', data: CARD_ONLY })
      }),
      http.post('*/api/v1/cash/withdrawals', () =>
        HttpResponse.json(
          {
            status: 400,
            code: 'WITHDRAWAL_ACCOUNT_REQUIRED',
            message: '계좌로 돌려드릴 금액이 있어 계좌 정보가 필요합니다.',
            data: null,
          },
          { status: 400 },
        ),
      ),
    )
    const user = userEvent.setup()
    renderWithClient(<DepositRefundPage />)

    await user.click(await submit())

    expect(
      await screen.findByText('계좌로 돌려드릴 금액이 있어요. 계좌를 입력하고 다시 신청해 주세요.'),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('계좌번호')).toBeInTheDocument()
    await vi.waitFor(() => expect(planCalls).toBe(2))
    expect(await submit()).toBeDisabled()
    expect(push).not.toHaveBeenCalled()
  })

  it('409 WITHDRAWAL_IN_PROGRESS면 처리 중인 환급이 끝난 뒤 다시 신청하라고 안내한다', async () => {
    const keys: (string | null)[] = []
    server.use(
      planHandler(CARD_ONLY),
      http.post('*/api/v1/cash/withdrawals', ({ request }) => {
        keys.push(request.headers.get('Idempotency-Key'))
        return HttpResponse.json(
          { status: 409, code: 'WITHDRAWAL_IN_PROGRESS', message: '아무 문구', data: null },
          { status: 409 },
        )
      }),
    )
    const user = userEvent.setup()
    renderWithClient(<DepositRefundPage />)

    await user.click(await submit())

    expect(
      await screen.findByText('처리 중인 환급이 끝난 뒤 다시 신청할 수 있어요.'),
    ).toBeInTheDocument()
    expect(screen.queryByText('잠시 뒤 다시 시도해 주세요.')).not.toBeInTheDocument()
    expect(push).not.toHaveBeenCalled()
    // 거절된 요청이라 나중에 다시 신청하면 새 키로 보낸다
    await user.click(await submit())
    await vi.waitFor(() => expect(keys).toHaveLength(2))
    expect(keys[1]).not.toBe(keys[0])
  })

  it('가짜 응답 모드에서 계좌 없이 신청하면 카드 결제취소로 답한다', async () => {
    const res = await fetch('http://localhost/api/v1/cash/withdrawals', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Idempotency-Key': 'card-key' },
      body: JSON.stringify({}),
    }).then((r) => r.json() as Promise<{ data: WithdrawalResult }>)

    expect(res.data).toEqual(
      withdrawalResult({ withdrawnAmount: 5000, cardAmount: 5000, accountAmount: 0 }),
    )
  })

  it('가짜 응답 모드에서는 같은 키로 두 번 보내도 한 번만 빠지고 같은 결과를 받는다', async () => {
    const send = (idempotencyKey: string) =>
      fetch('http://localhost/api/v1/cash/withdrawals', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Idempotency-Key': idempotencyKey },
        body: JSON.stringify({ bankCode: '004', accountNumber: '1', accountHolder: '김러너' }),
      }).then((r) => r.json() as Promise<{ data: WithdrawalResult }>)

    const first = await send('same-key')
    const again = await send('same-key')

    const result = withdrawalResult({ withdrawnAmount: 5000, cardAmount: 0, accountAmount: 5000 })
    expect(first.data).toEqual(result)
    expect(again.data).toEqual(result)
    // 다른 키로 보내면 잔액이 없어 거절된다
    const other = await fetch('http://localhost/api/v1/cash/withdrawals', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Idempotency-Key': 'other-key' },
      body: JSON.stringify({}),
    })
    expect(other.status).toBe(400)
    // 키가 없으면 진짜 서버처럼 400으로 거절한다
    const missing = await fetch('http://localhost/api/v1/cash/withdrawals', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({}),
    })
    expect(missing.status).toBe(400)
  })
})

describe('가짜 응답 모드의 돌려받기 조회', () => {
  const preview = (referer?: string) =>
    fetch('http://localhost/api/v1/cash/wallets/refundable-charges', {
      headers: referer ? { referer } : {},
    })
      .then((r) => r.json() as Promise<{ data: unknown }>)
      .then((body) => refundPlanSchema.parse(body.data))

  it('화면 주소의 case로 카드만 · 카드 + 계좌 · 계좌만 · 판매자를 고를 수 있고, 모두 계약 모양이다', async () => {
    const mixed = await preview()
    const card = await preview('http://localhost/me/deposit/refund?case=card')
    const account = await preview('http://localhost/me/deposit/refund?case=account')
    const seller = await preview('http://localhost/me/deposit/refund?case=seller')

    expect(mixed).toMatchObject({ accountRequired: true, cardRefundAmount: 60000 })
    expect(card).toMatchObject({ accountRequired: false, accountRefundAmount: 0 })
    expect(account).toMatchObject({ accountRequired: true, earliestCardCancelDate: null })
    expect(seller).toMatchObject({ cardRefundAmount: 0, accountRefundAmount: 30000 })
  })
})

describe('예치금 돌려받기 · 환급 완료 (M22-1)', () => {
  beforeEach(() => {
    replace.mockClear()
    sessionStorage.clear()
  })

  const save = (data: Record<string, unknown>) =>
    sessionStorage.setItem(
      'crewrun:last-withdrawal',
      JSON.stringify({
        amount: 5000,
        balance: 0,
        cardAmount: 0,
        accountAmount: 5000,
        processingAmount: 0,
        revertedAmount: 0,
        bankName: '국민은행',
        maskedAccount: '123456-**-***567',
        accountHolder: '김러너',
        at: new Date('2026-10-06T14:20:00+09:00').getTime(),
        ...data,
      }),
    )

  it('전부 계좌로 보냈으면 돌려받았다고 쓰고 가린 계좌 · 지금 예치금 · 일시를 보여 준다', () => {
    save({})
    renderWithClient(<DepositRefundDonePage />)

    expect(screen.getByRole('heading', { name: '예치금을 돌려받았어요' })).toBeInTheDocument()
    expect(screen.getByText('남은 예치금 전부를 아래 계좌로 보냈어요.')).toBeInTheDocument()
    expect(screen.getByText('계좌로 보낸 금액')).toBeInTheDocument()
    expect(screen.getByText('₩5,000')).toBeInTheDocument()
    expect(screen.getByText('국민은행 123456-**-***567')).toBeInTheDocument()
    expect(screen.getByText('₩0')).toBeInTheDocument()
    expect(screen.getByText('10/6 (화) 14:20')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '예치금 보기' })).toHaveAttribute('href', '/me/deposit')
  })

  it('네 금액 중 0원이 아닌 것만 보여 주고, 카드 몫은 요청했다고 · 며칠 걸릴 수 있다고 안내한다', () => {
    save({
      amount: 130000,
      balance: 10000,
      cardAmount: 30000,
      accountAmount: 70000,
      processingAmount: 20000,
      revertedAmount: 10000,
    })
    renderWithClient(<DepositRefundDonePage />)

    expect(screen.getByRole('heading', { name: '환급을 신청했어요' })).toBeInTheDocument()
    expect(screen.getByText('카드 결제취소 요청')).toBeInTheDocument()
    expect(screen.getByText('₩30,000')).toBeInTheDocument()
    expect(
      screen.getByText('결제한 카드로 결제취소를 요청했어요. 카드사에 따라 며칠 걸릴 수 있어요.'),
    ).toBeInTheDocument()
    expect(screen.getByText('₩70,000')).toBeInTheDocument()
    expect(screen.getByText('확인 중인 금액')).toBeInTheDocument()
    expect(screen.getByText('₩20,000')).toBeInTheDocument()
    expect(screen.getByText('예치금으로 되돌린 금액')).toBeInTheDocument()
    expect(screen.getAllByText('₩10,000')).toHaveLength(2)
    expect(screen.queryByText('남은 예치금 전부를 아래 계좌로 보냈어요.')).not.toBeInTheDocument()
  })

  it('카드로만 돌려받아 계좌가 없으면 0원인 금액 · 계좌 · 예금주 줄을 보여 주지 않는다', () => {
    save({
      amount: 65000,
      cardAmount: 65000,
      accountAmount: 0,
      bankName: undefined,
      maskedAccount: undefined,
      accountHolder: undefined,
    })
    renderWithClient(<DepositRefundDonePage />)

    expect(screen.getByRole('heading', { name: '환급을 신청했어요' })).toBeInTheDocument()
    expect(screen.getByText('카드 결제취소 요청')).toBeInTheDocument()
    expect(screen.queryByText('계좌로 보낸 금액')).not.toBeInTheDocument()
    expect(screen.queryByText('확인 중인 금액')).not.toBeInTheDocument()
    expect(screen.queryByText('예치금으로 되돌린 금액')).not.toBeInTheDocument()
    expect(screen.queryByText('받을 계좌')).not.toBeInTheDocument()
    expect(screen.queryByText('예금주')).not.toBeInTheDocument()
  })

  it('카드 결제취소가 전부 거절돼 예치금으로 되돌아왔으면 돌려받았다고 쓰지 않는다', () => {
    save({
      amount: 65000,
      balance: 65000,
      accountAmount: 0,
      revertedAmount: 65000,
      bankName: undefined,
      maskedAccount: undefined,
      accountHolder: undefined,
    })
    renderWithClient(<DepositRefundDonePage />)

    expect(screen.getByRole('heading', { name: '예치금으로 되돌아왔어요' })).toBeInTheDocument()
    expect(screen.queryByText('진행 단계')).not.toBeInTheDocument()
    expect(
      screen.getByText('카드 결제취소가 거절돼 예치금으로 되돌렸어요. 다시 신청할 수 있어요.'),
    ).toBeInTheDocument()
  })

  it('일부라도 예치금으로 되돌아왔으면 환급 완료로 보이지 않는다', () => {
    save({ amount: 65000, balance: 5000, accountAmount: 60000, revertedAmount: 5000 })
    renderWithClient(<DepositRefundDonePage />)

    expect(screen.getByRole('heading', { name: '환급을 신청했어요' })).toBeInTheDocument()
  })

  it('넘겨받은 값이 없으면(주소로 바로 들어옴) 예치금 화면으로 보낸다', async () => {
    renderWithClient(<DepositRefundDonePage />)

    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/me/deposit'))
  })
})

describe('maskAccount', () => {
  it('앞 6자리와 끝 3자리만 남기고 하이픈은 그대로 둔다', () => {
    expect(maskAccount('123456-01-234567')).toBe('123456-**-***567')
    expect(maskAccount('110123456789')).toBe('110123***789')
  })
})
