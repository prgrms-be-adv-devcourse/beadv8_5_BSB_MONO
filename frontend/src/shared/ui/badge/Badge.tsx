import type { HTMLAttributes } from 'react'

import { cn } from '@/shared/lib'

export type BadgeTone =
  'brand' | 'success' | 'warning' | 'danger' | 'info' | 'neutral' | 'accent' | 'live' | 'onHero'

// 대회·주문 상태를 짧게 보여 주는 레이스 태그(피그마 v2/Badge). 톤마다 바탕과 글자색이 함께 바뀐다.
// 접수 중=brand, 참가 확정=accent, 파랑·검정 면 위 상태=live, Hero 안 보조 상태=onHero,
// 결제 완료·환불 완료=success, 마감 임박=warning, 마감=neutral, 결제 대기=info, 실패=danger
const TONE: Record<BadgeTone, string> = {
  brand: 'bg-bg-brand-subtle text-text-brand',
  success: 'bg-bg-badge-success text-text-badge-success',
  warning: 'bg-bg-badge-warning text-text-badge-warning',
  danger: 'bg-bg-badge-danger text-text-badge-danger',
  info: 'bg-bg-badge-info text-text-badge-default',
  neutral: 'bg-bg-badge-neutral text-text-badge-default',
  accent: 'bg-bg-brand-subtle text-text-brand',
  live: 'bg-bg-accent text-text-on-accent',
  onHero: 'bg-bg-hero-raised text-text-on-hero',
}

export type BadgeProps = HTMLAttributes<HTMLSpanElement> & { tone?: BadgeTone }

export function Badge({ tone = 'neutral', className, ...props }: BadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex shrink-0 items-center rounded-sm px-2 py-1 text-caption-medium whitespace-nowrap',
        TONE[tone],
        className,
      )}
      {...props}
    />
  )
}
