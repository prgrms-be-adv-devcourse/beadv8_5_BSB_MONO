import { queryOptions } from '@tanstack/react-query'

import { fileApi, productApi, unwrapData, type productModel } from '@/shared/api'

import { toRaceEditRequest } from '../model/seller-race'

// 판매자 대회 관리 API. 로그인한 판매자만 부르므로 클라이언트(TanStack Query)에서 부른다.

/** 내 대회 목록 (최근 등록 순, 취소된 대회 포함) */
export function myRacesQueryOptions() {
  return queryOptions({
    queryKey: productApi.getFindMineQueryKey(),
    queryFn: async () => unwrapData(await productApi.findMine()),
  })
}

export type RaceForEdit = {
  race: productModel.RaceDetailDto
  /** 지금 상태 그대로의 수정 요청. 바꿀 값만 덮어써 PUT 한다 */
  request: productModel.RaceSaveRequestDto
}

/**
 * 수정할 대회: 상세와 종목별 이미지를 함께 받는다.
 * 수정은 이미지 목록도 통째로 바꾸므로, 이미지를 못 받으면 빈 목록으로 덮어쓰지 않게 실패로 둔다.
 */
export async function fetchRaceForEdit(raceId: number): Promise<RaceForEdit> {
  const race = unwrapData(await productApi.findRace(raceId))
  const courseIds = (race.courses ?? []).flatMap((c) =>
    c.raceCourseId === undefined ? [] : [c.raceCourseId],
  )
  const files = await Promise.all(
    courseIds.map(async (id) =>
      unwrapData(await fileApi.findByRef({ refType: 'RaceCourse', refId: id })),
    ),
  )
  const filesByCourse = Object.fromEntries(courseIds.map((id, i) => [id, files[i]]))
  return { race, request: toRaceEditRequest(race, filesByCourse) }
}

export function raceForEditQueryOptions(raceId: number) {
  return queryOptions({
    queryKey: [...productApi.getFindRaceQueryKey(raceId), 'edit'],
    queryFn: () => fetchRaceForEdit(raceId),
    // 이미지 URL이 30분 뒤 만료되고 판매 상태가 바뀌므로 화면에 들어올 때마다 새로 받는다
    staleTime: 0,
    // 폼이 받은 시각(dataUpdatedAt)을 key로 쓰므로, 창에 돌아올 때 다시 받으면 쓰던 입력이 사라진다
    refetchOnWindowFocus: false,
  })
}
