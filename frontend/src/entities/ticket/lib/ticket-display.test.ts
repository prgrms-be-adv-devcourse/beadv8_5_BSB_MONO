import { describe, expect, it } from 'vitest'

import { maskPhone, ticketNumber, ticketStatus } from './ticket-display'
import { parseTicketId, toTicketId } from './ticket-id'

describe('ticket-id', () => {
  it('주문 ID와 주문 상품 ID를 묶고 다시 푼다', () => {
    expect(toTicketId(12, 3)).toBe('12-3')
    expect(parseTicketId('12-3')).toEqual({ orderId: 12, orderItemId: 3 })
  })

  it('모양이 다르면 null', () => {
    expect(parseTicketId('R-20261005-0012')).toBeNull()
    expect(parseTicketId('0-1')).toBeNull()
  })
})

describe('ticket-display', () => {
  it('취소 > 종료 > 참가 확정 순으로 상태를 정한다', () => {
    expect(ticketStatus({ itemStatus: 'CANCELED' }, { raceOver: false })).toBe('canceled')
    expect(ticketStatus({ itemStatus: 'PAID' }, { raceOver: true })).toBe('finished')
    expect(ticketStatus({ itemStatus: 'PAID' }, { raceOver: false })).toBe('confirmed')
  })

  it('결제 기한이 있는 참가권(결제 대기 주문)은 결제 대기다', () => {
    expect(
      ticketStatus({ itemStatus: 'ORDERED', dueAt: '2026-10-15T12:30:00' }, { raceOver: false }),
    ).toBe('pending')
  })

  it('결제 중인 주문의 참가권은 대회 날짜와 상관없이 결제 확인 중이다', () => {
    expect(ticketStatus({ itemStatus: 'ORDERED', paying: true }, { raceOver: false })).toBe(
      'paying',
    )
    expect(ticketStatus({ itemStatus: 'ORDERED', paying: true }, { raceOver: true })).toBe('paying')
  })

  it('주문번호 끝자리를 번호로 쓴다', () => {
    expect(ticketNumber('OD-20261012-0001')).toBe('0001')
  })

  it('연락처 가운데 끝 두 자리를 가린다', () => {
    expect(maskPhone('010-9876-5432')).toBe('010-98**-5432')
    expect(maskPhone('01098765432')).toBe('010-98**-5432')
  })
})
