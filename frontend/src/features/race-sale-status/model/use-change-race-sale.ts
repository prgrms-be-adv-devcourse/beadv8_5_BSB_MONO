import { useMutation, useQueryClient } from '@tanstack/react-query'

import { productApi, unwrapData } from '@/shared/api'

import type { SaleAction } from '../lib/sale-action'

const REQUESTS = {
  seller: { open: productApi.open, stop: productApi.stop, resume: productApi.resume },
  admin: {
    open: productApi.openByAdmin,
    stop: productApi.stopByAdmin,
    resume: productApi.resumeByAdmin,
  },
} as const

/**
 * 판매 바로 시작 · 멈추기 · 다시 열기. 판매자는 본인 대회만, 관리자는 모든 대회를 바꾼다.
 * 성공하면 그 화면의 대회 목록과 이 대회 상세(공개 · 수정용)를 새로 받는다.
 */
export function useChangeRaceSale(raceId: number, by: 'seller' | 'admin') {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (action: SaleAction) => unwrapData(await REQUESTS[by][action](raceId)),
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({
          queryKey:
            by === 'admin'
              ? productApi.getFindAdminRacesQueryKey()
              : productApi.getFindMineQueryKey(),
        }),
        queryClient.invalidateQueries({ queryKey: productApi.getFindRaceQueryKey(raceId) }),
      ]),
  })
}
