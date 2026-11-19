import { queryOptions } from '@tanstack/react-query'

import { customFetch, orderModel, unwrapData, type RsData } from '@/shared/api'

// 파티 API. 백엔드 컨트롤러(ApiV1PartyController · ApiV1PartyQueryController · ApiV1PartyQueueController)를 그대로 따른다.
// openapi/order.json · queue.json에 파티 경로가 아직 없어 orval 함수가 없다. 명세가 들어오면 생성된 훅으로 바꾼다.

export type PartyStatus = 'RECRUITING' | 'PAYING' | 'PAID' | 'CLOSED'

export type PartyMember = {
  memberId: number
  nickname: string
  leader: boolean
  joinedAt: string
  participationStatus: string
  paid: boolean
}

export type PartyDetail = {
  id: number
  crewId: number
  raceId: number
  raceCourseId: number
  leaderMemberId: number
  status: PartyStatus
  paymentDueAt: string | null
  /** 파티장에게만 온다 */
  joinToken: string | null
  joinTokenExpiresAt: string
  memberCount: number
  paidCount: number
  members: PartyMember[]
}

export type PartySummary = Pick<
  PartyDetail,
  | 'id'
  | 'crewId'
  | 'raceId'
  | 'raceCourseId'
  | 'status'
  | 'leaderMemberId'
  | 'memberCount'
  | 'paidCount'
  | 'paymentDueAt'
> & { leaderNickname: string; myParty: boolean }

export type PartyPreview = Pick<
  PartyDetail,
  'id' | 'crewId' | 'raceId' | 'raceCourseId' | 'status' | 'memberCount' | 'joinTokenExpiresAt'
> & {
  leaderNickname: string
  joinLinkAlive: boolean
}

export type PartyRaceStatus = {
  role: 'LEADER' | 'MEMBER' | 'NONE'
  canApply: boolean
  reason?: string
  partyId?: number
  raceCourseId?: number
  partyStatus?: PartyStatus
  memberCount?: number
  minCount?: number
  maxCount?: number
  paidCount?: number
  paymentDueAt?: string
  myPaid?: boolean
  participationId?: number
}

export type PartyQueueStatus = { myRank: number; ahead: number; total: number; canEnter: boolean }

export type PartyCreateBody = {
  crewId: number
  raceCourseId: number
  participant: orderModel.ParticipantInfoRequestDto
  thirdPartyConsent: boolean
  // 초대할 크루원 회원 ID (BUK-182, 선택, 최대 50명). 파티를 만든 뒤 이 사람들에게 초대 알림과 참가 링크가 간다
  invitedMemberIds?: number[]
}
export type PartyJoinBody = {
  joinToken: string
  participant: orderModel.ParticipantInfoRequestDto
  thirdPartyConsent: boolean
}

export const ORDER = '/api/v1/order/parties'
export const QUEUE = '/api/v1/queue/parties'

export const json = (method: string, body?: unknown): RequestInit => ({
  method,
  ...(body === undefined
    ? {}
    : { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }),
})

export async function call<T>(url: string, init?: RequestInit): Promise<T> {
  return unwrapData(await customFetch<RsData<T>>(url, init))
}

export const partyKeys = {
  /** 파티 캐시 전체 (아래 키가 모두 이 아래에 있다) */
  all: ['party'] as const,
  detail: (partyId: number) => ['party', partyId] as const,
  raceStatus: (raceId: number) => ['party', 'race-status', raceId] as const,
  crewActive: (crewId: number) => ['party', 'crew-active', crewId] as const,
  preview: (token: string) => ['party', 'preview', token] as const,
  queue: (raceId: number, courseId: number) => ['party', 'queue', raceId, courseId] as const,
}

export const partyDetailQueryOptions = (partyId: number) =>
  queryOptions({
    queryKey: partyKeys.detail(partyId),
    queryFn: () => call<PartyDetail>(`${ORDER}/${partyId}`),
    // 403(파티원이 아님) · 404(없는 파티)는 다시 불러도 같다
    retry: (count, error) => !(isClientError(error) || count >= 2),
  })

export function isClientError(error: unknown) {
  const status = (error as { status?: number } | null)?.status
  return status !== undefined && status < 500
}
