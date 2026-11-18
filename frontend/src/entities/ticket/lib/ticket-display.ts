import type { MyTicket } from '../model/types'

export type TicketStatus = 'paying' | 'confirmed' | 'finished' | 'canceled'

export const TICKET_STATUS: Record<TicketStatus, { label: string; tone: 'neutral' | 'brand' }> = {
  // 피그마 V3 · M20 주문 내역 · 결제 확인 중 배지 (결제 대기와 같은 정보 톤)
  paying: { label: '결제 확인 중', tone: 'neutral' },
  confirmed: { label: '참가 확정', tone: 'brand' },
  finished: { label: '종료', tone: 'neutral' },
  canceled: { label: '취소', tone: 'neutral' },
}

/** 결제 확인 중 주문이면 결제 확인 중, 취소한 주문 상품이면 취소, 대회 날짜가 지났으면 종료, 아니면 참가 확정 */
export function ticketStatus(
  ticket: Pick<MyTicket, 'itemStatus'> & { paying?: boolean },
  { raceOver }: { raceOver: boolean },
): TicketStatus {
  if (ticket.paying) return 'paying'
  if (ticket.itemStatus === 'CANCELED') return 'canceled'
  return raceOver ? 'finished' : 'confirmed'
}

/**
 * 배번 자리에 보여 줄 번호. 배번은 아직 API가 없어 주문번호 끝자리(OD-20261012-0001 → 0001)를 쓴다.
 * TODO(api): 배번 배정 API가 생기면 바꾼다.
 */
export function ticketNumber(orderNo: string): string {
  return orderNo.split('-').at(-1) || orderNo
}

/** 010-9876-5432 → 010-98**-5432 */
export function maskPhone(phone: string): string {
  const digits = phone.replace(/\D/g, '')
  const match = /^(\d{3})(\d{3,4})(\d{4})$/.exec(digits)
  if (!match) return phone
  const [, head, middle, tail] = match
  return `${head}-${middle.slice(0, -2)}**-${tail}`
}
