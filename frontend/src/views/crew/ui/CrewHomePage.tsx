'use client'

import { useQueryClient } from '@tanstack/react-query'
import type { Route } from 'next'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useState } from 'react'

import { AppHeader } from '@/widgets/app-header'
import { useAuthStatus } from '@/features/auth'
import { canInvite, CREW_ROLE_LABEL } from '@/entities/crew'
import { useCrewActiveParties, type PartySummary } from '@/entities/party'
import { ApiError, memberApi, unwrapData, type memberModel } from '@/shared/api'
import {
  Button,
  buttonClassName,
  Dialog,
  Divider,
  EmptyState,
  Skeleton,
  useToast,
} from '@/shared/ui'

import { crewActionMessage } from '../model/crew-action-failure'
import { CrewBand, CrewHomeError, CrewIdentityBand, retryCrewQuery } from './CrewBand'
import { CrewMemberList } from './CrewMemberList'

// 피그마 시안v4 · M09 크루 홈 (크루장 · 크루원 · 크루원이 아님).
// 크루 정보와 크루원은 크루 홈 API(GET /api/v1/member/crews/{crewId})로 불러온다. 크루원이 아니면 403 CREW_NOT_MEMBER다.
// 크루 탈퇴(2그룹)는 시안에 자리가 없어 크루원 목록 아래 띠에 둔다.
export function CrewHomePage({ crewId }: { crewId: number }) {
  const crew = memberApi.useFindCrew(crewId, {
    query: {
      select: unwrapData,
      enabled: useAuthStatus() === 'authenticated',
      retry: retryCrewQuery,
    },
  })

  const parties = useCrewActiveParties(crewId)

  return (
    <>
      <AppHeader type="sub" title="크루 홈" backHref="/crews" />
      <main className="flex flex-1 flex-col">
        {crew.isPending ? (
          <CrewHomeSkeleton />
        ) : crew.isError ? (
          <CrewHomeError error={crew.error} onRetry={() => crew.refetch()} />
        ) : (
          <>
            <CrewIdentityBand
              name={crew.data.name}
              summary={`나는 ${CREW_ROLE_LABEL[crew.data.myRole]}`}
              action={
                canInvite(crew.data.myRole) && (
                  <Link
                    href={`/crews/${crewId}/invites` as Route}
                    className={buttonClassName({ variant: 'secondary', size: 's' })}
                  >
                    초대 링크 만들기
                  </Link>
                )
              }
            />
            <CrewBand>
              <div className="flex items-center justify-between gap-2">
                <h2 className="text-title3">진행 중인 파티</h2>
                <Link
                  href={`/parties/new?crewId=${crewId}` as Route}
                  className={buttonClassName({ variant: 'secondary', size: 's' })}
                >
                  파티 만들기
                </Link>
              </div>
              {parties.isPending ? (
                <Skeleton shape="rect" className="h-20 w-full" />
              ) : parties.isError ? (
                <CrewHomeError error={parties.error} onRetry={() => parties.refetch()} />
              ) : parties.data.length === 0 ? (
                <EmptyState
                  title="진행 중인 파티가 없어요"
                  description="대회 상세에서 「크루로 신청」을 누르면 파티를 만들 수 있어요."
                  action={
                    <Link
                      href="/races"
                      className={buttonClassName({ variant: 'secondary', size: 'm' })}
                    >
                      대회 둘러보기
                    </Link>
                  }
                />
              ) : (
                parties.data.map((party) => <PartyCard key={party.id} party={party} />)
              )}
            </CrewBand>
            <Divider type="section" />
            <CrewBand>
              <div className="flex items-center justify-between gap-2">
                <h2 className="text-title3">크루원 {crew.data.memberCount}명</h2>
                {crew.data.myRole === 'LEADER' && (
                  <Link
                    href={`/crews/${crewId}/manage` as Route}
                    className={buttonClassName({ variant: 'ghost', size: 's' })}
                  >
                    크루 관리
                  </Link>
                )}
              </div>
              <CrewMemberList members={crew.data.members} />
            </CrewBand>
            <Divider type="section" />
            <LeaveCrew crew={crew.data} onStale={() => void crew.refetch()} />
          </>
        )}
      </main>
    </>
  )
}

const PARTY_STATUS_LABEL: Record<PartySummary['status'], string> = {
  RECRUITING: '모집 중',
  PAYING: '결제 진행 중',
  PAID: '결제 끝',
  CLOSED: '끝난 파티',
}

function PartyCard({ party }: { party: PartySummary }) {
  const body = (
    <>
      <span className="text-body1-medium">{party.leaderNickname}님의 파티</span>
      <span className="text-caption-regular text-text-secondary">
        {PARTY_STATUS_LABEL[party.status]} · {party.memberCount}명 중 {party.paidCount}명 결제
      </span>
    </>
  )
  const className = 'flex flex-col gap-1 rounded-md bg-bg-surface p-4'
  // 파티원이 아니면 상세가 403이라 링크로 만들지 않는다
  if (!party.myParty) return <div className={className}>{body}</div>
  return (
    <Link href={`/parties/${party.id}` as Route} className={className}>
      {body}
    </Link>
  )
}

// 크루장은 바로 나갈 수 없다(정책 7장 2그룹 규칙: 넘긴 뒤에만 탈퇴). 그래서 크루장이 누르면 요청 없이 안내만 하고,
// 크루원으로 보이던 오래된 화면에서 그사이 크루장을 넘겨받았으면 서버의 409 CREW_LEADER_CANNOT_LEAVE로 같은 안내를 띄운다.
function LeaveCrew({ crew, onStale }: { crew: memberModel.CrewHomeDto; onStale: () => void }) {
  const router = useRouter()
  const queryClient = useQueryClient()
  const showToast = useToast()
  const leave = memberApi.useLeaveCrew()
  const [step, setStep] = useState<'confirm' | 'leader' | null>(null)

  const confirmLeave = () =>
    leave.mutate(
      { crewId: crew.crewId },
      {
        onSuccess: () => {
          showToast({ tone: 'success', message: `${crew.name}에서 나왔어요` })
          void queryClient.invalidateQueries({ queryKey: memberApi.getFindMyCrewsQueryKey() })
          router.replace('/crews')
          // 뒤로 가기로 돌아와도 나간 크루가 캐시에서 보이지 않게 지운다
          queryClient.removeQueries({ queryKey: memberApi.getFindCrewQueryKey(crew.crewId) })
        },
        onError: (error) => {
          onStale()
          if (error instanceof ApiError && error.code === 'CREW_LEADER_CANNOT_LEAVE') {
            setStep('leader')
            return
          }
          setStep(null)
          showToast({ tone: 'error', message: crewActionMessage(error) })
        },
      },
    )

  return (
    <CrewBand>
      <Button
        variant="ghost"
        size="m"
        className="self-start"
        onClick={() => setStep(crew.myRole === 'LEADER' ? 'leader' : 'confirm')}
      >
        크루 떠나기
      </Button>
      <Dialog
        open={step === 'confirm'}
        onOpenChange={(open) => !open && !leave.isPending && setStep(null)}
        type="danger"
        title={`${crew.name}에서 떠날까요?`}
        body="떠나면 크루 홈과 크루의 파티를 볼 수 없어요. 다시 들어오려면 새 초대 링크가 필요해요."
        confirmLabel="떠나기"
        confirming={leave.isPending}
        onConfirm={confirmLeave}
      />
      <Dialog
        open={step === 'leader'}
        onOpenChange={(open) => !open && setStep(null)}
        title="크루장을 넘긴 뒤에 나갈 수 있어요"
        body={
          crew.memberCount <= 1
            ? '크루원이 나뿐이라 크루 관리에서 크루를 없앨 수 있어요.'
            : '크루 관리에서 다른 크루원에게 크루장을 넘겨 주세요.'
        }
        cancelLabel="닫기"
        confirmLabel="크루 관리로 가기"
        onConfirm={() => router.push(`/crews/${crew.crewId}/manage` as Route)}
      />
    </CrewBand>
  )
}

// 시안에 없는 상태: 크루 홈 로딩. 내 크루 목록(M09-0) 로딩처럼 회색 줄로 자리만 잡는다.
function CrewHomeSkeleton() {
  return (
    <>
      <CrewBand>
        <span role="status" className="sr-only">
          크루를 불러오는 중
        </span>
        <div className="flex items-center gap-3">
          <Skeleton shape="circle" className="size-14" />
          <Skeleton className="flex-1" />
        </div>
      </CrewBand>
      <CrewBand>
        <Skeleton className="w-1/3" />
        <Skeleton shape="rect" className="h-34 w-full" />
      </CrewBand>
      <Divider type="section" />
      <CrewBand>
        <Skeleton className="w-1/4" />
        {[0, 1, 2, 3].map((i) => (
          <div key={i} className="flex items-center gap-3">
            <Skeleton shape="circle" className="size-8" />
            <Skeleton className="flex-1" />
          </div>
        ))}
      </CrewBand>
    </>
  )
}
