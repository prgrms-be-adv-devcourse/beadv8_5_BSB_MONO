import { useQuery } from '@tanstack/react-query'

import { daysUntil, raceCoverQueryOptions, raceQueryOptions } from '@/entities/race'
import {
  orderQueryOptions,
  parseTicketId,
  participationQueryOptions,
  ticketStatus,
  toMyTickets,
} from '@/entities/ticket'
import { ApiError } from '@/shared/api'

/** 남의 주문(403) · 없는 주문(404) · 잘못된 ID(400)는 '찾을 수 없음'으로 보여 준다 */
function isNotFound(error: Error | null) {
  return error instanceof ApiError && [400, 403, 404].includes(error.status)
}

/**
 * 참가권 상세 화면들이 함께 쓰는 데이터.
 * 주문 상세(주문 상품) → 참가 신청(참가자 정보) · 대회 상세(날짜 · 환불 기한 · 종목) · 종목 이미지.
 * notFound: 주소의 ID 모양이 틀렸거나, 주문을 못 찾았거나(404 · 403), 주문에 그 상품이 없을 때
 */
export function useTicket(ticketId: string) {
  const ids = parseTicketId(ticketId)
  const order = useQuery({ ...orderQueryOptions(ids?.orderId ?? 0), enabled: ids !== null })
  const ticket = order.data
    ? toMyTickets(order.data).find((t) => t.orderItemId === ids?.orderItemId)
    : undefined

  const participation = useQuery({
    ...participationQueryOptions(ticket?.participationId ?? 0),
    enabled: ticket !== undefined,
  })
  const race = useQuery({ ...raceQueryOptions(ticket?.raceId ?? 0), enabled: ticket !== undefined })
  const image = useQuery({
    ...raceCoverQueryOptions(ticket?.raceId ?? 0),
    enabled: ticket !== undefined,
  })

  const raceDto = race.data
  const course = raceDto?.courses?.find((c) => c.raceCourseId === ticket?.raceCourseId)
  const raceOver = raceDto?.raceDate ? daysUntil(raceDto.raceDate) < 0 : false

  return {
    ticket,
    order: order.data,
    participation: participation.data,
    race: raceDto,
    course,
    image: image.data ?? undefined,
    status: ticket ? ticketStatus(ticket, { raceOver }) : undefined,
    notFound: ids === null || (order.isSuccess && !ticket) || isNotFound(order.error),
    isPending:
      ids !== null &&
      !order.isError &&
      (order.isPending || (ticket !== undefined && (participation.isPending || race.isPending))),
    isError: (order.isError && !isNotFound(order.error)) || participation.isError || race.isError,
  }
}
