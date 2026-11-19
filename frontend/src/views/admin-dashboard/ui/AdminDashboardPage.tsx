'use client'

import type { Route } from 'next'
import Link from 'next/link'

import { ConsoleCard, ConsolePage } from '@/widgets/console-page'
import { useAuthStatus } from '@/features/auth'
import { useAdminReports } from '@/entities/board'
import { useManageInquiries } from '@/entities/inquiry'
import { memberApi, unwrapData } from '@/shared/api'
import { cn } from '@/shared/lib'
import { Button, FOCUS_RING, Icon, Skeleton } from '@/shared/ui'

import { oldestDay, todoSummary } from '../lib/todo-summary'

type Todo = {
  label: string
  href: Route
  /** 불러온 건수. 셀 수 있는 목록 API가 없는 줄은 null, 아직 못 불러왔으면 undefined */
  count: number | null | undefined
  oldestAt?: string
  /** 가장 오래된 건 앞에 붙는 말 */
  oldestLabel?: string
  /** 건수를 불러오지 못했다 */
  failed?: boolean
  /** 건수를 불러오는 중 */
  loading?: boolean
}

// 피그마 v3 · 관리자 대시보드: 할 일 목록과 이번 정산 상자만 둔다.
// 건수는 이미 있는 목록 API에서 세고, 셀 수 없는 줄은 숫자 없이 화면 링크만 둔다.
export function AdminDashboardPage() {
  const authenticated = useAuthStatus() === 'authenticated'
  const sellers = memberApi.useFindAll(
    { status: 'PENDING' },
    { query: { select: unwrapData, enabled: authenticated } },
  )
  const inquiries = useManageInquiries('admin', { status: 'WAITING' })
  const reports = useAdminReports()
  const identity = memberApi.useFindIdentityReview(
    { page: 0, size: 1 },
    { query: { select: unwrapData, enabled: authenticated } },
  )

  const todos: Todo[] = [
    {
      label: '판매자 승인 대기',
      href: '/admin/sellers',
      count: sellers.data?.length,
      failed: sellers.isError,
      loading: sellers.isLoading,
      oldestAt: oldestDay(sellers.data?.map((s) => s.requestedAt) ?? []),
      oldestLabel: '가장 오래된 신청',
    },
    {
      label: '답변 대기 문의',
      href: '/admin/inquiries',
      count: inquiries.data?.waitingCount,
      failed: inquiries.isError,
      loading: inquiries.isLoading,
    },
    {
      label: '숨겨진 신고 글',
      href: '/admin/community-reports',
      count: reports.data?.hiddenCount,
      failed: reports.isError,
      loading: reports.isLoading,
    },
    // 입금 기한이 지난 건만 세는 API가 없다
    { label: '입금 기한 지난 회수', href: '/admin/claims', count: null },
    {
      // totalElements는 계정 수가 아니라 같은 사람으로 보이는 묶음 수다
      label: '확인이 필요한 계정',
      href: '/admin/members',
      count: identity.data?.totalElements,
      failed: identity.isError,
      loading: identity.isLoading,
    },
  ]

  // 셀 수 있는 네 줄이 모두 실패하면 줄마다 알리지 않고 카드 하나로 알린다 (시안 불러오기 실패)
  const allFailed = todos.filter((t) => t.count !== null).every((t) => t.failed)
  const retryAll = () => {
    void sellers.refetch()
    void inquiries.refetch()
    void reports.refetch()
    void identity.refetch()
  }

  const total = todos.reduce((sum, t) => sum + (t.count ?? 0), 0)

  return (
    <ConsolePage title="대시보드">
      <div className="flex items-start gap-6">
        <ConsoleCard
          className="min-w-0 flex-1"
          title={
            <span className="flex items-center gap-2">
              할 일
              <span className="font-display text-number-s text-text-secondary tabular-nums">
                {total}
              </span>
            </span>
          }
        >
          {allFailed ? (
            <div role="alert" className="flex flex-col items-center gap-3 py-10">
              <p className="text-body1-regular text-text-secondary">할 일을 불러오지 못했어요</p>
              <Button variant="secondary" size="m" onClick={retryAll}>
                다시 시도하기
              </Button>
            </div>
          ) : (
            <ul className="flex flex-col gap-2">
              {todos.map((todo) => {
                const summary = todoSummary(todo.count, todo.oldestAt)
                return (
                  <li key={todo.href}>
                    <Link
                      href={todo.href}
                      className={cn(
                        'flex items-center gap-3 rounded-sm bg-bg-default py-3 pr-3 pl-4 shadow-sm',
                        FOCUS_RING,
                      )}
                    >
                      <span className="text-body1-medium">{todo.label}</span>
                      {summary.kind === 'count' && (
                        <span className="text-body1-bold tabular-nums">{summary.count}건</span>
                      )}
                      <span className="flex-1" />
                      {todo.loading && (
                        <>
                          <Skeleton shape="rect" className="h-4 w-24" />
                          <Skeleton shape="rect" className="h-4 w-8" />
                        </>
                      )}
                      {summary.kind === 'count' && summary.oldest && (
                        <span className="text-caption-regular text-text-secondary">
                          {todo.oldestLabel} {summary.oldest}
                        </span>
                      )}
                      {todo.failed && (
                        <span className="text-caption-regular text-text-secondary">
                          불러오지 못했어요
                        </span>
                      )}
                      {summary.kind === 'none' && !todo.loading && (
                        <span className="text-caption-regular text-text-secondary">없음</span>
                      )}
                      <Icon name="ChevronRight" className="size-5 text-text-primary" />
                    </Link>
                  </li>
                )
              })}
            </ul>
          )}
        </ConsoleCard>

        {/* 관리자 정산 요약 API가 없어 지급일·금액은 보여 주지 않고 화면으로 가는 버튼만 둔다 */}
        <ConsoleCard title="이번 정산" className="w-100 shrink-0">
          <Link
            href="/admin/settlements"
            className={cn(
              'inline-flex self-start rounded-md border border-border-strong bg-bg-default px-3 py-1 text-body1-medium shadow-sm',
              FOCUS_RING,
            )}
          >
            정산 관리
          </Link>
        </ConsoleCard>
      </div>
    </ConsolePage>
  )
}
