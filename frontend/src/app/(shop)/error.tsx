'use client'

import { useTransition } from 'react'

import { AppHeader } from '@/widgets/app-header'
import { BottomNav } from '@/widgets/bottom-nav'
import { LoadErrorBody, OfflineBody } from '@/widgets/status-screens'

// 피그마 시나리오 38: 회원 화면을 그리다 실패하면 브라우저 연결에 따라
// 01-A 연결 끊김 또는 01-B 불러오지 못함을 보여 주고, 다시 시도하기로 화면을 다시 그린다.
// 다시 그리는 동안에는 버튼을 막아 두 번 눌러도 한 번만 다시 그린다.
export default function ShopError({
  retry,
}: {
  error: Error & { digest?: string }
  retry: () => void
}) {
  const [retrying, startRetry] = useTransition()
  const onRetry = () => startRetry(() => retry())
  const offline = typeof navigator !== 'undefined' && !navigator.onLine
  return (
    <>
      <AppHeader />
      {offline ? (
        <OfflineBody onRetry={onRetry} retrying={retrying} />
      ) : (
        <LoadErrorBody onRetry={onRetry} retrying={retrying} />
      )}
      <BottomNav />
    </>
  )
}
