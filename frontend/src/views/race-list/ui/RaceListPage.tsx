'use client'

import { keepPreviousData, useInfiniteQuery, useQuery } from '@tanstack/react-query'
import Image from 'next/image'
import Link from 'next/link'
import { useState } from 'react'

import { AppHeader } from '@/widgets/app-header'
import { BottomNav } from '@/widgets/bottom-nav'
import {
  DEFAULT_RACE_SORT,
  raceListQueryOptions,
  RacePoster,
  RaceSort,
  raceSuggestionsQueryOptions,
  toRaceListItem,
  type RaceListItem,
} from '@/entities/race'
import { cn, formatNumber, useTrackOnce } from '@/shared/lib'
import {
  Badge,
  BottomSheet,
  Button,
  Chip,
  EmptyState,
  FOCUS_RING,
  Icon,
  IconButton,
  Skeleton,
  SortSelect,
  useToast,
} from '@/shared/ui'

import { useDebouncedValue } from '../model/use-debounced-value'

export type RaceListState = 'loading' | 'empty' | 'menu' | 'sort'

const SORTS = [
  { value: RaceSort.DEADLINE, label: '마감 임박순' },
  { value: RaceSort.RACE_DATE, label: '대회일 빠른순' },
  { value: RaceSort.LATEST, label: '최신 등록순' },
  { value: RaceSort.PRICE, label: '가격 낮은순' },
]

// 피그마 V2 · M18 대회 찾기 (+ 로딩, 결과 없음, 더보기 메뉴, M18-1 정렬 열림)
// 검색어 · 정렬은 화면 상태로 들고 있다. URL(?q=&sort=)에 담는 것은 PRO-105.
// TODO: 종목 · 지역 · 날짜 필터 칩은 아직 API 조건에 붙이지 않았다.
export function RaceListPage({ state }: { state?: RaceListState }) {
  const toast = useToast()
  const empty = state === 'empty'
  const [query, setQuery] = useState(empty ? '제주 트레일' : '')
  const [focused, setFocused] = useState(false)
  const [sort, setSort] = useState<RaceSort>(DEFAULT_RACE_SORT)
  const keyword = useDebouncedValue(query)

  const list = useInfiniteQuery({
    ...raceListQueryOptions({ keyword, sort }),
    placeholderData: keepPreviousData,
  })
  const suggestions = useQuery(raceSuggestionsQueryOptions(keyword))

  const loading = state === 'loading' || list.isPending
  const races = empty
    ? []
    : (list.data?.pages.flatMap((p) => p.races ?? []) ?? []).map((race) => toRaceListItem(race))
  const total = empty ? 0 : (list.data?.pages[0]?.totalElements ?? 0)
  // 처음 목록이 보일 때 한 번. 검색어는 사람이 이름 · 전화번호를 적을 수 있어 있음 · 없음만 보낸다. 시안 미리보기는 보내지 않는다
  useTrackOnce(
    'view_item_list',
    state || loading ? null : { hasKeyword: keyword.trim() !== '', resultCount: total },
  )
  // 'preview': ?preview=menu로 첫 대회의 더보기 메뉴를 열어 둔다
  const [menuRace, setMenuRace] = useState<RaceListItem | 'preview' | null>(
    state === 'menu' ? 'preview' : null,
  )
  const menuTarget = menuRace === 'preview' ? (races[0] ?? null) : menuRace
  const suggestionItems = [
    ...(suggestions.data?.races ?? []).map((r) => ({
      key: `race-${r.raceId}`,
      raceId: r.raceId,
      option: undefined,
      label: r.title ?? '',
    })),
    ...(suggestions.data?.courses ?? []).map((c) => ({
      key: `course-${c.raceCourseId}`,
      raceId: c.raceId,
      option: c.raceCourseId,
      label: c.label ?? '',
    })),
  ]
  const showSuggestions = focused && query.trim() !== '' && suggestionItems.length > 0

  function copyLink(race: RaceListItem) {
    void navigator.clipboard?.writeText(`${window.location.origin}/races/${race.id}`)
    setMenuRace(null)
    toast({ message: '링크를 복사했어요', tone: 'success' })
  }

  return (
    <>
      <AppHeader />
      <main className="flex flex-1 flex-col gap-4 px-4 pt-8 pb-8">
        <h1 className="text-heading-xl">대회 찾기</h1>
        <div className="relative">
          <label className="flex items-center gap-2 rounded-md bg-bg-surface p-4 inset-ring inset-ring-border-default focus-within:inset-ring-2 focus-within:inset-ring-border-focus">
            <Icon name="Search" className="size-5" />
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              onFocus={() => setFocused(true)}
              onBlur={() => setFocused(false)}
              placeholder="대회 이름이나 지역으로 찾기"
              aria-label="대회 찾기"
              className="min-w-0 flex-1 bg-transparent text-body-m outline-none placeholder:text-text-tertiary"
            />
          </label>
          {showSuggestions && (
            <ul
              aria-label="연관검색어"
              // 누르는 동안 입력창이 포커스를 잃어 목록이 먼저 닫히지 않게 한다
              onMouseDown={(e) => e.preventDefault()}
              className="absolute inset-x-0 top-full z-10 mt-1 flex flex-col rounded-md bg-bg-surface py-2 shadow-sm inset-ring inset-ring-border-default"
            >
              {suggestionItems.map((item) => (
                <li key={item.key}>
                  <Link
                    href={
                      item.option === undefined
                        ? `/races/${item.raceId}`
                        : `/races/${item.raceId}?option=${item.option}`
                    }
                    className={cn('flex items-center gap-2 px-4 py-2 text-body-m', FOCUS_RING)}
                  >
                    <Icon name="Search" className="size-4 text-text-tertiary" />
                    <span className="truncate">{item.label}</span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </div>
        <div className="flex flex-wrap gap-2">
          <Chip>종목</Chip>
          <Chip>지역</Chip>
          <Chip>11월</Chip>
        </div>
        <div className="flex items-center justify-between">
          {loading ? (
            <Skeleton className="w-12" />
          ) : (
            <p className="text-label-m text-text-secondary">대회 {formatNumber(total)}개</p>
          )}
          <SortSelect
            items={[...SORTS]}
            value={sort}
            onValueChange={setSort}
            defaultOpen={state === 'sort'}
            aria-label="대회 정렬"
          />
        </div>

        {loading && <ListSkeleton />}
        {!loading && list.isError && !empty && (
          <div className="flex flex-1 items-center justify-center">
            <EmptyState
              icon="Search"
              title="대회를 불러오지 못했어요"
              description="잠시 뒤 다시 시도해 주세요."
              action={
                <Button variant="secondary" onClick={() => void list.refetch()}>
                  다시 시도
                </Button>
              }
            />
          </div>
        )}
        {!loading && !list.isError && races.length === 0 && (
          <div className="flex flex-1 items-center justify-center">
            <EmptyState
              icon="Search"
              title={`'${query}'에 맞는 대회가 없어요`}
              description="다른 지역이나 종목으로 찾아보세요."
              action={
                <Button variant="secondary" onClick={() => setQuery('')}>
                  필터 초기화
                </Button>
              }
            />
          </div>
        )}
        {!loading && races.length > 0 && (
          <ul className="flex flex-col gap-3">
            {races.map((race) => (
              <li key={race.id} className="relative">
                <Link
                  href={`/races/${race.id}`}
                  className={cn(
                    'flex gap-3 rounded-lg bg-bg-surface p-4 pr-12 shadow-sm inset-ring inset-ring-border-default',
                    FOCUS_RING,
                  )}
                >
                  {race.image ? (
                    <Image
                      src={race.image}
                      alt=""
                      width={80}
                      height={80}
                      className="size-20 shrink-0 rounded-md object-cover"
                    />
                  ) : (
                    <RacePoster
                      seed={race.id}
                      title=""
                      label={race.posterLabel}
                      className="size-20 shrink-0 rounded-md"
                    />
                  )}
                  <div className="flex min-w-0 flex-1 flex-col items-start gap-1">
                    <Badge tone={race.badge.tone}>{race.badge.label}</Badge>
                    <p className="text-heading-s">{race.title}</p>
                    <p className="text-body-s text-text-secondary">{race.meta}</p>
                    <div className="flex w-full items-center justify-between pt-1">
                      <span className="text-label-m">{race.price}</span>
                      <span className="text-caption text-text-secondary">{race.remaining}</span>
                    </div>
                  </div>
                </Link>
                <IconButton
                  size="s"
                  aria-label={`${race.title} 더보기`}
                  className="absolute top-2 right-2"
                  onClick={() => setMenuRace(race)}
                >
                  <Icon name="MoreVertical" />
                </IconButton>
              </li>
            ))}
          </ul>
        )}
        {!loading && !empty && list.hasNextPage && (
          <Button
            variant="secondary"
            onClick={() => void list.fetchNextPage()}
            disabled={list.isFetchingNextPage}
          >
            {list.isFetchingNextPage ? '불러오는 중' : '더 보기'}
          </Button>
        )}
      </main>
      <BottomNav />
      <BottomSheet
        open={menuTarget !== null}
        onOpenChange={(open) => !open && setMenuRace(null)}
        title={menuTarget?.title ?? ''}
        footer={
          <Button variant="secondary" size="l" onClick={() => setMenuRace(null)}>
            닫기
          </Button>
        }
      >
        <div className="flex gap-8 px-2 py-2">
          <SheetAction
            icon="Copy"
            label="링크 복사"
            onClick={() => menuTarget && copyLink(menuTarget)}
          />
          {/* TODO: 기기 공유 창(navigator.share) */}
          <SheetAction icon="Share" label="공유하기" onClick={() => setMenuRace(null)} />
        </div>
      </BottomSheet>
    </>
  )
}

function SheetAction({
  icon,
  label,
  onClick,
}: {
  icon: 'Copy' | 'Share'
  label: string
  onClick: () => void
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn('flex items-center gap-3 rounded-md', FOCUS_RING)}
    >
      <span className="flex size-12 items-center justify-center rounded-full bg-bg-subtle">
        <Icon name={icon} className="size-5" />
      </span>
      <span className="text-label-s">{label}</span>
    </button>
  )
}

// 피그마 M18 · 로딩: 목록 카드 4장 자리
function ListSkeleton() {
  return (
    <div role="status" aria-label="불러오는 중" className="flex flex-col gap-3">
      {[0, 1, 2, 3].map((i) => (
        <div
          key={i}
          className="flex gap-3 rounded-lg bg-bg-surface p-4 shadow-sm inset-ring inset-ring-border-default"
        >
          <Skeleton shape="rect" className="size-20 shrink-0" />
          <div className="flex flex-1 flex-col gap-3 pt-1">
            <Skeleton shape="rect" className="h-4 w-12" />
            <Skeleton className="w-11/12" />
            <Skeleton className="w-4/5" />
            <div className="flex justify-between">
              <Skeleton className="w-10" />
              <Skeleton className="w-14" />
            </div>
          </div>
        </div>
      ))}
    </div>
  )
}
