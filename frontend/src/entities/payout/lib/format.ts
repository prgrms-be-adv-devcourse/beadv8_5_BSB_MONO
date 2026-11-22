import { formatNumber } from '@/shared/lib'

import { formatMonthDay, formatWeekday } from './period'

// 정산 화면의 날짜 · 금액 글자. 금액은 응답 값을 그대로 쓰고 새로 계산하지 않는다.

/** 'YYYY-MM-DD'(또는 날짜시간) → '11/1 (일)' */
export function formatDayWithWeekday(value: string): string {
  const day = value.slice(0, 10)
  return `${formatMonthDay(day)} (${formatWeekday(day)})`
}

/** 'YYYY-MM-DDTHH:mm:ss' → '11/1 (일) 06:10' */
export function formatMonthDayTime(dateTime: string): string {
  return `${formatDayWithWeekday(dateTime)} ${dateTime.slice(11, 16)}`
}

/** 'YYYY-MM-DDTHH:mm:ss' → '2026-11-01 (일) 06:10' */
export function formatDateTimeWithWeekday(dateTime: string): string {
  const day = dateTime.slice(0, 10)
  return `${day} (${formatWeekday(day)}) ${dateTime.slice(11, 16)}`
}

/** 공급 월 'YYYY-MM' → '2026.10' */
export function formatSupplyMonth(month: string): string {
  return month.replace('-', '.')
}

/** 공급 월 'YYYY-MM' → '2026년 10월 공급분' */
export function supplyMonthTitle(month: string): string {
  return `${month.slice(0, 4)}년 ${Number(month.slice(5, 7))}월 공급분`
}

/** 지급액에서 빼는 금액(응답은 양수) → '−440,000원'. 0원은 기호 없이 */
export function formatDeduction(amount: number): string {
  return `${amount > 0 ? '−' : ''}${formatNumber(amount)}원`
}
