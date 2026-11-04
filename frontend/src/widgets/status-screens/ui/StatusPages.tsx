'use client'

import Link from 'next/link'
import { useRouter } from 'next/navigation'

import { Button, buttonClassName, EmptyState } from '@/shared/ui'

// 피그마 세미 · 공통 · 찾을 수 없음 (회원 화면 본문 자리)
export function NotFoundBody() {
  return (
    <main className="flex flex-1 items-center justify-center px-4 py-6">
      <EmptyState
        title="찾을 수 없는 페이지예요"
        description="주소가 바뀌었거나 판매가 끝난 대회일 수 있어요."
        action={
          <Link href="/" className={buttonClassName({ variant: 'secondary' })}>
            홈으로
          </Link>
        }
      />
    </main>
  )
}

// 피그마 세미 · 공통 · 연결 끊김 (회원 화면 본문 자리)
export function OfflineBody() {
  const router = useRouter()
  return (
    <main className="flex flex-1 items-center justify-center px-4 py-6">
      <EmptyState
        title="인터넷에 연결되어 있지 않아요"
        description="연결을 확인한 뒤 다시 시도해 주세요"
        action={
          <Button variant="secondary" onClick={() => router.refresh()}>
            다시 시도
          </Button>
        }
      />
    </main>
  )
}
