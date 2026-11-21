'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { Route } from 'next'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useState } from 'react'

import { AppHeader } from '@/widgets/app-header'
import {
  canInvite,
  formatKstDateTime,
  formatKstMonthDayTime,
  inviteUrl,
  parseKstDateTime,
} from '@/entities/crew'
import {
  partyKeys,
  useLeaveParty,
  useMyRaceStatus,
  usePartyDetail,
  useRegenerateJoinLink,
  type PartyDetail,
  type PartyMember,
} from '@/entities/party'
import { raceQueryOptions } from '@/entities/race'
import { ApiError, memberApi, orderApi, unwrapData, useIdempotencyKey } from '@/shared/api'
import { cn, formatNumber, useNow } from '@/shared/lib'
import {
  Avatar,
  BottomCta,
  Button,
  buttonClassName,
  Dialog,
  Skeleton,
  useToast,
  type BadgeTone,
} from '@/shared/ui'

import { PARTY_STATUS_LABEL } from '../lib/party-copy'
import { partyJoinUrl } from '../lib/party-url'
import { PartyLoadError } from './PartyFailureScreen'

const BADGE_TEXT: Record<BadgeTone, string> = {
  brand: 'text-text-brand',
  success: 'text-text-success',
  warning: 'text-text-warning',
  danger: 'text-text-danger',
  neutral: 'text-text-secondary',
}

const ERROR_FALLBACK = '잠시 후 다시 시도해 주세요.'

// 시안 파티 04 · 20 · 21 · 22 · 23: 모집 중은 모두 결제 전, 결제 진행 중은 결제 완료 · 결제 전(주황), 결제 끝은 참가 확정 · 결제 안 함(빨강)
function memberBadge(
  m: PartyMember,
  status: PartyDetail['status'],
): { label: string; tone: BadgeTone } {
  if (status === 'PAID' || status === 'CLOSED') {
    return m.paid ? { label: '참가 확정', tone: 'brand' } : { label: '결제 안 함', tone: 'danger' }
  }
  if (status === 'PAYING') {
    return m.paid ? { label: '결제 완료', tone: 'neutral' } : { label: '결제 전', tone: 'warning' }
  }
  return { label: '결제 전', tone: 'neutral' }
}

// 피그마 파티 04 · 04-1 · 05 · 06 · 07 · 16~18. 파티장 · 파티원, 모집 중 · 결제 진행 중에 따라 「지금 할 일」이 달라진다.
export function PartyDetailPage({ partyId }: { partyId: number }) {
  const party = usePartyDetail(partyId)
  const data = party.data

  return (
    <>
      <AppHeader type="sub" title="파티 상세" />
      <main className="flex flex-1 flex-col bg-bg-surface">
        {party.isPending ? (
          <PartyDetailSkeleton />
        ) : data ? (
          <PartyDetailBody party={data} />
        ) : (
          <PartyLoadError onRetry={() => void party.refetch()} />
        )}
      </main>
    </>
  )
}

function PartyDetailBody({ party }: { party: PartyDetail }) {
  const router = useRouter()
  const toast = useToast()
  const queryClient = useQueryClient()
  const raceStatus = useMyRaceStatus(party.raceId)
  const me = memberApi.useFindMe({ query: { select: unwrapData } })
  const race = useQuery(raceQueryOptions(party.raceId))
  const crew = memberApi.useFindCrew(party.crewId, { query: { select: unwrapData } })
  const regenerate = useRegenerateJoinLink()
  const leave = useLeaveParty()
  const createInvite = memberApi.useCreateCrewInvite()
  // 초대 토큰은 만들 때 응답에만 오므로, 복사가 실패해도 다시 만들지 않게 만든 링크를 들고 있는다.
  // 참가 링크를 새로 만들면 옛 토큰이 든 링크가 되므로, 만들 때의 참가 토큰과 같을 때만 다시 쓴다.
  const [friendLink, setFriendLink] = useState<{ url: string; joinToken: string }>()
  const orderKey = useIdempotencyKey()
  const [confirm, setConfirm] = useState<'regenerate' | null>(null)
  const [leaveOpen, setLeaveOpen] = useState(false)

  const isLeader = raceStatus.data?.role === 'LEADER'
  const recruiting = party.status === 'RECRUITING'
  const course = race.data?.courses?.find((c) => c.raceCourseId === party.raceCourseId)
  const min = race.data?.crewMinCount ?? 0
  const max = race.data?.crewMaxCount
  const now = useNow()
  const linkAlive = !!now && parseKstDateTime(party.joinTokenExpiresAt) > now.getTime()
  const toastError = (error: unknown) =>
    toast({
      tone: 'error',
      message: (error instanceof ApiError && error.serverMessage) || ERROR_FALLBACK,
    })

  function makeLink() {
    regenerate.mutate(party.id, {
      onSuccess: () => {
        setConfirm(null)
        void queryClient.invalidateQueries({ queryKey: partyKeys.detail(party.id) })
      },
      onError: (error) => {
        setConfirm(null)
        toastError(error)
      },
    })
  }

  async function copy(url: string, done: string) {
    try {
      await navigator.clipboard.writeText(url)
      toast({ message: done, tone: 'success' })
    } catch {
      toast({ message: '복사하지 못했어요. 다시 시도해 주세요.', tone: 'error' })
    }
  }

  // 크루 가입부터 파티 합류까지 링크 하나로 잇는다(BUK-98). 초대 링크는 크루장 · 운영진만 만들 수 있다.
  const canInviteFriends = crew.data?.myRole !== undefined && canInvite(crew.data.myRole)
  function inviteFriend() {
    const joinToken = party.joinToken
    if (!joinToken) return
    if (friendLink?.joinToken === joinToken) {
      return void copy(friendLink.url, '초대 링크를 복사했어요')
    }
    createInvite.mutate(
      { crewId: party.crewId },
      {
        onSuccess: (res) => {
          const inviteToken = res.data?.inviteToken
          if (!inviteToken) return toastError(undefined)
          const url = inviteUrl(inviteToken, window.location.origin, joinToken)
          setFriendLink({ url, joinToken })
          void copy(url, '초대 링크를 복사했어요')
        },
        onError: toastError,
      },
    )
  }

  function copyLink() {
    if (party.joinToken) void copy(partyJoinUrl(party.joinToken), '참가 링크를 복사했어요')
  }

  const pay = useMutation({
    mutationFn: async (participationId: number) =>
      unwrapData(
        await orderApi.create({ participationId }, { idempotencyKey: orderKey(participationId) }),
      ),
    onSuccess: (order) => router.push(`/checkout?orderId=${order.id}` as Route),
    onError: toastError,
  })

  const myPaid = raceStatus.data?.myPaid === true
  const participationId = raceStatus.data?.participationId
  const price = course?.price ?? 0
  const todo: { title: string; description?: string } = (() => {
    if (party.status === 'CLOSED') return { title: '종료된 파티예요' }
    if (party.status === 'PAID') {
      return !isLeader && !myPaid
        ? {
            title: '결제 기간이 끝나 결제할 수 없어요',
            description: `${party.paymentDueAt ? formatKstDateTime(party.paymentDueAt) : ''}에 결제 기간이 끝났어요. 이 대회에는 다시 신청할 수 없어요.`,
          }
        : { title: '결제 기간이 끝났어요' }
    }
    if (party.status === 'PAYING') {
      if (myPaid) {
        return { title: isLeader ? '파티원 결제를 기다리고 있어요' : '결제를 마쳤어요' }
      }
      return {
        title: `내 몫 ${formatNumber(price)}원을 결제해 주세요`,
        description: '결제 기간이 끝나면 결제할 수 없어요. 이 파티로는 참가가 확정되지 않아요.',
      }
    }
    if (!isLeader) return { title: '파티장이 파티 참가하기를 누르면 결제할 수 있어요' }
    const rest = min - party.memberCount
    return { title: rest > 0 ? `${rest}명 더 모이면 참가할 수 있어요` : '파티로 참가할 수 있어요' }
  })()

  // 응답에 결제 기한이 있을 때만 파티장 화면에 마감 시각을 보여 준다.
  const payDueAt = party.status === 'PAYING' && isLeader && myPaid ? party.paymentDueAt : undefined

  // 먼저 합류한 파티원(파티장 다음 사람)이 새 파티장이 된다
  const nextLeader = party.members.find((m) => !m.leader)?.nickname
  const leaveCopy =
    party.memberCount <= 1
      ? {
          title: '나가면 파티가 끝나요',
          body: '남은 파티원이 없어서 파티가 종료돼요. 초대 링크도 더 쓸 수 없어요.',
          confirm: '파티 끝내기',
        }
      : isLeader
        ? {
            title: '파티장을 넘기고 나갈까요?',
            body: `가장 먼저 합류한 ${nextLeader ? `${nextLeader} 님` : '파티원'}이 파티장이 돼요. 내 결제 전 신청서는 취소돼요.`,
            confirm: '넘기고 나가기',
          }
        : {
            title: '파티에서 나갈까요?',
            body: '내 결제 전 신청서가 취소돼요. 같은 링크로 다시 합류하거나 이 대회에 개인으로 신청할 수 있어요.',
            confirm: '나가기',
          }

  const canApply = isLeader && recruiting && linkAlive && min > 0 && party.memberCount >= min

  return (
    <>
      <section className="flex flex-col gap-1 px-4 py-4">
        <span className="text-body1-medium text-text-brand">
          {PARTY_STATUS_LABEL[party.status]}
        </span>
        <h2 className="text-title2">{race.data?.title ?? <Skeleton className="h-8 w-2/3" />}</h2>
        <p className="text-caption-regular text-text-secondary">
          {[crew.data && `${crew.data.name} 크루`, course?.name].filter(Boolean).join(' · ')}
        </p>
      </section>
      <section className="flex flex-col gap-4 px-4 py-6">
        <div className="flex flex-col gap-2">
          <p className="text-caption-medium text-text-secondary">지금 할 일</p>
          <h3 className="text-title3">{todo.title}</h3>
        </div>
        {todo.description && (
          <p className="text-body2-regular text-text-secondary">{todo.description}</p>
        )}
        {party.status === 'PAYING' && !isLeader && myPaid && (
          <p className="text-body2-bold">
            {party.memberCount}명 중 {party.paidCount}명 결제했어요
          </p>
        )}
        {payDueAt && (
          <p className="text-body2-regular text-text-secondary">
            {formatKstMonthDayTime(payDueAt)}까지 파티원이 결제할 수 있어요
          </p>
        )}
        {party.status === 'PAYING' && participationId !== undefined && !myPaid && (
          <Button size="l" loading={pay.isPending} onClick={() => pay.mutate(participationId)}>
            {course?.name} · {formatNumber(price)}원 결제하기
          </Button>
        )}
        {party.status === 'PAID' && !isLeader && !myPaid && (
          <Link href="/races" className={buttonClassName({ variant: 'secondary', size: 'l' })}>
            다른 대회 보기
          </Link>
        )}
        {recruiting && isLeader && (
          <div className="flex flex-col rounded-md border border-border-default bg-bg-surface shadow-sm">
            <div className="flex h-19 flex-col justify-center gap-1 px-4 py-3">
              <span className="text-caption-medium text-text-secondary">초대 링크</span>
              {linkAlive ? (
                <span className="text-body1-medium">
                  {formatKstDateTime(party.joinTokenExpiresAt)}까지 쓸 수 있어요
                </span>
              ) : (
                <span className="text-body1-medium text-text-danger">
                  {formatKstDateTime(party.joinTokenExpiresAt)}에 끝났어요
                </span>
              )}
            </div>
            <div className="flex gap-2 px-4 pb-4">
              {!linkAlive ? (
                <Button
                  variant="ghost"
                  size="s"
                  className="flex-1"
                  loading={regenerate.isPending}
                  onClick={makeLink}
                >
                  새 초대 링크 만들기
                </Button>
              ) : canInviteFriends ? (
                <Button
                  variant="ghost"
                  size="s"
                  className="flex-1"
                  disabled={createInvite.isPending}
                  onClick={inviteFriend}
                >
                  친구 초대
                </Button>
              ) : (
                <Button variant="ghost" size="s" className="flex-1" onClick={copyLink}>
                  링크 복사
                </Button>
              )}
              {linkAlive && (
                <Button
                  variant="ghost"
                  size="s"
                  className="flex-1"
                  onClick={() => setConfirm('regenerate')}
                >
                  새로 만들기
                </Button>
              )}
            </div>
          </div>
        )}
      </section>
      <section className="flex flex-col gap-4 px-4 py-4">
        {recruiting ? (
          <h3 className="text-body1-bold">
            {party.memberCount}명 / 최소 {min}명{max ? ` · 최대 ${max}명` : ''}
          </h3>
        ) : (
          <div className="flex items-center justify-between gap-2">
            <h3 className="text-body1-bold">파티원</h3>
            <span className="text-body2-regular text-text-secondary">
              {party.status === 'PAYING' ? '결제 완료' : '참가 확정'} {party.paidCount} /{' '}
              {party.memberCount}명
            </span>
          </div>
        )}
        <ul className="flex flex-col">
          {party.members.map((m) => (
            <li key={m.memberId} className="flex items-center gap-3 py-2">
              <Avatar size="m" name={m.nickname} />
              <div className="flex min-w-0 flex-1 flex-col gap-1">
                <span className="truncate text-body1-medium">
                  {m.nickname}
                  {m.memberId === me.data?.id ? ' · 나' : ''}
                </span>
                <span className="text-caption-regular text-text-secondary">
                  {m.leader ? '파티장' : '파티원'}
                </span>
              </div>
              {!recruiting && (
                <span
                  className={cn('text-body2-medium', BADGE_TEXT[memberBadge(m, party.status).tone])}
                >
                  {memberBadge(m, party.status).label}
                </span>
              )}
            </li>
          ))}
        </ul>
        {recruiting && (
          <Button
            variant="secondary"
            size="l"
            className="text-text-danger"
            onClick={() => setLeaveOpen(true)}
          >
            파티 나가기
          </Button>
        )}
      </section>
      {recruiting && isLeader && (
        <BottomCta
          className="sticky bottom-0 z-10 border-t border-border-default px-4"
          primary={
            <Link
              href={`/parties/${party.id}/apply`}
              aria-disabled={!canApply}
              tabIndex={canApply ? undefined : -1}
              onClick={(e) => {
                if (!canApply) e.preventDefault()
              }}
              className={cn(
                buttonClassName({ variant: 'primary', size: 'l' }),
                !canApply && 'pointer-events-none bg-bg-brand-disabled',
              )}
            >
              파티 참가하기
            </Link>
          }
        />
      )}
      <Dialog
        open={confirm === 'regenerate'}
        onOpenChange={(open) => !open && setConfirm(null)}
        title="초대 링크를 새로 만들까요?"
        body={
          <>
            새 링크를 만들면 지금 링크로는 합류할 수 없어요.
            <br />
            이미 합류한 파티원은 그대로예요.
          </>
        }
        cancelLabel="돌아가기"
        confirmLabel="새로 만들기"
        onConfirm={makeLink}
        confirming={regenerate.isPending}
      />
      <Dialog
        open={leaveOpen}
        onOpenChange={setLeaveOpen}
        type="danger"
        title={leaveCopy.title}
        body={leaveCopy.body}
        confirmLabel={leaveCopy.confirm}
        onConfirm={() =>
          leave.mutate(party.id, {
            onSuccess: () => router.replace(`/crews/${party.crewId}` as Route),
            onError: (error) => {
              setLeaveOpen(false)
              toastError(error)
            },
          })
        }
        confirming={leave.isPending}
      />
    </>
  )
}

// 시안 2074:94849 · 파티 상세 로딩: 제목 줄, 정보 상자(줄 두 개 · 버튼 세 개), 참가자 세 줄 자리를 회색으로 잡는다.
function PartyDetailSkeleton() {
  return (
    <div role="status" aria-label="파티를 불러오는 중" className="flex flex-col px-4 pt-5 pb-4">
      <Skeleton className="h-4 w-full" />
      <Skeleton className="mt-4 h-5 w-85" />
      <Skeleton className="mt-3 w-85" />
      <Skeleton className="mt-11 w-full" />
      <Skeleton className="mt-4 h-5 w-85" />
      <div className="mt-5 flex flex-col gap-3 rounded-md border border-border-default bg-bg-surface p-4 pt-4">
        <Skeleton className="w-full" />
        <Skeleton className="h-4 w-80" />
        <div className="mt-4 flex items-center gap-2">
          <Skeleton shape="rect" className="h-8 w-40 rounded-full" />
          <Skeleton shape="rect" className="h-10 w-48" />
          <Skeleton shape="rect" className="h-8 flex-1 rounded-full" />
        </div>
      </div>
      <Skeleton className="mt-11 h-4 w-44" />
      <div className="mt-12 flex flex-col gap-5">
        {[0, 1, 2].map((i) => (
          <div key={i} className="flex gap-3">
            <Skeleton shape="circle" className="mt-1 size-8 shrink-0" />
            <div className="flex flex-1 flex-col gap-3">
              <Skeleton className="h-4 w-full" />
              <Skeleton className="w-3/4" />
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
