import { useQueries, useQuery } from '@tanstack/react-query'

import { formatKstDateTime } from '@/entities/crew'
import { daysUntil, formatRaceDate, raceCoverQueryOptions, raceQueryOptions } from '@/entities/race'
import {
  myTicketsQueryOptions,
  participationQueryOptions,
  ticketNumber,
  ticketStatus,
  type MyTicket,
  type TicketStatus,
} from '@/entities/ticket'
import { formatNumber } from '@/shared/lib'

export type TicketCard = {
  id: string
  orderId: number
  /** 결제 대기 카드의 결제 기한 */
  dueAt?: string
  status: TicketStatus
  image?: string
  eyebrow: string
  number: string
  title: string
  summary: string
}

function unique(values: number[]): number[] {
  return [...new Set(values)].filter((v) => v > 0)
}

/**
 * 내 참가권 카드. 주문(참가권) → 대회(이름 · 날짜 · 환불 기한) · 대회 대표 이미지 · 참가자 이름을 합친다.
 * 대회 · 이미지 · 참가자를 아직 못 받았으면 받은 것만으로 그린다.
 */
export function useTicketCards() {
  const tickets = useQuery(myTicketsQueryOptions())
  const list = tickets.data ?? []

  const raceIds = unique(list.map((t) => t.raceId))
  const races = useQueries({ queries: raceIds.map((id) => raceQueryOptions(id)) })
  const raceById = new Map(raceIds.map((id, i) => [id, races[i]?.data]))

  const covers = useQueries({ queries: raceIds.map((id) => raceCoverQueryOptions(id)) })
  const coverByRace = new Map(raceIds.map((id, i) => [id, covers[i]?.data ?? undefined]))

  const participations = useQueries({
    queries: list.map((t) => participationQueryOptions(t.participationId)),
  })

  const cards = list.map((ticket, i): TicketCard => {
    const race = raceById.get(ticket.raceId)
    const course = race?.courses?.find((c) => c.raceCourseId === ticket.raceCourseId)
    const runner = participations[i]?.data?.participant?.name
    const raceOver = race?.raceDate ? daysUntil(race.raceDate) < 0 : false
    const status = ticketStatus(ticket, { raceOver })
    const courseName = course?.name ?? ticket.itemName
    return {
      id: ticket.id,
      orderId: ticket.orderId,
      dueAt: ticket.dueAt,
      status,
      image: coverByRace.get(ticket.raceId),
      eyebrow: race?.title ?? ticket.itemName,
      number: ticketNumber(ticket.orderNo),
      title: runner ? `${runner} · ${courseName}` : courseName,
      summary: summaryText(ticket, status, race?.raceDate, race?.refundDeadline),
    }
  })

  return {
    // 결제하지 않으면 참가가 확정되지 않으므로 결제 대기 카드를 맨 위에 둔다
    upcoming: [
      ...cards.filter((c) => c.status === 'pending'),
      ...cards.filter((c) => c.status === 'confirmed' || c.status === 'paying'),
    ],
    past: cards.filter((c) => c.status === 'finished' || c.status === 'canceled'),
    isPending: tickets.isPending,
    isError: tickets.isError,
    refetch: () => void tickets.refetch(),
    isFetching: tickets.isFetching,
  }
}

/** 카드 아래 한 줄: 대회일 · 환불 안내 */
function summaryText(
  ticket: MyTicket,
  status: TicketStatus,
  raceDate: string | undefined,
  refundDeadline: string | undefined,
): string {
  const date = raceDate ? formatRaceDate(raceDate) : ''
  const note =
    status === 'pending'
      ? ''
      : status === 'canceled'
        ? '참가 취소'
        : status === 'finished'
          ? '대회 종료'
          : status === 'confirmed' && refundDeadline && daysUntil(refundDeadline) >= 0
            ? `${formatRaceDate(refundDeadline)}까지 취소하면 ${formatNumber(ticket.amount)}원 전액 환불`
            : ''
  if (status === 'pending' && ticket.dueAt) return `결제 기한 ${formatKstDateTime(ticket.dueAt)}`
  return [date, note].filter(Boolean).join(' · ')
}
