import { useMutation, useQueryClient } from '@tanstack/react-query'

import { productApi } from '@/shared/api'

/** 판매 멈추기(판매 중 → 판매 중지)와 다시 열기(판매 중지 → 판매 중). 성공하면 내 대회 목록과 이 대회 상세를 새로 받는다. */
export function useToggleSale(raceId: number, action: 'stop' | 'resume') {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => (action === 'stop' ? productApi.stop(raceId) : productApi.resume(raceId)),
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: productApi.getFindMineQueryKey() }),
        queryClient.invalidateQueries({ queryKey: productApi.getFindRaceQueryKey(raceId) }),
      ]),
  })
}
