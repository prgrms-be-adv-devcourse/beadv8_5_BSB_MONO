'use client'

import { useState } from 'react'

import type { RaceSearchFilters } from '@/entities/race'
import { cn } from '@/shared/lib'
import { FOCUS_RING, Icon, IconButton } from '@/shared/ui'

type Filters = Pick<RaceSearchFilters, 'regionSido' | 'courseType' | 'surface'>

const DISTANCES = [
  { label: '5K', sub: '5km', courseType: 'KM_5' },
  { label: '10K', sub: '10km', courseType: 'KM_10' },
  { label: '하프', sub: '21.1km', courseType: 'HALF' },
  { label: '풀', sub: '42.195km', courseType: 'FULL' },
] as const

// 지역 이름은 주소 검색 API가 채우는 시·도 이름이다. 묶음에 시·도가 여럿이면 시·도별 「전체 대회」만 둔다.
const REGIONS = [
  { label: '서울', sidos: ['서울'] },
  { label: '경기 · 인천', sidos: ['경기', '인천'] },
  { label: '강원', sidos: ['강원'] },
  { label: '충청', sidos: ['충북', '충남'] },
  { label: '전라', sidos: ['전북', '전남'] },
  { label: '경상', sidos: ['경북', '경남'] },
  { label: '제주', sidos: ['제주'] },
] as const

const REGION_ROWS = [
  { label: '5K', filters: { courseType: ['KM_5'] } },
  { label: '10K', filters: { courseType: ['KM_10'] } },
  { label: '하프', filters: { courseType: ['HALF'] } },
  { label: '풀코스', filters: { courseType: ['FULL'] } },
  { label: '트레일', filters: { surface: 'TRAIL' } },
] as const satisfies readonly { label: string; filters: Filters }[]

// 피그마 「카테고리」: 거리 · 지역으로 고르면 그 조건으로 걸러진 대회 찾기 목록으로 돌아간다.
export function CategoryScreen({
  onPick,
  onBack,
}: {
  onPick: (filters: Filters) => void
  onBack: () => void
}) {
  const [region, setRegion] = useState<(typeof REGIONS)[number]>(REGIONS[0])
  const multi = region.sidos.length > 1

  return (
    <main className="flex flex-1 flex-col">
      <header className="flex h-(--layout-header-height) shrink-0 items-center gap-1 border-b border-border-default px-1">
        <IconButton aria-label="뒤로 가기" onClick={onBack}>
          <Icon name="ChevronLeft" />
        </IconButton>
        <h1 className="text-body1-bold">카테고리</h1>
      </header>
      <ul aria-label="거리" className="flex gap-6 border-b border-border-default px-4 py-3">
        {DISTANCES.map((d) => (
          <li key={d.label}>
            <button
              type="button"
              onClick={() => onPick({ courseType: [d.courseType] })}
              className={cn('flex flex-col items-center gap-1 rounded-md', FOCUS_RING)}
            >
              <span className="flex size-11 items-center justify-center rounded-full bg-bg-subtle text-body2-bold text-text-brand">
                {d.label}
              </span>
              <span className="text-caption-regular text-text-tertiary">{d.sub}</span>
            </button>
          </li>
        ))}
      </ul>
      <div className="flex flex-1">
        <ul aria-label="지역" className="flex w-30 shrink-0 flex-col gap-2 px-4 py-4">
          <li>
            <button
              type="button"
              onClick={() => onPick({})}
              className={cn('py-2 text-body2-medium text-text-tertiary', FOCUS_RING)}
            >
              전체
            </button>
          </li>
          {REGIONS.map((r) => (
            <li key={r.label}>
              <button
                type="button"
                aria-current={r === region ? 'true' : undefined}
                onClick={() => setRegion(r)}
                className={cn(
                  'py-2 text-left text-body2-medium',
                  r === region ? 'text-body2-bold text-text-primary' : 'text-text-tertiary',
                  FOCUS_RING,
                )}
              >
                {r.label}
              </button>
            </li>
          ))}
        </ul>
        <ul aria-label={`${region.label} 대회`} className="flex flex-1 flex-col pr-4">
          {region.sidos.map((sido) => (
            <Row
              key={sido}
              label={`${multi ? sido : region.label} 전체 대회`}
              onClick={() => onPick({ regionSido: sido })}
            />
          ))}
          {!multi &&
            REGION_ROWS.map((row) => (
              <Row
                key={row.label}
                label={row.label}
                onClick={() => onPick({ regionSido: region.sidos[0], ...row.filters })}
              />
            ))}
        </ul>
      </div>
    </main>
  )
}

function Row({ label, onClick }: { label: string; onClick: () => void }) {
  return (
    <li>
      <button
        type="button"
        onClick={onClick}
        className={cn(
          'flex w-full items-center justify-between py-4 text-body1-medium',
          FOCUS_RING,
        )}
      >
        {label}
        <Icon name="ChevronRight" className="size-5" />
      </button>
    </li>
  )
}
