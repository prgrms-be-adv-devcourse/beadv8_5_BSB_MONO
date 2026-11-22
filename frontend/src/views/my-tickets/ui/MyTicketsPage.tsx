'use client'

import Link from 'next/link'

import { AppHeader } from '@/widgets/app-header'
import { BottomNav } from '@/widgets/bottom-nav'
import { TICKET_STATUS } from '@/entities/ticket'
import { cn, useSearchParamsState } from '@/shared/lib'
import {
  BibCard,
  Button,
  EmptyState,
  FOCUS_RING,
  Skeleton,
  Tab,
  TabBar,
  TabPanel,
  Tabs,
} from '@/shared/ui'

import { useTicketCards, type TicketCard } from '../model/use-ticket-cards'

export type MyTicketsState = 'loading' | 'empty'

// 피그마 V3 · 내 참가권 · 목록(1944:20420), v2 참가권 없음(1333:26995) · 불러오지 못함(1333:27000).
// 참가권 없음 · 불러오지 못함은 시안처럼 뒤로 가기 머리말이고 아래 탭은 없다.
// 크루 신청 줄은 참가권에 크루 정보가 없어 아직 없다.
// state는 시안 미리보기(?preview=loading|empty)용. 없으면 주문 API로 불러온다.
export function MyTicketsPage({ state }: { state?: MyTicketsState }) {
  // 탭은 URL(?tab=)에 담아 상세에 갔다 돌아와도 남긴다. 처음에는 다가오는 대회
  const [url, setUrl] = useSearchParamsState({ tab: 'upcoming' }, { tab: ['upcoming', 'past'] })
  const cards = useTicketCards()
  const loading = state === 'loading' || (state === undefined && cards.isPending)
  const upcoming = state === 'empty' ? [] : cards.upcoming
  const past = state === 'empty' ? [] : cards.past
  const failed = state === undefined && cards.isError

  // 피그마 v4: 불러오지 못함 · 참가권 없음도 목록처럼 로고 머리말과 아래 탭을 둔다
  if (failed || (!loading && upcoming.length === 0 && past.length === 0)) {
    return (
      <>
        <AppHeader search={false} className="border-b-0" />
        <main className="flex flex-1 flex-col items-center px-4 pt-30">
          {failed ? (
            <EmptyState
              icon={null}
              role="alert"
              title="참가권을 불러오지 못했어요"
              description="잠시 뒤 다시 열어 주세요"
              action={
                <Button
                  variant="secondary"
                  onClick={cards.refetch}
                  disabled={cards.isFetching}
                  loading={cards.isFetching}
                >
                  다시 불러오기
                </Button>
              }
            />
          ) : (
            <EmptyState
              icon={null}
              title="신청한 대회가 없어요"
              description="대회를 찾아 신청해 보세요"
            />
          )}
        </main>
        <BottomNav />
      </>
    )
  }

  if (loading) {
    return (
      <>
        <AppHeader search={false} className="border-b-0" />
        <main className="flex flex-1 flex-col gap-6 px-4 pt-6 pb-6">
          <TicketSkeletons />
        </main>
        <BottomNav />
      </>
    )
  }

  // 시안 v4 · 내 참가권 · 다가오는 대회·지난 대회 탭(2233:9773 · 2233:9874) · 비었을 때(2233:9983 · 2233:10098)
  return (
    <>
      <AppHeader search={false} className="border-b-0" />
      <Tabs
        className="flex flex-1 flex-col"
        value={url.tab}
        onValueChange={(value) => setUrl({ tab: String(value) })}
      >
        <TabBar>
          <Tab value="upcoming" count={upcoming.length}>
            다가오는 대회
          </Tab>
          <Tab value="past" count={past.length}>
            지난 대회
          </Tab>
        </TabBar>
        <TabPanel value="upcoming">
          <TicketList tickets={upcoming} emptyTitle="다가오는 대회가 없어요" />
        </TabPanel>
        <TabPanel value="past">
          <TicketList tickets={past} emptyTitle="지난 대회가 없어요" />
        </TabPanel>
      </Tabs>
      <BottomNav />
    </>
  )
}

// 탭 안 목록: 참가권 카드를 세로로 쌓고, 비었으면 빈 화면
function TicketList({ tickets, emptyTitle }: { tickets: TicketCard[]; emptyTitle: string }) {
  if (tickets.length === 0) {
    return (
      <div className="flex flex-col items-center px-4 pt-24">
        <EmptyState icon={null} title={emptyTitle} />
      </div>
    )
  }
  return (
    <section className="flex flex-col gap-4 px-4 pt-6 pb-6">
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
            // v3 시안: 참가 확정은 배지 없이 보여 주고, 확인 중 · 종료 · 취소만 배지를 단다
            status={
              ticket.status !== 'confirmed' && (
                <span className="text-body2-medium text-text-on-brand">
                  {TICKET_STATUS[ticket.status].label}
                </span>
              )
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

// 피그마 v4 · 내 참가권 로딩: 제목 자리 + 사진 · 글 두 줄 카드 두 장
function TicketSkeletons() {
  return (
    <div role="status" aria-label="불러오는 중" className="flex flex-col gap-4">
      {[0, 1].map((i) => (
        <div
          key={i}
          className="flex flex-col overflow-hidden rounded-lg bg-bg-surface inset-ring inset-ring-border-default"
        >
          <Skeleton shape="rect" className="h-32 w-full rounded-none" />
          <div className="flex flex-col gap-3 p-5">
            <Skeleton className="w-full" />
            <Skeleton className="w-2/3" />
          </div>
        </div>
      ))}
    </div>
  )
}
