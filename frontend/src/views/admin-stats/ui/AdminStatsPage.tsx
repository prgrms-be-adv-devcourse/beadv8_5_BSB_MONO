'use client'

import { format, parseISO } from 'date-fns'
import { useState } from 'react'

import { ConsolePage } from '@/widgets/console-page'
import {
  formatStatsDayWithWeekday,
  resolveStatsPeriod,
  seoulToday,
  STATS_PERIOD_ITEMS,
  statsPeriodQuerySchema,
  type DateSpan,
  type StatsPeriodKind,
  type StatsPeriodQuery,
} from '@/entities/usage-stats'
import { clientEnv } from '@/shared/config'
import { DateTimeField } from '@/shared/date-picker'
import { formatNumber, useSearchParamsState } from '@/shared/lib'
import { Button, buttonClassName, Chip, DataTable, EmptyState } from '@/shared/ui'

import { useRealtimeStats } from '../model/use-usage-stats'
import { FunnelSection } from './FunnelSection'
import { SectionState, StatsBlock, SubHeading } from './StatsBlock'
import { TrendSection } from './TrendSection'

// 어드민 「이용 통계」 (admin-spec R-06 · F-13, 디자인 계약 A-STATS · 피그마 961:157).
// GA4 숫자를 사람 수로만 보여 준다. 영역마다 따로 불러와 따로 실패한다.

export function AdminStatsPage() {
  // 기간은 URL(?period=&start=&end=)에 담는다. 값이 이상하면 지난 7일로 읽는다
  const [url, setUrl] = useSearchParamsState({ period: '7d', start: '', end: '' })
  const parsed = statsPeriodQuerySchema.safeParse(url)
  const query: StatsPeriodQuery = parsed.success ? parsed.data : { period: '7d' }
  const setQuery = (next: StatsPeriodQuery) =>
    setUrl(
      next.period === 'custom'
        ? { period: 'custom', start: next.start, end: next.end }
        : { period: next.period, start: '', end: '' },
    )
  const realtime = useRealtimeStats()

  // 키가 없으면 영역마다 같은 안내를 반복하지 않고 화면 전체에 한 번만 보여 준다
  if (realtime.data?.status === 'notConfigured') {
    return (
      <ConsolePage title="이용 통계" actions={<ClarityLink />}>
        <div className="flex flex-1 items-center justify-center py-20">
          <EmptyState title="GA4 연결 정보가 없어요" />
        </div>
      </ConsolePage>
    )
  }

  return (
    <ConsolePage
      title="이용 통계"
      actions={
        <div className="flex flex-col items-end gap-2">
          <ClarityLink />
          <PeriodPicker query={query} onChange={setQuery} />
        </div>
      }
    >
      <RealtimeSection result={realtime} />
      <TrendSection query={query} />
      <FunnelSection kind="buyer" query={query} />
      <FunnelSection kind="seller" query={query} />
    </ConsolePage>
  )
}

/** 숫자가 왜 그런지 한 사람의 화면 녹화로 이어서 보도록 Clarity 대시보드를 새 탭으로 연다. ID가 없으면 숨긴다 */
function ClarityLink() {
  const id = clientEnv.NEXT_PUBLIC_CLARITY_ID
  if (!id) return null
  return (
    <a
      href={`https://clarity.microsoft.com/projects/view/${id}/dashboard`}
      target="_blank"
      rel="noopener noreferrer"
      className={buttonClassName({ variant: 'secondary', size: 's' })}
    >
      Clarity에서 녹화 보기
    </a>
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
  const [custom, setCustom] = useState<DateSpan>(
    query.period === 'custom' ? { start: query.start, end: query.end } : { start: '', end: '' },
  )
  const today = seoulToday()
  const period = resolveStatsPeriod(query, today)
  const customQuery = statsPeriodQuerySchema.safeParse({ period: 'custom', ...custom })
  const customValid = customQuery.success && custom.start <= custom.end

  const choose = (next: StatsPeriodKind) => {
    setKind(next)
    if (next !== 'custom') onChange({ period: next })
  }

  return (
    <div className="flex flex-col items-end gap-2">
      <div role="group" aria-label="기간" className="flex items-center gap-2">
        {STATS_PERIOD_ITEMS.map((item) => (
          <Chip key={item.value} selected={kind === item.value} onClick={() => choose(item.value)}>
            {item.label}
          </Chip>
        ))}
      </div>
      {period && (
        <p className="text-caption-regular text-text-tertiary">
          {spanText(period.current)} · 비교 기간 {spanText(period.previous)}
        </p>
      )}
      {kind === 'custom' && (
        <div className="flex items-end gap-2">
          <DateTimeField
            mode="single"
            withTime={false}
            label="시작 날짜"
            className="w-52"
            defaultValue={custom.start ? parseISO(custom.start) : undefined}
            endMonth={parseISO(today)}
            isDateDisabled={(date) => format(date, 'yyyy-MM-dd') > today}
            onValueChange={(start) => setCustom((c) => ({ ...c, start }))}
          />
          <DateTimeField
            mode="single"
            withTime={false}
            label="끝 날짜"
            className="w-52"
            defaultValue={custom.end ? parseISO(custom.end) : undefined}
            endMonth={parseISO(today)}
            isDateDisabled={(date) => format(date, 'yyyy-MM-dd') > today}
            onValueChange={(end) => setCustom((c) => ({ ...c, end }))}
          />
          <Button
            variant="secondary"
            disabled={!customValid}
            onClick={() => customQuery.success && onChange(customQuery.data)}
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
    <StatsBlock title="지금 접속 중">
      <SectionState result={result} name="지금 접속 중을" skeletonClassName="h-46">
        {(data) => (
          <div className="flex items-start gap-4">
            <div className="flex w-80 shrink-0 flex-col items-center gap-1 p-4">
              <span className="text-caption-medium text-text-secondary">최근 30분 접속자</span>
              <span className="flex items-baseline gap-1">
                <span className="text-title2">{formatNumber(data.activeUsers)}</span>
                <span className="text-body2-medium">명</span>
              </span>
            </div>
            <div className="flex min-w-0 flex-1 flex-col gap-2">
              <SubHeading>많이 본 화면 5개</SubHeading>
              {data.topScreens.length === 0 ? (
                <p className="py-3 text-body2-regular text-text-secondary">
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
