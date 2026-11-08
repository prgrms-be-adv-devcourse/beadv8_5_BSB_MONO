import type { HTMLAttributes } from 'react'

import { cn } from '@/shared/lib'

export type BadgeTone =
  'brand' | 'success' | 'warning' | 'danger' | 'info' | 'neutral' | 'accent' | 'live' | 'onHero'

// 대회·주문 상태를 짧게 보여 주는 레이스 태그. 상태는 바탕색으로만 나누고 글자는 text-primary 하나다(10/10 색 규칙).
// 접수 중=brand, 참가 확정·할인 조건 달성=accent, 코발트·네이비 면 위 상태=live, Hero 안 보조 상태=onHero,
// 결제 완료·환불 완료=success, 마감 임박=warning, 마감=neutral, 결제 대기=info, 실패=danger
const TONE: Record<BadgeTone, string> = {
  brand: 'bg-bg-badge-brand text-text-primary',
  success: 'bg-bg-badge-success text-text-primary',
  warning: 'bg-bg-badge-warning text-text-primary',
  danger: 'bg-bg-badge-danger text-text-primary',
  info: 'bg-bg-badge-info text-text-primary',
  neutral: 'bg-bg-subtle text-text-primary',
  accent: 'bg-bg-accent-subtle text-text-primary',
  live: 'bg-bg-accent text-text-on-accent',
  onHero: 'bg-bg-hero-raised text-text-on-hero',
}

export type BadgeProps = HTMLAttributes<HTMLSpanElement> & { tone?: BadgeTone }

export function Badge({ tone = 'neutral', className, ...props }: BadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex shrink-0 items-center rounded-sm px-2 py-1 text-label-s whitespace-nowrap',
        TONE[tone],
        className,
      )}
      {...props}
    />
  )
}
