'use client'

import { z } from 'zod'

import { cashApi } from '@/shared/api'

// 예치금 명세(cash)는 잔액 응답에 스키마가 없어 생성 타입이 unknown이다. 결제 화면에 필요한 잔액만 검사해 꺼낸다.
const BalanceResponse = z.object({ data: z.object({ balance: z.number() }) })

/**
 * 결제 화면에 보여 줄 내 예치금 잔액.
 * 캐시는 생성된 잔액 조회 훅과 같은 키 · 같은 모양(RsData 그대로)으로 두고, 꺼낼 때만 검사한다.
 */
export function useDepositBalance() {
  const balance = cashApi.useGetBalance({
    query: { select: (response) => BalanceResponse.parse(response).data.balance },
  })
  return {
    balance: balance.data,
    isError: balance.isError,
    refetch: balance.refetch,
  }
}
