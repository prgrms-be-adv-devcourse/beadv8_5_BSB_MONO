'use client'

import { useQueryClient } from '@tanstack/react-query'
import type { Route } from 'next'
import Link from 'next/link'
import { useState } from 'react'

import { AppHeader } from '@/widgets/app-header'
import { useAuthStatus } from '@/features/auth'
import {
  canInvite,
  formatKstDateTime,
  formatRemaining,
  inviteCreatedLabel,
  withSubjectParticle,
} from '@/entities/crew'
import { ApiError, memberApi, memberModel, unwrapData } from '@/shared/api'
import {
  Button,
  buttonClassName,
  Dialog,
  Divider,
  EmptyState,
  Skeleton,
  useToast,
} from '@/shared/ui'

import { inviteUrl } from '../model/invite-url'
import { CrewBand, CrewLoadError } from './CrewBand'

// 피그마 시안v4 · M27 초대 링크 만들기 (만듦 · 취소 확인).
// 토큰은 만들 때 응답에만 온다(백엔드 결정 BE-028). 그래서 만든 링크는 이 화면에서 바로 복사하게 하고,
// 보낸 초대 목록에는 링크 대신 만든 시각과 남은 시간만 보여 준다.
// 보낸 초대 목록 API는 아직 쓸 수 있는 초대만 돌려준다. 가입 완료 · 취소함 · 기간 끝남 줄은 API가 생기면 더한다.
export function CrewInvitesPage({ crewId }: { crewId: number }) {
  const authenticated = useAuthStatus() === 'authenticated'
  const queryClient = useQueryClient()
  const showToast = useToast()
  // 내 역할로 초대 권한(크루장 · 운영진)과 취소 권한(크루장이거나 보낸 사람)을 정한다(정책 7장 「역할과 초대」 1·3번)
  const crew = memberApi.useFindCrew(crewId, {
    query: { select: unwrapData, enabled: authenticated },
  })
  const myRole = crew.data?.myRole
  const allowed = myRole !== undefined && canInvite(myRole)
  // 크루원은 보낸 초대 목록도 403이라 부르지 않는다
  const invites = memberApi.useFindCrewInvites(crewId, {
    query: { select: unwrapData, enabled: authenticated && allowed },
  })
  const me = memberApi.useFindMe({ query: { select: unwrapData, enabled: authenticated } })
  const create = memberApi.useCreateCrewInvite()
  const cancel = memberApi.useCancelCrewInvite()
  const [cancelTarget, setCancelTarget] = useState<number | null>(null)
  const created = create.data?.data

  const refreshInvites = () =>
    queryClient.invalidateQueries({ queryKey: memberApi.getFindCrewInvitesQueryKey(crewId) })

  const createInvite = () =>
    create.mutate(
      { crewId },
      {
        onSuccess: () => void refreshInvites(),
        onError: (error) => showToast({ tone: 'error', message: errorMessage(error) }),
      },
    )

  const copy = async (url: string) => {
    try {
      await navigator.clipboard.writeText(url)
      showToast({ tone: 'success', message: '초대 링크를 복사했어요' })
    } catch {
      showToast({ tone: 'error', message: '복사하지 못했어요. 링크를 길게 눌러 복사해 주세요.' })
    }
  }

  const confirmCancel = () => {
    if (cancelTarget === null) return
    cancel.mutate(
      { crewId, inviteId: cancelTarget },
      {
        onSuccess: () => {
          // 방금 만든 링크를 취소했으면 위쪽 카드도 비워 취소된 링크를 복사하지 못하게 한다
          if (created?.inviteId === cancelTarget) create.reset()
          setCancelTarget(null)
        },
        onError: (error) => {
          setCancelTarget(null)
          showToast({ tone: 'error', message: errorMessage(error) })
        },
        onSettled: () => void refreshInvites(),
      },
    )
  }

  const canCancel = (invite: memberModel.CrewInviteDto) =>
    myRole === 'LEADER' || invite.createdBy.memberId === me.data?.id

  return (
    <>
      <AppHeader type="sub" title="크루원 초대" backHref={`/crews/${crewId}` as Route} />
      {crew.isPending ? (
        <main className="flex flex-1 flex-col">
          <CrewBand>
            <InvitesSkeleton />
          </CrewBand>
        </main>
      ) : crew.isError ? (
        <main className="flex flex-1 flex-col">
          <CrewLoadError onRetry={() => crew.refetch()} />
        </main>
      ) : !allowed ? (
        // 시안에 없는 상태: 크루원이 이 주소를 직접 연 경우
        <main className="flex flex-1 flex-col">
          <EmptyState
            className="pt-26"
            title="크루장과 운영진만 초대할 수 있어요"
            description="초대 링크가 필요하면 크루장이나 운영진에게 요청해 주세요."
            action={
              <Link
                href={`/crews/${crewId}` as Route}
                className={buttonClassName({ variant: 'secondary', size: 'm' })}
              >
                크루 홈으로 가기
              </Link>
            }
          />
        </main>
      ) : (
        <main className="flex flex-1 flex-col">
          <CrewBand>
            {created ? (
              <>
                <InviteIntro title="초대 링크를 만들었어요" />
                <div className="flex flex-col gap-1 rounded-lg bg-bg-brand-subtle p-4">
                  <p className="text-heading-s break-all">
                    {inviteUrl(created.inviteToken).replace(/^https?:\/\//, '')}
                  </p>
                  <p className="text-caption text-text-secondary">
                    {formatKstDateTime(created.expiresAt)}까지 쓸 수 있어요
                  </p>
                </div>
                <Button size="l" onClick={() => copy(inviteUrl(created.inviteToken))}>
                  링크 복사하기
                </Button>
              </>
            ) : (
              // 시안에 없는 상태: 링크를 만들기 전. 만든 링크는 응답에만 오므로 이 화면에서 만든다.
              <>
                <InviteIntro title="초대 링크를 만들어요" />
                <Button size="l" loading={create.isPending} onClick={createInvite}>
                  초대 링크 만들기
                </Button>
              </>
            )}
          </CrewBand>
          <Divider type="section" />
          <CrewBand>
            <h2 className="text-heading-m">보낸 초대</h2>
            {invites.isPending ? (
              <InvitesSkeleton />
            ) : invites.isError ? (
              <CrewLoadError onRetry={() => invites.refetch()} />
            ) : invites.data.length === 0 ? (
              // 시안에 없는 상태: 보낸 초대가 없음
              <p className="text-body-s text-text-secondary">아직 보낸 초대가 없어요.</p>
            ) : (
              <ul className="flex flex-col gap-4">
                {invites.data.map((invite) => {
                  const remaining = formatRemaining(invite.expiresAt)
                  return (
                    <li key={invite.inviteId} className="flex items-center gap-3">
                      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                        <p className="text-body-m">{inviteCreatedLabel(invite.createdAt)}</p>
                        <p className="text-caption text-text-secondary">
                          {withSubjectParticle(invite.createdBy.nickname)} 보냄
                          {remaining && ` · ${remaining} 남음`}
                        </p>
                      </div>
                      {canCancel(invite) && (
                        <Button
                          variant="secondary"
                          size="s"
                          onClick={() => setCancelTarget(invite.inviteId)}
                        >
                          초대 취소
                        </Button>
                      )}
                    </li>
                  )
                })}
              </ul>
            )}
          </CrewBand>
        </main>
      )}
      <Dialog
        open={cancelTarget !== null}
        onOpenChange={(open) => !open && setCancelTarget(null)}
        type="danger"
        title="이 초대를 취소할까요?"
        body="취소하면 이 링크로 크루에 가입할 수 없어요."
        cancelLabel="돌아가기"
        confirmLabel="초대 취소하기"
        confirming={cancel.isPending}
        onConfirm={confirmCancel}
      />
    </>
  )
}

function InviteIntro({ title }: { title: string }) {
  return (
    <div className="flex flex-col gap-2">
      <h2 className="text-heading-m">{title}</h2>
      <p className="text-body-s text-text-secondary">
        링크를 받은 사람이 가입하기를 누르면 바로 크루원이 돼요. 링크 하나로 한 명이 가입할 수
        있어요.
      </p>
    </div>
  )
}

function InvitesSkeleton() {
  return (
    <div className="flex flex-col gap-3">
      <span role="status" className="sr-only">
        보낸 초대를 불러오는 중
      </span>
      <Skeleton className="w-2/3" />
      <Skeleton className="w-1/2" />
    </div>
  )
}

function errorMessage(error: unknown): string {
  return (error instanceof ApiError && error.serverMessage) || '잠시 후 다시 시도해 주세요.'
}
