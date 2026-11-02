import { clientEnv } from '@/shared/config'
import { trackEvent } from '@/shared/lib'

import { savePendingCharge } from '../lib/pending-charge'

/** 정책 4장 예치금-1: 충전 최소 금액 */
export const MIN_CHARGE = 10_000

/** 결제 중 모자란 금액으로 충전할 금액. 1만 원보다 적게 모자라면 1만 원을 충전한다. */
export function chargeAmountFor(shortage: number) {
  return Math.max(shortage, MIN_CHARGE)
}

/** 토스 결제창이 성공·실패 뒤 돌아오는 화면 */
export const CHARGE_RETURN_PATH = '/me/deposit/charge/return'

/** 결제창 이름에 보이는 주문명 */
const ORDER_NAME = '크루런 예치금 충전'

/** 토스가 결제창을 닫았을 때 던지는 코드. 결제를 포기한 것이라 실패가 아니다. */
const CANCEL_CODES = new Set(['USER_CANCEL', 'PAY_PROCESS_CANCELED'])

export function isChargeCanceled(code: string | undefined) {
  return code !== undefined && CANCEL_CODES.has(code)
}

export type ChargeRequest = {
  amount: number
  /** 결제 중 충전이면 그 주문. 돌아오면 같은 주문으로 다시 결제한다 */
  returnOrderId?: number
}

type Env = {
  mocking: boolean
  clientKey: string | undefined
  origin: string
  navigate: (url: string) => void
}

const browserEnv = (): Env => ({
  mocking: clientEnv.NEXT_PUBLIC_API_MOCKING === 'on',
  clientKey: clientEnv.NEXT_PUBLIC_TOSS_CLIENT_KEY,
  origin: window.location.origin,
  navigate: (url) => window.location.assign(url),
})

// 돌아갈 주문은 주소에 싣지 않고 sessionStorage의 충전 기록으로만 정한다 (주소를 고쳐도 다른 주문으로 가지 않게)
function returnUrl(origin: string, result: 'success' | 'fail') {
  return `${origin}${CHARGE_RETURN_PATH}?result=${result}`
}

/**
 * 토스페이먼츠 결제창(API 개별 연동, 카드)으로 충전을 요청한다.
 * 충전 orderId(= 충전 확정 API 멱등키)는 여기서 한 번만 만들고, 돌아온 화면이 그 값으로 확정한다.
 * 결제창이 페이지를 떠나므로 성공하면 돌아오지 않는다. 사용자가 결제창을 닫으면 'canceled'.
 * 가짜 응답 모드에서는 결제창을 띄울 수 없어, 결제창이 성공했을 때와 같은 주소로 바로 보낸다.
 */
export async function requestCharge(
  { amount, returnOrderId }: ChargeRequest,
  env: Env = browserEnv(),
): Promise<'redirecting' | 'canceled'> {
  trackEvent('deposit_charge_start', { amount, duringCheckout: returnOrderId !== undefined })
  const orderId = `charge-${crypto.randomUUID()}`
  savePendingCharge({ orderId, amount, returnOrderId })
  const successUrl = returnUrl(env.origin, 'success')
  const failUrl = returnUrl(env.origin, 'fail')

  if (env.mocking) {
    const params = new URLSearchParams({
      paymentType: 'NORMAL',
      paymentKey: `mock-payment-${crypto.randomUUID()}`,
      orderId,
      amount: String(amount),
    })
    env.navigate(`${successUrl}&${params}`)
    return 'redirecting'
  }

  if (!env.clientKey)
    throw new Error('NEXT_PUBLIC_TOSS_CLIENT_KEY가 없어 결제창을 띄울 수 없습니다.')
  const { ANONYMOUS, loadTossPayments } = await import('@tosspayments/tosspayments-sdk')
  const toss = await loadTossPayments(env.clientKey)
  try {
    await toss.payment({ customerKey: ANONYMOUS }).requestPayment({
      method: 'CARD',
      amount: { currency: 'KRW', value: amount },
      orderId,
      orderName: ORDER_NAME,
      successUrl,
      failUrl,
    })
  } catch (error) {
    const code = (error as { code?: unknown } | null)?.code
    if (typeof code === 'string' && isChargeCanceled(code)) return 'canceled'
    throw error
  }
  return 'redirecting'
}
