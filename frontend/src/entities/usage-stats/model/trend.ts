import { spanDates, type StatsPeriod } from './period'

// 일별 추이 그래프. 이번 기간과 비교 기간을 「며칠째」로 맞춰 두 선을 겹쳐 그린다.

export type TrendKind = 'visitors' | 'purchases'

export type TrendPoint = {
  /** 이번 기간 날짜 'YYYY-MM-DD' */
  date: string
  /** 같은 순서의 비교 기간 날짜 */
  previousDate: string
  value: number
  previousValue: number
}

export type TrendStats = { period: StatsPeriod; points: TrendPoint[] }

/** 날짜별 사람 수를 기간 전체 날짜에 맞춰 늘어놓는다. GA4가 주지 않은 날(기록 없음)은 0명이다 */
export function toTrendPoints(
  period: StatsPeriod,
  byDate: { current?: Record<string, number>; previous?: Record<string, number> },
): TrendPoint[] {
  const previousDates = spanDates(period.previous)
  return spanDates(period.current).map((date, i) => ({
    date,
    previousDate: previousDates[i],
    value: byDate.current?.[date] ?? 0,
    previousValue: byDate.previous?.[previousDates[i]] ?? 0,
  }))
}
