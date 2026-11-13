'use client'

import { RouteErrorScreen } from '@/widgets/status-screens'

// 어드민 화면을 그리다 실패하면 사이드바는 남기고 본문만 오류 화면으로 바꾼다.
export default function AdminError(props: {
  error: Error & { digest?: string }
  retry: () => void
}) {
  return <RouteErrorScreen {...props} />
}
