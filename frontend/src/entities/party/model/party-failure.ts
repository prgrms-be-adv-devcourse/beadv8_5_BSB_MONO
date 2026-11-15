import { ApiError } from '@/shared/api'

/** 합류·만들기가 막힌 이유. 화면 하나(피그마 파티 09~15 · 25~27)에 하나씩 맞는다. */
export type PartyFailure =
  | 'NOT_CREW_MEMBER'
  | 'LINK_EXPIRED'
  | 'LINK_NOT_FOUND'
  | 'NOT_RECRUITING'
  | 'ALREADY_JOINED'
  | 'OTHER_PARTY'
  | 'FULL'
  | 'JOINED_LATE'
  | 'INDIVIDUAL_EXISTS'

const BY_CODE: Record<string, PartyFailure> = {
  ORDER_CREW_MEMBER_REQUIRED: 'NOT_CREW_MEMBER',
  ORDER_PARTY_LINK_EXPIRED: 'LINK_EXPIRED',
  ORDER_PARTY_NOT_FOUND: 'LINK_NOT_FOUND',
  ORDER_PARTY_NOT_RECRUITING: 'NOT_RECRUITING',
  ORDER_PARTY_ALREADY_JOINED: 'ALREADY_JOINED',
  ORDER_PARTY_OTHER_PARTY_JOINED: 'OTHER_PARTY',
  ORDER_DUPLICATE_PARTICIPATION: 'OTHER_PARTY',
  ORDER_PARTY_FULL: 'FULL',
  ORDER_PARTY_MEMBER_JOINED_LATE: 'JOINED_LATE',
  ORDER_INDIVIDUAL_PARTICIPATION_EXISTS: 'INDIVIDUAL_EXISTS',
}

export function partyFailureOf(error: unknown): PartyFailure | undefined {
  return error instanceof ApiError && error.code ? BY_CODE[error.code] : undefined
}

/** 개인 신청이 남아 있을 때 서버가 data로 주는 신청서 번호 */
export function individualParticipationId(error: unknown): number | undefined {
  if (!(error instanceof ApiError) || error.code !== 'ORDER_INDIVIDUAL_PARTICIPATION_EXISTS') return
  return (error.body as { data?: { participationId?: number } })?.data?.participationId
}

export function isCrewSeatShortage(error: unknown) {
  return error instanceof ApiError && error.code === 'ORDER_PARTY_CREW_SEAT_SHORTAGE'
}
