'use client'

import { useQueries } from '@tanstack/react-query'
import Image from 'next/image'
import Link from 'next/link'
import { useState } from 'react'

import {
  formatRaceDate,
  RacePoster,
  regionRacesQueryOptions,
  toRaceListItem,
  type RaceSearchFilters,
} from '@/entities/race'
import { cn } from '@/shared/lib'
import { FOCUS_RING, Icon, IconButton } from '@/shared/ui'

import { addRecentCategory, loadRecentCategories } from '../model/recent-categories'

type Filters = Pick<RaceSearchFilters, 'regionSido' | 'courseType' | 'surface'>

const DISTANCES = [
  { label: '5K', sub: '5km', courseType: 'KM_5', recent: '5K' },
  { label: '10K', sub: '10km', courseType: 'KM_10', recent: '10K' },
  { label: '하프', sub: '21.1km', courseType: 'HALF', recent: '하프' },
  { label: '풀', sub: '42.195km', courseType: 'FULL', recent: '풀코스' },
] as const

// 대회의 시·도는 주소 검색(shorthand: false)이 채운 이름 그대로 저장되고, 검색 필터는 글자가 똑같아야 걸린다.
// 그래서 value는 저장되는 이름(서울특별시)이고, label은 화면에 보이는 짧은 이름(서울)이다.
// 묶음에 시·도가 여럿이면 시·도별 「전체 대회」만 둔다. 광역시는 가까운 도 묶음에 넣는다.
const REGIONS = [
  { label: '서울', sidos: [{ label: '서울', value: '서울특별시' }] },
  {
    label: '경기 · 인천',
    sidos: [
      { label: '경기', value: '경기도' },
      { label: '인천', value: '인천광역시' },
    ],
  },
  { label: '강원', sidos: [{ label: '강원', value: '강원특별자치도' }] },
  {
    label: '충청',
    sidos: [
      { label: '충북', value: '충청북도' },
      { label: '충남', value: '충청남도' },
      { label: '대전', value: '대전광역시' },
      { label: '세종', value: '세종특별자치시' },
    ],
  },
  {
    label: '전라',
    sidos: [
      { label: '전북', value: '전북특별자치도' },
      { label: '전남', value: '전라남도' },
      { label: '광주', value: '광주광역시' },
    ],
  },
  {
    label: '경상',
    sidos: [
      { label: '경북', value: '경상북도' },
      { label: '경남', value: '경상남도' },
      { label: '부산', value: '부산광역시' },
      { label: '대구', value: '대구광역시' },
      { label: '울산', value: '울산광역시' },
    ],
  },
  { label: '제주', sidos: [{ label: '제주', value: '제주특별자치도' }] },
] as const

const REGION_ROWS = [
  { label: '5K', filters: { courseType: ['KM_5'] } },
  { label: '10K', filters: { courseType: ['KM_10'] } },
  { label: '하프', filters: { courseType: ['HALF'] } },
  { label: '풀코스', filters: { courseType: ['FULL'] } },
  { label: '트레일', filters: { surface: 'TRAIL' } },
] as const satisfies readonly { label: string; filters: Filters }[]

// 피그마 V4 · 카테고리: 거리 · 지역으로 고르면 그 조건으로 걸러진 대회 찾기 목록으로 돌아간다.
// 종목을 고르면 그 종목을 「최근 본」으로 브라우저에 남긴다.
// 종목 동그라미는 시안처럼 다섯 칸 균등 폭에 네 개를 왼쪽부터 놓는다.
export function CategoryScreen({
  onPick,
  onBack,
}: {
  onPick: (filters: Filters) => void
  onBack: () => void
}) {
  const [region, setRegion] = useState<(typeof REGIONS)[number]>(REGIONS[0])
  // 이 화면은 대회 찾기에서 눌러 연 뒤에만 그려져 서버 렌더에 들어가지 않으므로 처음 그릴 때 읽는다
  const [recent] = useState(loadRecentCategories)
  const multi = region.sidos.length > 1
  const highlights = useQueries({
    queries: region.sidos.map((sido) => regionRacesQueryOptions(sido.value)),
    combine: (results) =>
      results
        .flatMap((r) => r.data?.races ?? [])
        .sort((a, b) => (a.raceDate ?? '').localeCompare(b.raceDate ?? ''))
        .slice(0, 2),
  })

  function pick(filters: Filters, recentLabel?: string) {
    if (recentLabel) addRecentCategory(recentLabel)
    onPick(filters)
  }

  return (
    <main className="flex flex-1 flex-col">
      <header className="flex h-(--layout-header-height) shrink-0 items-center gap-1 bg-bg-surface px-1">
        <IconButton aria-label="뒤로 가기" onClick={onBack}>
          <Icon name="ChevronLeft" />
        </IconButton>
        <h1 className="text-body1-bold">카테고리</h1>
      </header>
      <ul aria-label="거리" className="grid grid-cols-5 px-4 py-3">
        {DISTANCES.map((d) => (
          <li key={d.label} className="flex justify-center">
            <button
              type="button"
              onClick={() => pick({ courseType: [d.courseType] }, d.recent)}
              className={cn('flex flex-col items-center gap-2 rounded-md', FOCUS_RING)}
            >
              <span className="flex size-11 items-center justify-center rounded-full bg-bg-default text-body1-bold text-text-brand shadow-[0_2px_6px_0_rgba(23,26,28,0.04),0_1px_2px_0_rgba(23,26,28,0.06),0_0_0_1px_rgba(23,26,28,0.08)]">
                {d.label}
              </span>
              <span className="text-caption-regular text-text-secondary">{d.sub}</span>
            </button>
          </li>
        ))}
      </ul>
      <div className="flex flex-1 border-t border-border-default">
        <ul aria-label="지역" className="flex w-24 shrink-0 flex-col bg-bg-subtle">
          <li className="flex h-14 items-center">
            <button
              type="button"
              onClick={() => pick({})}
              className={cn(
                'flex h-full w-full items-center pr-2 pl-4 text-body1-regular whitespace-nowrap text-text-secondary',
                FOCUS_RING,
              )}
            >
              전체
            </button>
          </li>
          {REGIONS.map((r) => (
            <li key={r.label} className="flex h-14 items-center">
              <button
                type="button"
                aria-current={r === region ? 'true' : undefined}
                onClick={() => setRegion(r)}
                className={cn(
                  'flex h-full w-full items-center pr-2 pl-4 text-left whitespace-nowrap',
                  r === region
                    ? 'bg-bg-default text-body1-bold text-text-primary'
                    : 'text-body1-regular text-text-secondary',
                  FOCUS_RING,
                )}
              >
                {r.label}
              </button>
            </li>
          ))}
        </ul>
        <div className="flex min-w-0 flex-1 flex-col bg-bg-default px-4">
          {multi ? (
            <h2 className="flex h-13 items-center text-body1-bold text-text-brand">
              {region.label}
            </h2>
          ) : (
            <button
              type="button"
              onClick={() => pick({ regionSido: region.sidos[0].value })}
              className={cn('flex h-13 items-center gap-2 text-text-brand', FOCUS_RING)}
            >
              <h2 className="flex-1 text-left text-body1-bold">{region.label}</h2>
              <Icon name="ChevronRight" className="size-4" />
            </button>
          )}
          <ul aria-label={`${region.label} 대회`} className="flex flex-col">
            {highlights.map((race) => {
              const image = toRaceListItem(race).image
              return (
                <li key={race.raceId}>
                  <Link
                    href={`/races/${race.raceId}`}
                    className={cn('flex h-16 items-center gap-3', FOCUS_RING)}
                  >
                    {image ? (
                      <Image
                        src={image}
                        alt=""
                        width={40}
                        height={40}
                        className="size-10 shrink-0 rounded-sm object-cover"
                      />
                    ) : (
                      <RacePoster
                        seed={String(race.raceId)}
                        title=""
                        label=""
                        className="size-10 shrink-0 rounded-sm"
                      />
                    )}
                    <span className="min-w-0 flex-1 truncate text-body1-regular">{race.title}</span>
                    {race.raceDate && (
                      <span className="shrink-0 text-caption-regular text-text-secondary">
                        {formatRaceDate(race.raceDate)}
                      </span>
                    )}
                  </Link>
                </li>
              )
            })}
            {region.sidos.map((sido) => (
              <Row
                key={sido.value}
                label={`${multi ? sido.label : region.label} 전체 대회`}
                onClick={() => pick({ regionSido: sido.value })}
              />
            ))}
            {!multi &&
              REGION_ROWS.map((row) => (
                <Row
                  key={row.label}
                  label={row.label}
                  recent={recent.includes(row.label)}
                  onClick={() =>
                    pick({ regionSido: region.sidos[0].value, ...row.filters }, row.label)
                  }
                />
              ))}
          </ul>
        </div>
      </div>
    </main>
  )
}

function Row({ label, recent, onClick }: { label: string; recent?: boolean; onClick: () => void }) {
  return (
    <li>
      <button
        type="button"
        onClick={onClick}
        className={cn('flex h-13 w-full items-center gap-2 text-body1-regular', FOCUS_RING)}
      >
        {label}
        {recent && (
          <span className="rounded-full bg-bg-brand-subtle px-3 py-1 text-caption-medium text-text-brand">
            최근 본
          </span>
        )}
        <Icon name="ChevronRight" className="ml-auto size-4" />
      </button>
    </li>
  )
}
