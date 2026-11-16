import type { productModel } from '@/shared/api'

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']

/** 제목 아래 기준일: 「11월 3일(화) 기준」 */
export function formatBaseDate(now: Date): string {
  return `${now.getMonth() + 1}월 ${now.getDate()}일(${WEEKDAYS[now.getDay()]}) 기준`
}

/** 지급일(yyyy-MM-dd…) → 「11월 10일」. 시간대에 흔들리지 않게 글자에서 바로 읽는다 */
export function formatKoreanMonthDay(isoDate: string): string {
  const [, month, day] = isoDate.slice(0, 10).split('-').map(Number)
  return `${month}월 ${day}일`
}

/** 판매율(%). 정원이 0이면 0 */
export function soldRate(sold: number, capacity: number): number {
  return capacity > 0 ? Math.round((sold / capacity) * 100) : 0
}

export type CourseBar = {
  id: number
  name: string
  sold: number
  quota: number
  /** 정원 막대 높이: 가장 큰 정원을 100으로 본 비율 */
  quotaPct: number
  /** 정원 막대 안에서 판매가 차지하는 비율 */
  fillPct: number
}

/** 코스별 판매 현황 카드의 요약 숫자와 막대. 남은 자리 = 정원 - 판매 - 결제 대기 */
export function courseSales(courses: productModel.SellerRaceCourseDto[]) {
  const max = Math.max(0, ...courses.map((c) => c.quota ?? 0))
  const pct = (value: number) => (max > 0 ? (value / max) * 100 : 0)
  let sold = 0
  let reserved = 0
  let quota = 0
  const bars: CourseBar[] = courses.map((c) => {
    sold += c.sold ?? 0
    reserved += c.reserved ?? 0
    quota += c.quota ?? 0
    return {
      id: c.raceCourseId ?? 0,
      name: c.name ?? '',
      sold: c.sold ?? 0,
      quota: c.quota ?? 0,
      quotaPct: pct(c.quota ?? 0),
      fillPct: c.quota ? ((c.sold ?? 0) / c.quota) * 100 : 0,
    }
  })
  return { sold, reserved, remaining: Math.max(quota - sold - reserved, 0), bars }
}
