import { queryOptions, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

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
}
export type PartyJoinBody = {
  joinToken: string
  participant: orderModel.ParticipantInfoRequestDto
  thirdPartyConsent: boolean
}

const ORDER = '/api/v1/order/parties'
const QUEUE = '/api/v1/queue/parties'

const json = (method: string, body?: unknown): RequestInit => ({
  method,
  ...(body === undefined
    ? {}
    : { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }),
})

async function call<T>(url: string, init?: RequestInit): Promise<T> {
  return unwrapData(await customFetch<RsData<T>>(url, init))
}

export const partyKeys = {
  detail: (partyId: number) => ['party', partyId] as const,
  raceStatus: (raceId: number) => ['party', 'race-status', raceId] as const,
  crewActive: (crewId: number) => ['party', 'crew-active', crewId] as const,
  preview: (token: string) => ['party', 'preview', token] as const,
  queue: (raceId: number, courseId: number) => ['party', 'queue', raceId, courseId] as const,
}

/** 파티가 바뀌면 상세 · 내 상태 · 크루 파티 목록이 모두 달라지므로 파티 캐시를 통째로 무효화한다 */
function useInvalidateParties() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: ['party'] })
}

export const partyDetailQueryOptions = (partyId: number) =>
  queryOptions({
    queryKey: partyKeys.detail(partyId),
    queryFn: () => call<PartyDetail>(`${ORDER}/${partyId}`),
    // 403(파티원이 아님) · 404(없는 파티)는 다시 불러도 같다
    retry: (count, error) => !(isClientError(error) || count >= 2),
  })

export const usePartyDetail = (partyId: number) => useQuery(partyDetailQueryOptions(partyId))

export const useMyRaceStatus = (raceId: number | undefined) =>
  useQuery({
    queryKey: partyKeys.raceStatus(raceId ?? 0),
    queryFn: () => call<PartyRaceStatus>(`${ORDER}/me/race-status?raceId=${raceId}`),
    enabled: raceId !== undefined,
  })

export const useCrewActiveParties = (crewId: number) =>
  useQuery({
    queryKey: partyKeys.crewActive(crewId),
    queryFn: () => call<PartySummary[]>(`${ORDER}/crews/${crewId}/active`),
  })

/** 참가 링크로 파티 미리 보기. 토큰은 본문으로 보낸다(주소에 남지 않게) */
export const usePartyPreview = (joinToken: string) =>
  useQuery({
    queryKey: partyKeys.preview(joinToken),
    queryFn: () => call<PartyPreview>(`${ORDER}/preview`, json('POST', { joinToken })),
    retry: (count, error) => !(isClientError(error) || count >= 2),
  })

export const useCreateParty = () => {
  const invalidate = useInvalidateParties()
  return useMutation({
    onSuccess: invalidate,
    mutationFn: (body: PartyCreateBody) =>
      call<{ party: PartyDetail; participation: orderModel.ParticipationDto }>(
        ORDER,
        json('POST', body),
      ),
  })
}

export const useJoinParty = () => {
  const invalidate = useInvalidateParties()
  return useMutation({
    onSuccess: invalidate,
    mutationFn: (body: PartyJoinBody) =>
      call<orderModel.ParticipationDto>(`${ORDER}/join`, json('POST', body)),
  })
}

export const useLeaveParty = () => {
  const invalidate = useInvalidateParties()
  return useMutation({
    onSuccess: invalidate,
    mutationFn: (partyId: number) =>
      call<orderModel.ParticipationDto>(`${ORDER}/${partyId}/leave`, json('POST')),
  })
}

export const useRegenerateJoinLink = () =>
  useMutation({
    mutationFn: (partyId: number) =>
      call<PartyDetail>(`${ORDER}/${partyId}/join-link`, json('POST')),
  })

export const useApplyParty = () => {
  const invalidate = useInvalidateParties()
  return useMutation({
    onSuccess: invalidate,
    mutationFn: (partyId: number) => call<PartyDetail>(`${ORDER}/${partyId}/apply`, json('POST')),
  })
}

export const useRegisterPartyQueue = () =>
  useMutation({
    mutationFn: (body: { raceId: number; raceCourseId: number }) =>
      call<PartyQueueStatus>(QUEUE, json('POST', body)),
  })

export const usePartyQueueStatus = (raceId: number, courseId: number, enabled: boolean) =>
  useQuery({
    queryKey: partyKeys.queue(raceId, courseId),
    queryFn: () => call<PartyQueueStatus>(`${QUEUE}/races/${raceId}/racecourses/${courseId}`),
    enabled,
    refetchInterval: 3000,
  })

function isClientError(error: unknown) {
  const status = (error as { status?: number } | null)?.status
  return status !== undefined && status < 500
}
