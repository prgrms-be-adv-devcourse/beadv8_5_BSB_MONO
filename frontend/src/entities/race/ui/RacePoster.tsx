import { cn } from '@/shared/lib'

// 대회 목록 API는 이미지를 주지 않는다. 카드에는 대회 정보로 만든 색 포스터를 그린다.
const TONES = [
  'bg-bg-hero text-text-on-hero',
  'bg-bg-brand text-text-on-brand',
  'bg-bg-accent text-text-on-accent',
] as const

function toneOf(seed: string) {
  let hash = 0
  for (const ch of seed) hash = (hash * 31 + ch.charCodeAt(0)) >>> 0
  return TONES[hash % TONES.length]
}

export type RacePosterProps = {
  /** 색을 고르는 기준 (대회 ID) */
  seed: string
  title: string
  /** 크게 쓰는 종목 (예: 10K · 5K) */
  label: string
  className?: string
}

export function RacePoster({ seed, title, label, className }: RacePosterProps) {
  return (
    <div
      aria-hidden
      className={cn('flex flex-col justify-between overflow-hidden p-3', toneOf(seed), className)}
    >
      <span className="text-title3 break-keep">{label}</span>
      <span className="line-clamp-2 text-caption-regular break-keep opacity-85">{title}</span>
    </div>
  )
}
