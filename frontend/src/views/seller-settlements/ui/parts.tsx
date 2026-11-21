import Link from 'next/link'
import type { ReactNode } from 'react'

import { ApiError } from '@/shared/api'
import { cn } from '@/shared/lib'
import { Alert, buttonClassName, Skeleton } from '@/shared/ui'

export function errorText(error: unknown) {
  return (error instanceof ApiError && error.serverMessage) || '잠시 뒤 다시 시도해 주세요.'
}

/** 불러오는 중 · 오류 · 빈 목록을 같은 모양으로 보여 주고, 행이 있으면 children을 그린다 */
export function QueryRows<Row>({
  query,
  label,
  emptyTitle,
  children,
}: {
  query: { data?: Row[]; isPending: boolean; isError: boolean; error: unknown }
  label: string
  emptyTitle: string
  children: (rows: Row[]) => ReactNode
}) {
  if (query.isError) {
    return (
      <Alert
        tone="danger"
        title={`${label}을 불러오지 못했어요`}
        description={errorText(query.error)}
      />
    )
  }
  if (query.isPending || !query.data) {
    return <Skeleton className="h-40 w-full" />
  }
  if (query.data.length === 0) {
    // 시안 2070:70056: 아이콘과 설명 없이 글 한 줄
    return <p className="py-13 text-center text-body1-regular text-text-secondary">{emptyTitle}</p>
  }
  return children(query.data)
}

// 상태는 칩 대신 14 Medium 글자: 완료 초록, 실패·보류 빨강, 그 밖은 보조 글자색
const TONE_TEXT: Record<string, string> = {
  success: 'text-text-success',
  danger: 'text-text-danger',
  warning: 'text-text-danger',
}

export function StatusText({ label, tone }: { label: string; tone: string }) {
  return <span className={cn('text-body1-medium', TONE_TEXT[tone])}>{label}</span>
}

/** 제목 위 알약 모양 돌아가기 링크(시안 「버튼 · 정산」 54×32) */
export function PillLink({ href, children }: { href: string; children: ReactNode }) {
  return (
    <Link
      href={href as never}
      className={buttonClassName({ variant: 'ghost', size: 's', className: 'rounded-full' })}
    >
      {children}
    </Link>
  )
}

/** 공용 카드(ConsoleCard)에 구역 이름을 붙이는 껍데기. 화면 낭독기가 구역으로 읽는다 */
export function Named({
  label,
  className,
  children,
}: {
  label: string
  className?: string
  children: ReactNode
}) {
  return (
    <div role="region" aria-label={label} className={className}>
      {children}
    </div>
  )
}
