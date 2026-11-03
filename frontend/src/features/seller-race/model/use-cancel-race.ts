import { useMutation, useQueryClient } from '@tanstack/react-query'

import { productApi, unwrapData } from '@/shared/api'

/** 대회 취소 (되돌릴 수 없다). 성공하면 내 대회 목록과 이 대회 상세(수정용 · 장바구니 · 결제 화면용)를 새로 받는다. */
export function useCancelRace(raceId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async () => unwrapData(await productApi.cancel(raceId)),
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: productApi.getFindMineQueryKey() }),
        queryClient.invalidateQueries({ queryKey: productApi.getFindRaceQueryKey(raceId) }),
      ]),
  })
}
