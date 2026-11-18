'use client'

import { AppHeader } from '@/widgets/app-header'
import { BottomNav } from '@/widgets/bottom-nav'
import { DEFAULT_RACE_SORT } from '@/entities/race'
import { Chip, Icon, Skeleton, SortSelect } from '@/shared/ui'

// 대회 목록 로딩 화면(app/(shop)/races/loading.tsx). RaceListPage와 달리 쿼리 훅을 부르지 않는다.
// 서버가 로딩 화면을 그리다 대회 목록 쿼리를 캐시에 만들면, 진짜 페이지의 데이터 주입이 미뤄져 hydration 오류가 난다.
export function RaceListSkeleton() {
  return (
    <>
      <AppHeader />
      <main className="flex flex-1 flex-col gap-4 px-4 pt-8 pb-8">
        <h1 className="text-title1">대회 찾기</h1>
        <div className="flex items-center">
          <label className="flex min-w-0 flex-1 items-center gap-2 rounded-md py-4">
            <Icon name="Search" className="size-5" />
            <input
              disabled
              placeholder="대회 이름이나 지역으로 찾기"
              aria-label="대회 찾기"
              className="min-w-0 flex-1 bg-transparent text-body2-regular outline-none placeholder:text-text-tertiary"
            />
          </label>
        </div>
        <div className="flex flex-wrap gap-2">
          <Chip>종목</Chip>
          <Chip>지역</Chip>
          <Chip>이번 달</Chip>
        </div>
        <div className="flex items-center justify-between">
          <Skeleton className="w-12" />
          <SortSelect
            items={[{ value: DEFAULT_RACE_SORT, label: '마감 임박순' }]}
            value={DEFAULT_RACE_SORT}
            onValueChange={() => {}}
            aria-label="대회 정렬"
          />
        </div>
        <ListSkeleton />
      </main>
      <BottomNav />
    </>
  )
}

// 피그마 M18 · 로딩: 실제 목록 줄과 같은 평평한 줄 4개
export function ListSkeleton() {
  return (
    <div role="status" aria-label="불러오는 중" className="flex flex-col">
      {[0, 1, 2, 3].map((i) => (
        <div key={i} className="flex gap-3 border border-border-default bg-bg-surface py-4 pr-12">
          <Skeleton shape="rect" className="size-24 shrink-0" />
          <div className="flex flex-1 flex-col items-start gap-2">
            <Skeleton shape="rect" className="h-4 w-12" />
            <Skeleton className="w-11/12" />
            <Skeleton className="w-4/5" />
            <div className="flex w-full justify-between pt-1">
              <Skeleton className="w-10" />
              <Skeleton className="w-14" />
            </div>
          </div>
        </div>
      ))}
    </div>
  )
}
