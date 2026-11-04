import Link from 'next/link'
import type { ReactNode } from 'react'

import { ApiError } from '@/shared/api'
import { cn } from '@/shared/lib'
import { Button, buttonClassName, EmptyState } from '@/shared/ui'

// 크루 화면의 흰 띠. 머리글 아래로 기둥 폭을 가득 채우고, 띠 사이는 Divider(section)로 나눈다(11/1 배치 규칙).
export function CrewBand({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <section className={cn('flex flex-col gap-3 bg-bg-surface px-4 py-6', className)}>
      {children}
    </section>
  )
}

// 시안에 없는 상태: 네트워크 · 서버 오류로 불러오지 못했을 때. 디자인 담당에게 시안을 요청해 둔 자리다.
export function CrewLoadError({ onRetry }: { onRetry: () => void }) {
  return (
    <EmptyState
      title="크루 정보를 불러오지 못했어요"
      description="잠시 후 다시 시도해 주세요."
      action={
        <Button variant="secondary" size="m" onClick={onRetry}>
          다시 불러오기
        </Button>
      }
    />
  )
}

/** 크루원이 아님(403) · 없는 크루(404)는 다시 불러도 같아서 다시 시도하지 않는다. */
export function retryCrewQuery(count: number, error: unknown): boolean {
  return !(error instanceof ApiError && error.status < 500) && count < 2
}

// 크루원이 아님(403) · 없는 크루(404, 없앤 크루 포함)는 다시 불러도 같아서 내 크루 목록으로 보낸다.
// 「없는 크루」는 시안에 없는 상태라 크루원이 아님과 같은 모양을 쓴다.
const CREW_HOME_FAILURE: Record<string, { title: string; description: string }> = {
  CREW_NOT_MEMBER: {
    title: '크루원만 볼 수 있어요',
    description: '크루장이나 운영진에게 초대 링크를 받아 먼저 가입해 주세요.',
  },
  CREW_NOT_FOUND: {
    title: '없는 크루예요',
    description: '주소가 잘못됐거나 크루장이 크루를 없앴어요.',
  },
}

export function CrewHomeError({ error, onRetry }: { error: unknown; onRetry: () => void }) {
  const failure =
    error instanceof ApiError && error.code ? CREW_HOME_FAILURE[error.code] : undefined
  if (!failure) return <CrewLoadError onRetry={onRetry} />
  return (
    <EmptyState
      className="pt-26"
      title={failure.title}
      description={failure.description}
      action={
        <Link href="/crews" className={buttonClassName({ variant: 'secondary', size: 'm' })}>
          내 크루로 가기
        </Link>
      }
    />
  )
}
