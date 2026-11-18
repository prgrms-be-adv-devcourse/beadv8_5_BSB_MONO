import { useQueries, useQuery } from '@tanstack/react-query'

import { useAuthStatus } from '@/features/auth'
import { raceCoverQueryOptions, raceQueryOptions } from '@/entities/race'
import { myTicketsQueryOptions } from '@/entities/ticket'
import { useNow } from '@/shared/lib'

import { isConfirmedTicket, pickNextRace } from '../lib/next-race'

/** 로그인한 회원의 다음 대회. 로그인 전이거나 다가오는 참가권이 없으면 undefined */
export function useNextRace() {
  const status = useAuthStatus()
  // 대회일을 오늘과 견주므로 서버와 어긋나지 않게 마운트 뒤에 정한다
  const now = useNow()
  const tickets = useQuery({ ...myTicketsQueryOptions(), enabled: status === 'authenticated' })
  const confirmed = (tickets.data ?? []).filter(isConfirmedTicket)

  const raceIds = [...new Set(confirmed.map((t) => t.raceId))].filter((id) => id > 0)
  const races = useQueries({ queries: raceIds.map((id) => raceQueryOptions(id)) })
  const raceById = new Map(raceIds.map((id, i) => [id, races[i]?.data]))

  const next = now ? pickNextRace(confirmed, raceById, now) : null
  const image = useQuery({
    ...raceCoverQueryOptions(next?.ticket.raceId ?? 0),
    enabled: next !== null,
  })
  if (!next || !now) return undefined

  const course = next.race.courses?.find((c) => c.raceCourseId === next.ticket.raceCourseId)
  return {
    ticketId: next.ticket.id,
    raceId: String(next.ticket.raceId),
    title: next.race.title ?? next.ticket.itemName,
    raceDate: next.race.raceDate,
    courseName: course?.name ?? '',
    image: image.data ?? undefined,
    now,
  }
}
