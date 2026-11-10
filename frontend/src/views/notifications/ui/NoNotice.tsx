import { EmptyState } from '@/shared/ui'

// 피그마 시나리오 37 · 01-A 알림 없음
export function NoNotice() {
  return (
    <div className="flex flex-1 items-center justify-center px-4 py-8">
      <EmptyState
        icon="Bell"
        title="아직 알림이 없어요"
        description="대회 일정이 바뀌거나 환불이 끝나면 여기로 알려 드려요."
        className="max-w-86"
      />
    </div>
  )
}
