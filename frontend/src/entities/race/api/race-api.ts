import { infiniteQueryOptions, queryOptions } from '@tanstack/react-query'

import { ApiError, fileApi, productApi, productModel, unwrapData } from '@/shared/api'

import { courseImage, toRaceDetail } from '../model/mappers'
import type { RaceDetail } from '../model/types'

// 대회 조회 API는 로그인 없이 부르는 공개 API다 (토큰을 붙이지 않는다).

export type RaceSort = productModel.SearchSort
export const RaceSort = productModel.SearchSort

export type RaceSearchFilters = {
  keyword?: string
  sort?: RaceSort
}

export const RACE_PAGE_SIZE = 20

/** 대회 찾기 첫 화면 정렬. RSC에서 미리 받는 첫 페이지와 브라우저의 첫 요청이 같은 조건이어야 한다. */
export const DEFAULT_RACE_SORT: RaceSort = RaceSort.DEADLINE

/** 대회 목록 한 페이지. RSC(홈)와 대회 찾기가 같이 쓴다. */
export async function fetchRacePage(params: productModel.SearchParams) {
  return unwrapData(await productApi.search(params))
}

function searchParams(filters: RaceSearchFilters, page: number): productModel.SearchParams {
  const keyword = filters.keyword?.trim()
  return {
    ...(keyword ? { keyword } : {}),
    ...(filters.sort ? { sort: filters.sort } : {}),
    page,
    size: RACE_PAGE_SIZE,
  }
}

/** 대회 찾기 목록 (더 보기로 다음 페이지). 첫 페이지는 RSC에서 미리 받아 둔다. */
export function raceListQueryOptions(filters: RaceSearchFilters) {
  return infiniteQueryOptions({
    queryKey: [...productApi.getSearchQueryKey(searchParams(filters, 0)), 'infinite'],
    queryFn: ({ pageParam }) => fetchRacePage(searchParams(filters, pageParam)),
    initialPageParam: 0,
    getNextPageParam: (last) => {
      const next = (last.page ?? 0) + 1
      return next < (last.totalPages ?? 0) ? next : undefined
    },
  })
}

/** 검색창 연관검색어 (대회명 · '대회명 종목명' 각각 5개까지) */
export function raceSuggestionsQueryOptions(keyword: string) {
  const trimmed = keyword.trim()
  return queryOptions({
    queryKey: productApi.getSuggestQueryKey({ keyword: trimmed }),
    queryFn: async () => unwrapData(await productApi.suggest({ keyword: trimmed })),
    enabled: trimmed.length > 0,
    staleTime: 30 * 1000,
  })
}

/** 종목 이미지 (file 서비스). URL이 30분 뒤 만료되므로 화면을 그릴 때마다 새로 받는다. 실패하면 이미지 없이 그린다. */
async function fetchCourseFiles(raceCourseId: number) {
  try {
    return unwrapData(await fileApi.findByRef({ refType: 'RaceCourse', refId: raceCourseId }))
  } catch {
    return []
  }
}

/**
 * 대회 상세. 없는 대회(404)나 숫자가 아닌 ID(400)면 null → 화면에서 notFound().
 * 취소된 대회도 다른 사람에게는 404로 온다.
 */
export async function fetchRaceDetail(raceId: string): Promise<RaceDetail | null> {
  const id = Number(raceId)
  if (!Number.isInteger(id) || id <= 0) return null

  let race: productModel.RaceDetailDto
  try {
    race = unwrapData(await productApi.findRace(id))
  } catch (error) {
    if (error instanceof ApiError && (error.status === 404 || error.status === 400)) return null
    throw error
  }

  const courseIds = (race.courses ?? []).flatMap((c) =>
    c.raceCourseId === undefined ? [] : [c.raceCourseId],
  )
  const files = await Promise.all(courseIds.map(fetchCourseFiles))
  return toRaceDetail(race, Object.fromEntries(courseIds.map((id, i) => [id, files[i]])))
}

/**
 * 대회 상세 원본(상품 API 응답 그대로). 내 참가권처럼 날짜·종목 원본 값을 화면에서 직접 계산할 때 쓴다.
 * 로그인한 화면(클라이언트)에서 부른다. 없는 대회면 ApiError(404)를 던진다.
 */
export function raceQueryOptions(raceId: number) {
  return queryOptions({
    queryKey: productApi.getFindRaceQueryKey(raceId),
    queryFn: async ({ signal }) => unwrapData(await productApi.findRace(raceId, { signal })),
    staleTime: 60 * 1000,
  })
}

/** 종목 대표 이미지 한 장 (file 서비스). 없거나 못 받으면 null → 이미지 없이 그린다. URL이 30분 뒤 만료된다. */
export function courseImageQueryOptions(raceCourseId: number) {
  const params = { refType: 'RaceCourse', refId: raceCourseId } as const
  return queryOptions({
    queryKey: [...fileApi.getFindByRefQueryKey(params), 'image'],
    queryFn: async () => courseImage(await fetchCourseFiles(raceCourseId)) ?? null,
    staleTime: 10 * 60 * 1000,
  })
}

/**
 * 대회 상세를 클라이언트에서 불러올 때 (장바구니 · 결제처럼 로그인한 뒤 그리는 화면).
 * 종목 이미지 URL이 30분 뒤 만료되므로 그보다 짧게 캐시한다.
 * 키를 상품 API 대회 키 아래에 두어, 같은 브라우저에서 대회를 고치거나 취소하면 이 상세도 함께 새로 받는다.
 * 다른 사람의 브라우저는 위 캐시 시간이 지나야 새로 받는다.
 */
export function raceDetailQueryOptions(raceId: string) {
  return queryOptions({
    queryKey: [...productApi.getFindRaceQueryKey(Number(raceId)), 'detail'],
    queryFn: () => fetchRaceDetail(raceId),
    staleTime: 5 * 60 * 1000,
  })
}
