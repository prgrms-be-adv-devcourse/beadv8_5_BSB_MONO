import { RaceListSkeleton } from '@/views/race-list'

// 대회 목록 로딩: 헤더 · 검색 · 칩은 바로, 목록 자리는 회색 틀
// 쿼리를 부르지 않는 틀만 그린다 (RaceListPage를 그리면 hydration 오류가 난다)
export default function Loading() {
  return <RaceListSkeleton />
}
