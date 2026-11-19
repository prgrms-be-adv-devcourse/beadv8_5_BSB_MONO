'use client'

import Image from 'next/image'
import Link from 'next/link'

import { formatDDay, formatRaceDateTime, RacePoster } from '@/entities/race'
import { cn } from '@/shared/lib'
import { FOCUS_RING, Icon } from '@/shared/ui'

import { useNextRace } from '../model/use-next-race'

// 피그마 v3 홈 · 다음 대회: 로그인했고 다가오는 참가권이 있을 때만 보인다
export function NextRaceCard() {
  const next = useNextRace()
  if (!next) return null

  const when = formatRaceDateTime(next.raceDate)
  return (
    <div className="px-4 pt-4">
      <Link
        href={`/tickets/${next.ticketId}`}
        className={cn(
          'flex items-center gap-4 rounded-lg border border-border-default bg-bg-surface p-4 shadow-sm',
          FOCUS_RING,
        )}
      >
        <span className="relative block size-16 shrink-0 overflow-hidden rounded-md">
          {next.image ? (
            <Image src={next.image} alt="" fill sizes="64px" className="object-cover" />
          ) : (
            <RacePoster seed={next.raceId} title="" label="" className="size-full" />
          )}
        </span>
        <span className="flex min-w-0 flex-1 flex-col gap-0.5">
          <span className="text-body1-bold text-text-brand">
            다음 대회 · {formatDDay(next.raceDate, next.now)}
          </span>
          <span className="truncate text-body1-bold text-text-primary">{next.title}</span>
          <span className="truncate text-caption-regular text-text-secondary">
            {[when, next.courseName].filter(Boolean).join(' · ')}
          </span>
        </span>
        <Icon name="ChevronRight" className="size-5 shrink-0 text-text-tertiary" />
      </Link>
    </div>
  )
}
