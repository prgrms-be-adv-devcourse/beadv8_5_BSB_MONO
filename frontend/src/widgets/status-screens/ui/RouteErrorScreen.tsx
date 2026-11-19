'use client'

import { useTransition } from 'react'

import { useReportAppError } from '@/shared/lib'

import { LoadErrorBody, OfflineBody } from './StatusPages'

// 라우트 그룹 오류 경계(error.tsx)가 공통으로 그리는 본문. 오류를 기록하고, 연결 상태에 따라 문구를 고르고,
// 다시 시도하기로 화면을 다시 그린다(그리는 동안 버튼을 막아 한 번만 다시 그린다).
export function RouteErrorScreen({
  error,
  retry,
  title,
  description,
}: {
  error: Error & { digest?: string }
  retry: () => void
  /** 불러오지 못함 문구를 바꿀 때(센터 화면). 오프라인 문구는 그대로 */
  title?: string
  description?: string
}) {
  useReportAppError(error)
  const [retrying, startRetry] = useTransition()
  const onRetry = () => startRetry(() => retry())
  const offline = typeof navigator !== 'undefined' && !navigator.onLine
  return offline ? (
    <OfflineBody onRetry={onRetry} retrying={retrying} />
  ) : (
    <LoadErrorBody title={title} description={description} onRetry={onRetry} retrying={retrying} />
  )
}
