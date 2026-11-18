'use client'

import type { Route } from 'next'
import Link from 'next/link'

import { AppHeader } from '@/widgets/app-header'
import { BottomNav } from '@/widgets/bottom-nav'
import { useAuthStatus } from '@/features/auth'
import { CREW_ROLE_LABEL } from '@/entities/crew'
import { memberApi, unwrapData } from '@/shared/api'
import { cn } from '@/shared/lib'
import { Avatar, buttonClassName, EmptyState, FOCUS_RING, Icon, Skeleton } from '@/shared/ui'

import { CrewBand, CrewLoadError } from './CrewBand'

// 피그마 v3 · 내 크루 목록 (기본 · 없음 · 로딩). 아래 주요 메뉴의 「크루」 탭이다.
// 크루는 내 크루 목록(GET /api/v1/member/crews/me)으로 불러온다.
export function MyCrewsPage() {
  const crews = memberApi.useFindMyCrews({
    query: { select: unwrapData, enabled: useAuthStatus() === 'authenticated' },
  })
  const list = crews.data ?? []

  return (
    <>
      <AppHeader search={false} />
      <main className="flex flex-1 flex-col">
        {crews.isPending ? (
          <MyCrewsSkeleton />
        ) : crews.isError ? (
          <CrewLoadError onRetry={() => crews.refetch()} />
        ) : list.length === 0 ? (
          <EmptyState
            className="pt-26"
            title="아직 가입한 크루가 없어요"
            description="크루를 만들거나, 크루장에게 받은 초대 링크를 열어 가입해 보세요."
            action={
              <Link
                href="/crews/new"
                className={buttonClassName({ variant: 'secondary', size: 'm' })}
              >
                크루 만들기
              </Link>
            }
          />
        ) : (
          <CrewBand className="gap-2 px-5 pt-4 pb-0">
            <div className="flex items-center justify-between">
              <h2 className="text-body1-bold">가입한 크루 {list.length}</h2>
              <Link href="/crews/new" className="text-body2-medium text-text-brand">
                + 새 크루 만들기
              </Link>
            </div>
            <ul className="flex flex-col">
              {list.map((crew) => (
                <li key={crew.crewId}>
                  <Link
                    href={`/crews/${crew.crewId}` as Route}
                    className={cn('flex items-center gap-3 rounded-md py-3', FOCUS_RING)}
                  >
                    <Avatar size="xl" name={crew.name} className="size-12" />
                    <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                      <span className="truncate text-body1-bold">{crew.name}</span>
                      <span className="text-body2-regular text-text-secondary">
                        크루원 {crew.memberCount}명 · {CREW_ROLE_LABEL[crew.myRole]}
                      </span>
                    </span>
                    <Icon name="ChevronRight" className="size-5 text-text-secondary" />
                  </Link>
                </li>
              ))}
            </ul>
          </CrewBand>
        )}
      </main>
      <BottomNav />
    </>
  )
}

function MyCrewsSkeleton() {
  return (
    <CrewBand>
      <span role="status" className="sr-only">
        내 크루를 불러오는 중
      </span>
      <Skeleton className="w-full" />
      {[0, 1].map((i) => (
        <div key={i} className="flex items-center gap-3">
          <Skeleton shape="circle" className="size-10" />
          <Skeleton className="flex-1" />
        </div>
      ))}
    </CrewBand>
  )
}
