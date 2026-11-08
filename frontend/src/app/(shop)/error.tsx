'use client'

import { AppHeader } from '@/widgets/app-header'
import { BottomNav } from '@/widgets/bottom-nav'
import { LoadErrorBody, OfflineBody } from '@/widgets/status-screens'

// 피그마 시나리오 38: 회원 화면을 그리다 실패하면 브라우저 연결에 따라
// 01-A 연결 끊김 또는 01-B 불러오지 못함을 보여 주고, 다시 시도하기로 화면을 다시 그린다.
export default function ShopError({
  retry,
}: {
  error: Error & { digest?: string }
  retry: () => void
}) {
  const offline = typeof navigator !== 'undefined' && !navigator.onLine
  return (
    <>
      <AppHeader />
      {offline ? <OfflineBody onRetry={retry} /> : <LoadErrorBody onRetry={retry} />}
      <BottomNav />
    </>
  )
}
