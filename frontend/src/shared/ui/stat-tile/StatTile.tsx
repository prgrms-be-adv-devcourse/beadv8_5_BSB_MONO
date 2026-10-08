import type { ReactNode } from 'react'

import { cn } from '@/shared/lib'

export type StatTileTone = 'surface' | 'hero' | 'accent'

export type StatTileProps = {
  label: ReactNode
  /** 큰 숫자 */
  value: ReactNode
  /** 한글 단위(명, 남음 등). 비율·금액 같은 두 번째 정보는 caption에 넣는다. */
  unit?: ReactNode
  caption?: ReactNode
  tone?: StatTileTone
  className?: string
}

const TONE: Record<
  StatTileTone,
  { box: string; label: string; value: string; unit: string; caption: string }
> = {
  surface: {
    box: 'bg-bg-surface shadow-sm',
    label: 'text-text-tertiary',
    value: 'text-text-primary',
    unit: 'text-text-secondary',
    caption: 'text-text-tertiary',
  },
  hero: {
    box: 'bg-bg-hero-raised',
    label: 'text-text-on-hero-muted',
    value: 'text-text-on-hero',
    unit: 'text-text-on-hero-muted',
    caption: 'text-text-on-hero-muted',
  },
  accent: {
    box: 'bg-bg-accent',
    label: 'text-text-on-accent',
    value: 'text-text-on-accent',
    unit: 'text-text-on-accent',
    caption: 'text-text-on-accent',
  },
}

// 큰 숫자 하나를 담는 타일.
export function StatTile({
  label,
  value,
  unit,
  caption,
  tone = 'surface',
  className,
}: StatTileProps) {
  const colors = TONE[tone]
  return (
    <div
      className={cn(
        'flex min-w-0 flex-1 flex-col items-center gap-1 rounded-lg p-4',
        colors.box,
        className,
      )}
    >
      <span className={cn('w-full truncate text-center text-label-s', colors.label)}>{label}</span>
      <span className="flex items-baseline gap-1">
        <span className={cn('font-display text-display-m', colors.value)}>{value}</span>
        {unit && <span className={cn('text-label-m', colors.unit)}>{unit}</span>}
      </span>
      {caption && <span className={cn('text-caption', colors.caption)}>{caption}</span>}
    </div>
  )
}
