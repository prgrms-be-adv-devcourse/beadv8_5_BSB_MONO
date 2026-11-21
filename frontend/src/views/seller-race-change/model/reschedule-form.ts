import { addDays, format } from 'date-fns'

import { formatRaceDate } from '@/entities/race'

/** 일정이 바뀐 날부터 전액 환불을 허용하는 기간 (백엔드 scheduleChangedAt 기준 7일) */
export const SCHEDULE_CHANGE_REFUND_DAYS = 7

/** 오늘 알리면 전액 환불을 받을 수 있는 마지막 날 (M/d) */
export function refundUntil(now: Date = new Date()): string {
  return format(addDays(now, SCHEDULE_CHANGE_REFUND_DAYS), 'M/d')
}

/**
 * 참가자에게 보낼 알림 문구 미리보기.
 * currentRaceDate는 서버 일시(2026-11-01T08:00:00), nextRaceDay는 새 대회일 'yyyy-MM-dd'(없으면 null)
 */
export function rescheduleNotice(
  title: string,
  currentRaceDate: string,
  nextRaceDay: string | null,
  now?: Date,
): string {
  const next = nextRaceDay ? formatRaceDate(nextRaceDay) : '새 대회일'
  return (
    `[${title}] 대회일이 ${formatRaceDate(currentRaceDate)}에서 ${next}로 바뀌었어요.\n` +
    `오늘부터 ${SCHEDULE_CHANGE_REFUND_DAYS}일 동안(${refundUntil(now)}까지)은 전액 환불받을 수 있어요.`
  )
}
