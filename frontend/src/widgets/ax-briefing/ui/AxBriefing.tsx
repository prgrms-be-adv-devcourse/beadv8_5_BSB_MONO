'use client'

import type { Route } from 'next'
import Link from 'next/link'
import { useState } from 'react'

import { safeInternalPath } from '@/features/auth'
import {
  aiErrorMessage,
  useAxBriefing,
  type AiScreen,
  type AxBriefingMetric,
} from '@/entities/ai-assistant'
import { cn } from '@/shared/lib'
import { Alert, buttonClassName, Icon, Skeleton } from '@/shared/ui'

export type AxBriefingProps = {
  screen: AiScreen
  /** 「크루런 AI에 물어보기」가 여는 화면 */
  askHref: Route
  /** 「근거 보기」가 여는 화면 */
  basisHref: Route
  className?: string
}

const NOTE_TONE: Record<NonNullable<AxBriefingMetric['noteTone']>, string> = {
  good: 'text-text-success',
  bad: 'text-text-danger',
}

// 피그마 대시보드 AX 브리핑 (판매자 1493:103605, 관리자 1493:103896): 제목 바로 아래 흰 카드.
// 왼쪽은 한 문장 요약과 먼저 할 일 세 줄, 오른쪽은 평소와 다른 지표 두 칸이다.
// 판매자 · 관리자 대시보드가 `<AxBriefing screen="seller" askHref="/seller/ai" basisHref="…" />`로 제목 아래에 붙인다.
export function AxBriefing({ screen, askHref, basisHref, className }: AxBriefingProps) {
  const briefing = useAxBriefing(screen)
  // shortcut: 「오늘 숨기기」는 이 화면을 연 동안만 숨긴다, 날짜별로 기억하려면 서버 저장이 필요하다
  const [hidden, setHidden] = useState(false)
  if (hidden) return null

  if (briefing.isError) {
    return (
      <Alert
        tone="danger"
        title="크루런 AI 브리핑을 불러오지 못했어요"
        description={aiErrorMessage(briefing.error)}
      />
    )
  }
  if (!briefing.data) {
    return <Skeleton shape="rect" className="h-60 w-full" />
  }
  const { headline, basis, todos, metrics } = briefing.data

  return (
    <section
      aria-label="크루런 AI 브리핑"
      className={cn('flex flex-col gap-6 rounded-lg bg-bg-surface p-6 shadow-sm', className)}
    >
      <div className="flex items-center justify-between gap-4">
        <div className="flex items-center gap-2 text-caption-regular text-text-secondary">
          <Icon name="Info" className="size-4 text-text-brand" />
          <span className="text-caption-medium text-text-brand">크루런 AI 브리핑</span>
          <span>{basis}</span>
        </div>
        <Link href={askHref} className={buttonClassName({ variant: 'secondary', size: 's' })}>
          크루런 AI에 물어보기
        </Link>
      </div>
      <p className="text-body1-bold">{headline}</p>
      <div className="lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)] grid grid-cols-1 gap-6">
        <div className="flex flex-col gap-3">
          <p className="text-caption-medium text-text-secondary">먼저 할 일</p>
          <ol className="flex flex-col gap-3">
            {todos.map((todo, index) => {
              // 서버가 준 주소라 사이트 안 경로일 때만 링크로 만든다
              const href = safeInternalPath(todo.href)
              const className = 'flex items-center gap-3 rounded-sm hover:bg-bg-subtle'
              const row = (
                <>
                  <span className="flex size-6 shrink-0 items-center justify-center rounded-full bg-bg-subtle text-caption-medium">
                    {index + 1}
                  </span>
                  <span className="flex min-w-0 flex-1 flex-col">
                    <span className="flex items-center gap-2 text-body2-medium">
                      {todo.title}
                      {todo.flag && (
                        <span className="rounded-sm bg-bg-badge-danger px-2 text-caption-medium text-text-badge-danger">
                          {todo.flag}
                        </span>
                      )}
                    </span>
                    <span className="text-caption-regular text-text-secondary">
                      {todo.description}
                    </span>
                  </span>
                  {href && (
                    <span className="flex shrink-0 items-center gap-1 text-caption-regular text-text-secondary">
                      {todo.linkLabel}
                      <Icon name="ChevronRight" className="size-4" />
                    </span>
                  )}
                </>
              )
              return (
                <li key={todo.href + todo.title}>
                  {href ? (
                    <Link href={href} className={className}>
                      {row}
                    </Link>
                  ) : (
                    <div className={className}>{row}</div>
                  )}
                </li>
              )
            })}
          </ol>
        </div>
        <div className="flex flex-col gap-3">
          <p className="text-caption-medium text-text-secondary">평소와 다른 지표</p>
          {metrics.map((metric) => (
            <div key={metric.label} className="flex flex-col gap-1 rounded-md bg-bg-subtle p-4">
              <span className="text-caption-regular text-text-secondary">{metric.label}</span>
              <span className="flex items-baseline gap-2">
                <span className="text-title3">{metric.value}</span>
                <span
                  className={cn(
                    'text-caption-medium',
                    metric.noteTone ? NOTE_TONE[metric.noteTone] : 'text-text-secondary',
                  )}
                >
                  {metric.note}
                </span>
              </span>
            </div>
          ))}
        </div>
      </div>
      <div className="flex items-center gap-4 text-caption-regular text-text-secondary">
        <Link href={basisHref} className="hover:underline">
          근거 보기
        </Link>
        <button type="button" onClick={() => setHidden(true)} className="hover:underline">
          오늘 숨기기
        </button>
      </div>
    </section>
  )
}
