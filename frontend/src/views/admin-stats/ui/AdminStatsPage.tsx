'use client'

import type { UseQueryResult } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'

import { ConsoleCard, ConsolePage } from '@/widgets/console-page'
import {
  seoulToday,
  STATS_PERIOD_ITEMS,
  toFunnelView,
  type DateSpan,
  type FunnelKind,
  type FunnelStats,
  type StatsPeriodKind,
  type StatsPeriodQuery,
  type StatsResponse,
} from '@/entities/usage-stats'
import { formatNumber } from '@/shared/lib'
import { Badge, Button, DataTable, Segmented, Skeleton, StatTile, TextField } from '@/shared/ui'

import { isNotAdminError, useFunnelStats, useRealtimeStats } from '../model/use-usage-stats'

// 어드민 「이용 통계」(admin-spec R-06 · F-13). GA4 숫자를 사람 수로만 보여 준다.
// TODO(design): 디자인 계약(A-STATS)이 나오면 그 모양으로 맞춘다. 지금은 기존 어드민 부품으로 만든 뼈대다.

const TEXT = {
  loadFailed: '불러오지 못함',
  notConfigured: 'GA4 연결 정보가 없습니다',
  notAdmin: '관리자만 이용할 수 있어요.',
}

export function AdminStatsPage() {
  const [query, setQuery] = useState<StatsPeriodQuery>({ period: '7d' })

  return (
    <ConsolePage
      title="이용 통계"
      description="GA4 기준 어제까지의 기록입니다. 지금 접속 중만 실시간이에요. 사람 수만 보여 줘요."
    >
      <PeriodPicker query={query} onChange={setQuery} />
      <RealtimeCard />
      <FunnelCard kind="buyer" title="구매 단계별 남는 사람 수" query={query} />
      <FunnelCard kind="seller" title="판매자 단계별 남는 사람 수" query={query} />
    </ConsolePage>
  )
}

function PeriodPicker({
  query,
  onChange,
}: {
  query: StatsPeriodQuery
  onChange: (next: StatsPeriodQuery) => void
}) {
  const [kind, setKind] = useState<StatsPeriodKind>(query.period)
  const [custom, setCustom] = useState<DateSpan>({ start: '', end: '' })
  const today = seoulToday()
  const customValid = Boolean(custom.start && custom.end && custom.start <= custom.end)

  const choose = (next: StatsPeriodKind) => {
    setKind(next)
    if (next !== 'custom') onChange({ period: next })
  }

  return (
    <div className="flex flex-wrap items-end gap-3">
      <Segmented
        label="기간"
        layout="hug"
        items={STATS_PERIOD_ITEMS}
        value={kind}
        onChange={choose}
      />
      {kind === 'custom' && (
        <>
          <TextField
            label="시작일"
            type="date"
            max={today}
            value={custom.start}
            onChange={(e) => setCustom((c) => ({ ...c, start: e.target.value }))}
          />
          <TextField
            label="종료일"
            type="date"
            max={today}
            value={custom.end}
            onChange={(e) => setCustom((c) => ({ ...c, end: e.target.value }))}
          />
          <Button
            variant="secondary"
            disabled={!customValid}
            onClick={() => onChange({ period: 'custom', ...custom })}
          >
            적용
          </Button>
        </>
      )}
    </div>
  )
}

function RealtimeCard() {
  const realtime = useRealtimeStats()
  return (
    <ConsoleCard title="지금 접속 중" aside={<span className="text-caption">최근 30분</span>}>
      <SectionBody result={realtime}>
        {(data) => (
          <div className="flex gap-4">
            <StatTile label="접속한 사람" value={formatNumber(data.activeUsers)} unit="명" />
            <ol aria-label="많이 본 화면" className="flex flex-2 flex-col gap-2">
              {data.topScreens.map((screen) => (
                <li key={screen.name} className="flex justify-between gap-4 text-body-m">
                  <span className="truncate">{screen.name}</span>
                  <span className="shrink-0 text-text-secondary">
                    {formatNumber(screen.activeUsers)}명
                  </span>
                </li>
              ))}
            </ol>
          </div>
        )}
      </SectionBody>
    </ConsoleCard>
  )
}

function FunnelCard({
  kind,
  title,
  query,
}: {
  kind: FunnelKind
  title: string
  query: StatsPeriodQuery
}) {
  const funnel = useFunnelStats(kind, query)
  const period = funnel.data?.status === 'ok' ? funnel.data.data.period : undefined
  return (
    <ConsoleCard
      title={title}
      aside={
        period && (
          <span className="text-caption text-text-tertiary">
            {spanLabel(period.current)} (앞 기간 {spanLabel(period.previous)})
          </span>
        )
      }
    >
      <SectionBody result={funnel}>{(data) => <FunnelTable kind={kind} data={data} />}</SectionBody>
    </ConsoleCard>
  )
}

function FunnelTable({ kind, data }: { kind: FunnelKind; data: FunnelStats }) {
  const steps = toFunnelView(data.steps)
  return (
    <DataTable
      label={kind === 'buyer' ? '구매 단계' : '판매자 단계'}
      rows={steps}
      rowKey={(s) => s.event}
      columns={[
        {
          key: 'label',
          header: '단계',
          render: (s) => (
            <span className="flex items-center gap-2">
              {s.label}
              {s.biggestDrop && <Badge tone="warning">가장 많이 빠짐</Badge>}
            </span>
          ),
        },
        {
          key: 'users',
          header: '사람 수',
          align: 'right',
          render: (s) => `${formatNumber(s.users)}명`,
        },
        {
          key: 'rate',
          header: '앞 단계 대비',
          align: 'right',
          render: (s) => (s.rate === undefined ? '-' : `${Math.round(s.rate * 100)}%`),
        },
        {
          key: 'previous',
          header: '앞 기간',
          align: 'right',
          render: (s) => `${formatNumber(s.previousUsers)}명`,
        },
      ]}
    />
  )
}

/** 영역 하나의 로딩 · 실패 · 키 없음 · 정상을 나눈다. 다른 영역에는 영향이 없다 */
function SectionBody<T>({
  result,
  children,
}: {
  result: UseQueryResult<StatsResponse<T>>
  children: (data: T) => ReactNode
}) {
  if (result.isPending) {
    return (
      <div role="status" aria-label="불러오는 중">
        <Skeleton shape="rect" className="h-24 rounded-md" />
      </div>
    )
  }
  if (result.isError) {
    return (
      <div className="flex items-center justify-between gap-4 rounded-md bg-bg-subtle p-4">
        <p className="text-body-m text-text-secondary">
          {isNotAdminError(result.error) ? TEXT.notAdmin : TEXT.loadFailed}
        </p>
        {!isNotAdminError(result.error) && (
          <Button variant="secondary" onClick={() => result.refetch()}>
            다시 시도
          </Button>
        )}
      </div>
    )
  }
  if (result.data.status === 'notConfigured') {
    return (
      <p className="rounded-md bg-bg-subtle p-4 text-body-m text-text-secondary">
        {TEXT.notConfigured}
      </p>
    )
  }
  return children(result.data.data)
}

/** 2026-10-26 ~ 2026-11-01 → 10/26 ~ 11/1 */
function spanLabel({ start, end }: DateSpan) {
  const md = (iso: string) => `${Number(iso.slice(5, 7))}/${Number(iso.slice(8, 10))}`
  return `${md(start)} ~ ${md(end)}`
}
