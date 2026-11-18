import type { Route } from 'next'
import Link from 'next/link'

import { ConsoleCard, ConsolePage } from '@/widgets/console-page'
import { cn } from '@/shared/lib'
import { FOCUS_RING, KeyValue } from '@/shared/ui'

type Todo = {
  label: string
  /** 처리 대기 개수. 0이면 「없음」으로 쓰고 줄은 남긴다 */
  count: number
  /** 가장 오래 기다린 날(월/일). 개수가 0이면 없다 */
  oldest?: string
  href: Route
}

// TODO(api): 관리자 대시보드 API(처리 대기 개수)로 바꾼다. 지금은 시안 예시 값.
const TODOS: Todo[] = [
  { label: '판매자 승인 대기', count: 3, oldest: '11/2', href: '/admin/sellers' },
  { label: '답변 대기 문의', count: 5, oldest: '11/14', href: '/admin/inquiries' },
  { label: '숨겨진 신고 글', count: 2, oldest: '11/16', href: '/admin/community-reports' },
  { label: '입금 기한 지난 회수', count: 1, oldest: '11/10', href: '/admin/claims' },
  { label: '확인이 필요한 계정', count: 3, oldest: '11/2', href: '/admin/members' },
]

// 피그마 v3 · 관리자 대시보드: 할 일 목록과 이번 정산 상자 두 가지만 둔다
export function AdminDashboardPage() {
  return (
    <ConsolePage title="대시보드">
      <ConsoleCard title="할 일">
        <ul className="flex flex-col">
          {TODOS.map((todo) => (
            <li key={todo.href} className="border-b border-border-default last:border-b-0">
              <Link
                href={todo.href}
                className={cn('flex h-14 items-center gap-4 rounded-sm', FOCUS_RING)}
              >
                <span className="flex-1 text-body2-medium">{todo.label}</span>
                {todo.count > 0 ? (
                  <>
                    <span className="text-body2-bold tabular-nums">{todo.count}건</span>
                    <span className="w-32 text-right text-body2-regular text-text-secondary">
                      가장 오래된 {todo.oldest}
                    </span>
                  </>
                ) : (
                  <span className="text-body2-regular text-text-secondary">없음</span>
                )}
                <span aria-hidden className="text-text-secondary">
                  ›
                </span>
              </Link>
            </li>
          ))}
        </ul>
      </ConsoleCard>

      <ConsoleCard title="이번 정산">
        {/* TODO(api): 관리자 정산 요약 API로 바꾼다. 지금은 시안 예시 값 */}
        <KeyValue label="11/20 지급 예정" value="₩4,536,000 · 2건" />
        <KeyValue label="지급보류" value="₩860,000 · 1건" />
        <Link
          href="/admin/settlements"
          className={cn(
            'self-start rounded-sm border-t border-border-default pt-4 text-body2-medium text-text-brand',
            FOCUS_RING,
          )}
        >
          정산 관리 ›
        </Link>
      </ConsoleCard>
    </ConsolePage>
  )
}
