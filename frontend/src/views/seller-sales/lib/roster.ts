import type { orderModel } from '@/shared/api'

export type StatusTone = 'default' | 'muted' | 'success' | 'danger'
type Status = { label: string; tone: StatusTone }

// 시안 2070:66595 상태 칸. 참가 확정은 초록, 취소는 빨강, 결제 대기는 기본 글자색
export const ORDER_STATUS: Record<orderModel.SellerOrderDtoStatus, Status> = {
  ORDERED: { label: '결제 대기', tone: 'default' },
  PAID: { label: '참가 확정', tone: 'success' },
  CANCELING: { label: '취소 중', tone: 'danger' },
  CANCELED: { label: '취소', tone: 'danger' },
}

// 시안 2070:66774 상태 칸. 결제 중은 작은 회색 글자, 기한이 지나 끝난 파티(종료)는 신청 안 됨
export const PARTY_STATUS: Record<orderModel.SellerPartyDtoStatus, Status> = {
  RECRUITING: { label: '모집 중', tone: 'muted' },
  PAYING: { label: '결제 중', tone: 'muted' },
  PAID: { label: '참가 확정', tone: 'success' },
  CLOSED: { label: '신청 안 됨', tone: 'danger' },
}

export function partyNo(partyId: number) {
  return `P-${partyId}`
}

export function applyMethod(partyId?: number | null) {
  return partyId ? `파티 ${partyNo(partyId)}` : '개인'
}

/** 티셔츠 M · 양말 255-280. 옵션이 없던 옛 주문은 티셔츠 크기만 있다 */
export function kitOptionText(order: Pick<orderModel.SellerOrderDto, 'kitOptions' | 'tshirtSize'>) {
  const options = (order.kitOptions ?? []).map((o) => [o.name, o.value].filter(Boolean).join(' '))
  if (options.length > 0) return options.join(' · ')
  return order.tshirtSize ? `티셔츠 ${order.tshirtSize}` : '-'
}

/** 찾기 칸의 P-1023 · p1023 · 1023 → 1023. 숫자가 아니면 undefined */
export function parsePartyNo(text: string) {
  const match = /^p-?(\d+)$/i.exec(text.trim()) ?? /^(\d+)$/.exec(text.trim())
  const id = match ? Number(match[1]) : 0
  // 서버는 Long으로 받는다. 정확히 셀 수 없는 큰 수는 맞는 파티가 없는 것으로 본다
  return Number.isSafeInteger(id) && id > 0 ? id : undefined
}
