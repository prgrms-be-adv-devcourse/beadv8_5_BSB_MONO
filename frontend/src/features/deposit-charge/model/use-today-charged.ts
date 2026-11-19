'use client'

import { useQuery } from '@tanstack/react-query'

import { cashApi, unwrapData } from '@/shared/api'

import { MAX_CHARGE_PER_DAY, MIN_CHARGE, todayChargedTotal } from '../lib/charge-limit'

// 하루 한도(100만) ÷ 최소 충전(1만) = 하루에 들어올 수 있는 충전은 많아야 100건이라, 최신순 한 페이지로 오늘 것을 모두 본다
const PARAMS = { category: 'CHARGE', page: 0, size: MAX_CHARGE_PER_DAY / MIN_CHARGE } as const

/**
 * 오늘(한국 시간) 충전한 합계. 충전 화면에서 일일 한도를 미리 막는 데 쓴다.
 * 불러오는 중이거나 실패하면 undefined라, 그때는 서버가 확정할 때 막는다.
 */
export function useTodayCharged(enabled: boolean) {
  const query = useQuery({
    ...cashApi.getGetLedgersQueryOptions(PARAMS),
    select: (res) => todayChargedTotal(unwrapData(res).content, Date.now()),
    enabled,
  })
  return query.data
}
