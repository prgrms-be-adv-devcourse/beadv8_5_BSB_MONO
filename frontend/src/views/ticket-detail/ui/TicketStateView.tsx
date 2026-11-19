import Link from 'next/link'

import { Alert, buttonClassName, EmptyState, Skeleton } from '@/shared/ui'

/** 참가권 화면들이 함께 쓰는 불러오는 중 · 찾을 수 없음 · 실패 상태 */
export function TicketStateView({ state }: { state: 'loading' | 'notFound' | 'error' }) {
  if (state === 'loading') return <TicketSkeleton />
  if (state === 'error') {
    return <Alert tone="danger" title="참가권을 불러오지 못했어요" />
  }
  return (
    <div className="flex flex-1 items-center justify-center">
      <EmptyState
        icon={null}
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

const SKELETON_BOX =
  'flex flex-col gap-5 rounded-md border border-border-default bg-bg-surface px-4 pt-5 pb-4'

function SkeletonRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center gap-5">
      <Skeleton className={`h-4 ${label}`} />
      <Skeleton className={`h-4 ${value}`} />
    </div>
  )
}

// 시안 2074:94411 · 참가권 상세 로딩: 배번표 카드, 참가자 정보 상자, 취소·환불 상자 자리를 회색으로 잡는다.
function TicketSkeleton() {
  return (
    <div role="status" aria-label="불러오는 중" className="-mt-2 flex flex-col">
      <div className="overflow-hidden rounded-md border border-border-default bg-bg-surface">
        <Skeleton shape="rect" className="h-32 w-full rounded-none" />
        <div className="flex flex-col gap-3 px-6 pt-3 pb-6">
          <Skeleton className="h-4 w-full" />
          <Skeleton className="w-3/5" />
        </div>
      </div>
      <Skeleton className="mt-13 mb-4 h-5 w-24" />
      <div className={SKELETON_BOX}>
        <SkeletonRow label="w-15" value="w-51" />
        <SkeletonRow label="w-20" value="w-69" />
        <SkeletonRow label="w-20" value="w-42" />
        <Skeleton shape="rect" className="h-8 w-full rounded-full" />
      </div>
      <Skeleton className="mt-9 mb-4 h-5 w-21" />
      <div className={SKELETON_BOX}>
        <Skeleton className="mb-3 h-4 w-60" />
        <SkeletonRow label="w-15" value="w-44" />
        <SkeletonRow label="w-24" value="w-16" />
        <SkeletonRow label="w-20" value="w-11" />
        <Skeleton shape="rect" className="h-9 w-31 rounded-lg" />
      </div>
    </div>
  )
}
