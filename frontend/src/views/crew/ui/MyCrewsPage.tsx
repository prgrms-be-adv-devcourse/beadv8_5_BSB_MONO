'use client'

import type { Route } from 'next'
import Link from 'next/link'

import { AppHeader } from '@/widgets/app-header'
import { useAuthStatus } from '@/features/auth'
import { CREW_ROLE_LABEL, CREW_ROLE_TONE } from '@/entities/crew'
import { memberApi, unwrapData } from '@/shared/api'
import { cn } from '@/shared/lib'
import {
  Avatar,
  Badge,
  BottomCta,
  buttonClassName,
  EmptyState,
  FOCUS_RING,
  Skeleton,
} from '@/shared/ui'

import { CrewBand, CrewLoadError } from './CrewBand'

// 피그마 시안v4 · M09-0 내 크루 목록 (기본 · 없음 · 로딩). 마이 화면의 「내 크루」 메뉴로 들어온다.
// 크루는 내 크루 목록(GET /api/v1/member/crews/me)으로 불러온다.
export function MyCrewsPage() {
  const crews = memberApi.useFindMyCrews({
    query: { select: unwrapData, enabled: useAuthStatus() === 'authenticated' },
  })
  const list = crews.data ?? []

  return (
    <>
      <AppHeader type="sub" title="내 크루" backHref="/me" />
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
          <CrewBand>
            <h2 className="text-heading-m">가입한 크루 {list.length}개</h2>
            <ul className="flex flex-col gap-3">
              {list.map((crew) => (
                <li key={crew.crewId}>
                  <Link
                    href={`/crews/${crew.crewId}` as Route}
                    className={cn('flex items-center gap-3 rounded-md', FOCUS_RING)}
                  >
                    <Avatar size="l" name={crew.name} />
                    <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                      <span className="truncate text-heading-s">{crew.name}</span>
                      <span className="text-caption text-text-secondary">
                        크루원 {crew.memberCount}명
                      </span>
                    </span>
                    <Badge tone={CREW_ROLE_TONE[crew.myRole]}>{CREW_ROLE_LABEL[crew.myRole]}</Badge>
                  </Link>
                </li>
              ))}
            </ul>
          </CrewBand>
        )}
      </main>
      {list.length > 0 && (
        <BottomCta
          className="sticky bottom-0 z-10"
          primary={
            <Link href="/crews/new" className={buttonClassName({ variant: 'primary', size: 'l' })}>
              새 크루 만들기
            </Link>
          }
        />
      )}
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
