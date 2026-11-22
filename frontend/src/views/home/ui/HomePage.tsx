import type { Route } from 'next'
import Image from 'next/image'
import Link from 'next/link'
import type { ReactNode } from 'react'

import { AppHeader } from '@/widgets/app-header'
import { BottomNav } from '@/widgets/bottom-nav'
import { OfflineBody } from '@/widgets/status-screens'
import { RaceCard, type HomeBanner, type RaceSummary } from '@/entities/race'
import { clientEnv } from '@/shared/config'
import { cn } from '@/shared/lib'
import { Carousel, CarouselSlide, FOCUS_RING, Icon, Skeleton } from '@/shared/ui'

import { NextRaceCard } from './NextRaceCard'
import { RunnerStories } from './RunnerStories'

export type HomePageProps = {
  state?: 'loading' | 'offline'
  /** 곧 마감 (대회별 카드, 4개) */
  closingSoon: RaceSummary[]
  /** 새로 등록된 대회 (최신 등록순, 곧 마감과 겹치는 대회는 뺌, 최대 4개). 비면 영역을 그리지 않는다 */
  newRaces?: RaceSummary[]
  /** 메인 배너 (관리자가 고른 대회, 최대 5개). 비면 배너 영역을 그리지 않는다 */
  banners?: HomeBanner[]
}

// 피그마 V3 · 홈(11/17 확정): 로고·알림 → 메인 배너 → 다음 대회(로그인 + 다가오는 대회) → 곧 마감돼요(2열 4개) → 새로 등록된 대회(2열 4개, 없으면 숨김) → 러너 이야기
// (+ 홈 로딩, 공통 연결 끊김). 배너는 관리자가 고른 대회(v4 · 홈 배너 · 실제 대회, 2253:19903).
export function HomePage({ state, closingSoon, newRaces = [], banners = [] }: HomePageProps) {
  return (
    <>
      {state ? (
        // 시안 01 홈 로딩 · 01-A 연결 끊김은 홈 탭 없이 기본 헤더를 쓴다
        <>
          <AppHeader />
          {state === 'loading' ? <HomeSkeleton /> : <OfflineBody />}
        </>
      ) : (
        <>
          <AppHeader search={false} loginWhenGuest className="border-b-0" />
          <main className="flex-1">
            {banners.length > 0 && (
              <Carousel label="접수 중인 대회" showDots={false} className="pt-1 pb-2">
                {banners.map((banner, i) => (
                  <Link
                    key={banner.id}
                    href={`/races/${banner.id}`}
                    className={cn('block rounded-lg', FOCUS_RING)}
                  >
                    <CarouselSlide
                      dim={Boolean(banner.image)}
                      image={
                        banner.image ? (
                          <Image
                            src={banner.image}
                            alt=""
                            fill
                            sizes="568px"
                            // 첫 장이 첫 화면에서 가장 큰 요소(LCP)라 먼저 받는다. 나머지는 지연 로딩
                            loading={i === 0 ? 'eager' : undefined}
                            fetchPriority={i === 0 ? 'high' : undefined}
                            className="object-cover"
                          />
                        ) : (
                          // 대표 이미지가 없으면 대회 카드처럼 색 포스터에 종목을 크게 쓴다
                          <div
                            aria-hidden
                            className="absolute inset-0 bg-bg-hero p-6 text-title1 text-text-on-hero"
                          >
                            {banner.posterLabel}
                          </div>
                        )
                      }
                      chip={banner.chip}
                      title={banner.title}
                      description={banner.description}
                    />
                  </Link>
                ))}
              </Carousel>
            )}

            <NextRaceCard />

            <RaceSection id="closing-soon" title="곧 마감돼요" href="/races" races={closingSoon}>
              <EmptyRow>곧 마감되는 대회가 없어요</EmptyRow>
            </RaceSection>

            {newRaces.length > 0 && (
              <RaceSection
                id="new-races"
                title="새로 등록된 대회"
                href="/races?sort=LATEST"
                races={newRaces}
              />
            )}

            {clientEnv.NEXT_PUBLIC_API_MOCKING === 'on' && <RunnerStories />}
          </main>
        </>
      )}
      <BottomNav />
    </>
  )
}

// 제목 줄 전체가 목록 링크인 2열 카드 영역. 카드가 없을 때 보일 글은 children
function RaceSection({
  id,
  title,
  href,
  races,
  children,
}: {
  id: string
  title: string
  href: Route
  races: RaceSummary[]
  children?: ReactNode
}) {
  return (
    <section aria-labelledby={id} className="flex flex-col gap-4 px-4 py-6">
      <h2 id={id} className="text-title3">
        <Link href={href} className={cn('flex items-center gap-1 rounded-sm', FOCUS_RING)}>
          {title}
          {races.length > 0 && <span className="text-text-tertiary">{races.length}</span>}
          <Icon name="ChevronRight" className="size-5 text-text-tertiary" />
        </Link>
      </h2>
      {races.length === 0 ? (
        children
      ) : (
        <div className="grid grid-cols-2 gap-x-4 gap-y-6">
          {races.map((race) => (
            <RaceCard key={race.id} race={race} />
          ))}
        </div>
      )}
    </section>
  )
}

function EmptyRow({ children }: { children: string }) {
  return <p className="py-6 text-center text-body2-regular text-text-tertiary">{children}</p>
}

// 홈 로딩(피그마 V4 · 홈 · 로딩): 홈과 같은 배치(배너 · 다음 대회 · 곧 마감 2열 4장 · 러너 이야기 글 3줄)의 회색 틀.
// 0.3초 안에 끝나면 보이지 않는다(animate-delayed-show).
function HomeSkeleton() {
  return (
    <main
      role="status"
      aria-label="불러오는 중"
      className="flex flex-1 animate-delayed-show flex-col"
    >
      <div className="px-4 pt-1 pb-2">
        <Skeleton shape="rect" className="aspect-568/300 w-full rounded-lg" />
      </div>
      <div className="px-4 pt-4">
        <div className="flex items-center gap-4 rounded-lg border border-border-default bg-bg-surface p-4 shadow-sm">
          <Skeleton shape="rect" className="size-16 shrink-0 rounded-md" />
          <div className="flex flex-1 flex-col gap-1">
            <Skeleton className="h-3 w-60 max-w-full" />
            <Skeleton className="h-4 w-60 max-w-full" />
            <Skeleton className="h-3 w-60 max-w-full" />
          </div>
        </div>
      </div>
      <div className="flex flex-col gap-4 px-4 py-6">
        <SkeletonHead />
        <div className="grid grid-cols-2 gap-x-4 gap-y-6">
          {[0, 1, 2, 3].map((i) => (
            <SkeletonCard key={i} />
          ))}
        </div>
      </div>
      <div className="flex flex-col gap-4 px-4 pt-2 pb-6">
        <Skeleton className="h-4 w-24" />
        <div>
          {[0, 1, 2].map((i) => (
            <div key={i} className="flex gap-4 border-b border-border-default py-4 last:border-b-0">
              <div className="flex min-w-0 flex-1 flex-col gap-1">
                <Skeleton className="h-3 w-12" />
                <Skeleton className="h-4 w-60 max-w-full" />
                <Skeleton className="h-3 w-60 max-w-full" />
                <Skeleton className="mt-1 h-3 w-40" />
              </div>
              {i < 2 && <Skeleton shape="rect" className="size-18 shrink-0 rounded-sm" />}
            </div>
          ))}
        </div>
      </div>
    </main>
  )
}

function SkeletonHead() {
  return (
    <div className="flex items-center gap-1">
      <Skeleton className="h-4 w-24" />
      <Skeleton className="h-4 w-10" />
    </div>
  )
}

function SkeletonCard() {
  return (
    <div className="flex flex-col gap-8">
      <Skeleton shape="rect" className="aspect-4/3 w-full" />
      <div className="flex flex-col gap-2">
        <Skeleton className="h-4 w-48 max-w-full" />
        <Skeleton className="h-3 w-35 max-w-full" />
        <Skeleton className="h-3 w-24 max-w-full" />
      </div>
    </div>
  )
}
