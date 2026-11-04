'use client'

import { useRouter } from 'next/navigation'

import { Button, EmptyState, Skeleton } from '@/shared/ui'

// 피그마 세미 · 판매자 · 관리자 공통 · 로딩: 숫자 타일 4칸 + 줄 7개
export function ConsoleLoading() {
  return (
    <div role="status" aria-label="불러오는 중" className="flex flex-col gap-4">
      <div className="flex gap-4">
        {Array.from({ length: 4 }, (_, i) => (
          <Skeleton key={i} shape="rect" className="h-24 flex-1 rounded-lg" />
        ))}
      </div>
      <Skeleton shape="rect" className="h-11 rounded-md" />
      {Array.from({ length: 6 }, (_, i) => (
        <Skeleton key={i} shape="rect" className="h-9 rounded-md" />
      ))}
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
