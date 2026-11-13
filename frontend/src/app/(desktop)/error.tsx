'use client'

import { RouteErrorScreen } from '@/widgets/status-screens'

// PC 화면(판매자 신청 등)을 그리다 실패하면 머리말은 남기고 본문만 오류 화면으로 바꾼다.
export default function DesktopError(props: {
  error: Error & { digest?: string }
  retry: () => void
}) {
  return <RouteErrorScreen {...props} />
}
