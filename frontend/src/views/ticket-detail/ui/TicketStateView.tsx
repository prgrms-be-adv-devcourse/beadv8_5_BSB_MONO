import Link from 'next/link'

import { Alert, buttonClassName, EmptyState, Skeleton } from '@/shared/ui'

/** 참가권 화면들이 함께 쓰는 불러오는 중 · 찾을 수 없음 · 실패 상태 */
export function TicketStateView({ state }: { state: 'loading' | 'notFound' | 'error' }) {
  if (state === 'loading') {
    return (
      <div role="status" aria-label="불러오는 중" className="flex flex-col gap-4">
        <Skeleton shape="rect" className="h-40 w-full rounded-lg" />
        <Skeleton shape="rect" className="h-24 w-full rounded-lg" />
      </div>
    )
  }
  if (state === 'error') {
    return <Alert tone="danger" title="참가권을 불러오지 못했어요" />
  }
  return (
    <div className="flex flex-1 items-center justify-center">
      <EmptyState
        title="참가권을 찾을 수 없어요"
        action={
          <Link href="/tickets" className={buttonClassName({ variant: 'secondary' })}>
            내 참가권 보기
          </Link>
        }
      />
    </div>
  )
}

/** useTicket 결과 → 상태 화면을 그릴지. 그릴 게 없으면 undefined */
export function ticketViewState(t: {
  notFound: boolean
  isPending: boolean
  isError: boolean
}): 'loading' | 'notFound' | 'error' | undefined {
  if (t.notFound) return 'notFound'
  if (t.isError) return 'error'
  if (t.isPending) return 'loading'
  return undefined
}
