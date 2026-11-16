import { useQuery } from '@tanstack/react-query'

import {
  statsPeriodSearch,
  type FormErrorStats,
  type FunnelStats,
  type RealtimeStats,
  type StatsPeriodQuery,
  type StatsResponse,
  type StatsSection,
  type TrendStats,
} from '@/entities/usage-stats'
import { ApiError, customFetch } from '@/shared/api'

// 영역마다 따로 불러와서 한 영역이 실패해도 다른 영역은 그대로 보인다.
// customFetch가 Access Token을 붙이고 401이면 재발급 뒤 한 번 더 보낸다(다른 어드민 API와 같다).

const STATS_URL = '/admin/stats/data'

// 어드민 통계 API는 openapi 명세에 없어 orval 키 함수가 없다. 키는 이 파일에서만 만든다.
const statsKey = (...parts: unknown[]) => ['admin-stats', ...parts] as const

/** 실시간 영역을 새로 불러오는 간격 */
export const REALTIME_REFRESH_MS = 60_000

type SectionData = {
  realtime: RealtimeStats
  buyer: FunnelStats
  seller: FunnelStats
  formErrors: FormErrorStats
  visitors: TrendStats
  purchases: TrendStats
}

export function useRealtimeStats() {
  return useQuery({
    queryKey: statsKey('realtime'),
    queryFn: () => customFetch<StatsResponse<RealtimeStats>>(`${STATS_URL}?section=realtime`),
    refetchInterval: REALTIME_REFRESH_MS,
    retry: retryUnlessDenied,
  })
}

/** 고른 기간을 쓰는 영역 (구매·판매자 단계, 입력 오류, 일별 추이) */
export function usePeriodStats<S extends Exclude<StatsSection, 'realtime'>>(
  section: S,
  query: StatsPeriodQuery,
) {
  return useQuery({
    queryKey: statsKey(section, query),
    queryFn: () =>
      customFetch<StatsResponse<SectionData[S]>>(
        `${STATS_URL}?section=${section}&${statsPeriodSearch(query)}`,
      ),
    retry: retryUnlessDenied,
  })
}

/** 권한·입력 오류는 다시 보내도 같으니 GA4 실패만 한 번 더 시도한다 */
function retryUnlessDenied(failureCount: number, error: Error) {
  return !(error instanceof ApiError && error.status < 500) && failureCount < 1
}

export function isNotAdminError(error: unknown) {
  return error instanceof ApiError && error.status === 403
}

/** 토큰 재발급까지 실패한 401. 로그인이 풀린 것이라 다시 시도해도 같다 */
export function isLoginRequiredError(error: unknown) {
  return error instanceof ApiError && error.status === 401
}
