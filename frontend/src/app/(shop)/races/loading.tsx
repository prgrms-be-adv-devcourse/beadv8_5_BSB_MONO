import { RaceListPage } from '@/views/race-list'

// 대회 목록 로딩: 헤더 · 검색 · 칩은 바로, 목록 자리는 회색 틀
export default function Loading() {
  return <RaceListPage state="loading" />
}
