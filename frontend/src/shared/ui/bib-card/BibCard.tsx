import Image from 'next/image'
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
  /** 띠에 깔 대회 이미지(Image=On). 어두운 그라데이션을 덮어 흰 글자를 읽기 쉽게 한다. */
  image?: string
  /** 띠 높이에 맞춰 이미지의 어느 부분을 보일지 (object-position 값, 예: 50% 12%) */
  imagePosition?: string
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

// 배번표 모양 카드. 위 띠(색 또는 대회 이미지) + 아래 본문.
export function BibCard({
  type = 'ticket',
  eyebrow,
  status,
  number,
  unit,
  title,
  meta,
  progress,
  image,
  imagePosition,
  className,
}: BibCardProps) {
  const colors = BAND[type]
  const unitNode = unit && <span className={cn('text-body2-medium', colors.unit)}>{unit}</span>
  return (
    <article
      className={cn(
        'flex w-full flex-col overflow-hidden rounded-lg bg-bg-surface shadow-md',
        className,
      )}
    >
      <div className={cn('relative flex flex-col gap-1.5 overflow-hidden px-5 py-4', colors.band)}>
        {image && (
          <>
            <Image
              src={image}
              alt=""
              fill
              sizes="600px"
              className="object-cover"
              style={{ objectPosition: imagePosition }}
            />
            <div className="absolute inset-0 bg-linear-to-b from-[rgb(0_0_0/0.35)] to-[rgb(0_0_0/0.65)]" />
          </>
        )}
        <div className="relative flex items-center justify-between">
          <span className={cn('text-caption-medium', colors.eyebrow)}>{eyebrow}</span>
          {status}
        </div>
        <p className="relative flex items-baseline gap-1.5">
          {type === 'ticket' && unitNode}
          <span className={cn('font-display text-number-l', colors.number)}>{number}</span>
          {type === 'crew' && unitNode}
        </p>
      </div>
      <div className="flex flex-col gap-1.5 px-5 pt-2 pb-5">
        <p className="text-body1-bold text-text-primary">{title}</p>
        {progress}
        {meta && <p className="text-body2-regular text-text-secondary">{meta}</p>}
      </div>
    </article>
  )
}
