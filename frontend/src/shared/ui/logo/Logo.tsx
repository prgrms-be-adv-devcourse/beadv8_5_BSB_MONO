import Image from 'next/image'

import { cn } from '@/shared/lib'

// 피그마 Logo 페이지에서 받은 파일(public/brand). 글자색이 정해진 SVG라 바탕에 맞는 파일을 고른다.
const LOGO = {
  /** 밝은 바탕 헤더용 글자 로고 95.6×28 */
  wordmark: { src: '/brand/wordmark.svg', width: 95.6073, height: 28 },
  /** 네이비(Hero) 바탕 사이드바용 글자 로고 */
  wordmarkOnHero: { src: '/brand/wordmark-on-hero.svg', width: 95.6073, height: 28 },
  /** 하단 탭 가운데 홈 버튼 48 */
  navHome: { src: '/brand/nav-home.svg', width: 48, height: 48 },
  /** 접힌 사이드바용 정사각 로고 32 */
  symbol: { src: '/brand/symbol-on-hero.svg', width: 32, height: 32 },
  /** 빈 화면·불러오기 실패 화면용 회색 로고 32. 글자는 뚫려 있어 바탕색이 보인다 */
  symbolMuted: { src: '/brand/symbol-muted.svg', width: 32, height: 32 },
} as const

export type LogoVariant = keyof typeof LOGO

export type LogoProps = {
  variant?: LogoVariant
  /** 옆 글자가 뜻을 다 전하는 장식용이면 화면 낭독기가 읽지 않게 한다 */
  decorative?: boolean
  className?: string
}

export function Logo({ variant = 'wordmark', decorative = false, className }: LogoProps) {
  const { src, width, height } = LOGO[variant]
  return (
    <Image
      src={src}
      width={width}
      height={height}
      alt={decorative ? '' : '크루런'}
      aria-hidden={decorative || undefined}
      unoptimized
      priority={!decorative}
      className={cn('shrink-0', className)}
    />
  )
}
