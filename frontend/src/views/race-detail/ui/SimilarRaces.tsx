'use client'

import { useQuery } from '@tanstack/react-query'

import { RaceRecommendSection, similarRacesQueryOptions, toRaceListItem } from '@/entities/race'

// 피그마 M02-6 비슷한 대회: 대회 찾기 목록 줄과 같은 모양 3줄. 못 불러오거나 0개면 영역을 숨긴다.
export function SimilarRaces({ raceId }: { raceId: string }) {
  const { data } = useQuery(similarRacesQueryOptions(raceId))
  return (
    <RaceRecommendSection
      title="비슷한 대회"
      races={(data ?? []).map((race) => toRaceListItem(race))}
      className="bg-bg-surface px-4 py-6"
    />
  )
}
