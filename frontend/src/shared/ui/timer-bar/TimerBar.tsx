import type { ReactNode } from 'react'

import { cn } from '@/shared/lib'

export type TimerBarProps = {
  /** 왼쪽 안내 문구 */
  message: ReactNode
  /** 남은 초. mm:ss로 보여 준다. */
  remainingSeconds: number
  className?: string
}

/** 남은 초를 mm:ss로 바꾼다. 0보다 작으면 00:00. */
export function formatTimer(seconds: number) {
  const total = Math.max(0, Math.floor(seconds))
  const m = Math.floor(total / 60)
  const s = total % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

// 피그마 "timer bar (상단 고정)". 헤더 바로 아래에 붙어 남은 시간을 보여 준다.
// 대기열 내 차례(M05-Q2) → 참가 신청(M05) → 결제(M07)까지 같은 띠를 쓴다.
export function TimerBar({ message, remainingSeconds, className }: TimerBarProps) {
  return (
    <div
      role="timer"
      className={cn(
        'sticky top-(--layout-header-height) z-[5] flex items-center justify-between gap-2 bg-bg-brand-subtle px-4 py-3 text-text-brand',
        className,
      )}
    >
      <p className="text-body2-regular">{message}</p>
      <p className="text-body1-bold tabular-nums">{formatTimer(remainingSeconds)}</p>
    </div>
  )
}
