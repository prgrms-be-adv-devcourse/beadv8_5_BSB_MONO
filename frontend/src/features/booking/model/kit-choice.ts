/**
 * 참가 신청에서 고른 키트 옵션 ID. 결제(POST /orders/{orderId}/payment)가 kitOptionIds로 보낸다.
 * 결제 전 예치금 충전은 토스 결제창으로 페이지를 떠났다 돌아오므로 메모리 스토어로는 남지 않는다.
 * 그래서 주문마다 sessionStorage에 남긴다 (새로고침해도 같은 탭에서는 남는다).
 */
const key = (orderId: number) => `crewrun:kit-options:${orderId}`

export function saveKitOptionIds(orderId: number, kitOptionIds: number[]) {
  try {
    sessionStorage.setItem(key(orderId), JSON.stringify(kitOptionIds))
  } catch {
    // 저장소를 쓸 수 없으면(사생활 보호 모드 등) 결제에 옵션을 못 보내 서버가 400으로 알려 준다
  }
}

/** 남겨 둔 키트 옵션 ID. 없거나 깨졌으면 빈 목록 */
export function readKitOptionIds(orderId: number): number[] {
  try {
    const parsed: unknown = JSON.parse(sessionStorage.getItem(key(orderId)) ?? '[]')
    return Array.isArray(parsed) ? parsed.filter((id) => Number.isInteger(id) && id > 0) : []
  } catch {
    return []
  }
}

export function forgetKitOptionIds(orderId: number) {
  try {
    sessionStorage.removeItem(key(orderId))
  } catch {
    // 지울 것이 없다
  }
}
