'use client'

import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { customFetch, productApi, productModel, unwrapData, type RsData } from '@/shared/api'

import type { CreateInquiryRequest, Inquiry, InquiryManagePage, InquiryPage } from '../model/types'
import { BASE, json, KEY, type ManageFilters, type ManageRole } from './inquiry-api'

/** 내 문의 목록 (최신순). 20개씩 받고 fetchNextPage로 다음 쪽을 이어 붙인다 */
export function useMyInquiries() {
  return useInfiniteQuery({
    queryKey: [...KEY, 'mine'],
    queryFn: async ({ signal, pageParam }) =>
      unwrapData(
        await customFetch<RsData<InquiryPage>>(`${BASE}/inquiries/me?page=${pageParam}&size=20`, {
          signal,
        }),
      ),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
    select: (data) => ({ inquiries: data.pages.flatMap((p) => p.inquiries) }),
    retry: false,
  })
}

/** 내 문의 하나 */
export function useMyInquiry(id: number) {
  return useQuery({
    queryKey: [...KEY, 'mine', id],
    queryFn: async ({ signal }) =>
      unwrapData(await customFetch<RsData<Inquiry>>(`${BASE}/inquiries/${id}`, { signal })),
    retry: false,
  })
}

/** 문의 남기기. 판매자가 크루런에 보내는 문의는 type SELLER_SUPPORT에 대회 · 주문 없이 보낸다 */
export function useCreateInquiry() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (request: CreateInquiryRequest) =>
      unwrapData(await customFetch<RsData<Inquiry>>(`${BASE}/inquiries`, json('POST', request))),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: KEY }),
  })
}

/** 문의 고치기. 답변 대기일 때만 되고, 답변이 달렸으면 409 */
export function useEditInquiry(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (request: { title: string; body: string }) =>
      unwrapData(
        await customFetch<RsData<Inquiry>>(`${BASE}/inquiries/${id}`, json('PATCH', request)),
      ),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: KEY }),
  })
}

/** 문의 지우기. 답변 대기일 때만 되고, 답변이 달렸으면 409 */
export function useDeleteInquiry(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async () =>
      customFetch<RsData<null>>(`${BASE}/inquiries/${id}`, { method: 'DELETE' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: KEY }),
  })
}

/** 판매자(내 대회) · 관리자(전체) 문의 목록. 답변 대기가 먼저, 상태별 개수도 함께 온다 */
export function useManageInquiries(role: ManageRole, filters: ManageFilters) {
  const params = new URLSearchParams({ page: '0', size: '20' })
  if (filters.status) params.set('status', filters.status)
  if (role === 'admin' && filters.assignee) params.set('assignee', filters.assignee)
  return useQuery({
    queryKey: [...KEY, role, filters],
    queryFn: async ({ signal }) =>
      unwrapData(
        await customFetch<RsData<InquiryManagePage>>(`${BASE}/${role}/inquiries?${params}`, {
          signal,
        }),
      ),
    retry: false,
  })
}

/** 판매자 · 관리자 상세. 주문 정보(order)가 함께 온다 */
export function useManageInquiry(role: ManageRole, id: number | null) {
  return useQuery({
    queryKey: [...KEY, role, 'detail', id],
    queryFn: async ({ signal }) =>
      unwrapData(await customFetch<RsData<Inquiry>>(`${BASE}/${role}/inquiries/${id}`, { signal })),
    enabled: id !== null,
    retry: false,
  })
}

/** 답변 보내기. 한 번만 달 수 있어 이미 답했으면 409 */
export function useAnswerInquiry(role: ManageRole, id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (content: string) =>
      unwrapData(
        await customFetch<RsData<Inquiry>>(
          `${BASE}/${role}/inquiries/${id}/answer`,
          json('POST', { content }),
        ),
      ),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: KEY }),
  })
}

function useRace<T>(raceId: number | null, select: (race: productModel.RaceDetailDto) => T) {
  return useQuery({
    queryKey: productApi.getFindRaceQueryKey(raceId ?? 0),
    queryFn: async ({ signal }) => unwrapData(await productApi.findRace(raceId ?? 0, { signal })),
    select,
    enabled: raceId !== null,
    staleTime: 60 * 1000,
  }).data
}

/** 대회 이름. raceId가 없으면(판매자 문의 · 대회 없는 문의) 부르지 않는다 */
export function useRaceName(raceId: number | null) {
  return useRace(raceId, (race) => race.title)
}

/** 판매자(주최) 이름. 판매자가 답한 문의의 「판매자 답변 · 러너스컴퍼니」에 쓴다 */
export function useRaceSellerName(raceId: number | null) {
  return useRace(raceId, (race) => race.seller?.businessName)
}

/** 대회 날짜(2026-11-16T08:00:00). 참가권 고르기에서 날짜와 신청 · 참가 완료를 가를 때 쓴다 */
export function useRaceDate(raceId: number | null) {
  return useRace(raceId, (race) => race.raceDate)
}
