/** 지급일(yyyy-MM-dd…) → 「11월 10일」. 시간대에 흔들리지 않게 글자에서 바로 읽는다 */
export function formatKoreanMonthDay(isoDate: string): string {
  const [, month, day] = isoDate.slice(0, 10).split('-').map(Number)
  return `${month}월 ${day}일`
}

/** 판매율(%). 정원이 0이면 0 */
export function soldRate(sold: number, capacity: number): number {
  return capacity > 0 ? Math.round((sold / capacity) * 100) : 0
}

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']

/** 지급일(yyyy-MM-dd…) → 「11월 20일 (금)」 */
export function formatKoreanMonthDayWeekday(isoDate: string): string {
  const [year, month, day] = isoDate.slice(0, 10).split('-').map(Number)
  const weekday = WEEKDAYS[new Date(Date.UTC(year, month - 1, day)).getUTCDay()]
  return `${month}월 ${day}일 (${weekday})`
}
