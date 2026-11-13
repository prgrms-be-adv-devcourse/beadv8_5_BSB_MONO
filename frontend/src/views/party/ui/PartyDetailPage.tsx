'use client'

import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { Route } from 'next'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useState } from 'react'

import { AppHeader } from '@/widgets/app-header'
import { canInvite, formatKstDateTime, inviteUrl, parseKstDateTime } from '@/entities/crew'
import {
  partyKeys,
  useLeaveParty,
  useMyRaceStatus,
  usePartyDetail,
  useRegenerateJoinLink,
  type PartyDetail,
  type PartyMember,
} from '@/entities/party'
import {
  ApiError,
  memberApi,
  orderApi,
  productApi,
  unwrapData,
  useIdempotencyKey,
} from '@/shared/api'
import { cn, formatNumber } from '@/shared/lib'
import {
  Avatar,
  Badge,
  Button,
  buttonClassName,
  Dialog,
  Divider,
  Skeleton,
  useToast,
  type BadgeTone,
} from '@/shared/ui'

import { PARTY_STATUS_STEPS, PARTY_STEP_INDEX } from '../lib/party-copy'
import { partyJoinUrl } from '../lib/party-url'
import { PartyLoadError } from './PartyFailureScreen'

const ERROR_FALLBACK = '잠시 후 다시 시도해 주세요.'

// 시안 파티 04 · 20 · 21 · 22 · 23: 모집 중은 모두 결제 전, 결제 진행 중은 결제 완료 · 결제 전(주황), 결제 끝은 참가 확정 · 결제 안 함(빨강)
function memberBadge(
  m: PartyMember,
  status: PartyDetail['status'],
): { label: string; tone: BadgeTone } {
  if (status === 'PAID' || status === 'CLOSED') {
    return m.paid ? { label: '참가 확정', tone: 'accent' } : { label: '결제 안 함', tone: 'danger' }
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
          <div className="flex flex-col gap-4 p-4" aria-busy>
            <Skeleton className="h-8 w-2/3" />
            <Skeleton className="h-4 w-1/3" />
            <Skeleton className="h-24 w-full" />
          </div>
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
  const race = productApi.useFindRace(party.raceId, { query: { select: unwrapData } })
  const crew = memberApi.useFindCrew(party.crewId, { query: { select: unwrapData } })
  const regenerate = useRegenerateJoinLink()
  const leave = useLeaveParty()
  const createInvite = memberApi.useCreateCrewInvite()
  // 초대 토큰은 만들 때 응답에만 오므로, 복사가 실패해도 다시 만들지 않게 만든 링크를 들고 있는다
  const [friendLink, setFriendLink] = useState<string>()
  const orderKey = useIdempotencyKey()
  const [confirm, setConfirm] = useState<'regenerate' | 'leave' | null>(null)

  const isLeader = raceStatus.data?.role === 'LEADER'
  const recruiting = party.status === 'RECRUITING'
  const course = race.data?.courses?.find((c) => c.raceCourseId === party.raceCourseId)
  const min = race.data?.crewMinCount ?? 0
  const max = race.data?.crewMaxCount
  const [now] = useState(() => Date.now())
  const linkAlive = parseKstDateTime(party.joinTokenExpiresAt) > now
  const stepIndex = PARTY_STEP_INDEX[party.status]
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
    if (friendLink) return void copy(friendLink, '초대 링크를 복사했어요')
    createInvite.mutate(
      { crewId: party.crewId },
      {
        onSuccess: (res) => {
          const url = inviteUrl(res.data?.inviteToken ?? '', window.location.origin, joinToken)
          setFriendLink(url)
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
    if (!isLeader) return { title: '파티장이 파티를 신청하면 결제할 수 있어요' }
    if (!linkAlive) return { title: '참가 링크 기간이 끝났어요' }
    const rest = min - party.memberCount
    return { title: rest > 0 ? `${rest}명 더 모이면 신청할 수 있어요` : '파티를 신청할 수 있어요' }
  })()

  const canApply = isLeader && recruiting && min > 0 && party.memberCount >= min

  return (
    <>
      <section className="flex flex-col gap-4 px-4 py-6">
        <div className="flex flex-col gap-1">
          <h2 className="text-title1">{race.data?.title ?? <Skeleton className="h-8 w-2/3" />}</h2>
          <p className="text-body2-regular text-text-secondary">
            {[crew.data && `${crew.data.name} 크루`, course?.name].filter(Boolean).join(' · ')}
          </p>
        </div>
        <ol className="grid grid-cols-3 gap-2" aria-label="파티 진행 단계">
          {PARTY_STATUS_STEPS.map((label, i) => (
            <li key={label} className="flex flex-col gap-2">
              <span
                className={cn('h-1 rounded-full', i <= stepIndex ? 'bg-bg-brand' : 'bg-bg-muted')}
              />
              <span
                className={cn(
                  'text-body2-medium',
                  i === stepIndex ? 'text-text-primary' : 'text-text-tertiary',
                )}
                aria-current={i === stepIndex ? 'step' : undefined}
              >
                {label}
              </span>
            </li>
          ))}
        </ol>
      </section>
      <Divider />
      <section className="flex flex-col gap-3 px-4 py-6">
        <p className="text-caption-regular text-text-secondary">지금 할 일</p>
        <h3 className="text-title3">{todo.title}</h3>
        {todo.description && (
          <p className="text-body2-regular text-text-secondary">{todo.description}</p>
        )}
        {recruiting && isLeader && linkAlive && (
          <Link
            href={`/parties/${party.id}/apply`}
            aria-disabled={!canApply}
            tabIndex={canApply ? undefined : -1}
            onClick={(e) => {
              if (!canApply) e.preventDefault()
            }}
            className={cn(
              buttonClassName({ variant: 'primary', size: 'l' }),
              !canApply && 'pointer-events-none opacity-40',
            )}
          >
            파티 신청하기
          </Link>
        )}
        {recruiting && isLeader && !linkAlive && (
          <Button size="l" loading={regenerate.isPending} onClick={makeLink}>
            새 참가 링크 만들기
          </Button>
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
        {recruiting && isLeader && linkAlive && (
          <div className="flex items-center justify-between text-body2-regular text-text-secondary">
            <span>링크 · {formatKstDateTime(party.joinTokenExpiresAt)}까지</span>
            <span className="flex gap-4">
              {canInviteFriends ? (
                <button type="button" disabled={createInvite.isPending} onClick={inviteFriend}>
                  친구 초대
                </button>
              ) : (
                <button type="button" onClick={copyLink}>
                  링크 복사
                </button>
              )}
              <button type="button" onClick={() => setConfirm('regenerate')}>
                새로 만들기
              </button>
            </span>
          </div>
        )}
      </section>
      <Divider />
      <section className="flex flex-col gap-3 px-4 py-6">
        {recruiting ? (
          <h3 className="text-body1-medium">
            {party.memberCount}명 / 최소 {min}명{max ? ` · 최대 ${max}명` : ''}
          </h3>
        ) : (
          <div className="flex items-center justify-between gap-2">
            <h3 className="text-body1-medium">파티원</h3>
            <span className="text-body2-regular text-text-secondary">
              {party.status === 'PAYING' ? '결제 완료' : '참가 확정'} {party.paidCount} /{' '}
              {party.memberCount}명
            </span>
          </div>
        )}
        <ul className="flex flex-col gap-3">
          {party.members.map((m) => (
            <li key={m.memberId} className="flex items-center gap-3">
              <Avatar size="m" name={m.nickname} />
              <div className="flex min-w-0 flex-1 flex-col">
                <span className="truncate text-body2-medium">
                  {m.nickname}
                  {m.memberId === me.data?.id ? ' · 나' : ''}
                </span>
                <span className="text-caption-regular text-text-secondary">
                  {m.leader ? '파티장' : '파티원'}
                </span>
              </div>
              <Badge tone={memberBadge(m, party.status).tone}>
                {memberBadge(m, party.status).label}
              </Badge>
            </li>
          ))}
        </ul>
      </section>
      {recruiting && (
        <div className="flex justify-center pb-8">
          <Button variant="text" size="m" onClick={() => setConfirm('leave')}>
            파티 나가기
          </Button>
        </div>
      )}
      <Dialog
        open={confirm === 'regenerate'}
        onOpenChange={(open) => !open && setConfirm(null)}
        title="참가 링크를 새로 만들까요?"
        body="새로 만들면 옛 링크로는 합류할 수 없어요."
        confirmLabel="새로 만들기"
        onConfirm={makeLink}
        confirming={regenerate.isPending}
      />
      <Dialog
        open={confirm === 'leave'}
        onOpenChange={(open) => !open && setConfirm(null)}
        type="danger"
        title="파티에서 나갈까요?"
        body={
          party.memberCount <= 1
            ? '남은 사람이 없어 파티가 종료돼요.'
            : isLeader
              ? '먼저 합류한 파티원이 새 파티장이 돼요. 나간 뒤에도 같은 링크로 다시 합류할 수 있어요.'
              : '나간 뒤에도 같은 링크로 다시 합류할 수 있어요.'
        }
        confirmLabel="나가기"
        onConfirm={() =>
          leave.mutate(party.id, {
            onSuccess: () => router.replace(`/crews/${party.crewId}` as Route),
            onError: (error) => {
              setConfirm(null)
              toastError(error)
            },
          })
        }
        confirming={leave.isPending}
      />
    </>
  )
}
