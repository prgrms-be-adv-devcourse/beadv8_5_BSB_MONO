'use client'

import Link from 'next/link'
import { useState } from 'react'

import { AppHeader } from '@/widgets/app-header'
import { BottomNav } from '@/widgets/bottom-nav'
import { useAuthStatus } from '@/features/auth'
import { TICKET_STATUS } from '@/entities/ticket'
import { useMyBalance } from '@/entities/wallet'
import { memberApi, unwrapData } from '@/shared/api'
import { cn, formatWon } from '@/shared/lib'
import {
  Alert,
  Badge,
  BibCard,
  buttonClassName,
  EmptyState,
  FOCUS_RING,
  Segmented,
  Skeleton,
} from '@/shared/ui'

import { useTicketCards } from '../model/use-ticket-cards'

export type MyTicketsState = 'loading' | 'empty'

// 피그마 V2 · M15 내 참가 대회 (+ 로딩, 없음)
// state는 시안 미리보기(?preview=loading|empty)용. 없으면 주문 API로 불러온다.
// 닉네임 · 이름은 내 정보 조회(GET /api/v1/member/members/me)로 불러온다.
export function MyTicketsPage({ state }: { state?: MyTicketsState }) {
  const [tab, setTab] = useState<'upcoming' | 'past'>('upcoming')
  const cards = useTicketCards()
  const loading = state === 'loading' || (state === undefined && cards.isPending)
  const upcoming = state === 'empty' ? [] : cards.upcoming
  const past = state === 'empty' ? [] : cards.past
  const tickets = tab === 'upcoming' ? upcoming : past
  const failed = state === undefined && cards.isError
  const wallet = useMyBalance()
  const me = memberApi.useFindMe({
    // 로그인 확인(재발급)이 끝난 뒤에만 부른다 (마이페이지와 같은 방식)
    query: { select: unwrapData, enabled: useAuthStatus() === 'authenticated' },
  })
  const member = me.data

  return (
    <>
      <AppHeader />
      <main className="flex flex-1 flex-col gap-6 px-4 pt-(--layout-content-start) pb-6">
        <div className="flex items-center gap-3">
          <div className="size-12 shrink-0 rounded-full bg-bg-subtle" />
          <div className="flex min-w-0 flex-1 flex-col gap-1">
            {loading || (state === undefined && me.isPending) ? (
              <>
                <Skeleton className="h-4 w-48" />
                <Skeleton className="w-32" />
              </>
            ) : (
              <>
                <p className="text-title3">{member?.nickname ?? '-'}</p>
                {/* 실명은 Clarity 화면 녹화에서 가린다 */}
                <p className="text-body2-regular text-text-secondary" data-clarity-mask="true">
                  {member?.name ?? '-'}
                </p>
              </>
            )}
          </div>
          <Link href="/me/edit" className={buttonClassName({ variant: 'secondary', size: 's' })}>
            정보 수정
          </Link>
        </div>

        <div className="flex flex-col gap-3 rounded-lg bg-bg-brand-subtle p-5">
          <div className="flex items-center justify-between gap-2">
            <Link
              href="/me/deposit"
              className={cn('rounded-sm text-body2-medium text-text-secondary', FOCUS_RING)}
            >
              예치금
            </Link>
            {loading || wallet.isPending ? (
              <Skeleton className="h-5 w-16" />
            ) : (
              <p className="text-title2">
                {wallet.balance === undefined ? '-' : formatWon(wallet.balance)}
              </p>
            )}
          </div>
          <div className="flex gap-2">
            <Link
              href="/me/deposit/charge"
              className={buttonClassName({ variant: 'secondary', size: 's', className: 'flex-1' })}
            >
              충전
            </Link>
            <Link
              href="/me/deposit/refund"
              className={buttonClassName({ variant: 'secondary', size: 's', className: 'flex-1' })}
            >
              돌려받기
            </Link>
          </div>
        </div>

        <div className="flex flex-1 flex-col gap-3">
          <Segmented
            label="참가 대회"
            value={tab}
            onChange={setTab}
            items={[
              { value: 'upcoming', label: '참가 예정', count: upcoming.length },
              { value: 'past', label: '지난 대회', count: past.length },
            ]}
          />
          {loading && <TicketSkeletons />}
          {failed && <Alert tone="danger" title="참가권을 불러오지 못했어요" />}
          {!loading && !failed && tickets.length === 0 && (
            <div className="flex flex-1 items-center justify-center">
              <EmptyState
                title="아직 신청한 대회가 없어요"
                description="대회를 찾아 첫 참가권을 받아 보세요."
                action={
                  <Link href="/races" className={buttonClassName({ variant: 'secondary' })}>
                    대회 찾기
                  </Link>
                }
              />
            </div>
          )}
          {!loading &&
            tickets.map((ticket) => (
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
        </div>
      </main>
      <BottomNav />
    </>
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
