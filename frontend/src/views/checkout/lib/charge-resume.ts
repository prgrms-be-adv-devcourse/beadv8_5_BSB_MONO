/**
 * 이 주문의 결제 중 충전으로 토스 결제창에 다녀오는 중이라는 표시. 충전하러 떠나기 직전에 sessionStorage에 남긴다.
 * 주소의 `charged=1`은 누구나 붙일 수 있어서 믿지 않고, 이 표시가 있을 때만 돌아와 저절로 다시 결제한다.
 * 화면이 열릴 때 한 번 읽고 바로 지우므로 새로고침이나 다시 연 주소로는 다시 결제하지 않는다.
 */
const KEY = 'crewrun:checkout-charge-resume'

/** 결제 기한(10분)이 지난 표시는 믿지 않는다. 충전하러 떠난 채 돌아오지 않은 표시가 오래 남지 않게 한다 */
const MAX_AGE_MS = 10 * 60_000

export function markChargeResume(orderId: number) {
  try {
    sessionStorage.setItem(KEY, `${orderId}:${Date.now()}`)
  } catch {
    // 저장이 막혀 있으면 돌아와도 저절로 결제하지 않고 사용자가 직접 누른다
  }
}

export function hasChargeResume(orderId: number) {
  try {
    const [id, at] = (sessionStorage.getItem(KEY) ?? '').split(':')
    return id === String(orderId) && Date.now() - Number(at) < MAX_AGE_MS
  } catch {
    return false
  }
}

export function clearChargeResume() {
  try {
    sessionStorage.removeItem(KEY)
  } catch {
    // 지울 수 없어도 다음 충전이 덮어쓴다
  }
}
