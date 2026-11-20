'use client'

import { useRouter } from 'next/navigation'

import { Button, EmptyState, Skeleton } from '@/shared/ui'

// v4 시안(2070:15094·14010·59895) 로딩: 제목 막대 + 칩 막대 3개 + 머리줄 없이 줄 6개인 표 카드
export function ConsoleLoading() {
  return (
    <div role="status" aria-label="불러오는 중" className="flex flex-col gap-6">
      <Skeleton shape="rect" className="h-8 w-50 rounded-sm" />
      <div className="flex gap-3">
        <Skeleton shape="circle" className="h-9 w-24" />
        <Skeleton shape="circle" className="h-9 w-20" />
        <Skeleton shape="circle" className="h-9 w-20" />
      </div>
      <div className="overflow-hidden rounded-md border border-border-default bg-bg-surface">
        {Array.from({ length: 6 }, (_, i) => (
          <div
            key={i}
            className="flex h-13 items-center gap-4 border-b border-border-default px-6 last:border-b-0"
          >
            <Skeleton shape="rect" className="h-4 flex-1 rounded-sm" />
            <Skeleton shape="rect" className="h-4 w-30 rounded-sm" />
            <Skeleton shape="rect" className="h-4 w-24 rounded-sm" />
          </div>
        ))}
      </div>
    </div>
  )
}

// 피그마 세미 · 판매자 · 관리자 공통 · 연결 끊김
export function ConsoleOffline() {
  const router = useRouter()
  return (
    <div className="flex flex-1 items-center justify-center py-20">
      <EmptyState
        title="인터넷에 연결되어 있지 않아요"
        description="연결을 확인한 뒤 다시 시도해 주세요"
        action={
          <Button variant="secondary" onClick={() => router.refresh()}>
            다시 시도
          </Button>
        }
      />
    </div>
  )
}
