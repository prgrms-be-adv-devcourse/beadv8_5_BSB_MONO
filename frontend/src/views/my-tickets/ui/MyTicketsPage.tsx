'use client'

import Link from 'next/link'

import { AppHeader } from '@/widgets/app-header'
import { BottomNav } from '@/widgets/bottom-nav'
import { LoadErrorBody } from '@/widgets/status-screens'
import { TICKET_STATUS } from '@/entities/ticket'
import { cn } from '@/shared/lib'
import { Badge, BibCard, buttonClassName, EmptyState, FOCUS_RING, Skeleton } from '@/shared/ui'

import { useTicketCards, type TicketCard } from '../model/use-ticket-cards'

export type MyTicketsState = 'loading' | 'empty'

// 피그마 v2 · 내 참가권(1333:26919) · 참가권 없음(1333:26995) · 불러오지 못함(1333:27000).
// 참가권 없음 · 불러오지 못함은 시안처럼 뒤로 가기 머리말이고 아래 탭은 없다.
// 크루 신청 줄은 참가권에 크루 정보가 없어 아직 없다.
// state는 시안 미리보기(?preview=loading|empty)용. 없으면 주문 API로 불러온다.
export function MyTicketsPage({ state }: { state?: MyTicketsState }) {
  const cards = useTicketCards()
  const loading = state === 'loading' || (state === undefined && cards.isPending)
  const upcoming = state === 'empty' ? [] : cards.upcoming
  const past = state === 'empty' ? [] : cards.past
  const failed = state === undefined && cards.isError

  if (failed) {
    return (
      <>
        <AppHeader type="sub" title="내 참가권" backHref="/me" />
        <LoadErrorBody onRetry={cards.refetch} retrying={cards.isFetching} />
      </>
    )
  }
  if (!loading && upcoming.length === 0 && past.length === 0) {
    return (
      <>
        <AppHeader type="sub" title="내 참가권" backHref="/me" />
        <main className="flex flex-1 items-center justify-center px-4 pb-16">
          <EmptyState
            title="아직 참가 예정인 대회가 없어요"
            description="대회를 둘러보고 참가 신청을 해 보세요."
            action={
              <Link href="/races" className={buttonClassName({ variant: 'secondary' })}>
                대회 보러 가기
              </Link>
            }
          />
        </main>
      </>
    )
  }

  return (
    <>
      <AppHeader />
      <main className="flex flex-1 flex-col gap-6 px-4 pt-(--layout-content-start) pb-6">
        {loading && <TicketSkeletons />}
        {!loading && <TicketGroup title="참가 예정" tickets={upcoming} />}
        {!loading && <TicketGroup title="지난 대회" tickets={past} />}
      </main>
      <BottomNav />
    </>
  )
}

// 피그마 v2 · 내 참가권(1333:26919): 제목 + 개수, 아래에 참가권 카드
function TicketGroup({ title, tickets }: { title: string; tickets: TicketCard[] }) {
  if (tickets.length === 0) return null
  return (
    <section className="flex flex-col gap-3">
      <h2 className="text-title3">
        {title} {tickets.length}
      </h2>
      {tickets.map((ticket) => (
        <Link
          key={ticket.id}
          href={`/tickets/${ticket.id}`}
          className={cn('rounded-lg', FOCUS_RING)}
          // 카드 제목에 참가자 이름이 있어 Clarity 화면 녹화에서 가린다
          data-clarity-mask="true"
        >
          <BibCard
            image={ticket.image}
            imagePosition="50% 12%"
            eyebrow={ticket.eyebrow}
            status={
              <Badge tone={TICKET_STATUS[ticket.status].tone}>
                {TICKET_STATUS[ticket.status].label}
              </Badge>
            }
            unit="No."
            number={ticket.number}
            title={ticket.title}
            meta={ticket.summary}
          />
        </Link>
      ))}
    </section>
  )
}

// 피그마 M15 · 로딩: 카드 두 장 자리
function TicketSkeletons() {
  return (
    <div role="status" aria-label="불러오는 중" className="flex flex-col gap-3">
      {[0, 1].map((i) => (
        <div
          key={i}
          className="flex flex-col gap-4 rounded-lg bg-bg-surface p-5 shadow-md inset-ring inset-ring-border-default"
        >
          <div className="flex justify-between">
            <Skeleton className="w-24" />
            <Skeleton shape="rect" className="h-4 w-16 rounded-full" />
          </div>
          <div className="flex items-end gap-2">
            <Skeleton className="w-4" />
            <Skeleton shape="rect" className="h-7 w-24" />
          </div>
          <Skeleton className="w-11/12" />
          <Skeleton className="w-2/3" />
        </div>
      ))}
    </div>
  )
}
