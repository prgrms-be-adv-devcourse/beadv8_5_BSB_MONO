import { useMutation, useQueryClient } from '@tanstack/react-query'

import { productApi, productModel, unwrapData } from '@/shared/api'

/** 대회 수정 (대회 전체를 보낸다). 성공하면 이 브라우저의 내 대회 목록과 이 대회 상세(수정용 · 장바구니 · 결제 화면용)를 새로 받는다. */
export function useUpdateRace(raceId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (request: productModel.RaceSaveRequestDto) =>
      unwrapData(await productApi.update(raceId, request)),
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: productApi.getFindMineQueryKey() }),
        queryClient.invalidateQueries({ queryKey: productApi.getFindRaceQueryKey(raceId) }),
      ]),
  })
}
