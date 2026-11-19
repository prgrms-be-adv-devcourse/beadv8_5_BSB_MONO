import type { ComponentProps, ReactNode } from 'react'

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
  /** 숫자 크기. 좁은 3칸 배치(대회 상세 등)는 s */
  size?: 'm' | 's'
  /** tile: 칸마다 카드(기본). cell: 카드 없이 왼쪽 정렬 칸 — StatStrip 안에서 쓴다(판매자 숫자 요약, 2070:66595) */
  layout?: 'tile' | 'cell'
  /** cell에서 숫자를 빨갛게(지급보류·돌려줄 금액 등 2070:69108) */
  danger?: boolean
  className?: string
}

const TONE: Record<
  StatTileTone,
  { box: string; label: string; value: string; unit: string; caption: string }
> = {
  surface: {
    box: 'bg-bg-surface shadow-sm inset-ring inset-ring-border-default',
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
  size = 'm',
  layout = 'tile',
  danger = false,
  className,
}: StatTileProps) {
  const colors = TONE[tone]
  if (layout === 'cell') {
    return (
      <div className={cn('flex min-w-0 flex-1 flex-col items-start gap-1 px-6', className)}>
        <span className="w-full truncate text-caption-regular text-text-secondary">{label}</span>
        <span className="flex items-baseline gap-1">
          <span
            className={cn(
              size === 'm' ? 'font-display text-number-s' : 'text-title2',
              danger ? 'text-text-danger' : 'text-text-primary',
            )}
          >
            {value}
          </span>
          {unit && <span className="text-caption-regular text-text-secondary">{unit}</span>}
        </span>
        {caption && <span className="text-caption-regular text-text-secondary">{caption}</span>}
      </div>
    )
  }
  return (
    <div
      className={cn(
        'flex min-w-0 flex-1 flex-col items-center gap-1 rounded-lg p-4',
        colors.box,
        className,
      )}
    >
      <span className={cn('w-full truncate text-center text-caption-medium', colors.label)}>
        {label}
      </span>
      <span className="flex items-baseline gap-1">
        <span
          className={cn(size === 'm' ? 'font-display text-number-s' : 'text-title2', colors.value)}
        >
          {value}
        </span>
        {unit && <span className={cn('text-body1-medium', colors.unit)}>{unit}</span>}
      </span>
      {caption && <span className={cn('text-caption-regular', colors.caption)}>{caption}</span>}
    </div>
  )
}

// 숫자 칸(StatTile layout="cell") 여러 개를 한 카드에 담고 칸 사이에 세로선을 긋는다.
export function StatStrip({
  children,
  className,
  ...rest
}: { children: ReactNode; className?: string } & Omit<
  ComponentProps<'div'>,
  'children' | 'className'
>) {
  return (
    <div
      {...rest}
      className={cn(
        'flex w-full rounded-md bg-bg-surface py-6 shadow-sm inset-ring inset-ring-border-default [&>*+*]:border-l [&>*+*]:border-border-default',
        className,
      )}
    >
      {children}
    </div>
  )
}
