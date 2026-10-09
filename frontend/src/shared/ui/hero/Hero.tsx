import type { ReactNode } from 'react'

import { cn } from '@/shared/lib'

export type HeroProps = {
  /** race = 대회(큰 제목), crew = 크루 참가 현황(작은 제목) */
  type?: 'race' | 'crew'
  /** 예: SEOUL · 2026.11.15 SUN */
  eyebrow: ReactNode
  title: ReactNode
  subtitle?: ReactNode
  /** 뒤에 옅게 깔리는 큰 글자(예: 21K) */
  watermark?: ReactNode
  /** Badge(live·onHero) 묶음 */
  badges?: ReactNode
  /** 진행 막대(ProgressBar onHero)·숫자 타일(StatTile hero) 등 아래 내용 */
  children?: ReactNode
  className?: string
}

// 화면 맨 위 딥 네이비 영역. 숫자는 StatTile(hero)로 보여 준다.
export function Hero({
  type = 'race',
  eyebrow,
  title,
  subtitle,
  watermark,
  badges,
  children,
  className,
}: HeroProps) {
  return (
    <section
      className={cn(
        'relative flex w-full flex-col gap-4 overflow-hidden bg-bg-hero px-4 pt-7 pb-6',
        className,
      )}
    >
      <div aria-hidden className="absolute inset-x-0 top-0 h-1 bg-bg-accent" />
      {/* 워터마크는 제목 묶음 안에만 둔다. Hero 전체 기준으로 두면 아래 진행 막대 숫자와 겹친다. */}
      <div className="relative flex flex-col gap-4">
        {watermark && (
          // 200 크기 워터마크는 피그마 텍스트 스타일에 없는 장식이라 값을 그대로 적었다.
          <span
            aria-hidden
            className="pointer-events-none absolute -right-6 bottom-0 font-display text-[200px] leading-none font-extrabold text-text-on-hero opacity-10 select-none"
          >
            {watermark}
          </span>
        )}
        <p className="relative font-display text-eyebrow text-text-on-hero-accent">{eyebrow}</p>
        <div className="relative flex flex-col gap-1.5">
          <h1
            className={cn(
              'text-text-on-hero',
              type === 'race' ? 'font-display text-display-l' : 'text-heading-xl',
            )}
          >
            {title}
          </h1>
          {subtitle && <p className="text-body-m text-text-on-hero-muted">{subtitle}</p>}
        </div>
        {badges && <div className="relative flex flex-wrap gap-1.5">{badges}</div>}
      </div>
      {children && <div className="relative flex flex-col gap-4">{children}</div>}
    </section>
  )
}
