import type { Metadata } from 'next'
import { notFound } from 'next/navigation'
import { cache } from 'react'

import { RaceDetailPage } from '@/views/race-detail'
import { fetchRaceDetail } from '@/entities/race'
import { BASE_OPEN_GRAPH, DEFAULT_OG_IMAGE } from '@/shared/config'

// 메타데이터와 화면이 같은 요청 안에서 대회를 한 번만 불러온다
const getRace = cache(fetchRaceDetail)

export async function generateMetadata({
  params,
}: PageProps<'/races/[raceId]'>): Promise<Metadata> {
  const { raceId } = await params
  // 불러오지 못하면 루트 레이아웃의 기본 제목 · 설명을 쓴다
  const race = await getRace(raceId).catch(() => null)
  if (!race) return {}
  const description = [race.startsAt, race.place, '러닝 대회 참가권'].filter(Boolean).join(' · ')
  return {
    title: race.title,
    description,
    openGraph: {
      ...BASE_OPEN_GRAPH,
      title: race.title,
      description,
      url: `/races/${race.id}`,
      // shortcut: 대표 이미지 주소는 30분 뒤 만료된다. 나중에 다시 가져가는 검색엔진에는 깨질 수 있어, 영구 주소(CDN)가 생기면 바꾼다
      images: race.images[0] ?? DEFAULT_OG_IMAGE,
    },
  }
}

// ?preview=applied 로 M02-1(이미 신청함) 상태를 볼 수 있다. TODO(api): 신청 여부를 서버에서 받는다.
// ?option=<종목 ID> 처럼 종목을 골라 둔 채로 열 수 있다(곧 마감 카드).
export default async function Page({ params, searchParams }: PageProps<'/races/[raceId]'>) {
  const [{ raceId }, { preview, option }] = await Promise.all([params, searchParams])
  const race = await getRace(raceId)
  if (!race) notFound()
  return (
    <RaceDetailPage
      race={race}
      optionId={typeof option === 'string' ? option : undefined}
      alreadyApplied={preview === 'applied'}
    />
  )
}
