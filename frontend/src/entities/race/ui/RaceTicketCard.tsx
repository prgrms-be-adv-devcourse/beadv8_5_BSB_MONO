import Image from 'next/image'
import type { ReactNode } from 'react'

import { cn } from '@/shared/lib'

import { RacePoster } from './RacePoster'

export type RaceTicketCardProps = {
  /** 없으면 색 포스터 */
  image?: string
  title: string
  /** 일시 · 장소 */
  meta: string
  /** 종목 · 가격 */
  ticket: string
  /** 오른쪽 링크·버튼 (변경, 삭제 등) */
  action?: ReactNode
  className?: string
}

// 피그마 v3/RaceCard 가로: 사진 96×72 · 둥글기 12 · 글 순서. "card / 대회 요약": 신청·장바구니·결제에서 고른 참가권을 한 장으로 요약한다
export function RaceTicketCard({
  image,
  title,
  meta,
  ticket,
  action,
  className,
}: RaceTicketCardProps) {
  return (
    <div className={cn('flex items-center gap-4', className)}>
      {image ? (
        <Image
          src={image}
          alt=""
          width={96}
          height={72}
          className="h-18 w-24 shrink-0 rounded-md object-cover"
        />
      ) : (
        <RacePoster seed={title} title="" label="" className="h-18 w-24 shrink-0 rounded-md" />
      )}
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <p className="truncate text-body1-bold">{title}</p>
        <p className="truncate text-caption-regular text-text-secondary">{meta}</p>
        <p className="text-body2-medium">{ticket}</p>
      </div>
      {action}
    </div>
  )
}
