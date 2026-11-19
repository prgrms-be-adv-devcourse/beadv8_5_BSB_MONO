import type { ReactNode } from 'react'

import { cn } from '@/shared/lib'

import { Icon } from '../icon/icons'

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

// 피그마 v2/Timer. 시간 제한이 있는 화면(대기열·참가 신청·결제)은 모두 이 카드를 쓴다.
export function TimerBar({ message, remainingSeconds, className }: TimerBarProps) {
  return (
    <div role="timer" className={cn('bg-bg-surface px-4 pt-4 pb-0', className)}>
      <div className="flex items-center justify-between gap-4 rounded-lg bg-bg-brand p-4 text-text-on-brand">
        <div className="flex min-w-0 flex-col gap-1">
          <p className="text-body1-bold">남은 시간</p>
          <p className="text-caption-regular">{message}</p>
        </div>
        <p className="font-display text-number-m tabular-nums">{formatTimer(remainingSeconds)}</p>
      </div>
    </div>
  )
}

// 피그마 v3 헤더 오른쪽 남은 시간: 시계 아이콘 + mm:ss. 참가 신청 · 결제 헤더(AppHeader action)에 둔다.
export function HeaderTimer({ remainingSeconds }: { remainingSeconds: number }) {
  return (
    <div
      role="timer"
      aria-label="남은 시간"
      className="mr-3 flex shrink-0 items-center gap-1 text-body1-bold text-text-primary tabular-nums"
    >
      <Icon name="Clock" className="size-4" aria-hidden />
      {formatTimer(remainingSeconds)}
    </div>
  )
}
