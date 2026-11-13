import Image from 'next/image'
import Link from 'next/link'

import { AppHeader } from '@/widgets/app-header'
import { BottomNav } from '@/widgets/bottom-nav'
import { OfflineBody } from '@/widgets/status-screens'
import { RACE_BANNERS, RaceCard, type RaceSummary } from '@/entities/race'
import { clientEnv } from '@/shared/config'
import { cn } from '@/shared/lib'
import { Carousel, CarouselSlide, FOCUS_RING, Icon, Skeleton } from '@/shared/ui'

export type HomePageProps = {
  state?: 'loading' | 'offline'
  /** 곧 마감 (대회별 카드, 4개) */
  closingSoon: RaceSummary[]
}

const HOME_TABS = [
  { label: '홈', href: '/' },
  { label: '대회 일정', href: '/races' },
  { label: '크루', href: '/crews' },
  { label: '커뮤니티', href: '/community' },
] as const

const SHORTCUTS = [
  { label: '신청 내역', href: '/tickets', icon: 'TicketCheck' },
  { label: '예치금', href: '/me/deposit', icon: 'Wallet' },
  { label: '크루', href: '/crews', icon: 'Users' },
] as const

// 피그마 시나리오 38 · 02 홈(v2): 로고·알림 → 홈 탭 → 메인 배너 → 바로가기 → 곧 마감돼요(2열 4개)
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
          <AppHeader search={false} className="border-b-0" />
          <HomeTabs />
          <main className="flex-1">
            <Carousel label="접수 중인 대회" className="pt-4 pb-2">
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

            <nav aria-label="바로가기" className="flex gap-2 px-4 pt-3 pb-2">
              {SHORTCUTS.map((item) => (
                <Link
                  key={item.label}
                  href={item.href}
                  className={cn(
                    'flex flex-1 flex-col items-center gap-1.5 rounded-md bg-bg-subtle py-3 text-body2-medium text-text-secondary',
                    FOCUS_RING,
                  )}
                >
                  <Icon name={item.icon} className="size-5.5" />
                  {item.label}
                </Link>
              ))}
            </nav>

            <section aria-labelledby="closing-soon" className="flex flex-col gap-3.5 px-4 py-7">
              <div className="flex items-center justify-between">
                <h2 id="closing-soon" className="flex items-center gap-1.5 text-title3">
                  곧 마감돼요
                  {closingSoon.length > 0 && (
                    <span className="text-text-tertiary">{closingSoon.length}</span>
                  )}
                </h2>
                <Link
                  href="/races"
                  className={cn('rounded-sm text-body2-medium text-text-tertiary', FOCUS_RING)}
                >
                  전체 보기
                </Link>
              </div>
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
          </main>
        </>
      )}
      <BottomNav />
    </>
  )
}

// 홈 위쪽 탭. 화면을 바꾸는 링크라 Tabs 대신 nav로 그린다.
function HomeTabs() {
  return (
    <nav aria-label="홈 메뉴" className="flex gap-5 bg-bg-surface px-4">
      {HOME_TABS.filter(
        // shortcut: 백엔드 글 목록 API가 생기면 조건을 지운다. 지금은 실제 API 모드에서 첫 화면이 늘 오류다.
        (tab) => tab.href !== '/community' || clientEnv.NEXT_PUBLIC_API_MOCKING === 'on',
      ).map((tab) => {
        const current = tab.href === '/'
        return (
          <Link
            key={tab.label}
            href={tab.href}
            aria-current={current ? 'page' : undefined}
            className={cn(
              'flex flex-col gap-1.5 rounded-sm pt-1 text-body1-bold',
              current ? 'text-text-primary' : 'text-text-tertiary',
              FOCUS_RING,
            )}
          >
            {tab.label}
            <span aria-hidden className={cn('h-0.5', current && 'bg-text-primary')} />
          </Link>
        )
      })}
    </nav>
  )
}

function EmptyRow({ children }: { children: string }) {
  return <p className="py-6 text-center text-body2-regular text-text-tertiary">{children}</p>
}

// 홈 로딩: 02 홈과 같은 배치(배너 · 바로가기 · 곧 마감 2열 4장)의 회색 틀.
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
      <div className="flex gap-2 px-4 pt-3 pb-2">
        {SHORTCUTS.map((item) => (
          <Skeleton key={item.label} shape="rect" className="h-18 flex-1" />
        ))}
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
