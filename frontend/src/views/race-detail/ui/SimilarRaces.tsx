'use client'

import { useQuery } from '@tanstack/react-query'
import Image from 'next/image'
import Link from 'next/link'

import {
  RacePoster,
  similarRacesQueryOptions,
  toRaceListItem,
  type RaceListItem,
} from '@/entities/race'
import { cn } from '@/shared/lib'
import { Badge, FOCUS_RING } from '@/shared/ui'

// 피그마 M02-6 비슷한 대회: 대회 찾기 목록 줄과 같은 모양 3줄. 못 불러오거나 0개면 영역을 숨긴다.
export function SimilarRaces({ raceId }: { raceId: string }) {
  const { data } = useQuery(similarRacesQueryOptions(raceId))
  const races = (data ?? []).map((race) => toRaceListItem(race))
  if (races.length === 0) return null

  return (
    <section
      aria-labelledby="similar-races"
      className="flex flex-col gap-4 bg-bg-surface px-4 py-6"
    >
      <h2 id="similar-races" className="text-title3">
        비슷한 대회
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
