import type { ReactNode } from 'react'

import { cn } from '@/shared/lib'

export type BibCardProps = {
  /** ticket = 내 참가권(코발트 띠), crew = 크루 참가 현황(네이비 띠) */
  type?: 'ticket' | 'crew'
  eyebrow: ReactNode
  /** 오른쪽 위 상태 Badge(live) */
  status?: ReactNode
  number: ReactNode
  /** 예: No. */
  unit?: ReactNode
  title: ReactNode
  meta?: ReactNode
  /** crew에서 제목 아래 진행 막대(ProgressBar S) */
  progress?: ReactNode
  className?: string
}

const BAND = {
  ticket: {
    band: 'bg-bg-brand',
    eyebrow: 'text-text-on-brand',
    number: 'text-text-on-brand',
    unit: 'text-text-on-brand',
  },
  crew: {
    band: 'bg-bg-hero',
    eyebrow: 'text-text-on-hero-accent',
    number: 'text-text-on-hero',
    unit: 'text-text-on-hero-muted',
  },
}

// 배번표 모양 카드. 띠와 본문 사이에 절취선 구멍 20개가 있다.
export function BibCard({
  type = 'ticket',
  eyebrow,
  status,
  number,
  unit,
  title,
  meta,
  progress,
  className,
}: BibCardProps) {
  const colors = BAND[type]
  const unitNode = unit && <span className={cn('text-label-m', colors.unit)}>{unit}</span>
  return (
    <article
      className={cn(
        'flex w-full flex-col overflow-hidden rounded-lg bg-bg-surface shadow-md',
        className,
      )}
    >
      <div className={cn('flex flex-col gap-1.5 px-5 py-4', colors.band)}>
        <div className="flex items-center justify-between">
          <span className={cn('font-display text-eyebrow', colors.eyebrow)}>{eyebrow}</span>
          {status}
        </div>
        <p className="flex items-baseline gap-1.5">
          {type === 'ticket' && unitNode}
          <span className={cn('font-display text-display-xl', colors.number)}>{number}</span>
          {type === 'crew' && unitNode}
        </p>
      </div>
      <div aria-hidden className="flex h-3 items-center justify-between px-3">
        {Array.from({ length: 20 }, (_, i) => (
          <span key={i} className="size-1.25 rounded-full bg-bg-subtle" />
        ))}
      </div>
      <div className="flex flex-col gap-1.5 px-5 pt-2 pb-5">
        <p className="text-heading-s text-text-primary">{title}</p>
        {progress}
        {meta && <p className="text-body-s text-text-secondary">{meta}</p>}
      </div>
    </article>
  )
}
