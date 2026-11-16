'use client'

import { useTransition } from 'react'

import { useReportAppError } from '@/shared/lib'

import { LoadErrorBody, OfflineBody } from './StatusPages'

// 라우트 그룹 오류 경계(error.tsx)가 공통으로 그리는 본문. 오류를 기록하고, 연결 상태에 따라 문구를 고르고,
// 다시 시도하기로 화면을 다시 그린다(그리는 동안 버튼을 막아 한 번만 다시 그린다).
export function RouteErrorScreen({
  error,
  retry,
}: {
  error: Error & { digest?: string }
  retry: () => void
}) {
  useReportAppError(error)
  const [retrying, startRetry] = useTransition()
  const onRetry = () => startRetry(() => retry())
  const offline = typeof navigator !== 'undefined' && !navigator.onLine
  return offline ? (
    <OfflineBody onRetry={onRetry} retrying={retrying} />
  ) : (
    <LoadErrorBody onRetry={onRetry} retrying={retrying} />
  )
}
