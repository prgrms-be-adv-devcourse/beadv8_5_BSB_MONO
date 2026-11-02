'use client'

import {
  toFunnelView,
  type FunnelKind,
  type FunnelStepView,
  type StatsPeriodQuery,
} from '@/entities/usage-stats'
import { cn, formatNumber } from '@/shared/lib'
import { DataTable } from '@/shared/ui'

import { usePeriodStats } from '../model/use-usage-stats'
import { SectionState, StatsBlock, SubHeading } from './StatsBlock'

// 구매 · 판매자 단계 (A-STATS 「구매 단계」 「판매자 단계」). 막대는 첫 단계를 100%로 한 비례이고 축은 그리지 않는다.
// 화면 낭독기가 줄마다 읽도록 표로 만들고 막대 칸은 읽지 않게 한다.

const COPY: Record<FunnelKind, { title: string; name: string; empty: string }> = {
  buyer: {
    title: '구매 단계',
    name: '구매 단계를',
    empty: '이 기간에는 구매 기록이 없어요. 기간을 넓혀 보세요.',
  },
  seller: {
    title: '판매자 단계',
    name: '판매자 단계를',
    empty: '이 기간에는 판매자 기록이 없어요. 기간을 넓혀 보세요.',
  },
}

export function FunnelSection({ kind, query }: { kind: FunnelKind; query: StatsPeriodQuery }) {
  const result = usePeriodStats(kind, query)
  const steps = result.data?.status === 'ok' ? toFunnelView(result.data.data.steps) : undefined
  return (
    <StatsBlock title={COPY[kind].title} description={steps && funnelSummary(kind, steps)}>
      <SectionState result={result} name={COPY[kind].name} skeletonClassName="h-64">
        {() => <FunnelTable steps={steps ?? []} />}
      </SectionState>
      {kind === 'seller' && <FormErrorRanking query={query} />}
    </StatsBlock>
  )
}

/** 「가장 많이 빠지는 단계는 「대기열 진입 → 대기열 통과」예요. …」. 기록이 없으면 빈 기간 안내 */
export function funnelSummary(kind: FunnelKind, steps: FunnelStepView[]): string {
  if (steps.every((step) => step.users === 0)) return COPY[kind].empty
  const index = steps.findIndex((step) => step.biggestDrop)
  if (index < 1) return ''
  const before = steps[index - 1]
  const dropped = 100 - Math.round((steps[index].rate ?? 0) * 100)
  return `가장 많이 빠지는 단계는 「${before.label} → ${steps[index].label}」예요. 「${before.label}」까지 온 사람의 ${dropped}%가 다음 단계로 가지 않았어요.`
}

function rateText(step: FunnelStepView, first: boolean) {
  if (first) return '시작 단계'
  return step.rate === undefined ? '계산할 수 없어요' : `앞 단계의 ${Math.round(step.rate * 100)}%`
}

function FunnelTable({ steps }: { steps: FunnelStepView[] }) {
  return (
    <table className="w-full text-body-s">
      <thead className="text-caption text-text-tertiary">
        <tr>
          <th scope="col" className="w-28 py-2 text-left font-normal">
            단계
          </th>
          <td aria-hidden />
          <th scope="col" className="w-20 py-2 text-right font-normal">
            사람 수
          </th>
          <th scope="col" className="w-32 py-2 pl-3 text-left font-normal">
            앞 단계 대비
          </th>
          <th scope="col" className="w-32 py-2 text-left font-normal">
            지난 기간
          </th>
        </tr>
      </thead>
      <tbody>
        {steps.map((step, i) => {
          const rate = rateText(step, i === 0)
          const previous = `지난 기간 ${formatNumber(step.previousUsers)}명`
          return (
            <tr key={step.event} className="group">
              <th scope="row" className="py-2 text-left font-normal">
                {step.label}
              </th>
              <td aria-hidden className="relative py-2 pr-4">
                {step.barRatio > 0 && (
                  <>
                    <div
                      className={cn(
                        'rounded-r-xs h-5',
                        step.biggestDrop ? 'bg-bg-brand' : 'bg-border-strong',
                      )}
                      style={{ width: `${step.barRatio * 100}%` }}
                    />
                    <span className="pointer-events-none absolute top-full left-0 z-10 hidden rounded-md bg-bg-surface px-3 py-2 text-caption whitespace-nowrap shadow-md group-hover:block">
                      {formatNumber(step.users)}명 · {rate} · {previous}
                    </span>
                  </>
                )}
              </td>
              <td className="py-2 text-right text-label-strong">{formatNumber(step.users)}명</td>
              <td className={cn('py-2 pl-3', step.biggestDrop && 'text-text-brand')}>{rate}</td>
              <td className="py-2 text-text-secondary">{previous}</td>
            </tr>
          )
        })}
      </tbody>
    </table>
  )
}

/** 대회 등록에서 입력 오류가 많이 난 항목 묶음 상위 3개. 판매자 단계와 따로 불러온다 */
function FormErrorRanking({ query }: { query: StatsPeriodQuery }) {
  const result = usePeriodStats('formErrors', query)
  return (
    <div className="flex flex-col gap-2">
      <SubHeading>대회 등록에서 입력 오류가 많이 난 항목 묶음</SubHeading>
      <SectionState result={result} name="입력 오류 항목 묶음을" skeletonClassName="h-32">
        {(data) =>
          data.groups.length === 0 ? (
            <p className="py-3 text-body-s text-text-secondary">입력 오류가 없어요</p>
          ) : (
            <DataTable
              label="입력 오류 항목 묶음"
              rows={data.groups.map((group, i) => ({ ...group, rank: i + 1 }))}
              rowKey={(g) => g.name}
              columns={[
                { key: 'rank', header: '순위', width: 'w-20', render: (g) => g.rank },
                { key: 'name', header: '항목 묶음', render: (g) => g.name },
                {
                  key: 'users',
                  header: '오류가 난 사람 수',
                  align: 'right',
                  render: (g) => `${formatNumber(g.users)}명`,
                },
              ]}
            />
          )
        }
      </SectionState>
    </div>
  )
}
