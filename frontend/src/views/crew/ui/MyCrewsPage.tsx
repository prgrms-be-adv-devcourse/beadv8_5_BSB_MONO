'use client'

import type { Route } from 'next'
import Link from 'next/link'

import { AppHeader } from '@/widgets/app-header'
import { BottomNav } from '@/widgets/bottom-nav'
import { useAuthStatus } from '@/features/auth'
import { CREW_ROLE_LABEL } from '@/entities/crew'
import { memberApi, unwrapData } from '@/shared/api'
import { cn } from '@/shared/lib'
import {
  Avatar,
  Button,
  buttonClassName,
  EmptyState,
  FOCUS_RING,
  Icon,
  Skeleton,
} from '@/shared/ui'

import { CrewBand } from './CrewBand'

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
        ) : (
          <CrewBand className="gap-1 pt-4 pb-0">
            <div className="flex items-center justify-between">
              <h2 className="text-body1-bold">
                가입한 크루{crews.isError ? '' : ` ${list.length}`}
              </h2>
              <Link href="/crews/new" className={buttonClassName({ variant: 'ghost', size: 's' })}>
                새 크루 만들기
              </Link>
            </div>
            {crews.isError && (
              // 시안 2074:94702: 머리 줄은 그대로 두고 아래에 안내만 바꾼다
              <EmptyState
                icon={null}
                role="alert"
                className="pt-26"
                title="크루를 불러오지 못했어요"
                action={
                  <Button variant="secondary" size="m" onClick={() => void crews.refetch()}>
                    다시 불러오기
                  </Button>
                }
              />
            )}
            {!crews.isError && list.length === 0 && (
              <EmptyState
                icon={null}
                className="pt-26"
                title="아직 가입한 크루가 없어요"
                description="크루를 만들거나 초대 링크로 가입해요"
              />
            )}
            <ul className="flex flex-col">
              {list.map((crew) => (
                <li key={crew.crewId}>
                  <Link
                    href={`/crews/${crew.crewId}` as Route}
                    className={cn('flex items-center gap-3 rounded-md py-4', FOCUS_RING)}
                  >
                    <Avatar size="xl" name={crew.name} className="size-12" />
                    <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                      <span className="truncate text-body1-bold">{crew.name}</span>
                      <span className="text-caption-regular text-text-secondary">
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
    <CrewBand className="gap-1 pt-4 pb-0">
      <span role="status" className="sr-only">
        내 크루를 불러오는 중
      </span>
      <div className="flex items-center justify-between">
        <Skeleton className="w-20" />
        <Skeleton shape="rect" className="h-8 w-28" />
      </div>
      {[0, 1].map((i) => (
        <div key={i} className="flex items-center gap-3 py-4">
          <Skeleton shape="circle" className="size-12" />
          <span className="flex min-w-0 flex-1 flex-col gap-2">
            <Skeleton className="w-full" />
            <Skeleton className="w-2/3" />
          </span>
          <Skeleton shape="rect" className="size-5" />
        </div>
      ))}
    </CrewBand>
  )
}
