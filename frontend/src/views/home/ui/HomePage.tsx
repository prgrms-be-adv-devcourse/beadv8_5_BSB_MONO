import Image from 'next/image'
import Link from 'next/link'

import { AppHeader } from '@/widgets/app-header'
import { BottomNav } from '@/widgets/bottom-nav'
import { OfflineBody } from '@/widgets/status-screens'
import { RACE_BANNERS, RaceCard, type RaceSummary } from '@/entities/race'
import { clientEnv } from '@/shared/config'
import { cn } from '@/shared/lib'
import { Carousel, CarouselSlide, FOCUS_RING, Icon, Skeleton } from '@/shared/ui'

import { NextRaceCard } from './NextRaceCard'
import { RunnerStories } from './RunnerStories'

export type HomePageProps = {
  state?: 'loading' | 'offline'
  /** 곧 마감 (대회별 카드, 4개) */
  closingSoon: RaceSummary[]
}

// 피그마 V3 · 홈(11/17 확정): 로고·알림 → 메인 배너 → 다음 대회(로그인 + 다가오는 대회) → 곧 마감돼요(2열 4개) → 러너 이야기
// (+ 홈 로딩, 공통 연결 끊김). 배너는 API가 없어 시안 예시를 그대로 쓴다.
export function HomePage({ state, closingSoon }: HomePageProps) {
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
            <Carousel label="접수 중인 대회" className="pt-1 pb-2">
              {RACE_BANNERS.map((banner, i) => (
                <CarouselSlide
                  key={banner.id}
                  image={
                    <Image
                      src={banner.image}
                      alt={banner.title ?? ''}
                      fill
                      sizes="568px"
                      // 첫 장이 첫 화면에서 가장 큰 요소(LCP)라 먼저 받는다. 나머지는 지연 로딩
                      loading={i === 0 ? 'eager' : undefined}
                      fetchPriority={i === 0 ? 'high' : undefined}
                      className="object-cover"
                    />
                  }
                  chip={banner.chip}
                  title={banner.title}
                  description={banner.description}
                />
              ))}
            </Carousel>

            <NextRaceCard />

            <section aria-labelledby="closing-soon" className="flex flex-col gap-3.5 px-4 py-7">
              <h2 id="closing-soon" className="text-title3">
                <Link
                  href="/races"
                  className={cn('flex items-center gap-1 rounded-sm', FOCUS_RING)}
                >
                  곧 마감돼요
                  {closingSoon.length > 0 && (
                    <span className="text-text-tertiary">{closingSoon.length}</span>
                  )}
                  <Icon name="ChevronRight" className="size-5 text-text-tertiary" />
                </Link>
              </h2>
              {closingSoon.length === 0 ? (
                <EmptyRow>곧 마감되는 대회가 없어요</EmptyRow>
              ) : (
                <div className="grid grid-cols-2 gap-x-4 gap-y-6">
                  {closingSoon.map((race) => (
                    <RaceCard key={race.id} race={race} />
                  ))}
                </div>
              )}
            </section>

            {clientEnv.NEXT_PUBLIC_API_MOCKING === 'on' && <RunnerStories />}
          </main>
        </>
      )}
      <BottomNav />
    </>
  )
}

function EmptyRow({ children }: { children: string }) {
  return <p className="py-6 text-center text-body2-regular text-text-tertiary">{children}</p>
}

// 홈 로딩: 02 홈과 같은 배치(배너 · 곧 마감 2열 4장)의 회색 틀.
// 0.3초 안에 끝나면 보이지 않는다(animate-delayed-show).
function HomeSkeleton() {
  return (
    <main
      role="status"
      aria-label="불러오는 중"
      className="flex flex-1 animate-delayed-show flex-col"
    >
      <div className="px-4 pt-4 pb-2">
        <Skeleton shape="rect" className="aspect-568/300 w-full rounded-lg" />
      </div>
      <div className="flex flex-col gap-3.5 px-4 py-7">
        <SkeletonHead />
        <div className="grid grid-cols-2 gap-x-4 gap-y-6">
          {[0, 1, 2, 3].map((i) => (
            <SkeletonCard key={i} />
          ))}
        </div>
      </div>
    </main>
  )
}

function SkeletonHead() {
  return (
    <div className="flex h-7 items-center justify-between">
      <Skeleton className="h-3.75 w-28" />
      <Skeleton className="h-2.5 w-12" />
    </div>
  )
}

function SkeletonCard() {
  return (
    <div className="flex flex-col gap-2.5">
      <Skeleton shape="rect" className="aspect-4/3 w-full" />
      <div className="flex flex-col gap-2">
        <Skeleton className="h-2.75 w-5/6" />
        <Skeleton className="h-2.5 w-3/5" />
        <Skeleton className="h-2.5 w-1/3" />
      </div>
    </div>
  )
}
