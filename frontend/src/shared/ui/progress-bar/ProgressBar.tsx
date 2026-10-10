import type { ReactNode } from 'react'

import { cn } from '@/shared/lib'

export type ProgressBarProps = {
  /** 0~100 */
  value: number
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
  onHero: { value: 'text-text-on-hero-success', fill: 'bg-text-on-hero-success' },
}

// 진행 정도(크루 결제 5/8명, 접수 마감률)를 막대로 보여 준다.
export function ProgressBar({
  value,
  label,
  valueText,
  tone = 'brand',
  size = 'm',
  className,
}: ProgressBarProps) {
  const percent = Math.min(100, Math.max(0, value))
  const complete = percent === 100
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
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percent}
        className={cn(
          'w-full overflow-hidden rounded-full',
          size === 's' ? 'h-1' : 'h-2',
          colors.track,
        )}
      >
        <div
          className={cn('h-full rounded-full', complete ? COMPLETE[tone].fill : colors.fill)}
          style={{ width: `${percent}%` }}
        />
      </div>
    </div>
  )
}
