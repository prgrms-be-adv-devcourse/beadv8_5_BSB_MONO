'use client'

import { useQueryClient } from '@tanstack/react-query'

import { ApiError, payoutApi } from '@/shared/api'
import { useToast } from '@/shared/ui'

// 관리자 지급보류 걸기 · 풀기. 관리자 정산 화면과 지급보류 관리 화면이 같이 쓴다.
// 성공하면 판매자 정산 조회(요약 · 월별 · 건별 · 지급보류)를 다시 불러오게 한다.

function errorText(error: unknown) {
  return (error instanceof ApiError && error.serverMessage) || '잠시 뒤 다시 시도해 주세요.'
}

function useInvalidateSellerPayouts() {
  const queryClient = useQueryClient()
  return () =>
    Promise.all(
      [
        payoutApi.getSummaryQueryKey(),
        payoutApi.getPayoutsQueryKey(),
        payoutApi.getItemsQueryKey(),
        payoutApi.getHoldsQueryKey(),
        // 관리자 보류 걸기 · 풀기 창의 정산 목록과 정산 ID 조회
        payoutApi.getPayouts1QueryKey(),
        payoutApi.getFindPayoutQueryKey(),
      ].map((queryKey) => queryClient.invalidateQueries({ queryKey })),
    )
}

/** POST /api/v1/payout/payouts/{payoutId}/holds */
export function usePlaceHold() {
  const toast = useToast()
  const invalidate = useInvalidateSellerPayouts()
  return payoutApi.useHold<unknown>({
    mutation: {
      onSuccess: async () => {
        toast({ message: '지급보류를 걸었어요.', tone: 'success' })
        await invalidate()
      },
      onError: (error) => toast({ message: errorText(error), tone: 'error' }),
    },
  })
}

/** POST /api/v1/payout/holds/{holdId}/release */
export function useReleaseHold() {
  const toast = useToast()
  const invalidate = useInvalidateSellerPayouts()
  return payoutApi.useRelease<unknown>({
    mutation: {
      onSuccess: async () => {
        toast({ message: '지급보류를 풀었어요.', tone: 'success' })
        await invalidate()
      },
      onError: (error) => toast({ message: errorText(error), tone: 'error' }),
    },
  })
}
