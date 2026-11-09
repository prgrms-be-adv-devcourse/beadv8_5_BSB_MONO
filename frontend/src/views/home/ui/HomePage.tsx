import Image from 'next/image'
import Link from 'next/link'

import { AppHeader } from '@/widgets/app-header'
import { BottomNav } from '@/widgets/bottom-nav'
import { OfflineBody } from '@/widgets/status-screens'
import { RACE_BANNERS, RaceCard, type RaceSummary } from '@/entities/race'
import { cn } from '@/shared/lib'
import {
  buttonClassName,
  Carousel,
  CarouselSlide,
  DragScroll,
  FOCUS_RING,
  Skeleton,
} from '@/shared/ui'

export type HomePageProps = {
  state?: 'loading' | 'offline'
  /** 곧 마감 (종목별 카드) */
  closingSoon: RaceSummary[]
  /** 지금 접수 중 (대회별 카드, 4개) */
  openRaces: RaceSummary[]
}

// 피그마 V2 · M01 홈: ① 배너 → ② 곧 마감(가로 넘김) → ③ 지금 접수 중(2열, 4개만)
// (+ 홈 로딩, 공통 연결 끊김). 배너는 API가 없어 시안 예시를 그대로 쓴다.
export function HomePage({ state, closingSoon, openRaces }: HomePageProps) {
  if (state) {
    return (
      <>
        <AppHeader />
        {state === 'loading' ? <HomeSkeleton /> : <OfflineBody />}
        <BottomNav />
      </>
    )
  }
  return (
    <>
      <AppHeader />
      <main className="flex-1">
        <section className="pt-4 pb-2">
          <Carousel label="접수 중인 대회">
            {RACE_BANNERS.map((banner, i) => (
              <CarouselSlide
                key={banner.id}
                image={
                  <Image
                    src={banner.image}
                    alt={banner.title ?? ''}
                    fill
                    sizes="420px"
                    // 첫 장이 첫 화면에서 가장 큰 요소(LCP)라 먼저 받는다. 나머지는 지연 로딩
                    loading={i === 0 ? 'eager' : undefined}
                    fetchPriority={i === 0 ? 'high' : undefined}
                    className="object-cover"
                  />
                }
                title={banner.title}
                tags={banner.tags}
              />
            ))}
          </Carousel>
        </section>

        <section className="flex flex-col gap-4 pt-8 pb-2">
          <SectionHead title="곧 마감돼요" />
          {closingSoon.length === 0 ? (
            <EmptyRow>곧 마감되는 대회가 없어요</EmptyRow>
          ) : (
            <DragScroll className="scroll-px-(--layout-page-margin) gap-4 px-(--layout-page-margin)">
              {closingSoon.map((race) => (
                <RaceCard
                  key={`${race.id}-${race.option}`}
                  race={race}
                  className="w-54 shrink-0 snap-start"
                />
              ))}
            </DragScroll>
          )}
        </section>

        <section className="flex flex-col gap-4 py-8">
          <SectionHead title="지금 접수 중인 대회" />
          {openRaces.length === 0 ? (
            <EmptyRow>지금 접수 중인 대회가 없어요</EmptyRow>
          ) : (
            <div className="grid grid-cols-2 gap-x-4 gap-y-6 px-(--layout-page-margin)">
              {openRaces.map((race) => (
                <RaceCard key={race.id} race={race} />
              ))}
            </div>
          )}
          <div className="px-(--layout-page-margin)">
            <Link
              href="/races"
              className={buttonClassName({ variant: 'secondary', className: 'w-full' })}
            >
              대회 전체 보기
            </Link>
          </div>
        </section>
      </main>
      <BottomNav />
    </>
  )
}

function EmptyRow({ children }: { children: string }) {
  return (
    <p className="px-(--layout-page-margin) py-6 text-center text-body2-regular text-text-tertiary">
      {children}
    </p>
  )
}

function SectionHead({ title }: { title: string }) {
  return (
    <div className="flex items-end justify-between px-(--layout-page-margin)">
      <h2 className="text-title2">{title}</h2>
      <Link
        href="/races"
        className={cn('rounded-sm text-body2-medium text-text-secondary', FOCUS_RING)}
      >
        전체 보기
      </Link>
    </div>
  )
}

// 피그마 시나리오 38 · 01 홈 로딩: 배너 · 곧 마감(가로 카드) · 지금 접수 중(2열 4장 + 더 보기) 자리.
// 0.3초 안에 끝나면 보이지 않는다(animate-delayed-show).
function HomeSkeleton() {
  return (
    <main
      role="status"
      aria-label="불러오는 중"
      className="flex flex-1 animate-delayed-show flex-col"
    >
      <div className="flex gap-3 overflow-hidden pt-4 pb-2 pl-4">
        {[0, 1].map((i) => (
          <Skeleton key={i} shape="rect" className="h-73.5 w-105 shrink-0 rounded-lg" />
        ))}
      </div>
      <div className="flex flex-col gap-3 pt-8 pb-2">
        <SkeletonHead />
        <div className="flex gap-3 overflow-hidden pl-4">
          {[0, 1].map((i) => (
            <SkeletonCard key={i} className="w-44 shrink-0" lines={['w-full', 'w-3/5']} />
          ))}
        </div>
      </div>
      <div className="flex flex-col gap-2 pt-8 pb-8">
        <SkeletonHead />
        <div className="grid grid-cols-2 gap-x-4 gap-y-6 px-4">
          {[0, 1, 2, 3].map((i) => (
            <SkeletonCard key={i} lines={['w-5/6']} />
          ))}
        </div>
        <div className="px-4">
          <Skeleton shape="rect" className="h-11 w-full" />
        </div>
      </div>
    </main>
  )
}

function SkeletonHead() {
  return (
    <div className="flex h-7.5 items-center justify-between px-4">
      <Skeleton className="h-3.75 w-40" />
      <Skeleton className="h-2.5 w-12" />
    </div>
  )
}

function SkeletonCard({ lines, className }: { lines: string[]; className?: string }) {
  return (
    <div className={cn('flex flex-col', className)}>
      <Skeleton shape="rect" className="aspect-square w-full" />
      <div className="flex h-12 flex-col gap-2 pt-2">
        {lines.map((width) => (
          <Skeleton key={width} className={cn('h-2.75', width)} />
        ))}
      </div>
      <div className="flex flex-col gap-3 pt-2">
        <Skeleton className="h-2.5 w-21" />
        <Skeleton className="h-2.5 w-16" />
      </div>
    </div>
  )
}
