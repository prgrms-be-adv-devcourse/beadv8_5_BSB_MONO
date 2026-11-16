import { Suspense } from 'react'

import { RaceListPage } from '@/views/race-list'

// 대회 목록 로딩: 헤더 · 검색 · 칩은 바로, 목록 자리는 회색 틀
// RaceListPage가 URL 쿼리(useSearchParams)를 읽어서, 미리 그리는 로딩 화면은 Suspense로 감싸야 빌드된다
export default function Loading() {
  return (
    <Suspense>
      <RaceListPage state="loading" />
    </Suspense>
  )
}
