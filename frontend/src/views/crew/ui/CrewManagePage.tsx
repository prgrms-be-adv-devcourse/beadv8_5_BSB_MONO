'use client'

import { useQueryClient } from '@tanstack/react-query'
import type { Route } from 'next'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useState } from 'react'

import { AppHeader } from '@/widgets/app-header'
import { useAuthStatus } from '@/features/auth'
import { CREW_ROLE_LABEL, withObjectParticle } from '@/entities/crew'
import { memberApi, unwrapData, type memberModel } from '@/shared/api'
import {
  BottomSheet,
  BottomSheetOption,
  Button,
  buttonClassName,
  Dialog,
  Divider,
  EmptyState,
  Skeleton,
  useToast,
} from '@/shared/ui'

import { crewActionMessage } from '../model/crew-action-failure'
import { CrewBand, CrewHomeError, retryCrewQuery } from './CrewBand'
import { CrewMemberList } from './CrewMemberList'

type Member = memberModel.CrewMemberDto
type Pending = { kind: 'remove' | 'transfer'; member: Member } | { kind: 'disband' } | null

// 피그마 시안v8 · M28 크루 관리 (크루장 · 관리 메뉴 · 내보내기 확인 · 크루장 넘기기 확인 · 나뿐일 때 · 권한 없음).
// 크루장만 쓴다(2그룹 규칙: 내보내기 · 넘기기 · 없애기는 크루장만). 버튼은 화면에서 숨기고, 서버도 403 CREW_LEADER_ONLY로 막는다.
// 시안의 「운영진으로 정하기」는 API 계약이 없어 메뉴에서 뺐다. 크루 없애기 확인 창은 시안에 없어 내보내기 확인과 같은 모양을 쓴다.
export function CrewManagePage({ crewId }: { crewId: number }) {
  const router = useRouter()
  const queryClient = useQueryClient()
  const showToast = useToast()
  const crew = memberApi.useFindCrew(crewId, {
    query: {
      select: unwrapData,
      enabled: useAuthStatus() === 'authenticated',
      retry: retryCrewQuery,
    },
  })
  const remove = memberApi.useRemoveCrewMember()
  const transfer = memberApi.useTransferCrewLeader()
  const disband = memberApi.useDisbandCrew()
  const [menuTarget, setMenuTarget] = useState<Member | null>(null)
  const [pending, setPending] = useState<Pending>(null)

  const refreshCrew = () =>
    queryClient.invalidateQueries({ queryKey: memberApi.getFindCrewQueryKey(crewId) })

  // 실패는 대부분 다른 사람이 먼저 바꾼 오래된 화면이라, 이유를 알리고 크루 정보를 다시 불러온다
  const fail = (error: unknown) => {
    setPending(null)
    showToast({ tone: 'error', message: crewActionMessage(error) })
    void refreshCrew()
  }

  const choose = (kind: 'remove' | 'transfer', member: Member) => {
    setMenuTarget(null)
    setPending({ kind, member })
  }

  const confirm = () => {
    if (!pending || !crew.data) return
    const name = crew.data.name
    if (pending.kind === 'remove') {
      const { member } = pending
      remove.mutate(
        { crewId, memberId: member.memberId },
        {
          onSuccess: () => {
            setPending(null)
            showToast({ tone: 'success', message: `${member.nickname} 님을 크루에서 내보냈어요` })
            void refreshCrew()
          },
          onError: fail,
        },
      )
    } else if (pending.kind === 'transfer') {
      const { member } = pending
      transfer.mutate(
        { crewId, data: { memberId: member.memberId } },
        {
          onSuccess: () => {
            showToast({ tone: 'success', message: `${member.nickname} 님에게 크루장을 넘겼어요` })
            // 내 역할이 크루원으로 바뀌었으니 크루 홈을 다시 불러 바뀐 화면을 보여 준다
            void refreshCrew()
            router.replace(`/crews/${crewId}` as Route)
          },
          onError: fail,
        },
      )
    } else {
      disband.mutate(
        { crewId },
        {
          onSuccess: () => {
            showToast({ tone: 'success', message: `${withObjectParticle(name)} 없앴어요` })
            void queryClient.invalidateQueries({ queryKey: memberApi.getFindMyCrewsQueryKey() })
            router.replace('/crews')
          },
          onError: fail,
        },
      )
    }
  }

  return (
    <>
      <AppHeader type="sub" title="크루 관리" backHref={`/crews/${crewId}` as Route} />
      <main className="flex flex-1 flex-col">
        {crew.isPending ? (
          <ManageSkeleton />
        ) : crew.isError ? (
          <CrewHomeError error={crew.error} onRetry={() => crew.refetch()} />
        ) : crew.data.myRole !== 'LEADER' ? (
          <EmptyState
            className="pt-26"
            title="크루장만 크루를 관리할 수 있어요"
            description="크루장을 넘기거나 크루원을 내보내는 일은 크루장이 해요."
            action={
              <Link
                href={`/crews/${crewId}` as Route}
                className={buttonClassName({ variant: 'secondary', size: 'm' })}
              >
                크루 홈으로
              </Link>
            }
          />
        ) : (
          <>
            <CrewBand>
              <div className="flex flex-col gap-2">
                <h2 className="text-heading-m">
                  {crew.data.name} 크루원 {crew.data.memberCount}명
                </h2>
                <p className="text-body-s text-text-secondary">
                  {crew.data.memberCount <= 1
                    ? '아직 다른 크루원이 없어요. 초대 링크로 크루원을 모아 보세요.'
                    : '크루원의 「관리」를 눌러 크루장을 넘기거나 크루에서 내보낼 수 있어요.'}
                </p>
              </div>
              <CrewMemberList
                members={crew.data.members}
                // 이 화면은 크루장만 보므로 크루장 줄이 나다
                label={(member) =>
                  member.role === 'LEADER' ? `${member.nickname} (나)` : member.nickname
                }
                action={(member) =>
                  member.role !== 'LEADER' && (
                    <Button variant="ghost" size="s" onClick={() => setMenuTarget(member)}>
                      관리
                    </Button>
                  )
                }
              />
              {crew.data.memberCount <= 1 && (
                <Link
                  href={`/crews/${crewId}/invites` as Route}
                  className={buttonClassName({ variant: 'secondary', size: 'm' })}
                >
                  초대 링크 만들기
                </Link>
              )}
            </CrewBand>
            <Divider type="section" />
            <CrewBand>
              <Button
                variant="ghost"
                size="m"
                className="self-start"
                disabled={crew.data.memberCount > 1}
                onClick={() => setPending({ kind: 'disband' })}
              >
                크루 없애기
              </Button>
              <p className="text-body-s text-text-secondary">
                {crew.data.memberCount <= 1
                  ? '크루원이 나뿐이라 크루를 없앨 수 있어요.'
                  : '다른 크루원이 있으면 크루를 없앨 수 없어요. 크루장을 넘긴 뒤 크루를 떠날 수 있어요.'}
              </p>
            </CrewBand>
          </>
        )}
      </main>
      <BottomSheet
        open={menuTarget !== null}
        onOpenChange={(open) => !open && setMenuTarget(null)}
        title={menuTarget && `${menuTarget.nickname} · ${CREW_ROLE_LABEL[menuTarget.role]}`}
      >
        {menuTarget && (
          <>
            <BottomSheetOption onClick={() => choose('transfer', menuTarget)}>
              크루장 넘기기
            </BottomSheetOption>
            <BottomSheetOption onClick={() => choose('remove', menuTarget)}>
              크루에서 내보내기
            </BottomSheetOption>
          </>
        )}
      </BottomSheet>
      <Dialog
        open={pending !== null}
        onOpenChange={(open) => !open && setPending(null)}
        type="danger"
        {...dialogCopy(pending, crew.data?.name ?? '')}
        confirming={remove.isPending || transfer.isPending || disband.isPending}
        onConfirm={confirm}
      />
    </>
  )
}

function dialogCopy(pending: Pending, crewName: string) {
  if (pending?.kind === 'remove') {
    const { nickname } = pending.member
    return {
      title: `${nickname} 님을 크루에서 내보낼까요?`,
      body: '내보내도 이미 결제한 참가권은 그대로예요. 결제하지 않고 잡아 둔 자리만 풀려요.',
      confirmLabel: '내보내기',
    }
  }
  if (pending?.kind === 'transfer') {
    const { nickname } = pending.member
    return {
      title: `${nickname} 님에게 크루장을 넘길까요?`,
      body: `넘기면 크루 관리는 ${nickname} 님이 해요. 나는 크루원이 돼요.`,
      confirmLabel: '크루장 넘기기',
    }
  }
  return {
    title: `${withObjectParticle(crewName)} 없앨까요?`,
    body: '없앤 크루는 되살릴 수 없어요. 남아 있던 초대 링크도 쓸 수 없게 돼요.',
    confirmLabel: '없애기',
  }
}

// 시안 M28 「로딩」: 띠 두 개에 회색 줄로 자리만 잡는다
function ManageSkeleton() {
  return (
    <CrewBand>
      <span role="status" className="sr-only">
        크루 관리를 불러오는 중
      </span>
      <Skeleton className="w-2/3" />
      <Skeleton className="w-full" />
      <Skeleton className="w-1/2" />
    </CrewBand>
  )
}
