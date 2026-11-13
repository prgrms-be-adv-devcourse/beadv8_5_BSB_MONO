'use client'

import { keepPreviousData, useInfiniteQuery, useQuery } from '@tanstack/react-query'
import Image from 'next/image'
import Link from 'next/link'
import { useRef, useState } from 'react'

import { AppHeader } from '@/widgets/app-header'
import { BottomNav } from '@/widgets/bottom-nav'
import { RaceShareSheet } from '@/widgets/race-share-sheet'
import {
  DEFAULT_RACE_SORT,
  raceListQueryOptions,
  RacePoster,
  RaceSort,
  raceSuggestionsQueryOptions,
  toRaceListItem,
  type RaceListItem,
  type RaceSearchFilters,
} from '@/entities/race'
import {
  addRecentSearch,
  clearRecentSearches,
  cn,
  formatNumber,
  loadRecentSearches,
  removeRecentSearch,
  useTrackOnce,
} from '@/shared/lib'
import {
  Badge,
  Button,
  Chip,
  EmptyState,
  FOCUS_RING,
  Icon,
  IconButton,
  Skeleton,
  SortSelect,
} from '@/shared/ui'

import { useDebouncedValue } from '../model/use-debounced-value'
import { CategoryScreen } from './CategoryScreen'
import { RecentSearches } from './RecentSearches'

export type RaceListState = 'loading' | 'empty' | 'menu' | 'sort'

const SORTS = [
  { value: RaceSort.DEADLINE, label: '마감 임박순' },
  { value: RaceSort.RACE_DATE, label: '대회일 빠른순' },
  { value: RaceSort.LATEST, label: '최신 등록순' },
  { value: RaceSort.PRICE, label: '가격 낮은순' },
]

// 피그마 V2 · M18 대회 찾기 (+ 로딩, 결과 없음, 더보기 메뉴, M18-1 정렬 열림)
// 검색어 · 정렬은 화면 상태로 들고 있다. URL(?q=&sort=)에 담는 것은 PRO-105.
// 종목 · 지역 칩은 카테고리 화면에서 거리 · 지역을 고르고, 이번 달 칩은 대회일 범위로 건다.
export function RaceListPage({ state }: { state?: RaceListState }) {
  const empty = state === 'empty'
  const [query, setQuery] = useState(empty ? '제주 트레일' : '')
  const [focused, setFocused] = useState(false)
  const [sort, setSort] = useState<RaceSort>(DEFAULT_RACE_SORT)
  const [category, setCategory] = useState<Filters>({})
  const [thisMonth, setThisMonth] = useState(false)
  const [categoryOpen, setCategoryOpen] = useState(false)
  const [recent, setRecent] = useState<string[]>([])
  const inputRef = useRef<HTMLInputElement>(null)
  const keyword = useDebouncedValue(query)
  const now = new Date()
  const filters: RaceSearchFilters = {
    keyword,
    sort,
    ...category,
    ...(thisMonth ? monthRange(now) : {}),
  }
  const filtered = Object.keys(category).length > 0 || thisMonth

  const list = useInfiniteQuery({
    ...raceListQueryOptions(filters),
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
  const firstImageId = races.find((race) => race.image)?.id
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

  function submitSearch(term: string) {
    setRecent(addRecentSearch(term))
    inputRef.current?.blur()
  }

  function resetFilters() {
    setQuery('')
    setCategory({})
    setThisMonth(false)
  }

  if (categoryOpen) {
    return (
      <CategoryScreen
        onBack={() => setCategoryOpen(false)}
        onPick={(picked) => {
          setCategory(picked)
          setCategoryOpen(false)
        }}
      />
    )
  }

  return (
    <>
      {!focused && <AppHeader />}
      <main className="flex flex-1 flex-col gap-4 px-4 pt-8 pb-8">
        {!focused && <h1 className="text-title1">대회 찾기</h1>}
        <form
          className="relative flex items-center"
          onSubmit={(e) => {
            e.preventDefault()
            submitSearch(query)
          }}
        >
          {focused && (
            <IconButton
              aria-label="뒤로 가기"
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => inputRef.current?.blur()}
            >
              <Icon name="ChevronLeft" />
            </IconButton>
          )}
          <label className="flex min-w-0 flex-1 items-center gap-2 rounded-md py-4 focus-within:inset-ring-2 focus-within:inset-ring-border-focus">
            <Icon name="Search" className="size-5" />
            <input
              ref={inputRef}
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              onFocus={() => {
                setRecent(loadRecentSearches())
                setFocused(true)
              }}
              onBlur={() => setFocused(false)}
              placeholder="대회 이름이나 지역으로 찾기"
              aria-label="대회 찾기"
              className="min-w-0 flex-1 bg-transparent text-body2-regular outline-none placeholder:text-text-tertiary"
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
                    className={cn(
                      'flex items-center gap-2 px-4 py-2 text-body2-regular',
                      FOCUS_RING,
                    )}
                  >
                    <Icon name="Search" className="size-4 text-text-tertiary" />
                    <span className="truncate">{item.label}</span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </form>
        {focused && query.trim() === '' && (
          <RecentSearches
            terms={recent}
            onPick={(term) => {
              setQuery(term)
              submitSearch(term)
            }}
            onRemove={(term) => setRecent(removeRecentSearch(term))}
            onClear={() => setRecent(clearRecentSearches())}
          />
        )}
        {!focused && (
          <div className="flex flex-wrap gap-2">
            <Chip
              selected={category.courseType !== undefined || category.surface !== undefined}
              onClick={() => setCategoryOpen(true)}
            >
              종목
            </Chip>
            <Chip
              selected={category.regionSido !== undefined}
              onClick={() => setCategoryOpen(true)}
            >
              지역
            </Chip>
            <Chip selected={thisMonth} onClick={() => setThisMonth(!thisMonth)}>
              {now.getMonth() + 1}월
            </Chip>
          </div>
        )}
        {!focused && (
          <div className="flex items-center justify-between">
            {loading ? (
              <Skeleton className="w-12" />
            ) : (
              <p className="text-body2-medium text-text-secondary">대회 {formatNumber(total)}개</p>
            )}
            <SortSelect
              items={[...SORTS]}
              value={sort}
              onValueChange={setSort}
              defaultOpen={state === 'sort'}
              aria-label="대회 정렬"
            />
          </div>
        )}

        {!focused && loading && <ListSkeleton />}
        {!focused && !loading && list.isError && !empty && (
          <div className="flex flex-1 items-center justify-center">
            <EmptyState
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
        {!focused && !loading && !list.isError && races.length === 0 && (
          <div className="flex flex-1 justify-center">
            <EmptyState
              icon="Search"
              title={query.trim() ? `‘${query}’에 맞는 대회가 없어요` : '맞는 대회가 없어요'}
              action={
                (query.trim() !== '' || filtered) && (
                  <Button variant="secondary" onClick={resetFilters}>
                    필터 초기화
                  </Button>
                )
              }
            />
          </div>
        )}
        {!focused && !loading && races.length > 0 && (
          <ul className="flex flex-col">
            {races.map((race) => (
              <li key={race.id} className="relative">
                <Link
                  href={`/races/${race.id}`}
                  className={cn(
                    'flex gap-3 border border-border-default bg-bg-surface py-4 pr-12',
                    FOCUS_RING,
                  )}
                >
                  {race.image ? (
                    <Image
                      src={race.image}
                      alt=""
                      width={96}
                      height={96}
                      // 사진이 있는 첫 카드의 썸네일이 목록 첫 화면에서 가장 큰 요소(LCP)라 먼저 받는다
                      loading={race.id === firstImageId ? 'eager' : undefined}
                      fetchPriority={race.id === firstImageId ? 'high' : undefined}
                      className="size-24 shrink-0 rounded-md object-cover"
                    />
                  ) : (
                    <RacePoster
                      seed={race.id}
                      title=""
                      label={race.posterLabel}
                      className="size-24 shrink-0 rounded-md"
                    />
                  )}
                  <div className="flex min-w-0 flex-1 flex-col items-start gap-1">
                    <Badge tone={race.badge.tone}>{race.badge.label}</Badge>
                    <p className="text-body1-bold">{race.title}</p>
                    <p className="text-body2-regular text-text-secondary">{race.meta}</p>
                    <span className="pt-1 text-body2-medium">{race.price}</span>
                  </div>
                </Link>
                <IconButton
                  size="s"
                  aria-label={`${race.title} 더보기`}
                  className="absolute top-2.5 right-2"
                  onClick={() => setMenuRace(race)}
                >
                  <Icon name="MoreVertical" />
                </IconButton>
              </li>
            ))}
          </ul>
        )}
        {!focused && !loading && !empty && list.hasNextPage && (
          <Button
            variant="secondary"
            onClick={() => void list.fetchNextPage()}
            disabled={list.isFetchingNextPage}
          >
            {list.isFetchingNextPage ? '불러오는 중' : '더 보기'}
          </Button>
        )}
      </main>
      {!focused && <BottomNav />}
      <RaceShareSheet
        open={menuTarget !== null}
        onOpenChange={(open) => !open && setMenuRace(null)}
        title={menuTarget?.title ?? ''}
        url={`${typeof window === 'undefined' ? '' : window.location.origin}/races/${menuTarget?.id ?? ''}`}
      />
    </>
  )
}

type Filters = Pick<RaceSearchFilters, 'regionSido' | 'courseType' | 'surface'>

/** 이번 달 1일 ~ 말일 (YYYY-MM-DD) */
function monthRange(now: Date) {
  const pad = (n: number) => String(n).padStart(2, '0')
  const y = now.getFullYear()
  const m = now.getMonth() + 1
  return {
    raceDateFrom: `${y}-${pad(m)}-01`,
    raceDateTo: `${y}-${pad(m)}-${pad(new Date(y, m, 0).getDate())}`,
  }
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
