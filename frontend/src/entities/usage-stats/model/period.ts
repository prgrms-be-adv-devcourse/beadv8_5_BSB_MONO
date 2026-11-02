import { z } from 'zod'

// 이용 통계 기간. GA4 기본 보고서는 반영에 하루 이상 걸려서, 정해진 기간은 어제에서 끝난다.
// 날짜는 한국 시간 기준 'YYYY-MM-DD'다 (GA4 속성 시간대와 맞춘다).

export type StatsPeriodKind = '7d' | '30d' | 'custom'

export const STATS_PERIOD_ITEMS: { value: StatsPeriodKind; label: string }[] = [
  { value: '7d', label: '지난 7일' },
  { value: '30d', label: '지난 30일' },
  { value: 'custom', label: '직접 고르기' },
]

export type DateSpan = { start: string; end: string }

export type StatsPeriod = {
  current: DateSpan
  /** 고른 기간과 길이가 같은 바로 앞 기간 */
  previous: DateSpan
}

/** 직접 고르기로 볼 수 있는 가장 긴 기간(일) */
export const MAX_CUSTOM_DAYS = 366

const ISO_DATE = z.iso.date()

export const statsPeriodQuerySchema = z.discriminatedUnion('period', [
  z.object({ period: z.literal('7d') }),
  z.object({ period: z.literal('30d') }),
  z.object({ period: z.literal('custom'), start: ISO_DATE, end: ISO_DATE }),
])

export type StatsPeriodQuery = z.infer<typeof statsPeriodQuerySchema>

const DAY_MS = 24 * 60 * 60 * 1000

// 'YYYY-MM-DD'를 UTC 자정으로 다뤄 서버 시간대와 상관없이 날짜만 더하고 뺀다
const toDay = (iso: string) => Date.parse(`${iso}T00:00:00Z`) / DAY_MS
const fromDay = (day: number) => new Date(day * DAY_MS).toISOString().slice(0, 10)

/** 지금 한국 날짜 'YYYY-MM-DD' */
export function seoulToday(now: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul' }).format(now)
}

/**
 * 고른 기간과 앞 기간을 날짜로 푼다. 직접 고른 기간이 거꾸로이거나, 오늘 뒤이거나, 너무 길면 null.
 * 오늘은 아직 모이는 중이라 넣을 수 있지만 숫자가 적게 나온다.
 */
export function resolveStatsPeriod(query: StatsPeriodQuery, today: string): StatsPeriod | null {
  let start: number
  let end: number
  if (query.period === 'custom') {
    start = toDay(query.start)
    end = toDay(query.end)
    if (start > end || end > toDay(today) || end - start + 1 > MAX_CUSTOM_DAYS) return null
  } else {
    end = toDay(today) - 1
    start = end - (query.period === '7d' ? 7 : 30) + 1
  }
  const length = end - start + 1
  return {
    current: { start: fromDay(start), end: fromDay(end) },
    previous: { start: fromDay(start - length), end: fromDay(start - 1) },
  }
}

/** 화면 주소의 조회 값으로 바꾼다 */
export function statsPeriodSearch(query: StatsPeriodQuery): string {
  const params = new URLSearchParams({ period: query.period })
  if (query.period === 'custom') {
    params.set('start', query.start)
    params.set('end', query.end)
  }
  return params.toString()
}

/** 기간 안의 날짜를 처음부터 차례로 */
export function spanDates({ start, end }: DateSpan): string[] {
  const dates: string[] = []
  for (let day = toDay(start); day <= toDay(end); day++) dates.push(fromDay(day))
  return dates
}

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']

/** '2026-10-26' → '10/26' */
export function formatStatsDay(iso: string): string {
  return `${Number(iso.slice(5, 7))}/${Number(iso.slice(8, 10))}`
}

/** '2026-10-26' → '10/26 (월)' */
export function formatStatsDayWithWeekday(iso: string): string {
  return `${formatStatsDay(iso)} (${WEEKDAYS[new Date(`${iso}T00:00:00Z`).getUTCDay()]})`
}
