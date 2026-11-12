import Link from 'next/link'
import type { ReactNode } from 'react'

import { ApiError } from '@/shared/api'
import { cn } from '@/shared/lib'
import { Avatar, Button, buttonClassName, EmptyState } from '@/shared/ui'

// 크루 화면의 흰 띠. 머리글 아래로 기둥 폭을 가득 채우고, 띠 사이는 Divider(section)로 나눈다(11/1 배치 규칙).
export function CrewBand({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <section className={cn('flex flex-col gap-3 bg-bg-surface px-4 py-6', className)}>
      {children}
    </section>
  )
}

// 크루 홈(M09) · 초대 받기(M10) 맨 위의 흰 띠. 크루 얼굴과 이름, 이름 아래 한 줄, 오른쪽 아래 버튼(선택).
export function CrewIdentityBand({
  name,
  summary,
  action,
}: {
  name: string
  summary: ReactNode
  action?: ReactNode
}) {
  return (
    <CrewBand className="gap-4">
      <div className="flex items-center gap-3">
        <Avatar size="xl" name={name} />
        <div className="flex min-w-0 flex-1 flex-col gap-1">
          <h2 className="truncate text-title1">{name}</h2>
          <p className="text-body2-regular text-text-secondary">{summary}</p>
        </div>
      </div>
      {action && <div className="flex justify-end">{action}</div>}
    </CrewBand>
  )
}

// 시안 M28 「불러오지 못함」(1333:26189): 네트워크 · 서버 오류로 불러오지 못했을 때
export function CrewLoadError({ onRetry }: { onRetry: () => void }) {
  return (
    <EmptyState
      className="pt-26"
      title="정보를 불러오지 못했어요"
      description="연결이 잠시 끊겼어요. 네트워크를 확인한 뒤 다시 시도해 주세요."
      action={
        <Button variant="secondary" size="m" onClick={onRetry}>
          다시 시도
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
