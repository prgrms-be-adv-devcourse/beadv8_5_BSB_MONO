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
import { ApiError, memberApi, productApi, unwrapData, type memberModel } from '@/shared/api'
import { cn, useSearchParamsState } from '@/shared/lib'
import {
  Avatar,
  Button,
  buttonClassName,
  Dialog,
  Divider,
  EmptyState,
  FOCUS_RING,
  Icon,
  Skeleton,
  Tab,
  TabBar,
  TabPanel,
  Tabs,
  useToast,
} from '@/shared/ui'

import { crewActionMessage } from '../model/crew-action-failure'
import { CrewBand, CrewHomeError, retryCrewQuery } from './CrewBand'
import { CrewMemberList } from './CrewMemberList'

// 피그마 Main Design · M09 크루 홈 v3 (11/16 프로필 + 탭 C안).
// 크루 정보와 크루원은 크루 홈 API(GET /api/v1/member/crews/{crewId})로 불러온다. 크루원이 아니면 403 CREW_NOT_MEMBER다.
// 크루 떠나기는 크루원 탭 맨 아래.
export function CrewHomePage({ crewId }: { crewId: number }) {
  const crew = memberApi.useFindCrew(crewId, {
    query: {
      select: unwrapData,
      enabled: useAuthStatus() === 'authenticated',
      retry: retryCrewQuery,
    },
  })

  const parties = useCrewActiveParties(crewId)
  // 고른 탭은 주소(?tab=)에 둔다. 크루 관리 · 크루원 목록에서 뒤로 와도 보던 탭이 열린다
  const [url, setUrl] = useSearchParamsState({ tab: 'parties' }, { tab: ['parties', 'members'] })

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
            <CrewBand className="gap-4">
              <div className="flex flex-col items-center gap-2 text-center">
                <Avatar size="xl" name={crew.data.name} />
                <h2 className="w-full truncate text-title1">{crew.data.name}</h2>
                <p className="text-body2-regular text-text-secondary">
                  나는 {CREW_ROLE_LABEL[crew.data.myRole]} · 크루원 {crew.data.memberCount}명
                </p>
              </div>
              <div className="flex gap-2">
                <Link
                  href={`/parties/new?crewId=${crewId}` as Route}
                  className={buttonClassName({
                    variant: 'primary',
                    size: 'm',
                    className: 'flex-1',
                  })}
                >
                  파티 만들기
                </Link>
                {canInvite(crew.data.myRole) && (
                  <Link
                    href={`/crews/${crewId}/invites` as Route}
                    className={buttonClassName({
                      variant: 'secondary',
                      size: 'm',
                      className: 'flex-1',
                    })}
                  >
                    초대 링크 만들기
                  </Link>
                )}
              </div>
            </CrewBand>
            <Tabs value={url.tab} onValueChange={(tab) => setUrl({ tab: String(tab) })}>
              <TabBar>
                <Tab value="parties">
                  파티{parties.data?.length ? ` ${parties.data.length}` : ''}
                </Tab>
                <Tab value="members">크루원 {crew.data.memberCount}</Tab>
              </TabBar>
              <TabPanel value="parties">
                <CrewBand>
                  {parties.isPending ? (
                    <Skeleton shape="rect" className="h-20 w-full" />
                  ) : parties.isError ? (
                    <CrewHomeError error={parties.error} onRetry={() => parties.refetch()} />
                  ) : parties.data.length === 0 ? (
                    <EmptyState title="진행 중인 파티가 없어요" />
                  ) : (
                    parties.data.map((party) => <PartyCard key={party.id} party={party} />)
                  )}
                </CrewBand>
              </TabPanel>
              <TabPanel value="members">
                <CrewBand>
                  {crew.data.myRole === 'LEADER' && (
                    <Link
                      href={`/crews/${crewId}/manage` as Route}
                      className={buttonClassName({
                        variant: 'ghost',
                        size: 's',
                        className: 'self-end',
                      })}
                    >
                      크루 관리
                    </Link>
                  )}
                  <CrewMemberList
                    members={crew.data.members}
                    moreHref={`/crews/${crewId}/members` as Route}
                  />
                </CrewBand>
                {crew.data.myRole !== 'LEADER' && (
                  <>
                    <Divider type="section" />
                    <LeaveCrew
                      crew={crew.data}
                      joinedParty={parties.data?.find((party) => party.myParty)}
                      onStale={() => void crew.refetch()}
                    />
                  </>
                )}
              </TabPanel>
            </Tabs>
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

// 시안 파티 19 · 크루 홈 진행 중인 파티: 모집 상태 칩, 대회 이름, 파티장 · 인원. 내 파티만 「파티 보기」로 이어진다.
function PartyCard({ party }: { party: PartySummary }) {
  const race = productApi.useFindRace(party.raceId, { query: { select: unwrapData } })
  const max = race.data?.crewMaxCount
  const body = (
    <>
      <div className="flex min-h-6 items-center justify-between gap-2">
        <span className="text-body2-medium text-text-brand">
          {PARTY_STATUS_LABEL[party.status]}
        </span>
        {party.myParty ? (
          <Icon name="ChevronRight" className="size-5 shrink-0 text-text-tertiary" />
        ) : (
          <span className="text-caption-regular text-text-secondary">내가 없는 파티</span>
        )}
      </div>
      <h3 className="text-body1-medium">{race.data?.title ?? <Skeleton className="w-2/3" />}</h3>
      <p className="text-body2-regular text-text-secondary">
        파티장 {party.leaderNickname} · {party.memberCount}명{max ? ` / 최대 ${max}명` : ''}
      </p>
    </>
  )
  const card = 'flex flex-col gap-2 rounded-md border border-border-default bg-bg-surface p-4'
  return party.myParty ? (
    <Link href={`/parties/${party.id}` as Route} className={cn(card, FOCUS_RING)}>
      {body}
    </Link>
  ) : (
    <div className={card}>{body}</div>
  )
}

// 크루장은 바로 나갈 수 없다(정책 7장 2그룹 규칙: 넘긴 뒤에만 탈퇴). 그래서 크루장이 누르면 요청 없이 안내만 하고,
// 크루원으로 보이던 오래된 화면에서 그사이 크루장을 넘겨받았으면 서버의 409 CREW_LEADER_CANNOT_LEAVE로 같은 안내를 띄운다.
function LeaveCrew({
  crew,
  joinedParty,
  onStale,
}: {
  crew: memberModel.CrewHomeDto
  joinedParty?: PartySummary
  onStale: () => void
}) {
  const router = useRouter()
  const queryClient = useQueryClient()
  const showToast = useToast()
  const leave = memberApi.useLeaveCrew()
  const [step, setStep] = useState<'confirm' | 'leader' | 'party' | null>(null)
  const joinedRace = productApi.useFindRace(joinedParty?.raceId ?? 0, {
    query: { enabled: joinedParty !== undefined, select: unwrapData },
  })

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
        onClick={() =>
          // 시안 11/5: 진행 중인 파티에 들어 있으면 요청 없이 막힘 안내만 띄운다
          setStep(joinedParty ? 'party' : crew.myRole === 'LEADER' ? 'leader' : 'confirm')
        }
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
        open={step === 'party'}
        onOpenChange={(open) => !open && setStep(null)}
        type="alert"
        title="진행 중인 파티가 끝난 뒤 떠날 수 있어요"
        body={`지금 ${joinedRace.data?.title ?? '파티'} 파티에 들어 있어요. 이 파티가 끝나면 크루를 떠날 수 있어요.`}
        confirmLabel="확인"
        onConfirm={() => setStep(null)}
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

// 시안에 없는 상태: 크루 홈 로딩. 프로필 · 버튼 줄 · 파티 카드 자리만 회색으로 잡는다.
function CrewHomeSkeleton() {
  return (
    <>
      <CrewBand className="items-center gap-4">
        <span role="status" className="sr-only">
          크루를 불러오는 중
        </span>
        <Skeleton shape="circle" className="size-14" />
        <Skeleton className="w-1/3" />
        <Skeleton className="w-1/2" />
        <Skeleton shape="rect" className="h-11 w-full" />
      </CrewBand>
      <CrewBand>
        <Skeleton shape="rect" className="h-34 w-full" />
        <Skeleton shape="rect" className="h-34 w-full" />
      </CrewBand>
    </>
  )
}
