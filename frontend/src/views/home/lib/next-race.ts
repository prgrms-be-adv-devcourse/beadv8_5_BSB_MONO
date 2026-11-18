import { daysUntil } from '@/entities/race'
import type { MyTicket } from '@/entities/ticket'
import type { productModel } from '@/shared/api'

type Race = productModel.RaceDetailDto

/** 결제를 마친 참가권만 다음 대회로 센다 (취소 · 결제 확인 중은 뺀다) */
export function isConfirmedTicket(ticket: MyTicket): boolean {
  return ticket.itemStatus === 'PAID' && !ticket.paying
}

/** 결제한 참가권 중 대회일이 오늘이거나 앞으로인 것에서 가장 가까운 대회. 없으면 null */
export function pickNextRace(
  tickets: MyTicket[],
  raceById: Map<number, Race | undefined>,
  now: Date,
): { ticket: MyTicket; race: Race & { raceDate: string } } | null {
  let best: { ticket: MyTicket; race: Race & { raceDate: string } } | null = null
  for (const ticket of tickets.filter(isConfirmedTicket)) {
    const race = raceById.get(ticket.raceId)
    if (!race?.raceDate || daysUntil(race.raceDate, now) < 0) continue
    if (!best || race.raceDate < best.race.raceDate) {
      best = { ticket, race: { ...race, raceDate: race.raceDate } }
    }
  }
  return best
}
