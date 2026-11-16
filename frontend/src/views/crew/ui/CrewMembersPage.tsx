'use client'

import type { Route } from 'next'

import { AppHeader } from '@/widgets/app-header'
import { useAuthStatus } from '@/features/auth'
import { memberApi, unwrapData } from '@/shared/api'
import { Divider, Skeleton } from '@/shared/ui'

import { CrewBand, CrewHomeError, retryCrewQuery } from './CrewBand'
import { CrewMemberList } from './CrewMemberList'

// 피그마 크루원 목록(1333:27062): 「나」 구역과 「크루원 N명」 구역. 크루 홈의 「크루원 N명 더 보기」가 이 화면으로 온다.
// 줄을 눌러 갈 곳(크루원 상세)이 없어 오른쪽 화살표는 그리지 않는다. 가입 연월은 응답에 없어 넣지 않는다.
export function CrewMembersPage({ crewId }: { crewId: number }) {
  const authenticated = useAuthStatus() === 'authenticated'
  const crew = memberApi.useFindCrew(crewId, {
    query: { select: unwrapData, enabled: authenticated, retry: retryCrewQuery },
  })
  const me = memberApi.useFindMe({ query: { select: unwrapData, enabled: authenticated } })
  const mine = crew.data?.members.filter((member) => member.memberId === me.data?.id) ?? []
  const others = crew.data?.members.filter((member) => member.memberId !== me.data?.id) ?? []

  return (
    <>
      <AppHeader type="sub" title="크루원" backHref={`/crews/${crewId}` as Route} />
      <main className="flex flex-1 flex-col">
        {crew.isPending || me.isPending ? (
          <CrewBand>
            <span role="status" className="sr-only">
              크루원을 불러오는 중
            </span>
            <Skeleton className="w-1/2" />
            <Skeleton shape="rect" className="h-20 w-full" />
          </CrewBand>
        ) : crew.isError ? (
          <CrewHomeError error={crew.error} onRetry={() => crew.refetch()} />
        ) : me.isError ? (
          <CrewHomeError error={me.error} onRetry={() => me.refetch()} />
        ) : (
          <>
            <CrewBand className="pb-0">
              <h2 className="text-title3">
                {crew.data.name} 크루원 {crew.data.memberCount}명
              </h2>
            </CrewBand>
            {mine.length > 0 && (
              <CrewBand>
                <h3 className="text-caption-regular text-text-secondary">나</h3>
                <CrewMemberList members={mine} showAll />
              </CrewBand>
            )}
            <Divider type="section" />
            <CrewBand>
              <h3 className="text-caption-regular text-text-secondary">크루원 {others.length}명</h3>
              <CrewMemberList members={others} showAll />
            </CrewBand>
          </>
        )}
      </main>
    </>
  )
}
