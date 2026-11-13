'use client'

import { RouteErrorScreen } from '@/widgets/status-screens'

// 로그인·가입 화면을 그리다 실패하면 기둥 안에 오류 화면을 보여 준다.
export default function AuthError(props: {
  error: Error & { digest?: string }
  retry: () => void
}) {
  return <RouteErrorScreen {...props} />
}
