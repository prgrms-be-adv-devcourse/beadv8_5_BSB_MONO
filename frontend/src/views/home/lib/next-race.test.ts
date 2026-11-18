import { describe, expect, it } from 'vitest'

import type { MyTicket } from '@/entities/ticket'
import type { productModel } from '@/shared/api'

import { pickNextRace } from './next-race'

const NOW = new Date('2026-10-15T12:00:00+09:00')

function ticket(id: number, raceId: number, overrides: Partial<MyTicket> = {}): MyTicket {
  return {
    id: `${id}-${id}`,
    orderId: id,
    orderItemId: id,
    orderNo: `NO${id}`,
    raceId,
    raceCourseId: raceId * 10,
    participationId: id,
    itemName: '',
    amount: 50000,
    itemStatus: 'PAID',
    paying: false,
    ...overrides,
  }
}

function race(raceDate?: string): productModel.RaceDetailDto {
  return { raceDate } as productModel.RaceDetailDto
}

describe('pickNextRace', () => {
  it('결제한 참가권 중 대회일이 가장 가까운 것을 고른다', () => {
    const picked = pickNextRace(
      [ticket(1, 1), ticket(2, 2)],
      new Map([
        [1, race('2026-11-20T08:00:00')],
        [2, race('2026-11-02T08:00:00')],
      ]),
      NOW,
    )
    expect(picked?.ticket.raceId).toBe(2)
  })

  it('오늘 열리는 대회도 다음 대회다', () => {
    const picked = pickNextRace([ticket(1, 1)], new Map([[1, race('2026-10-15T08:00:00')]]), NOW)
    expect(picked?.ticket.raceId).toBe(1)
  })

  it('대회일이 지났거나 취소했거나 결제 확인 중이면 고르지 않는다', () => {
    const picked = pickNextRace(
      [ticket(1, 1), ticket(2, 2, { itemStatus: 'CANCELED' }), ticket(3, 3, { paying: true })],
      new Map([
        [1, race('2026-10-01T08:00:00')],
        [2, race('2026-11-02T08:00:00')],
        [3, race('2026-11-03T08:00:00')],
      ]),
      NOW,
    )
    expect(picked).toBeNull()
  })

  it('대회 정보를 아직 못 받았거나 대회일이 없으면 고르지 않는다', () => {
    expect(pickNextRace([ticket(1, 1), ticket(2, 2)], new Map([[2, race()]]), NOW)).toBeNull()
  })

  it('참가권이 없으면 null이다', () => {
    expect(pickNextRace([], new Map(), NOW)).toBeNull()
  })
})
