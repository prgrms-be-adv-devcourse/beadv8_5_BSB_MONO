'use client'

import { Fragment, useState } from 'react'

import { ConsolePage } from '@/widgets/console-page'
import {
  formatStatsDayWithWeekday,
  resolveStatsPeriod,
  seoulToday,
  STATS_PERIOD_ITEMS,
  type DateSpan,
  type StatsPeriodKind,
  type StatsPeriodQuery,
} from '@/entities/usage-stats'
import { formatNumber } from '@/shared/lib'
import { Button, DataTable, Divider, EmptyState, Segmented, TextField } from '@/shared/ui'

import { useRealtimeStats } from '../model/use-usage-stats'
import { FunnelSection } from './FunnelSection'
import { SectionState, StatsBlock, SubHeading } from './StatsBlock'
import { TrendSection } from './TrendSection'

// 어드민 「이용 통계」 (admin-spec R-06 · F-13, 디자인 계약 A-STATS · 피그마 961:157).
// GA4 숫자를 사람 수로만 보여 준다. 영역마다 따로 불러와 따로 실패한다.

export function AdminStatsPage() {
  const [query, setQuery] = useState<StatsPeriodQuery>({ period: '7d' })
  const realtime = useRealtimeStats()

  // 키가 없으면 영역마다 같은 안내를 반복하지 않고 화면 전체에 한 번만 보여 준다
  if (realtime.data?.status === 'notConfigured') {
    return (
      <ConsolePage title="이용 통계">
        <div className="flex flex-1 items-center justify-center py-20">
          <EmptyState
            title="GA4 연결 정보가 없어요"
            description="서버 환경 변수에 GA4 속성 ID와 서비스 계정 키를 넣으면 숫자가 보여요."
          />
        </div>
      </ConsolePage>
    )
  }

  return (
    <ConsolePage
      title="이용 통계"
      description="GA4 기준 어제까지의 기록이에요. 「지금 접속 중」만 몇 초 안에 반영돼요."
      actions={<PeriodPicker query={query} onChange={setQuery} />}
    >
      {[
        <RealtimeSection key="realtime" result={realtime} />,
        <TrendSection key="trend" query={query} />,
        <FunnelSection key="buyer" kind="buyer" query={query} />,
        <FunnelSection key="seller" kind="seller" query={query} />,
      ].map((section, i) => (
        <Fragment key={section.key}>
          {i > 0 && <Divider />}
          {section}
        </Fragment>
      ))}
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
  const period = resolveStatsPeriod(query, today)
  const customValid = Boolean(custom.start && custom.end && custom.start <= custom.end)

  const choose = (next: StatsPeriodKind) => {
    setKind(next)
    if (next !== 'custom') onChange({ period: next })
  }

  return (
    <div className="flex flex-col items-end gap-2">
      <Segmented
        label="기간"
        layout="hug"
        items={STATS_PERIOD_ITEMS}
        value={kind}
        onChange={choose}
      />
      {period && (
        <p className="text-caption text-text-tertiary">
          {spanText(period.current)} · 비교 기간 {spanText(period.previous)}
        </p>
      )}
      {kind === 'custom' && (
        <div className="flex items-end gap-2">
          <TextField
            label="시작 날짜"
            type="date"
            max={today}
            value={custom.start}
            onChange={(e) => setCustom((c) => ({ ...c, start: e.target.value }))}
          />
          <TextField
            label="끝 날짜"
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
        </div>
      )}
    </div>
  )
}

function RealtimeSection({ result }: { result: ReturnType<typeof useRealtimeStats> }) {
  return (
    <StatsBlock
      title="지금 접속 중"
      description="최근 30분 동안 접속한 사람 수예요. 몇 초 안에 반영돼요."
    >
      <SectionState result={result} name="지금 접속 중을">
        {(data) => (
          <div className="flex items-start gap-4">
            <div className="flex w-80 shrink-0 flex-col items-center gap-1 rounded-lg bg-bg-subtle p-4">
              <span className="text-label-s text-text-secondary">최근 30분 접속자</span>
              <span className="flex items-baseline gap-1">
                <span className="font-display text-display-s">
                  {formatNumber(data.activeUsers)}
                </span>
                <span className="text-label-m">명</span>
              </span>
            </div>
            <div className="flex min-w-0 flex-1 flex-col gap-2">
              <SubHeading>많이 본 화면 5개</SubHeading>
              {data.topScreens.length === 0 ? (
                <p className="py-3 text-body-s text-text-secondary">
                  최근 30분 동안 본 화면이 없어요
                </p>
              ) : (
                <DataTable
                  label="많이 본 화면"
                  rows={data.topScreens}
                  rowKey={(s) => s.name}
                  columns={[
                    // GA4 실시간 보고서는 화면 주소 대신 화면 제목을 준다
                    { key: 'name', header: '화면', render: (s) => s.name },
                    {
                      key: 'users',
                      header: '사람 수',
                      align: 'right',
                      render: (s) => `${formatNumber(s.activeUsers)}명`,
                    },
                  ]}
                />
              )}
            </div>
          </div>
        )}
      </SectionState>
    </StatsBlock>
  )
}

function spanText({ start, end }: DateSpan) {
  return `${formatStatsDayWithWeekday(start)} ~ ${formatStatsDayWithWeekday(end)}`
}
