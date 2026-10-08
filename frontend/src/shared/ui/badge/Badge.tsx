import type { HTMLAttributes } from 'react'

import { cn } from '@/shared/lib'

export type BadgeTone =
  'brand' | 'success' | 'warning' | 'danger' | 'info' | 'neutral' | 'accent' | 'live' | 'onHero'

// 대회·주문 상태를 짧게 보여 주는 레이스 태그.
// 접수 중=brand, 참가 확정·할인 조건 달성=accent, 코발트·네이비 면 위 상태=live, Hero 안 보조 상태=onHero,
// 결제 완료·환불 완료=success, 마감 임박=warning, 마감=neutral, 결제 대기=info, 실패=danger
const TONE: Record<BadgeTone, string> = {
  brand: 'bg-bg-subtle text-text-brand',
  success: 'bg-bg-subtle text-text-success',
  warning: 'bg-bg-subtle text-text-warning',
  danger: 'bg-bg-subtle text-text-danger',
  info: 'bg-bg-subtle text-text-info',
  neutral: 'bg-bg-subtle text-text-secondary',
  accent: 'bg-bg-hero text-text-on-hero-accent',
  live: 'bg-bg-accent text-text-on-accent',
  onHero: 'bg-bg-hero-raised text-text-on-hero',
}

export type BadgeProps = HTMLAttributes<HTMLSpanElement> & { tone?: BadgeTone }

// 모서리 3은 피그마 Radius 변수에 없는 값이라 그대로 적었다.
export function Badge({ tone = 'neutral', className, ...props }: BadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex shrink-0 items-center gap-1.25 rounded-[3px] py-0.75 pr-1.75 pl-1.5 text-label-tag whitespace-nowrap',
        TONE[tone],
        className,
      )}
      {...props}
    />
  )
}
