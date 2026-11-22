import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { readKitOptionIds, saveKitOptionIds } from '@/features/booking'
import { partyKeys } from '@/entities/party'
import { cashApi, orderApi } from '@/shared/api'
import { orderMock, productMock, server } from '@/shared/testing'

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
    status?: 'CREATED' | 'PAYING' | 'EXPIRED' | 'PAID'
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

type KitGroups = NonNullable<
  NonNullable<ReturnType<typeof productMock.getFindKitOptionsResponseMock>['data']>['kitOptions']
>

const kitOptionsHandler = (kitOptions: KitGroups) =>
  productMock.getFindKitOptionsMockHandler({
    status: 200,
    message: '키트 옵션 목록을 조회했습니다.',
    data: { raceId: 1, kitOptions },
  })

/** 명세 예시: 기념 티셔츠(M 11 · L 12), 양말(230-250 21 · 255-280 22) */
const KIT_GROUPS: KitGroups = [
  {
    name: '기념 티셔츠',
    values: [
      { kitOptionId: 11, value: 'M', stock: 100 },
      { kitOptionId: 12, value: 'L', stock: 80 },
    ],
  },
  {
    name: '양말',
    values: [
      { kitOptionId: 21, value: '230-250', stock: null },
      { kitOptionId: 22, value: '255-280', stock: null },
    ],
  },
]

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
  '한 번에 30만 원까지 충전할 수 있어요. 충전한 뒤 남은 금액은 다시 충전해 주세요.'

const timer = () => screen.getByRole('timer')
const agree = (user: ReturnType<typeof userEvent.setup>) =>
  user.click(screen.getByRole('checkbox', { name: '주문 내용 확인 및 결제 동의' }))

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
      // 기본은 키트 옵션이 없는 대회. 있는 대회는 아래 「키트 옵션」 묶음이 따로 켠다
      kitOptionsHandler([]),
    )
  })
  afterEach(() => {
    vi.useRealTimers()
    sessionStorage.clear()
  })

  it('참가자 · 대회 · 결제 금액과 결제 기한까지 남은 시간을 보여 준다', async () => {
    renderCheckout()

    expect(await screen.findByText('이달림 · 1994.06.25 · 남성')).toBeInTheDocument()
    expect(screen.getByText('2026 용감한 쿠키RUN in 서울')).toBeInTheDocument()
    expect(timer()).toHaveTextContent(/10:00|09:5\d/)
    // Clarity 화면 녹화에서 참가자 이름이 든 줄을 가린다
    expect(
      screen.getByText('이달림 · 1994.06.25 · 남성').closest('[data-clarity-mask="true"]'),
    ).not.toBeNull()
  })

  it('키트 배송지 · 옵션도 Clarity 화면 녹화에서 가린다', async () => {
    const example = orderMock.getFindById1ResponseMock()
    server.use(
      orderMock.getFindById1MockHandler({
        ...orderResponse(),
        data: {
          ...orderResponse().data,
          shippingAddress: { ...example.data!.shippingAddress, address: '서울 성동구 왕십리로 1' },
        },
      }),
    )
    renderCheckout()

    const address = await screen.findByText(/서울 성동구 왕십리로 1/)
    expect(address.closest('[data-clarity-mask="true"]')).not.toBeNull()
  })

  it('대기열에 입장 중이면 결제 기한 대신 대기열에서 받은 남은 시간을 센다', async () => {
    server.use(
      orderMock.getFindAdmissionMockHandler({
        status: 200,
        data: { admitted: true, admissionEndsAt: DUE_AT, remainingSeconds: 300 },
      }),
    )
    renderCheckout()

    await screen.findByText('이달림 · 1994.06.25 · 남성')
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

    await screen.findByText('이달림 · 1994.06.25 · 남성')
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

    const pay = await screen.findByRole('button', { name: '45,000원 결제하기' })
    await agree(user)
    await user.click(pay)

    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/checkout/complete?orderId=1'))
    expect(paid).toBe(true)
  })

  it('참가 신청에서 고른 키트 옵션을 결제 본문 kitOptionIds로 보낸다', async () => {
    saveKitOptionIds(1, [11, 22])
    const bodies: unknown[] = []
    server.use(
      http.post('*/api/v1/order/orders/:orderId/payment', async ({ request }) => {
        bodies.push(await request.json())
        return HttpResponse.json(orderMock.getPayResponseMock())
      }),
    )
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderCheckout()

    const pay = await screen.findByRole('button', { name: '45,000원 결제하기' })
    await agree(user)
    await user.click(pay)

    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/checkout/complete?orderId=1'))
    expect(bodies).toEqual([{ kitOptionIds: [11, 22] }])
  })

  it('충전하고 돌아와 다시 결제할 때도 고른 키트 옵션을 보낸다', async () => {
    saveKitOptionIds(1, [11, 22])
    const bodies: unknown[] = []
    server.use(
      http.post('*/api/v1/order/orders/:orderId/payment', async ({ request }) => {
        bodies.push(await request.json())
        return HttpResponse.json(orderMock.getPayResponseMock())
      }),
    )
    renderCheckout(undefined, { charged: true })

    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/checkout/complete?orderId=1'))
    expect(bodies).toEqual([{ kitOptionIds: [11, 22] }])
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
    // 파티원이 결제 전에 본 파티 상세 · 내 파티 상태 (CRU-153). 지우지 않으면 돌아갔을 때 「결제 전」이 남는다
    client.setQueryData(partyKeys.detail(3), { id: 3 })
    client.setQueryData(partyKeys.raceStatus(9), { myPaid: false })
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    render(
      <QueryClientProvider client={client}>
        <CheckoutPage orderId={1} />
      </QueryClientProvider>,
    )

    const pay = await screen.findByRole('button', { name: '45,000원 결제하기' })
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
    expect(client.getQueryState(partyKeys.detail(3))?.isInvalidated).toBe(true)
    expect(client.getQueryState(partyKeys.raceStatus(9))?.isInvalidated).toBe(true)
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

    const pay = await screen.findByRole('button', { name: '45,000원 결제하기' })
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
    const button = await screen.findByRole('button', { name: '10,000원 충전하고 결제하기' })
    // 결제 금액 구역에 카드로 충전할 금액이 보인다
    expect(
      within(screen.getByRole('region', { name: '결제 금액' })).getByText('10,000원'),
    ).toBeInTheDocument()
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
    const button = await screen.findByRole('button', { name: '300,000원 충전하고 결제하기' })
    expect(button).toHaveAccessibleDescription(LIMIT_NOTICE)
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

    await screen.findByRole('button', { name: '300,000원 충전하고 결제하기' })
    expect(screen.queryByText(LIMIT_NOTICE)).not.toBeInTheDocument()
  })

  it('결제 때 예치금이 모자라다고 하면(409 ORDER_INSUFFICIENT_BALANCE) 서버가 알려 준 부족 금액만큼 충전한다', async () => {
    server.use(payHandler(() => conflict('ORDER_INSUFFICIENT_BALANCE', { shortage: 12_000 })))
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderCheckout()

    const pay = await screen.findByRole('button', { name: '45,000원 결제하기' })
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

    const pay = await screen.findByRole('button', { name: '45,000원 결제하기' })
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

    const pay = await screen.findByRole('button', { name: '45,000원 결제하기' })
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

    const pay = await screen.findByRole('button', { name: '45,000원 결제하기' })
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

    const button = await screen.findByRole('button', { name: '10,000원 충전하고 결제하기' })
    await vi.waitFor(() => expect(button).toBeEnabled())
    expect(requestCharge).not.toHaveBeenCalled()

    await user.click(button)
    await vi.waitFor(() =>
      expect(requestCharge).toHaveBeenCalledWith({ amount: 10_000, returnOrderId: 1 }),
    )
  })

  it('30만 원을 충전하고 돌아와도 모자라면 남은 금액을 충전하는 버튼을 보여 주고 한도 안내는 감춘다', async () => {
    // 45만 원 주문에 30만 원을 충전하고 돌아온 상태. 다시 결제하면 15만 원이 모자라다
    let paid = 0
    server.use(
      orderMock.getFindById1MockHandler(orderResponse({ totalAmount: 450_000 })),
      balanceHandler(300_000),
      payHandler(() => {
        paid += 1
        return conflict('ORDER_INSUFFICIENT_BALANCE', { shortage: 150_000 })
      }),
    )
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderCheckout(undefined, { charged: true })

    const button = await screen.findByRole('button', { name: '150,000원 충전하고 결제하기' })
    await vi.waitFor(() => expect(button).toBeEnabled())
    expect(paid).toBe(1)
    expect(requestCharge).not.toHaveBeenCalled()
    expect(screen.queryByText(LIMIT_NOTICE)).not.toBeInTheDocument()

    await user.click(button)
    await vi.waitFor(() =>
      expect(requestCharge).toHaveBeenCalledWith({ amount: 150_000, returnOrderId: 1 }),
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

    expect(await screen.findByRole('button', { name: '45,000원 결제하기' })).toBeInTheDocument()
  })

  it('결제 때 기한이 지났다고 하면(409 ORDER_PAYMENT_DEADLINE_PASSED) 시간 초과 화면을 보여 준다', async () => {
    server.use(payHandler(() => conflict('ORDER_PAYMENT_DEADLINE_PASSED')))
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderCheckout()

    const pay = await screen.findByRole('button', { name: '45,000원 결제하기' })
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

    const pay = await screen.findByRole('button', { name: '45,000원 결제하기' })
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

  describe('결제 확인 중 (BUK-232)', () => {
    const CONFIRMING = '결제를 확인하고 있어요'
    const LATE = '결제 확인이 늦어지고 있어요'
    /** 주문 조회가 차례대로 돌려줄 상태. 마지막 것을 계속 돌려준다 */
    const orderSequence = (...statuses: ('CREATED' | 'PAYING' | 'PAID' | 'EXPIRED')[]) => {
      let call = 0
      return http.get('*/api/v1/order/orders/:orderId', () =>
        HttpResponse.json(
          orderResponse({ status: statuses[Math.min(call++, statuses.length - 1)] }),
        ),
      )
    }

    it('결제 응답이 202(결제 중)이면 실패나 완료 대신 결제 확인 중 화면을 보여 준다', async () => {
      server.use(
        orderSequence('CREATED', 'PAYING'),
        payHandler(() =>
          HttpResponse.json(
            {
              status: 202,
              message: '결제를 확인하고 있어요',
              data: orderResponse({ status: 'PAYING' }).data,
            },
            { status: 202 },
          ),
        ),
      )
      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
      renderCheckout()

      const pay = await screen.findByRole('button', { name: '45,000원 결제하기' })
      await agree(user)
      await user.click(pay)

      expect(await screen.findByText(CONFIRMING)).toBeInTheDocument()
      expect(screen.queryByText('결제하지 못했어요')).not.toBeInTheDocument()
      expect(replace).not.toHaveBeenCalledWith('/checkout/complete?orderId=1')
    })

    it('결제 중인 주문으로 들어오면 결제 확인 중 화면을 보여 주고 다시 결제하는 버튼은 없다', async () => {
      server.use(orderSequence('PAYING'))
      renderCheckout()

      expect(await screen.findByText(CONFIRMING)).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /결제/ })).not.toBeInTheDocument()
    })

    it('결제 기한이 지나도 결제 중이면 시간 초과 대신 결제 확인 중을 보여 준다', async () => {
      server.use(
        http.get('*/api/v1/order/orders/:orderId', () =>
          HttpResponse.json(
            orderResponse({ status: 'PAYING', paymentDueAt: '2026-10-14T09:59:00' }),
          ),
        ),
      )
      renderCheckout()

      expect(await screen.findByText(CONFIRMING)).toBeInTheDocument()
      expect(screen.queryByText('결제 시간이 지났어요')).not.toBeInTheDocument()
    })

    it('3초마다 주문을 다시 조회해 결제 완료가 되면 완료 화면으로 간다', async () => {
      server.use(orderSequence('PAYING', 'PAYING', 'PAID'))
      renderCheckout()
      await screen.findByText(CONFIRMING)

      await act(() => vi.advanceTimersByTimeAsync(3000))
      expect(replace).not.toHaveBeenCalled()
      await act(() => vi.advanceTimersByTimeAsync(3000))

      await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/checkout/complete?orderId=1'))
    })

    it('결제 중이던 주문이 결제 대기로 돌아오면(결제 거절) 결제 실패 화면을 보여 준다', async () => {
      server.use(orderSequence('PAYING', 'CREATED'))
      renderCheckout()
      await screen.findByText(CONFIRMING)

      await act(() => vi.advanceTimersByTimeAsync(3000))

      expect(await screen.findByText('결제하지 못했어요')).toBeInTheDocument()
    })

    it('202를 받으면 주문을 다시 받기 전에도 바로 결제 확인 중을 보여 주고, 고른 키트 옵션은 남겨 둔다', async () => {
      saveKitOptionIds(1, [11])
      server.use(
        payHandler(() =>
          HttpResponse.json(
            {
              status: 202,
              message: '결제를 확인하고 있어요',
              data: orderResponse({ status: 'PAYING' }).data,
            },
            { status: 202 },
          ),
        ),
      )
      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
      renderCheckout()

      const pay = await screen.findByRole('button', { name: '45,000원 결제하기' })
      await agree(user)
      await user.click(pay)

      expect(await screen.findByText(CONFIRMING)).toBeInTheDocument()
      expect(readKitOptionIds(1)).toEqual([11])
    })

    it('결제 중에 주문 재조회가 실패해도 결제 실패로 바꾸지 않는다', async () => {
      let call = 0
      server.use(
        http.get('*/api/v1/order/orders/:orderId', () =>
          call++ === 0
            ? HttpResponse.json(orderResponse({ status: 'PAYING' }))
            : HttpResponse.json({ status: 500, message: '오류', data: null }, { status: 500 }),
        ),
      )
      renderCheckout()
      await screen.findByText(CONFIRMING)

      await act(() => vi.advanceTimersByTimeAsync(3000))
      await act(() => vi.advanceTimersByTimeAsync(3000))

      expect(call).toBeGreaterThan(1)
      expect(screen.getByText(CONFIRMING)).toBeInTheDocument()
      expect(screen.queryByText('결제하지 못했어요')).not.toBeInTheDocument()
    })

    it('재조회는 3초 간격이다', async () => {
      let calls = 0
      server.use(
        http.get('*/api/v1/order/orders/:orderId', () => {
          calls++
          return HttpResponse.json(orderResponse({ status: 'PAYING' }))
        }),
      )
      renderCheckout()
      await screen.findByText(CONFIRMING)
      const first = calls

      await act(() => vi.advanceTimersByTimeAsync(2900))
      expect(calls).toBe(first)
      await act(() => vi.advanceTimersByTimeAsync(200))
      expect(calls).toBe(first + 1)
    })

    it('재조회로 결제 완료가 되면 고른 키트 옵션을 지우고 완료 화면으로 간다', async () => {
      saveKitOptionIds(1, [11])
      server.use(orderSequence('PAYING', 'PAID'))
      renderCheckout()
      await screen.findByText(CONFIRMING)

      await act(() => vi.advanceTimersByTimeAsync(3000))

      await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/checkout/complete?orderId=1'))
      expect(readKitOptionIds(1)).toEqual([])
    })

    it('재조회로 결제 완료가 되면 파티 캐시도 지운다 (CRU-153)', async () => {
      server.use(orderSequence('PAYING', 'PAID'))
      const client = new QueryClient({
        defaultOptions: { queries: { retry: false, staleTime: 60_000 } },
      })
      client.setQueryData(partyKeys.detail(3), { id: 3 })
      render(
        <QueryClientProvider client={client}>
          <CheckoutPage orderId={1} />
        </QueryClientProvider>,
      )
      await screen.findByText(CONFIRMING)

      await act(() => vi.advanceTimersByTimeAsync(3000))

      await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/checkout/complete?orderId=1'))
      expect(client.getQueryState(partyKeys.detail(3))?.isInvalidated).toBe(true)
    })

    it('2분이 지나도 결제 중이면 내 참가권에서 결과를 보라고 안내한다', async () => {
      server.use(orderSequence('PAYING'))
      renderCheckout()
      await screen.findByText(CONFIRMING)

      await act(() => vi.advanceTimersByTimeAsync(2 * 60 * 1000))

      expect(await screen.findByText(LATE)).toBeInTheDocument()
      expect(screen.getByRole('link', { name: '내 참가권 보기' })).toHaveAttribute(
        'href',
        '/tickets',
      )
    })
  })

  describe('v3 배치', () => {
    const headings = () => screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent)
    const region = (name: string) => screen.getByRole('region', { name })

    it('남은 시간은 헤더 아래 남은 시간 띠에 보인다', async () => {
      renderCheckout()

      await screen.findByRole('heading', { name: '결제 금액' })
      expect(screen.getByRole('timer')).toHaveTextContent(/(10:00|09:5\d)/)
      expect(screen.getByText(/자리를 잡아 뒀어요/)).toBeInTheDocument()
    })

    it('예치금이 모자라면 여섯 구역이 시안 순서로 보인다', async () => {
      server.use(balanceHandler(40_000))
      renderCheckout()

      await screen.findByRole('heading', { name: '결제 금액' })
      expect(headings()).toEqual([
        '참가자 · 배송',
        '신청 대회',
        '예치금',
        '충전 수단',
        '결제 금액',
        '결제 동의',
      ])
    })

    it('참가자 상자는 이름 · 생년월일 · 성별, 배송 주소, 비상 연락처를 보여 준다', async () => {
      renderCheckout()

      const box = await screen.findByRole('region', { name: '참가자 · 배송' })
      expect(await within(box).findByText('이달림 · 1994.06.25 · 남성')).toBeInTheDocument()
      expect(within(box).getByText(/서울 성동구 왕십리로/)).toBeInTheDocument()
      expect(within(box).getByText('비상 연락처 김가족 (010-9876-5432)')).toBeInTheDocument()
    })

    it('참가 신청을 못 받으면 이름 · 비상 연락처 줄은 빼고 배송 주소만 보여 준다', async () => {
      server.use(
        http.get('*/api/v1/order/participations/:id', () =>
          HttpResponse.json({ status: 500, message: '오류', data: null }, { status: 500 }),
        ),
      )
      renderCheckout()

      const box = await screen.findByRole('region', { name: '참가자 · 배송' })
      expect(await within(box).findByText(/서울 성동구 왕십리로/)).toBeInTheDocument()
      expect(within(box).queryByText(/비상 연락처/)).not.toBeInTheDocument()
      expect(within(box).queryByText(/1994/)).not.toBeInTheDocument()
    })

    it('신청 대회 상자는 대회 · 종목 · 키트 옵션 · 참가비를 보여 준다', async () => {
      renderCheckout()

      const box = await screen.findByRole('region', { name: '신청 대회' })
      expect(await within(box).findByText('2026 용감한 쿠키RUN in 서울')).toBeInTheDocument()
      expect(within(box).getByText(/ · 10K$/)).toBeInTheDocument()
      expect(within(box).getByText('키트 티셔츠 L')).toBeInTheDocument()
      expect(within(box).getByText('참가비')).toBeInTheDocument()
      expect(within(box).getByText('45,000원')).toBeInTheDocument()
    })

    it('예치금이 모자라면 보유 · 쓸 예치금, 충전 수단, 카드로 충전할 금액과 남는 예치금을 보여 준다', async () => {
      server.use(balanceHandler(40_000))
      renderCheckout()

      const deposit = await screen.findByRole('region', { name: '예치금' })
      await within(deposit).findByText('40,000원 (전액)')
      expect(within(deposit).getByText('보유 예치금')).toBeInTheDocument()
      expect(within(deposit).getByText('40,000원')).toBeInTheDocument()
      expect(screen.getByRole('radio', { name: '카드 · 간편결제 (토스페이먼츠)' })).toBeChecked()

      const amount = region('결제 금액')
      expect(within(amount).getByText('45,000원')).toBeInTheDocument()
      expect(within(amount).getByText('-40,000원')).toBeInTheDocument()
      expect(within(amount).getByText('5,000원')).toBeInTheDocument()
      expect(within(amount).getByText('카드로 충전할 금액')).toBeInTheDocument()
      expect(within(amount).getByText('10,000원')).toBeInTheDocument()
      expect(
        within(amount).getByText('최소 1만 원부터 충전해요. 남는 5,000원은 예치금으로 남아요.'),
      ).toBeInTheDocument()
    })

    it('예치금이 넉넉하면 충전 수단 구역과 카드로 충전할 금액 줄이 없다', async () => {
      renderCheckout()

      const amount = await screen.findByRole('region', { name: '결제 금액' })
      const deposit = await screen.findByRole('region', { name: '예치금' })
      await within(deposit).findByText('45,000원')
      expect(within(deposit).queryByText(/전액/)).not.toBeInTheDocument()
      expect(screen.queryByRole('heading', { name: '충전 수단' })).not.toBeInTheDocument()
      expect(within(amount).queryByText('카드로 충전할 금액')).not.toBeInTheDocument()
      expect(within(amount).getByText('-45,000원')).toBeInTheDocument()
      expect(within(amount).getByText('0원')).toBeInTheDocument()
    })

    it('보유 예치금을 전부 쓰면 쓸 예치금 뒤에 (전액)을 붙인다', async () => {
      server.use(balanceHandler(45_000))
      renderCheckout()

      const deposit = await screen.findByRole('region', { name: '예치금' })
      expect(await within(deposit).findByText('45,000원 (전액)')).toBeInTheDocument()
    })

    it('잔액을 못 불러오면 예치금 구역은 값 대신 -를 보여 준다', async () => {
      server.use(
        http.get('*/api/v1/cash/wallets/balance', () =>
          HttpResponse.json({ status: 500, message: '오류', data: null }, { status: 500 }),
        ),
      )
      renderCheckout()

      const deposit = await screen.findByRole('region', { name: '예치금' })
      expect(await within(deposit).findAllByText('-')).toHaveLength(2)
      expect(screen.queryByRole('heading', { name: '충전 수단' })).not.toBeInTheDocument()
    })

    it('결제 동의는 체크 하나와 필수 두 줄, 통신판매중개 고지로 이루어지고 체크 전에는 결제할 수 없다', async () => {
      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
      renderCheckout()

      const pay = await screen.findByRole('button', { name: '45,000원 결제하기' })
      expect(pay).toBeDisabled()
      const consent = region('결제 동의')
      expect(
        await within(consent).findByText(/^\(필수\) .+ 뒤에는 취소해도 환불되지 않아요$/),
      ).toBeInTheDocument()
      expect(within(consent).getByText('(필수) 결제대행 서비스 이용약관 동의')).toBeInTheDocument()
      expect(
        within(consent).getByText(
          '크루런은 통신판매중개자이며 거래 당사자가 아니에요. 대회 운영과 환불 책임은 판매자에게 있어요.',
        ),
      ).toBeInTheDocument()
      await agree(user)
      expect(pay).toBeEnabled()
    })

    it('환불 기한이 없으면 날짜 문장은 빼고 동의 체크와 이용약관 줄만 남기며, 체크하면 결제할 수 있다', async () => {
      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
      const example = productMock.getFindRaceResponseMock()
      server.use(
        productMock.getFindRaceMockHandler({
          ...example,
          data: { ...example.data!, refundDeadline: undefined },
        }),
      )
      renderCheckout()

      const pay = await screen.findByRole('button', { name: '45,000원 결제하기' })
      const consent = region('결제 동의')
      await within(consent).findByText('(필수) 결제대행 서비스 이용약관 동의')
      expect(within(consent).queryByText(/뒤에는 취소해도 환불되지 않아요/)).not.toBeInTheDocument()
      expect(within(consent).queryByText(/-/)).not.toBeInTheDocument()
      expect(pay).toBeDisabled()
      await agree(user)
      expect(pay).toBeEnabled()
    })
  })

  it('?preview=offline 시안은 시안의 남은 시간에서 센다', () => {
    renderCheckout('offline')

    expect(timer()).toHaveTextContent('05:10')
    expect(screen.getByRole('checkbox', { name: '주문 내용 확인 및 결제 동의' })).toBeChecked()
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

  describe('키트 옵션', () => {
    const PAY = '45,000원 결제하기'
    const payBodies = () => {
      const bodies: unknown[] = []
      server.use(
        http.post('*/api/v1/order/orders/:orderId/payment', async ({ request }) => {
          bodies.push(await request.json())
          return HttpResponse.json(orderMock.getPayResponseMock())
        }),
      )
      return bodies
    }
    const chip = (name: string) => screen.getByRole('button', { name })
    const setup = () => userEvent.setup({ advanceTimers: vi.advanceTimersByTime })

    it('키트 옵션이 있는 대회는 옵션 칩을 보여 준다', async () => {
      server.use(kitOptionsHandler(KIT_GROUPS))
      renderCheckout()

      expect(await screen.findByRole('heading', { name: '키트 옵션' })).toBeInTheDocument()
      expect(screen.getByText('기념 티셔츠')).toBeInTheDocument()
      expect(chip('255-280')).toBeInTheDocument()
    })

    it('키트 옵션이 없는 대회는 칸이 없고 결제 버튼이 바로 열린다', async () => {
      const user = setup()
      renderCheckout()

      const pay = await screen.findByRole('button', { name: PAY })
      await agree(user)

      expect(screen.queryByRole('heading', { name: '키트 옵션' })).not.toBeInTheDocument()
      expect(pay).toBeEnabled()
    })

    it('옵션을 다 고르기 전에는 동의해도 결제 버튼이 막힌다', async () => {
      server.use(kitOptionsHandler(KIT_GROUPS))
      const user = setup()
      renderCheckout()

      const pay = await screen.findByRole('button', { name: PAY })
      await screen.findByRole('heading', { name: '키트 옵션' })
      await agree(user)
      expect(pay).toBeDisabled()

      await user.click(chip('L'))
      expect(pay).toBeDisabled()

      await user.click(chip('230-250'))
      expect(pay).toBeEnabled()
    })

    it('고른 옵션을 결제 본문 kitOptionIds로 보낸다', async () => {
      server.use(kitOptionsHandler(KIT_GROUPS))
      const bodies = payBodies()
      const user = setup()
      renderCheckout()

      await user.click(await screen.findByRole('button', { name: 'L' }))
      await user.click(chip('255-280'))
      await agree(user)
      await user.click(screen.getByRole('button', { name: PAY }))

      await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/checkout/complete?orderId=1'))
      expect(bodies).toEqual([{ kitOptionIds: [12, 22] }])
    })

    it('고를 때마다 주문별로 저장해 충전하고 돌아와도 남는다', async () => {
      server.use(kitOptionsHandler(KIT_GROUPS))
      const user = setup()
      renderCheckout()

      await user.click(await screen.findByRole('button', { name: 'L' }))
      expect(readKitOptionIds(1)).toEqual([12])
      await user.click(chip('230-250'))
      expect(readKitOptionIds(1)).toEqual([12, 21])
      // 같은 옵션 이름에서 다시 고르면 바꾼다
      await user.click(chip('M'))
      expect(readKitOptionIds(1)).toEqual([11, 21])
    })

    it('개인 예매에서 이미 고른 값은 미리 채워져 있고 바로 결제할 수 있다', async () => {
      saveKitOptionIds(1, [12, 21])
      server.use(kitOptionsHandler(KIT_GROUPS))
      const bodies = payBodies()
      const user = setup()
      renderCheckout()

      const pay = await screen.findByRole('button', { name: PAY })
      await screen.findByRole('heading', { name: '키트 옵션' })
      expect(chip('L')).toHaveAttribute('aria-pressed', 'true')
      expect(chip('M')).toHaveAttribute('aria-pressed', 'false')
      expect(chip('230-250')).toHaveAttribute('aria-pressed', 'true')

      await agree(user)
      await user.click(pay)
      await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/checkout/complete?orderId=1'))
      expect(bodies).toEqual([{ kitOptionIds: [12, 21] }])
    })

    it('충전하고 돌아와 자동으로 다시 결제할 때 저장해 둔 키트 옵션을 본문에 싣는다', async () => {
      saveKitOptionIds(1, [12, 22])
      server.use(kitOptionsHandler(KIT_GROUPS))
      const bodies = payBodies()
      renderCheckout(undefined, { charged: true })

      await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/checkout/complete?orderId=1'))
      expect(bodies).toEqual([{ kitOptionIds: [12, 22] }])
    })

    it('저장된 값에 목록에 없는 옵션이나 같은 이름의 중복이 있어도 화면에 보이는 선택만 보낸다', async () => {
      saveKitOptionIds(1, [99, 11, 12, 21])
      server.use(kitOptionsHandler(KIT_GROUPS))
      const bodies = payBodies()
      const user = setup()
      renderCheckout()

      const pay = await screen.findByRole('button', { name: PAY })
      await screen.findByRole('heading', { name: '키트 옵션' })
      await agree(user)
      await user.click(pay)

      await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/checkout/complete?orderId=1'))
      // 같은 이름에 둘이면 먼저 나온 값(M)만 고른 것으로 본다
      expect(bodies).toEqual([{ kitOptionIds: [11, 21] }])
    })

    it('키트 옵션 목록을 불러오지 못하면 결제 버튼을 막고 다시 시도하게 한다', async () => {
      let fail = true
      server.use(
        http.get('*/api/v1/product/races/:raceId/kit-options', () =>
          fail
            ? HttpResponse.json({ status: 500, message: '오류', data: null }, { status: 500 })
            : HttpResponse.json({
                status: 200,
                message: '',
                data: { raceId: 1, kitOptions: KIT_GROUPS },
              }),
        ),
      )
      const user = setup()
      renderCheckout()

      const pay = await screen.findByRole('button', { name: PAY })
      await agree(user)
      expect(await screen.findByText('키트 옵션을 불러오지 못했어요')).toBeInTheDocument()
      expect(pay).toBeDisabled()

      fail = false
      await user.click(screen.getByRole('button', { name: '다시 시도하기' }))
      expect(await screen.findByRole('button', { name: 'L' })).toBeInTheDocument()
      expect(screen.queryByText('키트 옵션을 불러오지 못했어요')).not.toBeInTheDocument()
    })

    it.each([
      [400, 'ORDER_KIT_OPTION_INVALID', '키트 옵션을 옵션 이름마다 하나씩 골라 주세요.'],
      [409, 'ORDER_KIT_OPTION_SOLD_OUT', '고른 키트 옵션이 모두 소진됐어요.'],
    ])(
      '%i %s이면 결제 화면에 남아 서버 문구를 키트 칸 위 안내로 보여 준다',
      async (status, code, message) => {
        saveKitOptionIds(1, [11, 21])
        server.use(
          kitOptionsHandler(KIT_GROUPS),
          payHandler(() => HttpResponse.json({ status, code, message, data: null }, { status })),
        )
        const user = setup()
        renderCheckout()

        const pay = await screen.findByRole('button', { name: PAY })
        await screen.findByRole('heading', { name: '키트 옵션' })
        await agree(user)
        await user.click(pay)

        const alert = await screen.findByRole('alert')
        expect(alert).toHaveTextContent(message)
        // 실패 화면으로 넘어가지 않고 칩을 다시 고를 수 있다
        expect(screen.queryByText('결제하지 못했어요')).not.toBeInTheDocument()
        expect(screen.queryByText('남은 자리가 없어요')).not.toBeInTheDocument()
        expect(chip('M')).toBeInTheDocument()
        // 안내는 키트 칸 제목과 칩 사이에 있다
        const heading = screen.getByRole('heading', { name: '키트 옵션' })
        expect(
          heading.compareDocumentPosition(alert) & Node.DOCUMENT_POSITION_FOLLOWING,
        ).toBeTruthy()
        expect(
          alert.compareDocumentPosition(chip('M')) & Node.DOCUMENT_POSITION_FOLLOWING,
        ).toBeTruthy()

        // 다시 고르면 안내가 사라진다
        await user.click(chip('L'))
        expect(screen.queryByRole('alert')).not.toBeInTheDocument()
      },
    )
  })
})

describe('결제 화면 · 입장 해제', () => {
  let released: string[]
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    vi.setSystemTime(NOW)
    released = []
    requestCharge.mockReset()
    requestCharge.mockResolvedValue('redirecting')
    server.use(
      orderMock.getFindById1MockHandler(orderResponse()),
      orderMock.getFindByIdMockHandler(participationResponse()),
      balanceHandler(100_000),
      kitOptionsHandler([]),
      http.delete('*/api/v1/queue/races/:raceId/racecourses/:courseId/active', ({ params }) => {
        released.push(`${params.raceId}/${params.courseId}`)
        return HttpResponse.json({ status: 200, message: '', data: null })
      }),
    )
    window.history.pushState({}, '', '/checkout')
  })
  afterEach(() => {
    // 같은 입장 안의 주소에서 화면을 내려, 남은 화면이 다음 테스트에 해제를 흘리지 않게 한다
    window.history.pushState({}, '', '/checkout')
    cleanup()
    vi.useRealTimers()
    window.history.pushState({}, '', '/')
  })

  it('결제 화면에서 다른 화면으로 나가면 해제를 부른다', async () => {
    renderCheckout()
    await screen.findByRole('timer')
    window.history.pushState({}, '', '/me/tickets')
    cleanup()
    await vi.waitFor(() => expect(released).toEqual(['1/1']))
  })

  it('탭을 닫으면(pagehide) 해제를 부른다', async () => {
    renderCheckout()
    await screen.findByRole('timer')
    window.dispatchEvent(new Event('pagehide'))
    await vi.waitFor(() => expect(released).toEqual(['1/1']))
  })

  it('이미 결제한 주문은 해제하지 않는다 (결제 뒤는 주문 쪽이 맡는다)', async () => {
    server.use(orderMock.getFindById1MockHandler(orderResponse({ status: 'PAID' })))
    renderCheckout()
    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/checkout/complete?orderId=1'))
    window.history.pushState({}, '', '/checkout/complete')
    cleanup()
    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(released).toEqual([])
  })

  it('결제 직후 주문을 다시 받기 전에 완료 화면으로 가도 해제하지 않는다', async () => {
    // 결제는 PAID로 답하지만 주문 조회는 결제 대기 그대로다 (재조회 실패와 같은 상태)
    server.use(
      payHandler(() =>
        HttpResponse.json({
          status: 200,
          message: '',
          data: orderResponse({ status: 'PAID' }).data,
        }),
      ),
    )
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderCheckout()
    const pay = await screen.findByRole('button', { name: '45,000원 결제하기' })
    await agree(user)
    await user.click(pay)
    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/checkout/complete?orderId=1'))
    window.history.pushState({}, '', '/checkout/complete')
    cleanup()
    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(released).toEqual([])
  })

  it('토스 충전창으로 떠나는 중에는 pagehide에도 해제하지 않는다', async () => {
    server.use(
      balanceHandler(40_000),
      http.post('*/api/v1/order/orders/:orderId/payment', () =>
        conflict('ORDER_INSUFFICIENT_BALANCE', { shortage: 5000 }),
      ),
    )
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderCheckout()
    const button = await screen.findByRole('button', { name: '10,000원 충전하고 결제하기' })
    await agree(user)
    await user.click(button)
    await vi.waitFor(() => expect(requestCharge).toHaveBeenCalled())
    window.dispatchEvent(new Event('pagehide'))
    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(released).toEqual([])
  })
})
