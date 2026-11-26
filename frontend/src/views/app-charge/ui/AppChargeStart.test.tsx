import { render, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { AppChargeStart } from './AppChargeStart'

const requestPayment = vi.fn<(arg: unknown) => Promise<unknown>>()
const payment = vi.fn<(arg: unknown) => { requestPayment: typeof requestPayment }>(() => ({
  requestPayment,
}))
const loadTossPayments = vi.fn<(key: string) => Promise<unknown>>()
vi.mock('@tosspayments/tosspayments-sdk', () => ({
  loadTossPayments: (key: string) => loadTossPayments(key),
}))
vi.mock('@/shared/config', () => ({ clientEnv: { NEXT_PUBLIC_TOSS_CLIENT_KEY: 'test_ck_x' } }))

const query = {
  orderId: 'charge-abc123',
  amount: 12_000,
  orderName: '크루런 예치금 충전',
  customerKey: 'cust-1',
}

beforeEach(() => {
  requestPayment.mockReset().mockResolvedValue(undefined)
  payment.mockClear()
  loadTossPayments.mockReset().mockResolvedValue({ payment })
})

describe('AppChargeStart', () => {
  it('쿼리 값으로 토스 결제창을 열고, 성공·실패 주소는 코드에 고정된 이 사이트 주소다', async () => {
    const navigate = vi.fn()
    render(<AppChargeStart query={query} navigate={navigate} />)

    await waitFor(() => expect(requestPayment).toHaveBeenCalledTimes(1))
    expect(loadTossPayments).toHaveBeenCalledWith('test_ck_x')
    expect(payment).toHaveBeenCalledWith({ customerKey: 'cust-1' })
    expect(requestPayment).toHaveBeenCalledWith({
      method: 'CARD',
      amount: { currency: 'KRW', value: 12_000 },
      orderId: 'charge-abc123',
      orderName: '크루런 예치금 충전',
      successUrl: `${window.location.origin}/app-charge/done`,
      failUrl: `${window.location.origin}/app-charge/fail`,
    })
    expect(navigate).not.toHaveBeenCalled()
  })

  it('쿼리가 올바르지 않으면 토스를 열지 않고 실패 주소로 보낸다', async () => {
    const navigate = vi.fn()
    render(<AppChargeStart query={undefined} navigate={navigate} />)

    await waitFor(() => expect(navigate).toHaveBeenCalledTimes(1))
    expect(navigate.mock.calls[0][0]).toMatch(/^\/app-charge\/fail\?.*code=INVALID_REQUEST/)
    expect(loadTossPayments).not.toHaveBeenCalled()
  })

  it('토스 SDK를 못 불러오면 실패 주소로 보낸다', async () => {
    loadTossPayments.mockRejectedValue(new Error('network'))
    const navigate = vi.fn()
    render(<AppChargeStart query={query} navigate={navigate} />)

    await waitFor(() => expect(navigate).toHaveBeenCalledTimes(1))
    expect(navigate.mock.calls[0][0]).toContain('code=SDK_LOAD_FAILED')
    expect(navigate.mock.calls[0][0]).toContain('orderId=charge-abc123')
  })

  it('사용자가 결제창을 닫으면 토스가 준 코드로 실패 주소를 보낸다', async () => {
    requestPayment.mockRejectedValue({ code: 'USER_CANCEL', message: '취소했어요' })
    const navigate = vi.fn()
    render(<AppChargeStart query={query} navigate={navigate} />)

    await waitFor(() => expect(navigate).toHaveBeenCalledTimes(1))
    expect(navigate.mock.calls[0][0]).toContain('code=USER_CANCEL')
  })

  it('다시 그려져도 결제창을 한 번만 연다', async () => {
    const { rerender } = render(<AppChargeStart query={query} navigate={vi.fn()} />)
    rerender(<AppChargeStart query={{ ...query }} navigate={vi.fn()} />)

    await waitFor(() => expect(requestPayment).toHaveBeenCalled())
    expect(requestPayment).toHaveBeenCalledTimes(1)
  })
})
