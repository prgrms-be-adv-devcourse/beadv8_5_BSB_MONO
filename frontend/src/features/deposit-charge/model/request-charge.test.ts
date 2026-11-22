import { beforeEach, describe, expect, it, vi } from 'vitest'

import { readPendingCharge } from '../lib/pending-charge'
import { chargeAmountFor, requestCharge } from './request-charge'

const requestPayment = vi.fn<() => Promise<unknown>>()
vi.mock('@tosspayments/tosspayments-sdk', () => ({
  ANONYMOUS: 'ANONYMOUS',
  loadTossPayments: async () => ({ payment: () => ({ requestPayment }) }),
}))

const env = (
  patch: { mocking?: boolean; clientKey?: string; historyLengthNow?: () => number } = {},
) => ({
  mocking: false,
  clientKey: undefined,
  origin: 'https://crewrun.test',
  historyLength: 3,
  historyLengthNow: () => 3,
  navigate: vi.fn<(url: string) => void>(),
  goBack: vi.fn<(steps: number) => void>(),
  ...patch,
})

beforeEach(() => sessionStorage.clear())

describe('chargeAmountFor', () => {
  it('1만 원보다 적게 모자라면 1만 원, 그 이상이면 모자란 만큼 충전한다', () => {
    expect(chargeAmountFor(0)).toBe(10_000)
    expect(chargeAmountFor(5_000)).toBe(10_000)
    expect(chargeAmountFor(12_000)).toBe(12_000)
  })

  it('1회 충전 한도(30만 원)를 넘게 모자라면 30만 원까지만 충전한다', () => {
    expect(chargeAmountFor(300_000)).toBe(300_000)
    expect(chargeAmountFor(300_001)).toBe(300_000)
    expect(chargeAmountFor(450_000)).toBe(300_000)
  })
})

describe('requestCharge', () => {
  it('가짜 응답 모드에서는 결제창 대신 성공 주소로 바로 보내고, 충전 orderId · 금액 · 돌아갈 주문을 적어 둔다', async () => {
    const mock = env({ mocking: true })

    await expect(requestCharge({ amount: 12_000, returnOrderId: 1 }, mock)).resolves.toBe(
      'redirecting',
    )

    const pending = readPendingCharge()
    expect(pending).toMatchObject({ amount: 12_000, returnOrderId: 1 })
    expect(pending?.orderId).toMatch(/^charge-[0-9a-f-]{36}$/)

    const url = new URL(mock.navigate.mock.calls[0][0])
    expect(url.origin + url.pathname).toBe('https://crewrun.test/me/deposit/charge/return')
    expect(url.searchParams.get('result')).toBe('success')
    // 돌아갈 주문은 주소에 싣지 않는다 (sessionStorage 기록으로만 정한다)
    expect(url.searchParams.get('returnOrderId')).toBeNull()
    expect(url.searchParams.get('orderId')).toBe(pending?.orderId)
    expect(url.searchParams.get('amount')).toBe('12000')
    expect(url.searchParams.get('paymentKey')).toMatch(/^mock-payment-/)
  })

  it('누를 때마다 새 충전 orderId를 만든다 (확정 전 재시도는 돌아온 화면이 같은 값을 쓴다)', async () => {
    const mock = env({ mocking: true })
    await requestCharge({ amount: 10_000 }, mock)
    const first = readPendingCharge()?.orderId
    await requestCharge({ amount: 10_000 }, mock)

    expect(readPendingCharge()?.orderId).not.toBe(first)
  })

  it('떠나기 직전의 기록 길이를 충전 기록에 남긴다 (돌아와 그만큼 거슬러 가려고)', async () => {
    await requestCharge({ amount: 10_000 }, env({ mocking: true }))

    expect(readPendingCharge()?.historyLength).toBe(3)
  })

  it('클라이언트 키가 없으면 결제창을 띄우지 않고 실패한다', async () => {
    const real = env()

    await expect(requestCharge({ amount: 10_000 }, real)).rejects.toThrow(
      'NEXT_PUBLIC_TOSS_CLIENT_KEY',
    )
    expect(real.navigate).not.toHaveBeenCalled()
  })

  it('결과를 모르는 충전이 10분 안에 있으면 결제창을 띄우지 않고, 그 충전 기록도 덮어쓰지 않는다', async () => {
    const prior = { orderId: 'charge-prior', amount: 12_000, uncertainAt: Date.now() - 60_000 }
    sessionStorage.setItem('crewrun:pending-charge', JSON.stringify(prior))
    const mock = env({ mocking: true })

    await expect(requestCharge({ amount: 10_000 }, mock)).resolves.toBe('held')

    expect(mock.navigate).not.toHaveBeenCalled()
    expect(readPendingCharge()).toEqual(prior)
  })

  it('결과를 모르는 충전이 10분보다 오래됐으면 새로 충전한다', async () => {
    const prior = { orderId: 'charge-prior', amount: 12_000, uncertainAt: Date.now() - 11 * 60_000 }
    sessionStorage.setItem('crewrun:pending-charge', JSON.stringify(prior))
    const mock = env({ mocking: true })

    await expect(requestCharge({ amount: 10_000 }, mock)).resolves.toBe('redirecting')
    expect(readPendingCharge()?.orderId).not.toBe('charge-prior')
  })

  describe('결제창을 닫아 취소했을 때', () => {
    beforeEach(() => {
      requestPayment.mockReset()
    })
    const cancel = () =>
      requestPayment.mockImplementation(() => Promise.reject({ code: 'USER_CANCEL' }))

    it('결제창이 쌓은 기록만큼 되감아 뒤로 가기 한 번이면 이전 화면으로 가게 한다', async () => {
      cancel()
      const real = env({ clientKey: 'test_ck_x', historyLengthNow: () => 4 })

      await expect(requestCharge({ amount: 10_000 }, real)).resolves.toBe('canceled')

      expect(real.goBack).toHaveBeenCalledExactlyOnceWith(1)
    })

    it('기록이 늘지 않았으면 되감지 않는다', async () => {
      cancel()
      const real = env({ clientKey: 'test_ck_x' })

      await expect(requestCharge({ amount: 10_000 }, real)).resolves.toBe('canceled')

      expect(real.goBack).not.toHaveBeenCalled()
    })

    it('기록 길이가 상한(50)에 닿아 셀 수 없으면 되감지 않는다', async () => {
      cancel()
      const real = env({ clientKey: 'test_ck_x', historyLengthNow: () => 50 })

      await requestCharge({ amount: 10_000 }, real)

      expect(real.goBack).not.toHaveBeenCalled()
    })

    it('취소가 아닌 오류는 되감지 않고 그대로 던진다', async () => {
      requestPayment.mockImplementation(() => Promise.reject({ code: 'INVALID_CARD' }))
      const real = env({ clientKey: 'test_ck_x', historyLengthNow: () => 4 })

      await expect(requestCharge({ amount: 10_000 }, real)).rejects.toEqual({
        code: 'INVALID_CARD',
      })

      expect(real.goBack).not.toHaveBeenCalled()
    })
  })
})
