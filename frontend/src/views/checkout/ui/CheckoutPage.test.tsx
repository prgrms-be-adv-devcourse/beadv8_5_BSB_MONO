import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { cashApi, orderApi } from '@/shared/api'
import { orderMock, server } from '@/shared/testing'

import { CheckoutPage, type CheckoutState } from './CheckoutPage'

const push = vi.fn()
const replace = vi.fn()
// 토스 결제창은 페이지를 떠나므로 테스트에서는 띄우지 않고 무엇으로 불렀는지만 본다
const requestCharge = vi.fn()
vi.mock('@/features/deposit-charge', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/features/deposit-charge')>()),
  requestCharge: (...args: unknown[]) => requestCharge(...args),
}))
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push, back: vi.fn(), replace }),
  usePathname: () => '/checkout',
}))

// 지금은 2026-10-14 10:00 (한국 시간). 주문의 결제 기한은 10분 뒤.
const NOW = new Date('2026-10-14T10:00:00+09:00')
const DUE_AT = '2026-10-14T10:10:00'

/** 상품 API 명세 예시의 대회 1 · 종목 1(10K, 45,000원)을 결제 대기로 잡아 둔 주문 */
function orderResponse(
  patch: {
    status?: 'CREATED' | 'EXPIRED' | 'PAID'
    paymentDueAt?: string
    totalAmount?: number
  } = {},
) {
  const totalAmount = patch.totalAmount ?? 45000
  const example = orderMock.getFindById1ResponseMock()
  return {
    ...example,
    data: {
      ...example.data,
      status: patch.status ?? 'CREATED',
      totalAmount,
      paymentDueAt: patch.paymentDueAt ?? DUE_AT,
      items: example.data!.items!.map((item) => ({
        ...item,
        raceCourseId: 1,
        amount: totalAmount,
      })),
    },
  } as const
}

/** 주문의 참가 신청(명세 예시 1번)에 적힌 참가자 이름. 배송지 받는 사람(김러너)과 다르게 둔다 */
function participationResponse() {
  const example = orderMock.getFindByIdResponseMock()
  return {
    ...example,
    data: { ...example.data, participant: { ...example.data!.participant, name: '이달림' } },
  }
}

const balanceHandler = (balance: number) =>
  http.get('*/api/v1/cash/wallets/balance', () =>
    HttpResponse.json({ status: 200, message: '잔액 조회 성공', data: { holderId: 1, balance } }),
  )

const conflict = (code: string, data: unknown = null) =>
  HttpResponse.json({ status: 409, code, message: '아무 문구', data }, { status: 409 })

const payHandler = (respond: () => Response) =>
  http.post('*/api/v1/order/orders/:orderId/payment', respond)

function renderCheckout(
  state?: CheckoutState,
  props: { charged?: boolean; chargeFailed?: boolean } = {},
) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <CheckoutPage state={state} orderId={state ? undefined : 1} {...props} />
    </QueryClientProvider>,
  )
}

const LIMIT_NOTICE =
  '한 번에 30만 원까지 충전할 수 있어요. 충전한 뒤 남은 금액을 한 번 더 충전해 주세요.'

const timer = () => screen.getByRole('timer')
const agree = (user: ReturnType<typeof userEvent.setup>) =>
  user.click(screen.getByRole('checkbox', { name: '[필수] 환불 기한을 확인했어요' }))

describe('결제 화면', () => {
  beforeEach(() => {
    // MSW 응답(Promise)이 흐르도록 실제 시간도 같이 흘려보낸다.
    vi.useFakeTimers({ shouldAdvanceTime: true })
    vi.setSystemTime(NOW)
    push.mockClear()
    replace.mockClear()
    requestCharge.mockReset()
    requestCharge.mockResolvedValue('redirecting')
    server.use(
      orderMock.getFindById1MockHandler(orderResponse()),
      orderMock.getFindByIdMockHandler(participationResponse()),
      balanceHandler(100_000),
    )
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('주문 금액 · 대회 · 참가자와 결제 기한까지 남은 시간을 보여 준다', async () => {
    renderCheckout()

    expect(
      await screen.findByText('2026 용감한 쿠키RUN in 서울 · 10K · 이달림'),
    ).toBeInTheDocument()
    // 예치금 10만 원에서 45,000원을 내고 55,000원이 남는다
    expect(screen.getByText('₩55,000')).toBeInTheDocument()
    expect(timer()).toHaveTextContent(/10:00|09:5\d/)
    // Clarity 화면 녹화에서 참가자 이름이 든 주문 요약을 가린다
    expect(
      screen
        .getByText('2026 용감한 쿠키RUN in 서울 · 10K · 이달림')
        .closest('[data-clarity-mask="true"]'),
    ).not.toBeNull()
  })

  it('대기열에 입장 중이면 결제 기한 대신 대기열에서 받은 남은 시간을 센다', async () => {
    server.use(
      orderMock.getFindAdmissionMockHandler({
        status: 200,
        data: { admitted: true, admissionEndsAt: DUE_AT, remainingSeconds: 300 },
      }),
    )
    renderCheckout()

    await screen.findByText('2026 용감한 쿠키RUN in 서울 · 10K · 이달림')
    await vi.waitFor(() => expect(timer()).toHaveTextContent(/05:00|04:5\d/))
  })

  it('입장 끝이 결제 기한보다 늦으면 결제 기한 기준으로 남은 시간을 센다', async () => {
    server.use(
      orderMock.getFindById1MockHandler(orderResponse({ paymentDueAt: '2026-10-14T10:05:00' })),
      orderMock.getFindAdmissionMockHandler({
        status: 200,
        data: { admitted: true, admissionEndsAt: DUE_AT, remainingSeconds: 600 },
      }),
    )
    renderCheckout()

    await screen.findByText('2026 용감한 쿠키RUN in 서울 · 10K · 이달림')
    await vi.waitFor(() => expect(timer()).toHaveTextContent(/05:00|04:5\d/))
  })

  it('결제 기한이 이미 지났으면 입장 확인이 새 시간을 줘도 시간 초과 화면을 보여 준다', async () => {
    server.use(
      orderMock.getFindById1MockHandler(orderResponse({ paymentDueAt: '2026-10-14T09:55:00' })),
      orderMock.getFindAdmissionMockHandler({
        status: 200,
        data: { admitted: true, admissionEndsAt: DUE_AT, remainingSeconds: 600 },
      }),
    )
    renderCheckout()

    expect(await screen.findByText('결제 시간이 지났어요')).toBeInTheDocument()
    expect(screen.queryByRole('timer')).not.toBeInTheDocument()
  })

  it('잔액이 충분하면 예치금으로 결제하고 결제 완료 화면으로 간다', async () => {
    let paid = false
    server.use(
      http.post('*/api/v1/order/orders/:orderId/payment', () => {
        paid = true
        return HttpResponse.json(orderMock.getPayResponseMock())
      }),
    )
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderCheckout()

    const pay = await screen.findByRole('button', { name: '₩45,000 결제' })
    await agree(user)
    await user.click(pay)

    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/checkout/complete?orderId=1'))
    expect(paid).toBe(true)
  })

  it('결제하면 결제 전 주문 · 잔액 · 내 참가권 캐시를 지워 다음 화면이 결제한 값을 받는다', async () => {
    let paid = false
    server.use(
      http.get('*/api/v1/order/orders/:orderId', () =>
        HttpResponse.json(orderResponse({ status: paid ? 'PAID' : 'CREATED' })),
      ),
      http.post('*/api/v1/order/orders/:orderId/payment', () => {
        paid = true
        return HttpResponse.json(orderMock.getPayResponseMock())
      }),
      http.get('*/api/v1/cash/wallets/balance', () =>
        HttpResponse.json({
          status: 200,
          message: '잔액 조회 성공',
          data: { holderId: 1, balance: paid ? 55000 : 100000 },
        }),
      ),
    )
    // 앱처럼 1분 동안은 다시 받지 않는다 (shared/api/query-client.ts)
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false, staleTime: 60_000 } },
    })
    // 결제 전에 열어 둔 내 참가권 목록 (지금은 화면에 없다)
    const ticketsKey = [...orderApi.getFindMineQueryKey({ page: 0, size: 100 }), 'tickets']
    client.setQueryData(ticketsKey, [])
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    render(
      <QueryClientProvider client={client}>
        <CheckoutPage orderId={1} />
      </QueryClientProvider>,
    )

    const pay = await screen.findByRole('button', { name: '₩45,000 결제' })
    await agree(user)
    await user.click(pay)

    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/checkout/complete?orderId=1'))
    // 넘어갈 때는 이미 결제한 주문을 받아 두었고, 잔액은 곧 새 값으로 바뀐다
    expect(client.getQueryData(orderApi.getFindById1QueryKey(1))).toMatchObject({
      data: { status: 'PAID' },
    })
    await vi.waitFor(() =>
      expect(client.getQueryData(cashApi.getGetBalanceQueryKey())).toMatchObject({
        data: { balance: 55000 },
      }),
    )
    expect(client.getQueryState(ticketsKey)?.isInvalidated).toBe(true)
    // 같은 완료 주소로 push까지 하면 기록이 두 번 남는다
    expect(push).not.toHaveBeenCalled()
  })

  it('결제 뒤 잔액을 다시 받다가 실패해도 기다리지 않고 완료 화면으로 넘어간다', async () => {
    let paid = false
    server.use(
      http.get('*/api/v1/order/orders/:orderId', () =>
        HttpResponse.json(orderResponse({ status: paid ? 'PAID' : 'CREATED' })),
      ),
      http.post('*/api/v1/order/orders/:orderId/payment', () => {
        paid = true
        return HttpResponse.json(orderMock.getPayResponseMock())
      }),
      http.get('*/api/v1/cash/wallets/balance', () =>
        paid
          ? HttpResponse.json({ status: 500, message: '오류', data: null }, { status: 500 })
          : HttpResponse.json({
              status: 200,
              message: '잔액 조회 성공',
              data: { holderId: 1, balance: 100000 },
            }),
      ),
    )
    // 앱처럼 실패하면 다시 시도한다. 그동안 기다리면 넘어가지 못한다
    const client = new QueryClient({
      defaultOptions: { queries: { retry: 3, retryDelay: 5_000, staleTime: 60_000 } },
    })
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    render(
      <QueryClientProvider client={client}>
        <CheckoutPage orderId={1} />
      </QueryClientProvider>,
    )

    const pay = await screen.findByRole('button', { name: '₩45,000 결제' })
    await agree(user)
    await user.click(pay)

    // 결제 onSuccess(주문 다시 받기 → 완료 화면)가 끝났을 때 잔액은 아직 다시 시도하는 중이다.
    // 잔액까지 기다리면 재시도가 끝난 몇 초 뒤에야 끝나고, 그사이 다른 화면에 가 있어도 완료 화면으로 다시 보낸다
    await vi.waitFor(() =>
      expect(client.getMutationCache().getAll()[0]?.state.status).toBe('success'),
    )
    expect(replace).toHaveBeenCalledWith('/checkout/complete?orderId=1')
    expect(client.getQueryState(cashApi.getGetBalanceQueryKey())?.fetchStatus).toBe('fetching')
    client.clear()
  })

  it('잔액이 모자라 보이면 충전하고 결제 버튼을 보여 주고, 누르면 결제 API부터 부른다', async () => {
    server.use(balanceHandler(40_000))
    let paid = 0
    server.use(
      payHandler(() => {
        paid += 1
        return conflict('ORDER_INSUFFICIENT_BALANCE', { shortage: 5000 })
      }),
    )
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderCheckout()

    // 45,000원 중 40,000원은 예치금에서, 모자란 5,000원은 최소 충전 금액 1만 원으로 충전한다.
    const button = await screen.findByRole('button', { name: '₩10,000 충전하고 결제' })
    expect(screen.getByText('+₩10,000')).toBeInTheDocument()
    await agree(user)
    await user.click(button)

    // 서버가 5,000원 모자라다고 하면 최소 1만 원을 이 주문으로 돌아오게 충전한다
    await vi.waitFor(() =>
      expect(requestCharge).toHaveBeenCalledWith({ amount: 10_000, returnOrderId: 1 }),
    )
    expect(paid).toBe(1)
    expect(replace).not.toHaveBeenCalled()
  })

  it('1회 충전 한도(30만 원)보다 많이 모자라면 30만 원만 충전하고, 나눠 충전하라는 안내를 보여 준다', async () => {
    server.use(
      orderMock.getFindById1MockHandler(orderResponse({ totalAmount: 450_000 })),
      balanceHandler(0),
      payHandler(() => conflict('ORDER_INSUFFICIENT_BALANCE', { shortage: 450_000 })),
    )
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderCheckout()

    // 버튼에 보이는 충전 금액과 실제로 보내는 충전 금액이 같아야 한다
    const button = await screen.findByRole('button', { name: '₩300,000 충전하고 결제' })
    expect(screen.getByText(LIMIT_NOTICE)).toBeInTheDocument()
    await agree(user)
    await user.click(button)

    await vi.waitFor(() =>
      expect(requestCharge).toHaveBeenCalledWith({ amount: 300_000, returnOrderId: 1 }),
    )
  })

  it('정확히 30만 원 모자라면 한 번에 충전하고 한도 안내를 보여 주지 않는다', async () => {
    server.use(
      orderMock.getFindById1MockHandler(orderResponse({ totalAmount: 300_000 })),
      balanceHandler(0),
    )
    renderCheckout()

    await screen.findByRole('button', { name: '₩300,000 충전하고 결제' })
    expect(screen.queryByText(LIMIT_NOTICE)).not.toBeInTheDocument()
  })

  it('결제 때 예치금이 모자라다고 하면(409 ORDER_INSUFFICIENT_BALANCE) 서버가 알려 준 부족 금액만큼 충전한다', async () => {
    server.use(payHandler(() => conflict('ORDER_INSUFFICIENT_BALANCE', { shortage: 12_000 })))
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderCheckout()

    const pay = await screen.findByRole('button', { name: '₩45,000 결제' })
    await agree(user)
    await user.click(pay)

    await vi.waitFor(() =>
      expect(requestCharge).toHaveBeenCalledWith({ amount: 12_000, returnOrderId: 1 }),
    )
    // 서버 문구는 보여 주지 않는다
    expect(screen.queryByText('아무 문구')).not.toBeInTheDocument()
  })

  it('토스 결제창을 닫으면 결제 화면에 그대로 남아 다시 누를 수 있다', async () => {
    requestCharge.mockResolvedValue('canceled')
    server.use(payHandler(() => conflict('ORDER_INSUFFICIENT_BALANCE', { shortage: 12_000 })))
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderCheckout()

    const pay = await screen.findByRole('button', { name: '₩45,000 결제' })
    await agree(user)
    await user.click(pay)

    await vi.waitFor(() => expect(requestCharge).toHaveBeenCalled())
    await vi.waitFor(() => expect(pay).toBeEnabled())
    expect(screen.queryByText('결제하지 못했어요')).not.toBeInTheDocument()
  })

  it('앞선 충전 결과를 아직 모르면 결제창을 띄우지 않고 기다리라고 안내한다', async () => {
    requestCharge.mockResolvedValue('held')
    server.use(payHandler(() => conflict('ORDER_INSUFFICIENT_BALANCE', { shortage: 12_000 })))
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderCheckout()

    const pay = await screen.findByRole('button', { name: '₩45,000 결제' })
    await agree(user)
    await user.click(pay)

    expect(await screen.findByText('앞선 충전 결과를 확인하고 있어요')).toBeInTheDocument()
    await vi.waitFor(() => expect(pay).toBeEnabled())
  })

  it('토스 결제창을 띄우지 못하면 결제 실패 화면(M07-3)을 보여 준다', async () => {
    requestCharge.mockRejectedValue(new Error('no client key'))
    server.use(payHandler(() => conflict('ORDER_INSUFFICIENT_BALANCE', { shortage: 12_000 })))
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderCheckout()

    const pay = await screen.findByRole('button', { name: '₩45,000 결제' })
    await agree(user)
    await user.click(pay)

    expect(await screen.findByText('결제하지 못했어요')).toBeInTheDocument()
  })

  it('충전하고 돌아오면(charged) 동의를 다시 받지 않고 같은 주문으로 다시 결제해 완료 화면으로 간다', async () => {
    const paidOrders: string[] = []
    server.use(
      http.post('*/api/v1/order/orders/:orderId/payment', ({ params }) => {
        paidOrders.push(String(params.orderId))
        return HttpResponse.json(orderMock.getPayResponseMock())
      }),
    )
    renderCheckout(undefined, { charged: true })

    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/checkout/complete?orderId=1'))
    expect(paidOrders).toEqual(['1'])
    expect(requestCharge).not.toHaveBeenCalled()
  })

  it('충전하고 돌아와 다시 결제했는데 또 모자라면 결제창을 저절로 띄우지 않고 버튼으로 다시 충전하게 한다', async () => {
    server.use(
      balanceHandler(40_000),
      payHandler(() => conflict('ORDER_INSUFFICIENT_BALANCE', { shortage: 5000 })),
    )
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderCheckout(undefined, { charged: true })

    const button = await screen.findByRole('button', { name: '₩10,000 충전하고 결제' })
    await vi.waitFor(() => expect(button).toBeEnabled())
    expect(requestCharge).not.toHaveBeenCalled()

    await user.click(button)
    await vi.waitFor(() =>
      expect(requestCharge).toHaveBeenCalledWith({ amount: 10_000, returnOrderId: 1 }),
    )
  })

  it('충전하고 돌아왔는데 결제 기한이 지났으면 다시 결제하지 않고 시간 초과 화면(M07-1)을 보여 준다', async () => {
    let paid = false
    server.use(
      orderMock.getFindById1MockHandler(orderResponse({ paymentDueAt: '2026-10-14T09:59:00' })),
      payHandler(() => {
        paid = true
        return HttpResponse.json(orderMock.getPayResponseMock())
      }),
    )
    renderCheckout(undefined, { charged: true })

    expect(await screen.findByText('결제 시간이 지났어요')).toBeInTheDocument()
    expect(screen.getByText(/충전한 예치금은 그대로 남아 있어요\./)).toBeInTheDocument()
    expect(paid).toBe(false)
  })

  it('충전하고 다시 결제했는데 서버가 기한이 지났다고 하면 시간 초과 화면(M07-1)을 보여 준다', async () => {
    server.use(payHandler(() => conflict('ORDER_PAYMENT_DEADLINE_PASSED')))
    renderCheckout(undefined, { charged: true })

    expect(await screen.findByText('결제 시간이 지났어요')).toBeInTheDocument()
    expect(replace).not.toHaveBeenCalled()
  })

  it('충전이 안 돼 돌아오면(chargeFailed) 결제 실패 화면을 보여 주고, 다시 결제하기로 결제 화면에 돌아간다', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderCheckout(undefined, { chargeFailed: true })

    expect(await screen.findByText('결제하지 못했어요')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '다시 결제하기' }))

    expect(await screen.findByRole('button', { name: '₩45,000 결제' })).toBeInTheDocument()
  })

  it('결제 때 기한이 지났다고 하면(409 ORDER_PAYMENT_DEADLINE_PASSED) 시간 초과 화면을 보여 준다', async () => {
    server.use(payHandler(() => conflict('ORDER_PAYMENT_DEADLINE_PASSED')))
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderCheckout()

    const pay = await screen.findByRole('button', { name: '₩45,000 결제' })
    await agree(user)
    await user.click(pay)

    expect(await screen.findByText('결제 시간이 지났어요')).toBeInTheDocument()
    expect(screen.getByText(/입장 시간이 끝나 잡아 둔 자리가 풀렸어요\./)).toBeInTheDocument()
    expect(screen.getByText(/충전한 예치금은 그대로 남아 있어요\./)).toBeInTheDocument()
    // 주문의 대회 상세(M02)로 돌아간다
    expect(screen.getByRole('link', { name: '대회 다시 보기' })).toHaveAttribute('href', '/races/1')
  })

  it('결제 때 남은 자리가 없다고 하면(409 ORDER_SOLD_OUT) 자리 마감 화면을 보여 준다', async () => {
    server.use(payHandler(() => conflict('ORDER_SOLD_OUT')))
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderCheckout()

    const pay = await screen.findByRole('button', { name: '₩45,000 결제' })
    await agree(user)
    await user.click(pay)

    expect(await screen.findByText('남은 자리가 없어요')).toBeInTheDocument()
  })

  it('결제 기한이 지나면 시간 초과 화면으로 바뀐다', async () => {
    renderCheckout()
    await screen.findByRole('timer')

    act(() => vi.advanceTimersByTime(10 * 60 * 1000))

    expect(await screen.findByText('결제 시간이 지났어요')).toBeInTheDocument()
    expect(screen.queryByRole('timer')).not.toBeInTheDocument()
  })

  it('기한이 만료된 주문이면 바로 시간 초과 화면을 보여 준다', async () => {
    server.use(orderMock.getFindById1MockHandler(orderResponse({ status: 'EXPIRED' })))
    renderCheckout()

    expect(await screen.findByText('결제 시간이 지났어요')).toBeInTheDocument()
  })

  it('이미 결제한 주문이면 결제 완료 화면으로 보낸다', async () => {
    server.use(orderMock.getFindById1MockHandler(orderResponse({ status: 'PAID' })))
    renderCheckout()

    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/checkout/complete?orderId=1'))
  })

  it('?preview=offline 시안은 시안의 남은 시간에서 센다', () => {
    renderCheckout('offline')

    expect(timer()).toHaveTextContent('05:10')
    expect(screen.getByRole('checkbox', { name: '[필수] 환불 기한을 확인했어요' })).toBeChecked()
  })

  it('?preview=timeout 시안은 바로 시간 초과 화면을 보여 준다', () => {
    renderCheckout('timeout')

    expect(screen.getByText('결제 시간이 지났어요')).toBeInTheDocument()
    // 대회를 모르는 미리보기는 대회 목록으로 간다
    expect(screen.getByRole('link', { name: '대회 다시 보기' })).toHaveAttribute('href', '/races')
  })

  it('대기열 입장 시간이 끝나 넘어오면(?result=timeout&raceId=) 그 대회 상세로 돌아간다', () => {
    const client = new QueryClient()
    render(
      <QueryClientProvider client={client}>
        <CheckoutPage state="timeout" raceId={1} />
      </QueryClientProvider>,
    )

    expect(screen.getByText('결제 시간이 지났어요')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '대회 다시 보기' })).toHaveAttribute('href', '/races/1')
  })
})
