import { useQuery } from '@tanstack/react-query'

import {
  statsPeriodSearch,
  type FunnelKind,
  type FunnelStats,
  type RealtimeStats,
  type StatsPeriodQuery,
  type StatsResponse,
} from '@/entities/usage-stats'
import { ApiError, customFetch } from '@/shared/api'

// 영역마다 따로 불러와서 한 영역이 실패해도 다른 영역은 그대로 보인다.
// customFetch가 Access Token을 붙이고 401이면 재발급 뒤 한 번 더 보낸다(다른 어드민 API와 같다).

const STATS_URL = '/admin/stats/data'

/** 실시간 영역을 새로 불러오는 간격 */
export const REALTIME_REFRESH_MS = 60_000

export function useFunnelStats(kind: FunnelKind, query: StatsPeriodQuery) {
  return useQuery({
    queryKey: ['admin-stats', kind, query],
    queryFn: () =>
      customFetch<StatsResponse<FunnelStats>>(
        `${STATS_URL}?section=${kind}&${statsPeriodSearch(query)}`,
      ),
    retry: retryUnlessDenied,
  })
}

export function useRealtimeStats() {
  return useQuery({
    queryKey: ['admin-stats', 'realtime'],
    queryFn: () => customFetch<StatsResponse<RealtimeStats>>(`${STATS_URL}?section=realtime`),
    refetchInterval: REALTIME_REFRESH_MS,
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
