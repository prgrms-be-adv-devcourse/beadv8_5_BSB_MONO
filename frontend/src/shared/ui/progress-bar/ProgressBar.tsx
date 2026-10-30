import type { ReactNode } from 'react'

import { cn } from '@/shared/lib'

export type ProgressBarProps = {
  /** 0~100. indeterminate면 쓰지 않는다. */
  value?: number
  /** 진행률 없이 기다리는 중임을 보여 준다(대기열). 막대가 계속 움직인다. */
  indeterminate?: boolean
  /** 머리줄(label) 없이 쓸 때 화면 읽기 프로그램이 읽을 이름 */
  'aria-label'?: string
  label?: ReactNode
  /** 오른쪽 숫자(예: 5/8명) */
  valueText?: ReactNode
  /** 밝은 바탕은 brand, 네이비 Hero 안은 onHero */
  tone?: 'brand' | 'onHero'
  /** S 4 / M 8 */
  size?: 's' | 'm'
  className?: string
}

const TONE = {
  brand: {
    label: 'text-text-primary',
    value: 'text-text-primary',
    track: 'bg-bg-track',
    fill: 'bg-bg-brand',
  },
  onHero: {
    label: 'text-text-on-hero',
    value: 'text-text-on-hero-accent',
    track: 'bg-bg-track-on-hero',
    fill: 'bg-bg-accent',
  },
}

// 100(다 참)일 때만 초록으로 바뀐다.
const COMPLETE = {
  brand: { value: 'text-text-success', fill: 'bg-text-success' },
  onHero: { value: 'text-text-on-hero-success', fill: 'bg-bg-success-on-hero' },
}

// 진행 정도(크루 결제 5/8명, 접수 마감률)를 막대로 보여 준다. indeterminate면 값 없이 기다리는 중을 보여 준다.
export function ProgressBar({
  value = 0,
  indeterminate = false,
  'aria-label': ariaLabel,
  label,
  valueText,
  tone = 'brand',
  size = 'm',
  className,
}: ProgressBarProps) {
  const percent = Math.min(100, Math.max(0, value))
  const complete = !indeterminate && percent === 100
  const colors = TONE[tone]
  return (
    <div className={cn('flex w-full flex-col gap-2', className)}>
      {(label || valueText) && (
        <div className="flex items-center justify-between gap-2">
          <span className={cn('text-label-m', colors.label)}>{label}</span>
          <span className={cn('text-label-strong', complete ? COMPLETE[tone].value : colors.value)}>
            {valueText}
          </span>
        </div>
      )}
      <div
        role="progressbar"
        aria-label={ariaLabel}
        aria-valuemin={0}
        aria-valuemax={100}
        // 값이 없는 막대는 aria-valuenow를 빼야 '진행 중'으로 읽힌다
        aria-valuenow={indeterminate ? undefined : percent}
        className={cn(
          'w-full overflow-hidden rounded-full',
          size === 's' ? 'h-1' : 'h-2',
          colors.track,
        )}
      >
        {indeterminate ? (
          // 시안의 30% 길이 막대가 왼쪽에서 오른쪽으로 계속 지나간다
          <div
            className={cn(
              'h-full w-[30%] animate-progress-indeterminate rounded-full motion-reduce:animate-none',
              colors.fill,
            )}
          />
        ) : (
          <div
            className={cn('h-full rounded-full', complete ? COMPLETE[tone].fill : colors.fill)}
            style={{ width: `${percent}%` }}
          />
        )}
      </div>
    </div>
  )
}
