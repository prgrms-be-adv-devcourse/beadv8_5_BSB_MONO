'use client'

import { useQueryClient } from '@tanstack/react-query'
import type { Route } from 'next'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useEffect } from 'react'

import { AppHeader } from '@/widgets/app-header'
import { useAuthStatus } from '@/features/auth'
import { CrewHero, formatKstDateTime, formatRemaining } from '@/entities/crew'
import { ApiError, memberApi, unwrapData } from '@/shared/api'
import { Alert, BottomCta, Button, buttonClassName, EmptyState, Skeleton } from '@/shared/ui'

import { inviteFailureOf } from '../model/invite-failure'
import { CrewBand, CrewLoadError } from './CrewBand'

// 피그마 시안v4 · M10 초대 받기 (기본 · 링크 끝남).
// 초대 링크 정보 조회와 가입 모두 로그인이 필요하다(백엔드 결정 BE-028). 로그인하지 않았으면
// ProtectedScreenGuard가 로그인 화면으로 보내고, 로그인을 마치면 같은 초대 주소로 돌아온다.
// 이미 그 크루의 크루원이면 이 화면 없이 크루 홈으로 보낸다.
export function CrewInvitePage({ inviteToken }: { inviteToken: string }) {
  const router = useRouter()
  const queryClient = useQueryClient()
  const invite = memberApi.useFindCrewInvite(inviteToken, {
    query: {
      select: unwrapData,
      enabled: useAuthStatus() === 'authenticated',
      retry: (count, error) => !(error instanceof ApiError && error.status < 500) && count < 2,
    },
  })
  const join = memberApi.useJoinCrew()
  const crewId = invite.data?.crewId
  const alreadyMember = invite.data?.alreadyMember === true

  const goCrewHome = (id: number) => {
    void queryClient.invalidateQueries({ queryKey: memberApi.getFindMyCrewsQueryKey() })
    router.replace(`/crews/${id}` as Route)
  }

  useEffect(() => {
    if (alreadyMember && crewId !== undefined) router.replace(`/crews/${crewId}` as Route)
  }, [alreadyMember, crewId, router])

  const failure = inviteFailureOf(invite.error ?? join.error)
  const joinIfPossible = () => {
    if (crewId === undefined) return
    join.mutate(
      { inviteToken },
      {
        onSuccess: (response) => goCrewHome(response.data?.crewId ?? crewId),
        onError: (error) => {
          // 그사이 다른 기기에서 가입했으면(409) 크루 홈으로 보낸다
          if (error instanceof ApiError && error.code === 'CREW_ALREADY_MEMBER') goCrewHome(crewId)
        },
      },
    )
  }

  if (failure) {
    return (
      <>
        <AppHeader type="sub" title="크루 초대" />
        <main className="flex flex-1 flex-col">
          <EmptyState
            className="pt-26"
            title={failure.title}
            description={failure.description}
            action={
              <Link href="/" className={buttonClassName({ variant: 'secondary', size: 'm' })}>
                홈으로 가기
              </Link>
            }
          />
        </main>
      </>
    )
  }

  const data = invite.data
  const remaining = data ? formatRemaining(data.expiresAt) : null
  return (
    <>
      <AppHeader type="sub" title="크루 초대" />
      <main className="flex flex-1 flex-col">
        {invite.isPending || alreadyMember ? (
          <InviteSkeleton />
        ) : !data ? (
          <CrewLoadError onRetry={() => invite.refetch()} />
        ) : (
          <>
            <CrewHero name={data.crewName} summary={`크루원 ${data.memberCount}명`} />
            <CrewBand>
              <h2 className="text-heading-m">{data.crewName}에서 초대했어요</h2>
              <Alert
                tone="info"
                title={
                  remaining ? `이 초대 링크는 ${remaining} 뒤에 끝나요` : '이 초대 링크는 곧 끝나요'
                }
                description={`${formatKstDateTime(data.expiresAt)}까지 가입할 수 있어요.`}
              />
              <p className="text-body-s text-text-secondary">
                가입하면 크루원 목록에 내 닉네임이 보여요. 실명은 보이지 않아요.
              </p>
              {join.isError &&
                !(join.error instanceof ApiError && join.error.code === 'CREW_ALREADY_MEMBER') && (
                  <p role="alert" className="text-body-s text-text-danger">
                    가입하지 못했어요. 잠시 후 다시 시도해 주세요.
                  </p>
                )}
            </CrewBand>
          </>
        )}
      </main>
      {data && !alreadyMember && (
        <BottomCta
          className="sticky bottom-0 z-10"
          primary={
            <Button size="l" loading={join.isPending} onClick={joinIfPossible}>
              {data.crewName}에 가입하기
            </Button>
          }
        />
      )}
    </>
  )
}

// 시안에 없는 상태: 초대 링크 정보를 불러오는 중
function InviteSkeleton() {
  return (
    <>
      <section className="flex items-center gap-3 bg-bg-hero px-4 py-6">
        <span role="status" className="sr-only">
          초대 링크를 확인하는 중
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
