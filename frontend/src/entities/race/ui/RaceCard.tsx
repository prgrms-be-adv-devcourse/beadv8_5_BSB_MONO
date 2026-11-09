import Image from 'next/image'
import Link from 'next/link'

import { cn } from '@/shared/lib'
import { FOCUS_RING, Icon } from '@/shared/ui'

import type { RaceSummary } from '../model/types'
import { RacePoster } from './RacePoster'

export type RaceCardProps = { race: RaceSummary; className?: string }

// 피그마 ImageCard: 1:1 대표 이미지(정책 2장 이미지 규격) + 대회명 + 메타 두 줄
export function RaceCard({ race, className }: RaceCardProps) {
  return (
    <Link
      href={race.option ? `/races/${race.id}?option=${race.option}` : `/races/${race.id}`}
      className={cn('flex flex-col gap-2 rounded-md', FOCUS_RING, className)}
    >
      {race.image ? (
        <Image
          src={race.image}
          alt=""
          width={216}
          height={216}
          className="aspect-square w-full rounded-md object-cover"
        />
      ) : (
        <RacePoster
          seed={race.id}
          title={race.title}
          label={race.posterLabel}
          className="aspect-square w-full rounded-md"
        />
      )}
      <p className="text-body1-bold break-keep whitespace-pre-line">{race.title}</p>
      <div className="flex flex-col gap-1 text-body2-regular text-text-secondary">
        <RaceMeta icon="Calendar">{race.schedule}</RaceMeta>
        <RaceMeta icon="MapPin">{race.place}</RaceMeta>
      </div>
    </Link>
  )
}

function RaceMeta({ icon, children }: { icon: 'Calendar' | 'MapPin'; children: string }) {
  return (
    <span className="flex items-center gap-1">
      <Icon name={icon} className="size-3.5" />
      <span className="truncate">{children}</span>
    </span>
  )
}
