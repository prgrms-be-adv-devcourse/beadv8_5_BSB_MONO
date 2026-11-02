'use client'

import dynamic from 'next/dynamic'
import { useState } from 'react'

import {
  formatStatsDay,
  formatStatsDayWithWeekday,
  trendDeltaText,
  type StatsPeriodQuery,
  type TrendKind,
} from '@/entities/usage-stats'
import { cn, formatNumber } from '@/shared/lib'
import { FOCUS_RING, Skeleton } from '@/shared/ui'

import { usePeriodStats } from '../model/use-usage-stats'
import { SectionState } from './StatsBlock'

// 일별 추이 카드 하나 (디자인 계약 A-STATS 「일별 추이 카드」). 오른쪽 위 알약 버튼으로 방문자 · 구매를 바꿔 본다.
// 왼쪽 위 큰 숫자는 이번 기간 날짜별 사람 수 합계, 배지는 지난 기간 합계와의 차이다. 비율은 쓰지 않는다.

// Recharts는 이 화면에서만 쓰므로 필요할 때 따로 불러온다 (구매자 화면 번들에 들어가지 않게)
const TrendChart = dynamic(() => import('./TrendChart'), {
  ssr: false,
  loading: () => <Skeleton shape="rect" className="h-55 w-full rounded-md" />,
})

const COPY: Record<TrendKind, { pill: string; who: string; name: string; empty: string }> = {
  visitors: {
    pill: '방문자',
    who: '방문한 사람',
    name: '일별 방문자 수를',
    empty: '이 기간에는 방문 기록이 없어요. 기간을 넓혀 보세요.',
  },
  purchases: {
    pill: '구매',
    who: '구매한 사람',
    name: '일별 구매 수를',
    empty: '이 기간에는 구매 기록이 없어요. 기간을 넓혀 보세요.',
  },
}

const KINDS: TrendKind[] = ['visitors', 'purchases']

export function TrendSection({ query }: { query: StatsPeriodQuery }) {
  const [kind, setKind] = useState<TrendKind>('visitors')
  const result = usePeriodStats(kind, query)
  const title = `${periodName(query)} ${COPY[kind].who}`

  return (
    <section
      aria-label="일별 추이"
      className="flex flex-col gap-6 rounded-xl border border-border-default bg-bg-surface p-8 shadow-[0_8px_24px_rgb(0_0_0/0.06)]"
    >
      <div className="flex items-start justify-between gap-4">
        <h2 className="text-label-m text-text-secondary">{title}</h2>
        <div role="group" aria-label="볼 숫자" className="flex gap-1 rounded-full bg-bg-subtle p-1">
          {KINDS.map((k) => (
            <button
              key={k}
              type="button"
              aria-pressed={kind === k}
              onClick={() => setKind(k)}
              className={cn(
                'h-8 rounded-full px-4 text-label-m',
                kind === k ? 'bg-bg-inverse text-text-inverse' : 'text-text-secondary',
                FOCUS_RING,
              )}
            >
              {COPY[k].pill}
            </button>
          ))}
        </div>
      </div>
      <SectionState result={result} name={COPY[kind].name} skeletonClassName="h-72">
        {(data) => {
          const empty = data.points.every((p) => p.value === 0)
          return (
            <div className="flex flex-col gap-4">
              <div className="flex items-center gap-3">
                <p className="flex items-baseline gap-1">
                  <span className="font-display text-display-l">{formatNumber(data.total)}</span>
                  <span className="text-label-m">명</span>
                </p>
                {!empty && (
                  <p className="flex items-center gap-1.5">
                    <span className="rounded-sm bg-bg-inverse px-2 py-0.5 text-label-s text-text-inverse">
                      {trendDeltaText(data.total, data.previousTotal)}
                    </span>
                    <span className="text-caption text-text-tertiary">지난 기간보다</span>
                  </p>
                )}
              </div>
              <div className="relative">
                <TrendChart points={data.points} empty={empty} />
                {empty && (
                  <p className="absolute inset-x-0 top-1/3 text-center text-body-s text-text-secondary">
                    {COPY[kind].empty}
                  </p>
                )}
              </div>
              {/* 그래프를 보지 못하는 사용자를 위한 같은 숫자의 표 */}
              <table className="sr-only">
                <caption>{title}</caption>
                <thead>
                  <tr>
                    <th scope="col">날짜</th>
                    <th scope="col">사람 수</th>
                  </tr>
                </thead>
                <tbody>
                  {data.points.map((p) => (
                    <tr key={p.date}>
                      <th scope="row">{formatStatsDayWithWeekday(p.date)}</th>
                      <td>{formatNumber(p.value)}명</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <p className="sr-only">{totalSentence(data.total, data.previousTotal)}</p>
            </div>
          )
        }}
      </SectionState>
    </section>
  )
}

/** 카드 작은 제목의 기간 이름: 지난 7일, 지난 30일, 10/1 ~ 10/14 */
function periodName(query: StatsPeriodQuery) {
  if (query.period === '7d') return '지난 7일'
  if (query.period === '30d') return '지난 30일'
  return `${formatStatsDay(query.start)} ~ ${formatStatsDay(query.end)}`
}

/** 화면 낭독기용 표 아래 한 줄: 합계 1,782명, 지난 기간보다 244명 많아요 */
function totalSentence(total: number, previousTotal: number) {
  const delta = total - previousTotal
  const compare =
    delta === 0
      ? '지난 기간과 같아요'
      : `지난 기간보다 ${formatNumber(Math.abs(delta))}명 ${delta > 0 ? '많아요' : '적어요'}`
  return `합계 ${formatNumber(total)}명, ${compare}`
}
