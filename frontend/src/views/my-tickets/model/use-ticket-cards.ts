import { useQueries, useQuery } from '@tanstack/react-query'

import {
  courseImageQueryOptions,
  daysUntil,
  formatRaceDate,
  raceQueryOptions,
} from '@/entities/race'
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
 * 내 참가권 카드. 주문(참가권) → 대회(이름 · 날짜 · 환불 기한) · 종목 이미지 · 참가자 이름을 합친다.
 * 대회 · 이미지 · 참가자를 아직 못 받았으면 받은 것만으로 그린다.
 */
export function useTicketCards() {
  const tickets = useQuery(myTicketsQueryOptions())
  const list = tickets.data ?? []

  const raceIds = unique(list.map((t) => t.raceId))
  const races = useQueries({ queries: raceIds.map((id) => raceQueryOptions(id)) })
  const raceById = new Map(raceIds.map((id, i) => [id, races[i]?.data]))

  const courseIds = unique(list.map((t) => t.raceCourseId))
  const images = useQueries({ queries: courseIds.map((id) => courseImageQueryOptions(id)) })
  const imageByCourse = new Map(courseIds.map((id, i) => [id, images[i]?.data ?? undefined]))

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
      status,
      image: imageByCourse.get(ticket.raceCourseId),
      eyebrow: race?.title ?? ticket.itemName,
      number: ticketNumber(ticket.orderNo),
      title: runner ? `${runner} · ${courseName}` : courseName,
      summary: summaryText(ticket, status, race?.raceDate, race?.refundDeadline),
    }
  })

  return {
    upcoming: cards.filter((c) => c.status === 'confirmed'),
    past: cards.filter((c) => c.status !== 'confirmed'),
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
    status === 'canceled'
      ? '참가 취소'
      : status === 'finished'
        ? '대회 종료'
        : refundDeadline && daysUntil(refundDeadline) >= 0
          ? `${formatRaceDate(refundDeadline)}까지 취소하면 ${formatNumber(ticket.amount)}원 전액 환불`
          : ''
  return [date, note].filter(Boolean).join(' · ')
}
