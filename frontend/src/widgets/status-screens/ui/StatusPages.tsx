'use client'

import Link from 'next/link'
import { useRouter } from 'next/navigation'

import { Button, buttonClassName, EmptyState } from '@/shared/ui'

// 피그마 시나리오 38 · 01-C 찾을 수 없음 (회원 화면 본문 자리)
export function NotFoundBody() {
  return (
    <main className="flex flex-1 items-center justify-center px-4 py-8">
      <EmptyState
        icon="Search"
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

// 피그마 시나리오 38 · 01-A 연결 끊김 (처음 불러올 때 실패, 회원 화면 본문 자리)
// onRetry가 없으면 서버 컴포넌트를 다시 불러온다.
export function OfflineBody({ onRetry }: { onRetry?: () => void }) {
  const router = useRouter()
  return (
    <main className="flex flex-1 items-center justify-center px-4 py-8">
      <EmptyState
        icon="CircleAlert"
        title="인터넷에 연결되어 있지 않아요"
        description="연결을 확인한 뒤 다시 시도해 주세요"
        action={
          <Button variant="secondary" onClick={onRetry ?? (() => router.refresh())}>
            다시 시도하기
          </Button>
        }
      />
    </main>
  )
}

export type LoadErrorBodyProps = {
  onRetry: () => void
  /** 다시 불러오는 중이면 버튼을 막아 요청이 겹치지 않게 한다 */
  retrying?: boolean
}

// 피그마 시나리오 38 · 01-B 불러오지 못함 (화면 안에서 불러오기 실패, 본문 자리)
export function LoadErrorBody({ onRetry, retrying = false }: LoadErrorBodyProps) {
  return (
    <main className="flex flex-1 flex-col justify-center px-4 pb-16">
      <EmptyState
        role="alert"
        title="정보를 불러오지 못했어요"
        description="연결이 잠시 끊겼어요. 네트워크를 확인한 뒤 다시 시도해 주세요."
        action={
          <Button variant="secondary" onClick={onRetry} disabled={retrying} loading={retrying}>
            다시 시도하기
          </Button>
        }
      />
    </main>
  )
}
