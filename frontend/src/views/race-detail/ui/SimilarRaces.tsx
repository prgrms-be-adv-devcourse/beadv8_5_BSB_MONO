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
      <ul className="flex flex-col gap-4">
        {races.map((race) => (
          <li key={race.id}>
            <Row race={race} />
          </li>
        ))}
      </ul>
    </section>
  )
}

function Row({ race }: { race: RaceListItem }) {
  return (
    <Link href={`/races/${race.id}`} className={cn('flex gap-3 rounded-md', FOCUS_RING)}>
      {race.image ? (
        <Image
          src={race.image}
          alt=""
          width={80}
          height={80}
          className="size-20 shrink-0 rounded-md object-cover"
        />
      ) : (
        <RacePoster
          seed={race.id}
          title=""
          label={race.posterLabel}
          className="size-20 shrink-0 rounded-md"
        />
      )}
      <div className="flex min-w-0 flex-1 flex-col items-start gap-1">
        <Badge tone={race.badge.tone}>{race.badge.label}</Badge>
        <p className="text-body1-bold">{race.title}</p>
        <p className="text-body2-regular text-text-secondary">{race.meta}</p>
        <span className="pt-1 text-body2-medium">{race.price}</span>
      </div>
    </Link>
  )
}
