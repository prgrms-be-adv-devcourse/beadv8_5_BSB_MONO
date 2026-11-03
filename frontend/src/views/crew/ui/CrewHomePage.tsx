'use client'

import { useQueryClient } from '@tanstack/react-query'
import type { Route } from 'next'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useState } from 'react'

import { AppHeader } from '@/widgets/app-header'
import { useAuthStatus } from '@/features/auth'
import { canInvite, CREW_ROLE_LABEL, CrewHero } from '@/entities/crew'
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
import { CrewBand, CrewHomeError, retryCrewQuery } from './CrewBand'
import { CrewMemberList } from './CrewMemberList'

// 피그마 시안v4 · M09 크루 홈 (크루장 · 크루원 · 크루원이 아님).
// 크루 정보와 크루원은 크루 홈 API(GET /api/v1/member/crews/{crewId})로 불러온다. 크루원이 아니면 403 CREW_NOT_MEMBER다.
// 크루 탈퇴(2그룹)는 시안에 자리가 없어 크루원 목록 아래 띠에 둔다.
// TODO(api): 파티 API 계약이 아직 없어 진행 중인 파티는 늘 「없음」으로 보인다.
export function CrewHomePage({ crewId }: { crewId: number }) {
  const crew = memberApi.useFindCrew(crewId, {
    query: {
      select: unwrapData,
      enabled: useAuthStatus() === 'authenticated',
      retry: retryCrewQuery,
    },
  })

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
            <CrewHero
              name={crew.data.name}
              summary={`크루원 ${crew.data.memberCount}명 · 나는 ${CREW_ROLE_LABEL[crew.data.myRole]}`}
              people={crew.data.members.map((member) => ({ name: member.nickname }))}
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
              <h2 className="text-heading-m">진행 중인 파티</h2>
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
            </CrewBand>
            <Divider type="section" />
            <CrewBand>
              <div className="flex items-center justify-between gap-2">
                <h2 className="text-heading-m">크루원 {crew.data.memberCount}명</h2>
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
        크루 나가기
      </Button>
      <Dialog
        open={step === 'confirm'}
        onOpenChange={(open) => !open && !leave.isPending && setStep(null)}
        type="danger"
        title={`${crew.name}에서 나갈까요?`}
        body="나가도 이미 결제한 참가권은 그대로예요. 결제하지 않고 잡아 둔 자리만 풀려요."
        confirmLabel="나가기"
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
      <section className="flex items-center gap-3 bg-bg-hero px-4 py-6">
        <span role="status" className="sr-only">
          크루를 불러오는 중
        </span>
        <Skeleton shape="circle" className="size-14" />
        <Skeleton className="flex-1" />
      </section>
      <CrewBand>
        <Skeleton className="w-full" />
        <Skeleton className="w-2/3" />
      </CrewBand>
    </>
  )
}
