import { cn } from '@/shared/lib'

export type SkeletonProps = {
  /** text(줄) / rect(이미지·카드) / circle(아바타). 크기는 className으로 정한다. */
  shape?: 'text' | 'rect' | 'circle'
  className?: string
}

const SHAPE = {
  text: 'h-3 rounded-sm',
  rect: 'rounded-md',
  circle: 'rounded-full',
}

// 내용을 불러오는 동안 자리를 잡아 주는 회색 상자. 실제 화면 배치대로 조합한다.
export function Skeleton({ shape = 'text', className }: SkeletonProps) {
  return <div aria-hidden className={cn('animate-pulse bg-bg-track', SHAPE[shape], className)} />
}
