import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { delay, http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { requestCharge } from '@/features/deposit-charge'
import { ApiError, orderApi } from '@/shared/api'
import { cashZod } from '@/shared/api-zod'
import { server } from '@/shared/testing'

import { DepositChargeReturnPage } from './DepositChargeReturnPage'

const replace = vi.fn()
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), replace }),
  usePathname: () => '/me/deposit/charge/return',
}))

const PENDING_KEY = 'crewrun:pending-charge'
const ORDER_ID = 'charge-11111111-2222-3333-4444-555555555555'

function savePending(pending: {
  orderId: string
  amount: number
  returnOrderId?: number
  uncertainAt?: number
}) {
  sessionStorage.setItem(PENDING_KEY, JSON.stringify(pending))
}

function renderPage(props: Parameters<typeof DepositChargeReturnPage>[0]) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <DepositChargeReturnPage {...props} />
    </QueryClientProvider>,
  )
}

/** 충전 확정 요청 본문을 모으고 outcome으로 답한다 */
function confirmHandler(
  outcome: 'COMPLETED' | 'FAILED' | 'UNCERTAIN',
  bodies: unknown[] = [],
  message = '아무 문구',
) {
  return http.post('*/api/v1/cash/charges/confirm', async ({ request }) => {
    bodies.push(await request.json())
    // 확인 중일 때 balance는 이 충전이 들어가기 전 잔액이다
    return HttpResponse.json({ status: 200, message, data: { outcome, balance: 50_000 } })
  })
}

/** 충전 확정이 HTTP 오류로 끝난다 */
function confirmErrorHandler(status: number, message: string) {
  return http.post('*/api/v1/cash/charges/confirm', () =>
    HttpResponse.json({ status, message, data: null }, { status }),
  )
}

const UNCERTAIN_TITLE = '충전 결과를 확인하고 있어요'

const success = (patch: Partial<Parameters<typeof DepositChargeReturnPage>[0]> = {}) => ({
  result: 'success',
  paymentKey: 'pk_test',
  orderId: ORDER_ID,
  amount: 12_000,
  ...patch,
})

describe('충전 결과 화면 (토스 결제창에서 돌아옴)', () => {
  beforeEach(() => {
    replace.mockClear()
    sessionStorage.clear()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('충전 성공: 돌아온 값으로 충전을 확정하고 같은 주문의 결제 화면으로 돌아가 다시 결제하게 한다', async () => {
    savePending({ orderId: ORDER_ID, amount: 12_000, returnOrderId: 1 })
    const bodies: unknown[] = []
    server.use(confirmHandler('COMPLETED', bodies))

    renderPage(success())

    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/checkout?orderId=1&charged=1'))
    expect(bodies).toEqual([{ paymentKey: 'pk_test', orderId: ORDER_ID, amount: 12_000 }])
    // 다 쓴 충전 기록은 지운다
    expect(sessionStorage.getItem(PENDING_KEY)).toBeNull()
  })

  it('충전 실패(200 · FAILED): 서버가 알려 준 거절 사유를 보여 주고, 결제 화면으로 돌아가는 길을 준다', async () => {
    savePending({ orderId: ORDER_ID, amount: 12_000, returnOrderId: 1 })
    server.use(confirmHandler('FAILED', [], '카드 한도가 초과되었습니다.'))

    renderPage(success())

    expect(await screen.findByText('충전하지 못했어요')).toBeInTheDocument()
    expect(screen.getByText(/카드 한도가 초과되었습니다\./)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '결제 화면으로 돌아가기' })).toHaveAttribute(
      'href',
      '/checkout?orderId=1',
    )
    expect(replace).not.toHaveBeenCalled()
    // 돈이 빠지지 않았으니 다 쓴 충전 기록은 지운다
    expect(sessionStorage.getItem(PENDING_KEY)).toBeNull()
  })

  it('예치금 화면에서 한 충전이 FAILED면 거절 사유와 다시 충전하기를 보여 준다', async () => {
    savePending({ orderId: ORDER_ID, amount: 12_000 })
    server.use(confirmHandler('FAILED', [], '카드 한도가 초과되었습니다.'))

    renderPage(success())

    expect(await screen.findByText(/카드 한도가 초과되었습니다\./)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '다시 충전하기' })).toHaveAttribute(
      'href',
      '/me/deposit/charge',
    )
  })

  it('확인 중(200 · UNCERTAIN): 기다리라고 안내하고, 다시 결제하게 하거나 충전 전 잔액을 보여 주지 않는다', async () => {
    savePending({ orderId: ORDER_ID, amount: 12_000 })
    const bodies: unknown[] = []
    server.use(
      confirmHandler(
        'UNCERTAIN',
        bodies,
        '충전 결과를 확인하지 못했습니다. 잠시 후 다시 확인해주세요.',
      ),
    )

    renderPage(success())

    expect(await screen.findByText(UNCERTAIN_TITLE)).toBeInTheDocument()
    // 결제하지 말라고 하면 결제 중 충전한 사용자가 결제를 미루다 결제 기한을 넘길 수 있다
    expect(screen.getByText(/카드 결제가 됐다면/)).toBeInTheDocument()
    expect(screen.getByText(/다시 충전하지 말고/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '예치금 보기' })).toHaveAttribute('href', '/me/deposit')
    expect(screen.queryByRole('link', { name: /충전|결제/ })).toBeNull()
    expect(screen.queryByText(/50,000/)).toBeNull()
    expect(replace).not.toHaveBeenCalled()
    expect(bodies).toHaveLength(1)
  })

  it('주문 결제 중 충전이 확인 중이면 결제 화면으로 돌아가도 저절로 다시 결제하지 않는다', async () => {
    savePending({ orderId: ORDER_ID, amount: 12_000, returnOrderId: 1 })
    server.use(confirmHandler('UNCERTAIN'))

    renderPage(success())

    expect(await screen.findByText(UNCERTAIN_TITLE)).toBeInTheDocument()
    // charged=1을 붙이지 않아야 결제 화면이 같은 주문을 바로 다시 결제하지 않는다
    expect(screen.getByRole('link', { name: '결제 화면으로 돌아가기' })).toHaveAttribute(
      'href',
      '/checkout?orderId=1',
    )
    expect(replace).not.toHaveBeenCalled()
  })

  it('먼저 보낸 확정이 아직 처리 중(409)이면 확인 중으로 안내한다', async () => {
    savePending({ orderId: ORDER_ID, amount: 12_000 })
    server.use(
      confirmErrorHandler(409, '이미 처리 중인 충전 요청입니다. 잠시 후 다시 확인해 주세요.'),
    )

    renderPage(success())

    expect(await screen.findByText(UNCERTAIN_TITLE)).toBeInTheDocument()
  })

  it('서버 오류(5xx)면 끝까지 처리했는지 몰라 확인 중으로 안내한다', async () => {
    savePending({ orderId: ORDER_ID, amount: 12_000, returnOrderId: 1 })
    server.use(confirmErrorHandler(502, 'Bad Gateway'))

    renderPage(success())

    expect(await screen.findByText(UNCERTAIN_TITLE)).toBeInTheDocument()
    expect(replace).not.toHaveBeenCalled()
  })

  it('네트워크 오류로 응답을 못 받으면 실패가 아니라 확인 중으로 안내한다', async () => {
    savePending({ orderId: ORDER_ID, amount: 12_000, returnOrderId: 1 })
    server.use(http.post('*/api/v1/cash/charges/confirm', () => HttpResponse.error()))

    renderPage(success())

    expect(await screen.findByText(UNCERTAIN_TITLE)).toBeInTheDocument()
    expect(replace).not.toHaveBeenCalled()
  })

  it('응답이 30초 넘게 오지 않으면 기다리지 않고 확인 중으로 안내한다', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    savePending({ orderId: ORDER_ID, amount: 12_000 })
    server.use(
      http.post('*/api/v1/cash/charges/confirm', async () => {
        await delay('infinite')
        return HttpResponse.json({})
      }),
    )

    renderPage(success())
    await vi.advanceTimersByTimeAsync(30_000)

    expect(await screen.findByText(UNCERTAIN_TITLE)).toBeInTheDocument()
  })

  it('확인 중 화면을 새로고침해도 충전 확정을 다시 부르지 않고 확인 중을 그대로 보여 준다', async () => {
    savePending({ orderId: ORDER_ID, amount: 12_000 })
    const bodies: unknown[] = []
    server.use(confirmHandler('UNCERTAIN', bodies))

    const { unmount } = renderPage(success())
    expect(await screen.findByText(UNCERTAIN_TITLE)).toBeInTheDocument()
    unmount()

    renderPage(success())
    expect(await screen.findByText(UNCERTAIN_TITLE)).toBeInTheDocument()
    // 확정을 다시 부르면 서버가 토스에 승인을 한 번 더 요청한다
    expect(bodies).toHaveLength(1)
  })

  it('확정 응답을 기다리는 동안 새로고침해도 확정을 다시 부르지 않는다', async () => {
    savePending({ orderId: ORDER_ID, amount: 12_000 })
    const bodies: unknown[] = []
    server.use(
      http.post('*/api/v1/cash/charges/confirm', async ({ request }) => {
        bodies.push(await request.json())
        await delay('infinite')
        return HttpResponse.json({})
      }),
    )

    const { unmount } = renderPage(success())
    await vi.waitFor(() => expect(bodies).toHaveLength(1))
    unmount()

    renderPage(success())
    expect(await screen.findByText(UNCERTAIN_TITLE)).toBeInTheDocument()
    expect(bodies).toHaveLength(1)
  })

  it('다른 충전의 돌아온 주소를 열어도 결과를 모르는 충전 기록은 남는다', async () => {
    savePending({ orderId: ORDER_ID, amount: 12_000, uncertainAt: Date.now() })
    const bodies: unknown[] = []
    server.use(confirmHandler('COMPLETED', bodies))

    const { unmount } = renderPage(success({ orderId: 'charge-other' }))
    await vi.waitFor(() => expect(replace).toHaveBeenCalled())
    unmount()

    renderPage(success())
    expect(await screen.findByText(UNCERTAIN_TITLE)).toBeInTheDocument()
    expect(bodies).toEqual([])
  })

  it('요청이 잘못됨(400)이면 지금처럼 결제 실패 화면으로 보낸다', async () => {
    savePending({ orderId: ORDER_ID, amount: 12_000, returnOrderId: 1 })
    server.use(confirmErrorHandler(400, '최소 충전 금액은 10,000원입니다.'))

    renderPage(success())

    await vi.waitFor(() =>
      expect(replace).toHaveBeenCalledWith('/checkout?orderId=1&charge=failed'),
    )
  })

  it('돌아온 금액이 떠나기 전에 적어 둔 금액과 다르면 확정하지 않는다', async () => {
    savePending({ orderId: ORDER_ID, amount: 12_000, returnOrderId: 1 })
    const bodies: unknown[] = []
    server.use(confirmHandler('COMPLETED', bodies))

    renderPage(success({ amount: 100 }))

    await vi.waitFor(() =>
      expect(replace).toHaveBeenCalledWith('/checkout?orderId=1&charge=failed'),
    )
    expect(bodies).toEqual([])
  })

  it('결제창을 닫고 돌아오면(failUrl · PAY_PROCESS_CANCELED) 결제 화면으로 조용히 돌아간다', async () => {
    savePending({ orderId: ORDER_ID, amount: 12_000, returnOrderId: 1 })

    renderPage({
      result: 'fail',
      code: 'PAY_PROCESS_CANCELED',
      orderId: ORDER_ID,
    })

    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/checkout?orderId=1'))
  })

  it('적어 둔 충전과 orderId가 다른 주소로 열면 그 기록의 주문으로 가지 않는다', async () => {
    savePending({ orderId: 'charge-other', amount: 12_000, returnOrderId: 1 })
    const bodies: unknown[] = []
    server.use(confirmHandler('COMPLETED', bodies))

    renderPage(success())

    // 결제 중 충전인지 알 수 없어 예치금 충전 화면으로 보내고, 확정도 부르지 않는다
    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/me/deposit/charge'))
    expect(bodies).toEqual([])
  })

  it('예치금 화면에서 충전했으면 성공은 예치금(M17), 실패는 충전 화면(M21)으로 간다', async () => {
    savePending({ orderId: ORDER_ID, amount: 12_000 })
    server.use(confirmHandler('COMPLETED'))
    const { unmount } = renderPage(success())
    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/me/deposit'))
    unmount()

    replace.mockClear()
    renderPage({ result: 'fail', code: 'REJECT_CARD_COMPANY', orderId: ORDER_ID })
    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/me/deposit/charge'))
  })

  it('가짜 응답 모드에서 잔액 부족 → 충전 → 같은 주문으로 다시 결제까지 이어진다', async () => {
    // 목 잔액 5,000원, 주문 50,000원
    const firstPay = await orderApi.pay(1).catch((error: unknown) => error)
    expect(firstPay).toBeInstanceOf(ApiError)
    expect((firstPay as ApiError).code).toBe('ORDER_INSUFFICIENT_BALANCE')
    const shortage = ((firstPay as ApiError).body as { data: { shortage: number } }).data.shortage
    expect(shortage).toBe(45_000)

    const navigate = vi.fn()
    await requestCharge(
      { amount: shortage, returnOrderId: 1 },
      { mocking: true, clientKey: undefined, origin: 'http://localhost', navigate },
    )
    const back = new URL(navigate.mock.calls[0][0] as string).searchParams
    renderPage({
      result: back.get('result') ?? undefined,
      paymentKey: back.get('paymentKey') ?? undefined,
      orderId: back.get('orderId') ?? undefined,
      amount: Number(back.get('amount')),
    })
    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/checkout?orderId=1&charged=1'))

    const secondPay = await orderApi.pay(1)
    expect(secondPay.data?.status).toBe('PAID')
  })

  it('계약: 충전 확정 응답의 결과에 확인 중(UNCERTAIN)이 있다', () => {
    const parsed = cashZod.ConfirmWalletChargeResponse.safeParse({
      status: 200,
      message: '충전 결과를 확인하지 못했습니다. 잠시 후 다시 확인해주세요.',
      data: { outcome: 'UNCERTAIN', balance: 50_000 },
    })
    expect(parsed.success).toBe(true)
  })
})
