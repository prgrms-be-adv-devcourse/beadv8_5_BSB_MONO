import Image from 'next/image'
import Link from 'next/link'
import { useId } from 'react'

import { cn } from '@/shared/lib'
import { Badge, FOCUS_RING } from '@/shared/ui'

import type { RaceListItem } from '../model/types'
import { RacePoster } from './RacePoster'

// 피그마 M02-6 비슷한 대회 · 대회 찾기 AI 추천: 대회 찾기 목록 줄과 같은 모양의 줄 목록. 비어 있으면 영역을 숨긴다.
export function RaceRecommendSection({
  title,
  races,
  className,
}: {
  title: string
  races: RaceListItem[]
  className?: string
}) {
  const headingId = useId()
  if (races.length === 0) return null

  return (
    <section aria-labelledby={headingId} className={cn('flex flex-col gap-4', className)}>
      <h2 id={headingId} className="text-title3">
        {title}
      </h2>
      <ul className="flex flex-col gap-6">
        {races.map((race) => (
          <li key={race.id}>
            <Row race={race} />
          </li>
        ))}
      </ul>
    </section>
  )
}

// 피그마: 대회 찾기 목록 줄과 같은 모양(사진 96×72 위 상태 칩)
function Row({ race }: { race: RaceListItem }) {
  return (
    <Link
      href={`/races/${race.id}`}
      className={cn('flex items-center gap-4 rounded-md', FOCUS_RING)}
    >
      <span className="relative block h-18 w-24 shrink-0">
        {race.image ? (
          <Image
            src={race.image}
            alt=""
            width={96}
            height={72}
            className="h-18 w-24 rounded-md object-cover"
          />
        ) : (
          <RacePoster
            seed={race.id}
            title=""
            label={race.posterLabel}
            className="h-18 w-24 rounded-md"
          />
        )}
        {race.badge && (
          <Badge tone={race.badge.tone} className="absolute top-2 left-2">
            {race.badge.label}
          </Badge>
        )}
      </span>
      <span className="flex min-w-0 flex-1 flex-col gap-1">
        <span className="truncate text-body1-bold">{race.title}</span>
        <span className="truncate text-caption-regular text-text-secondary">{race.meta}</span>
        <span className="text-body1-medium">{race.price}</span>
      </span>
    </Link>
  )
}
