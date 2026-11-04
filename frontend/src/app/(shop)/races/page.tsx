import { dehydrate, HydrationBoundary } from '@tanstack/react-query'
import type { Metadata } from 'next'

import { RaceListPage, type RaceListState } from '@/views/race-list'
import { DEFAULT_RACE_SORT, raceListQueryOptions } from '@/entities/race'
import { getQueryClient } from '@/shared/api'
import { BASE_OPEN_GRAPH, DEFAULT_OG_IMAGE } from '@/shared/config'

const TITLE = '대회 찾기'
const DESCRIPTION = '접수 중인 마라톤 · 10K · 5K · 트레일 러닝 대회 참가권을 찾아보세요.'

export const metadata: Metadata = {
  title: TITLE,
  description: DESCRIPTION,
  openGraph: {
    ...BASE_OPEN_GRAPH,
    title: TITLE,
    description: DESCRIPTION,
    url: '/races',
    images: DEFAULT_OG_IMAGE,
  },
}

const STATES: RaceListState[] = ['loading', 'empty', 'menu', 'sort']

// ?preview=loading|empty|menu|sort 로 M18의 로딩 · 결과 없음 · 더보기 메뉴 · 정렬 열림을 볼 수 있다
// 첫 페이지는 RSC에서 받아 두고, 검색 · 정렬 · 더 보기는 브라우저에서 TanStack Query로 부른다.
export default async function Page({ searchParams }: PageProps<'/races'>) {
  const { preview } = await searchParams
  const queryClient = getQueryClient()
  // 실패해도 화면은 그리고, 브라우저에서 다시 부른 뒤 오류 화면을 보여 준다
  await queryClient.prefetchInfiniteQuery(raceListQueryOptions({ sort: DEFAULT_RACE_SORT }))
  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <RaceListPage state={STATES.find((s) => s === preview)} />
    </HydrationBoundary>
  )
}
