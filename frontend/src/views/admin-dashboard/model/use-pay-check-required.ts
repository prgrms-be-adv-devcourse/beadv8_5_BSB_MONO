'use client'

import { detailPeriods } from '@/entities/payout'
import { payoutApi, unwrapData } from '@/shared/api'
import { useNow } from '@/shared/lib'

export const PAY_CHECK_PAGE_SIZE = 10

/**
 * 최근 1개월(API 한도)의 '지급 확인 필요' 이상 감지 기록 (BUK-175).
 * 숫자 타일과 표가 같은 쿼리를 불러 요청은 한 번만 나간다. 기간은 마운트 뒤 오늘로 정한다.
 */
export function usePayCheckRequired() {
  const now = useNow()
  const period = now ? detailPeriods(now, 1)[0] : undefined
  return payoutApi.useCreateFailures(
    {
      from: period?.from ?? '',
      to: period?.to ?? '',
      type: '지급_확인_필요',
      size: PAY_CHECK_PAGE_SIZE,
    },
    { query: { select: unwrapData, enabled: !!period } },
  )
}
