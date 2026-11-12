'use client'

import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { Route } from 'next'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useState } from 'react'

import { AppHeader } from '@/widgets/app-header'
import { formatKstDateTime, parseKstDateTime } from '@/entities/crew'
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
import { cn } from '@/shared/lib'
import {
  Avatar,
  Badge,
  Button,
  buttonClassName,
  Dialog,
  Divider,
  Skeleton,
  useToast,
} from '@/shared/ui'

import { PARTY_STATUS_STEPS, PARTY_STEP_INDEX } from '../lib/party-copy'
import { partyJoinUrl } from '../lib/party-url'
import { PartyLoadError } from './PartyFailureScreen'

const ERROR_FALLBACK = '잠시 후 다시 시도해 주세요.'

function memberBadge(m: PartyMember) {
  if (m.paid) return '참가 확정'
  return m.participationStatus === 'APPLIED' ? '결제 대기' : '결제 전'
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
  const race = productApi.useFindRace(party.raceId, { query: { select: unwrapData } })
  const crew = memberApi.useFindCrew(party.crewId, { query: { select: unwrapData } })
  const regenerate = useRegenerateJoinLink()
  const leave = useLeaveParty()
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

  async function copyLink() {
    if (!party.joinToken) return
    try {
      await navigator.clipboard.writeText(partyJoinUrl(party.joinToken))
      toast({ message: '참가 링크를 복사했어요', tone: 'success' })
    } catch {
      toast({ message: '복사하지 못했어요. 다시 시도해 주세요.', tone: 'error' })
    }
  }

  const pay = useMutation({
    mutationFn: async (participationId: number) =>
      unwrapData(
        await orderApi.create({ participationId }, { idempotencyKey: orderKey(participationId) }),
      ),
    onSuccess: (order) => router.push(`/checkout?orderId=${order.id}` as Route),
    onError: toastError,
  })

  const todo = (() => {
    if (party.status === 'CLOSED') return { title: '종료된 파티예요' }
    if (party.status === 'PAID') return { title: '파티원 모두 결제했어요' }
    const due = party.paymentDueAt
      ? `${formatKstDateTime(party.paymentDueAt)}까지 결제해 주세요`
      : ''
    if (party.status === 'PAYING') {
      return raceStatus.data?.myPaid
        ? { title: '결제를 마쳤어요', description: due }
        : { title: '지금 결제할 수 있어요', description: due }
    }
    if (!isLeader) return { title: '파티장이 파티를 신청하면 결제할 수 있어요' }
    if (!linkAlive) return { title: '참가 링크 기간이 끝났어요' }
    const rest = min - party.memberCount
    return { title: rest > 0 ? `${rest}명 더 모이면 신청할 수 있어요` : '파티를 신청할 수 있어요' }
  })()

  const canApply = isLeader && recruiting && min > 0 && party.memberCount >= min
  const participationId = raceStatus.data?.participationId

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
        {party.status === 'PAYING' && participationId !== undefined && !raceStatus.data?.myPaid && (
          <Button size="l" loading={pay.isPending} onClick={() => pay.mutate(participationId)}>
            결제하기
          </Button>
        )}
        {recruiting && isLeader && linkAlive && (
          <div className="flex items-center justify-between text-body2-regular text-text-secondary">
            <span>링크 · {formatKstDateTime(party.joinTokenExpiresAt)}까지</span>
            <span className="flex gap-4">
              <button type="button" onClick={() => void copyLink()}>
                링크 복사
              </button>
              <button type="button" onClick={() => setConfirm('regenerate')}>
                새로 만들기
              </button>
            </span>
          </div>
        )}
      </section>
      <Divider />
      <section className="flex flex-col gap-3 px-4 py-6">
        <h3 className="text-body1-medium">
          {party.memberCount}명 / 최소 {min}명{max ? ` · 최대 ${max}명` : ''}
        </h3>
        <ul className="flex flex-col gap-3">
          {party.members.map((m) => (
            <li key={m.memberId} className="flex items-center gap-3">
              <Avatar size="m" name={m.nickname} />
              <div className="flex min-w-0 flex-1 flex-col">
                <span className="truncate text-body2-medium">
                  {m.nickname}
                  {m.leader && isLeader ? ' · 나' : ''}
                </span>
                <span className="text-caption-regular text-text-secondary">
                  {m.leader ? '파티장' : '파티원'}
                </span>
              </div>
              <Badge>{memberBadge(m)}</Badge>
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
