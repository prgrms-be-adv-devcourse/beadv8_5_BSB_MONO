'use client'

import { RouteErrorScreen } from '@/widgets/status-screens'

// 판매자 센터 화면을 그리다 실패하면 사이드바는 남기고 본문만 오류 화면으로 바꾼다.
export default function SellerError(props: {
  error: Error & { digest?: string }
  retry: () => void
}) {
  return (
    <RouteErrorScreen
      {...props}
      title="화면을 불러오지 못했어요"
      description="잠시 뒤 다시 시도해 주세요."
    />
  )
}
