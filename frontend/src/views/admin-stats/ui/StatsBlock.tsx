'use client'

import type { UseQueryResult } from '@tanstack/react-query'
import { useId, type ReactNode } from 'react'

import type { StatsResponse } from '@/entities/usage-stats'
import { cn } from '@/shared/lib'
import { Alert, Button, EmptyState, Skeleton } from '@/shared/ui'

import { isLoginRequiredError, isNotAdminError } from '../model/use-usage-stats'

// 이용 통계의 영역 틀과 영역 하나의 상태(로딩 · 불러오지 못함 · 관리자 아님 · 정상). 디자인 계약 A-STATS 「상태별 문구」.

export function StatsBlock({
  title,
  description,
  aside,
  children,
}: {
  title: string
  description?: ReactNode
  aside?: ReactNode
  children: ReactNode
}) {
  const id = useId()
  return (
    <section aria-labelledby={id} className="flex flex-col gap-4">
      <div className="flex items-start justify-between gap-4">
        <div className="flex flex-col gap-1">
          <h2 id={id} className="text-heading-s">
            {title}
          </h2>
          {description && <p className="text-body-s text-text-secondary">{description}</p>}
        </div>
        {aside}
      </div>
      {children}
    </section>
  )
}

export function SubHeading({ children }: { children: ReactNode }) {
  return <h3 className="text-label-strong">{children}</h3>
}

/**
 * 영역 하나의 상태를 나눈다. 다른 영역에는 영향이 없다.
 * 연결 정보 없음은 화면 전체에서 한 번만 보여 주므로 여기서는 그리지 않는다.
 */
export function SectionState<T>({
  result,
  name,
  skeletonClassName = 'h-40',
  children,
}: {
  result: UseQueryResult<StatsResponse<T>>
  /** 「구매 단계를 불러오지 못했어요」의 「구매 단계를」 자리 */
  name: string
  skeletonClassName?: string
  children: (data: T) => ReactNode
}) {
  if (result.isPending) {
    return (
      <div role="status" aria-label="불러오는 중">
        <Skeleton shape="rect" className={cn('w-full rounded-md', skeletonClassName)} />
      </div>
    )
  }
  if (result.isError) {
    if (isNotAdminError(result.error)) {
      return <Alert tone="danger" title="관리자만 이용할 수 있어요." />
    }
    // 「불러오지 못함」으로 뭉뚱그리면 GA4 문제로 보여서 따로 알린다
    if (isLoginRequiredError(result.error)) {
      return (
        <Alert
          tone="danger"
          title="로그인이 풀렸어요"
          description="다시 로그인하면 숫자가 보여요."
        />
      )
    }
    return (
      <EmptyState
        role="alert"
        className="py-8"
        title={`${name} 불러오지 못했어요`}
        description="잠시 뒤 다시 시도해 주세요. 다른 영역은 그대로 볼 수 있어요."
        action={
          <Button variant="secondary" onClick={() => result.refetch()}>
            다시 시도
          </Button>
        }
      />
    )
  }
  if (result.data.status !== 'ok') return null
  return children(result.data.data)
}
