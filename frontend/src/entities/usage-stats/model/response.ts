import type { FunnelStepStat } from './funnels'
import type { StatsPeriod } from './period'

// 어드민 이용 통계 응답. Next 서버(/admin/stats/data)가 GA4 숫자를 이 모양으로 바꿔 내려 준다.
// 사람 수와 화면 이름만 담는다. 개인을 알아볼 수 있는 값은 없다.

export type StatsSection = 'realtime' | 'buyer' | 'seller' | 'formErrors' | 'visitors' | 'purchases'

export type FunnelStats = { period: StatsPeriod; steps: FunnelStepStat[] }

export type RealtimeStats = {
  /** 최근 30분 동안 접속한 사람 수 */
  activeUsers: number
  /** 최근 30분 동안 많이 본 화면 (GA4 실시간 보고서는 화면 주소 대신 화면 제목을 준다) */
  topScreens: { name: string; activeUsers: number }[]
}

export type StatsResponse<T> =
  | { status: 'ok'; data: T }
  /** 서버에 GA4 속성 ID·서비스 계정 키가 없다 */
  | { status: 'notConfigured' }

/** 실시간 보고서에서 보여 줄 화면 수 */
export const TOP_SCREEN_COUNT = 5
