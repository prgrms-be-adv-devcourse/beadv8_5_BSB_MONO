'use client'

import type { Route } from 'next'
import Link from 'next/link'

import { ConsoleCard, ConsolePage } from '@/widgets/console-page'
import { useAuthStatus } from '@/features/auth'
import { useAdminReports } from '@/entities/board'
import { useManageInquiries } from '@/entities/inquiry'
import { memberApi, unwrapData } from '@/shared/api'
import { cn } from '@/shared/lib'
import { FOCUS_RING } from '@/shared/ui'

import { oldestDay, todoSummary } from '../lib/todo-summary'

type Todo = {
  label: string
  href: Route
  /** 불러온 건수. 셀 수 있는 목록 API가 없는 줄은 null, 아직 못 불러왔으면 undefined */
  count: number | null | undefined
  oldestAt?: string
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
      oldestAt: oldestDay(sellers.data?.map((s) => s.requestedAt) ?? []),
    },
    { label: '답변 대기 문의', href: '/admin/inquiries', count: inquiries.data?.waitingCount },
    { label: '숨겨진 신고 글', href: '/admin/community-reports', count: reports.data?.hiddenCount },
    // 입금 기한이 지난 건만 세는 API가 없다
    { label: '입금 기한 지난 회수', href: '/admin/claims', count: null },
    {
      label: '확인이 필요한 계정',
      href: '/admin/members',
      count: identity.data?.totalElements,
    },
  ]

  return (
    <ConsolePage title="대시보드">
      <ConsoleCard title="할 일">
        <ul className="flex flex-col">
          {todos.map((todo) => {
            const summary = todoSummary(todo.count, todo.oldestAt)
            return (
              <li key={todo.href} className="border-b border-border-default last:border-b-0">
                <Link
                  href={todo.href}
                  className={cn('flex h-14 items-center gap-4 rounded-sm', FOCUS_RING)}
                >
                  <span className="flex-1 text-body2-medium">{todo.label}</span>
                  {summary.kind === 'count' && (
                    <>
                      <span className="text-body2-bold tabular-nums">{summary.count}건</span>
                      {summary.oldest && (
                        <span className="w-32 text-right text-body2-regular text-text-secondary">
                          가장 오래된 {summary.oldest}
                        </span>
                      )}
                    </>
                  )}
                  {summary.kind === 'none' && (
                    <span className="text-body2-regular text-text-secondary">없음</span>
                  )}
                  <span aria-hidden className="text-text-secondary">
                    ›
                  </span>
                </Link>
              </li>
            )
          })}
        </ul>
      </ConsoleCard>

      {/* 관리자 정산 요약 API가 없어 금액을 보여 주지 않고 화면으로 가는 링크만 둔다 */}
      <ConsoleCard title="이번 정산">
        <Link
          href="/admin/settlements"
          className={cn('self-start rounded-sm text-body2-medium text-text-brand', FOCUS_RING)}
        >
          정산 관리 ›
        </Link>
      </ConsoleCard>
    </ConsolePage>
  )
}
