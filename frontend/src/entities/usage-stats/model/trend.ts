import { spanDates, type DateSpan, type StatsPeriod } from './period'

// 일별 추이 (방문자 · 구매). 그래프는 이번 기간 선 하나이고, 지난 기간은 합계 차이 배지로만 보여 준다.

export type TrendKind = 'visitors' | 'purchases'

export type TrendPoint = {
  /** 'YYYY-MM-DD' */
  date: string
  /** 그날 사람 수 */
  value: number
}

export type TrendStats = {
  period: StatsPeriod
  points: TrendPoint[]
  /** 이번 기간 날짜별 사람 수의 합. 이틀 온 사람은 두 번 센다 (디자인 계약 A-STATS 「일별 추이 카드」) */
  total: number
  /** 지난 기간 날짜별 사람 수의 합 */
  previousTotal: number
}

/** 날짜별 사람 수를 기간 전체 날짜에 맞춰 늘어놓는다. GA4가 주지 않은 날(기록 없음)은 0명이다 */
export function toTrendPoints(span: DateSpan, byDate: Record<string, number> = {}): TrendPoint[] {
  return spanDates(span).map((date) => ({ date, value: byDate[date] ?? 0 }))
}

/** 이번 · 지난 기간 날짜별 사람 수로 추이를 만든다 */
export function toTrendStats(
  period: StatsPeriod,
  byDate: { current?: Record<string, number>; previous?: Record<string, number> },
): TrendStats {
  const points = toTrendPoints(period.current, byDate.current)
  const sum = (list: TrendPoint[]) => list.reduce((total, p) => total + p.value, 0)
  return {
    period,
    points,
    total: sum(points),
    previousTotal: sum(toTrendPoints(period.previous, byDate.previous)),
  }
}

/** 지난 기간보다 늘어난 사람 수 배지: +11, −3, 0 */
export function trendDeltaText(total: number, previousTotal: number): string {
  const delta = total - previousTotal
  if (delta > 0) return `+${delta.toLocaleString('ko-KR')}`
  if (delta < 0) return `\u2212${(-delta).toLocaleString('ko-KR')}`
  return '0'
}
