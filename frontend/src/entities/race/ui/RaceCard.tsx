import Image from 'next/image'
import Link from 'next/link'

import { cn } from '@/shared/lib'
import { FOCUS_RING } from '@/shared/ui'

import type { RaceSummary } from '../model/types'
import { RacePoster } from './RacePoster'

export type RaceCardProps = { race: RaceSummary; className?: string }

// 피그마 v2 · 대회 카드: 4:3 이미지 위 상태 칩 + 대회명 + 대회일 · 장소 + 가격
export function RaceCard({ race, className }: RaceCardProps) {
  return (
    <Link
      href={`/races/${race.id}`}
      className={cn('flex flex-col gap-2.5 rounded-md', FOCUS_RING, className)}
    >
      <span className="relative block aspect-4/3 w-full overflow-hidden rounded-md">
        {race.image ? (
          <Image src={race.image} alt="" fill sizes="276px" className="object-cover" />
        ) : (
          <RacePoster
            seed={race.id}
            title={race.title}
            label={race.posterLabel}
            // 위쪽 상태 칩과 겹치지 않게 종목 글자를 내린다
            className="size-full pt-11"
          />
        )}
        {/* 이미지 위 반투명 칩은 피그마에도 변수 없이 쓴 값이다 */}
        {race.status && (
          <span className="absolute top-2.5 left-2.5 rounded-sm bg-[rgb(18_23_33/0.72)] px-2 py-0.75 text-caption-medium text-text-on-image">
            {race.status}
          </span>
        )}
      </span>
      <span className="flex flex-col gap-1">
        <span className="text-body1-bold break-keep">{race.title}</span>
        <span className="truncate text-caption-regular text-text-tertiary">{race.meta}</span>
        <span className="text-body2-medium text-text-secondary">{race.price}</span>
      </span>
    </Link>
  )
}
