'use client'

import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
  type TooltipContentProps,
  type TooltipValueType,
} from 'recharts'

import { formatStatsDay, type TrendPoint } from '@/entities/usage-stats'
import { formatNumber } from '@/shared/lib'

// 일별 추이 선형 그래프 (시안 v9 · 어드민 이용 통계 「일별 추이」). 이번 기간은 브랜드 실선, 지난 기간은 회색 점선.
// Recharts는 이 파일에서만 불러오고, 이 파일은 TrendSection이 next/dynamic으로 불러온다.
// 그래서 다른 화면 번들에는 Recharts가 들어가지 않는다.

const CURRENT = 'var(--color-bg-brand)'
const PREVIOUS = 'var(--color-border-strong)'
const AXIS = { fontSize: 11, fill: 'var(--color-text-tertiary)' }

export default function TrendChart({ points }: { points: TrendPoint[] }) {
  return (
    <ResponsiveContainer width="100%" height={160}>
      <LineChart data={points} margin={{ top: 8, right: 8, bottom: 0, left: -16 }}>
        <CartesianGrid vertical={false} stroke="var(--color-border-default)" />
        <XAxis
          dataKey="date"
          tickFormatter={formatStatsDay}
          tick={AXIS}
          tickLine={false}
          axisLine={false}
          interval="preserveStartEnd"
        />
        <YAxis tick={AXIS} tickLine={false} axisLine={false} allowDecimals={false} width={48} />
        <Tooltip content={TrendTooltip} />
        <Line
          dataKey="previousValue"
          name="지난 기간"
          stroke={PREVIOUS}
          strokeDasharray="4 3"
          strokeWidth={1.5}
          dot={false}
          isAnimationActive={false}
        />
        <Line
          dataKey="value"
          name="이번 기간"
          stroke={CURRENT}
          strokeWidth={2}
          dot={false}
          activeDot={{ r: 4 }}
          isAnimationActive={false}
        />
      </LineChart>
    </ResponsiveContainer>
  )
}

function TrendTooltip({ active, payload }: TooltipContentProps<TooltipValueType, number | string>) {
  const point = payload?.[0]?.payload as TrendPoint | undefined
  if (!active || !point) return null
  return (
    <div className="flex flex-col gap-1 rounded-md bg-bg-surface px-3 py-2 text-caption shadow-md">
      <TooltipRow
        color={CURRENT}
        label={`${formatStatsDay(point.date)} (이번 기간)`}
        value={point.value}
      />
      <TooltipRow
        color={PREVIOUS}
        label={`${formatStatsDay(point.previousDate)} (지난 기간)`}
        value={point.previousValue}
      />
    </div>
  )
}

function TooltipRow({ color, label, value }: { color: string; label: string; value: number }) {
  return (
    <p className="flex items-center gap-2">
      <span aria-hidden className="size-2 rounded-full" style={{ background: color }} />
      <span className="text-text-secondary">{label}</span>
      <span className="ml-auto text-label-s text-text-primary">{formatNumber(value)}명</span>
    </p>
  )
}
