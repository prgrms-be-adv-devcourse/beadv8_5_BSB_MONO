type Params = Record<string, string | string[] | undefined>

/** 모바일 앱이 충전 결과를 받는 주소. 쿼리로 바꿀 수 없게 코드에 고정한다 (다른 사이트로 보내는 데 악용되지 않게) */
const APP_RETURN_URL = 'crewrun://deposit/charge-return'

const text = (v: string | string[] | undefined) =>
  typeof v === 'string' && v !== '' ? v : undefined
const positiveInt = (v: string | undefined) =>
  v !== undefined && /^[1-9]\d*$/.test(v) ? Number(v) : undefined

export type ChargeQuery = {
  orderId: string
  amount: number
  orderName: string
  customerKey: string
}

/** 앱이 만든 충전 요청 값. 하나라도 빠졌거나 금액이 숫자가 아니면 undefined */
export function parseChargeQuery(params: Params): ChargeQuery | undefined {
  const orderId = text(params.orderId)
  const orderName = text(params.orderName)
  const customerKey = text(params.customerKey)
  const amount = positiveInt(text(params.amount))
  if (!orderId || !orderName || !customerKey || amount === undefined) return undefined
  return { orderId, amount, orderName, customerKey }
}

function appUrl(entries: Record<string, string | undefined>) {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(entries)) if (value !== undefined) query.set(key, value)
  return `${APP_RETURN_URL}?${query}`
}

/** 토스 성공 주소로 돌아온 값을 앱 주소로 바꾼다. 값이 이상하면 성공으로 넘기지 않는다 */
export function appDoneUrl(params: Params) {
  const paymentKey = text(params.paymentKey)
  const orderId = text(params.orderId)
  const amount = text(params.amount)
  if (!paymentKey || !orderId || positiveInt(amount) === undefined) {
    return appFailUrl({ code: 'INVALID_RETURN', message: '결제 결과를 확인할 수 없어요', orderId })
  }
  return appUrl({ paymentKey, orderId, amount })
}

export function appFailUrl(params: Params) {
  return appUrl({
    code: text(params.code) ?? 'UNKNOWN',
    message: text(params.message),
    orderId: text(params.orderId),
  })
}

/** 이 사이트의 실패 페이지. 토스를 못 열 때 여기로 보낸다 */
export function failPath(code: string, message: string, orderId?: string) {
  const query = new URLSearchParams({ code, message })
  if (orderId) query.set('orderId', orderId)
  return `/app-charge/fail?${query}`
}
