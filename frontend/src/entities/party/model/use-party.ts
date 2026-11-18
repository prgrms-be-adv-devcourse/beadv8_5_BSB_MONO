import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { orderModel } from '@/shared/api'

import {
  call,
  isClientError,
  json,
  ORDER,
  partyDetailQueryOptions,
  partyKeys,
  QUEUE,
  type PartyCreateBody,
  type PartyDetail,
  type PartyJoinBody,
  type PartyPreview,
  type PartyQueueStatus,
  type PartyRaceStatus,
  type PartySummary,
} from '../api/party-api'

/** 파티가 바뀌면 상세 · 내 상태 · 크루 파티 목록이 모두 달라지므로 파티 캐시를 통째로 무효화한다 */
function useInvalidateParties() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: partyKeys.all })
}

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
