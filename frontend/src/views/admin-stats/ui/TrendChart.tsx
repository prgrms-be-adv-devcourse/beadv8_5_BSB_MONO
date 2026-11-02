'use client'

import { useId } from 'react'
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  useActiveTooltipLabel,
  XAxis,
  YAxis,
  type TooltipContentProps,
  type TooltipValueType,
} from 'recharts'

import { formatStatsDay, formatStatsDayWithWeekday, type TrendPoint } from '@/entities/usage-stats'
import { formatNumber } from '@/shared/lib'

// 일별 추이 면적 그래프 (디자인 계약 A-STATS 「일별 추이 카드 · Recharts 설정」).
// 선 하나(이번 기간)만 그리고, 지난 기간은 카드의 배지 숫자로만 보여 준다. 세로 격자만 있고 세로축 숫자는 없다.
// Recharts는 이 파일에서만 불러오고, 이 파일은 TrendSection이 next/dynamic으로 불러온다.
// 그래서 다른 화면 번들에는 Recharts가 들어가지 않는다.
// 키보드: Recharts의 accessibilityLayer(기본 켜짐)가 그래프에 초점이 오면 왼쪽·오른쪽 화살표로 날짜를 옮긴다.

const BRAND = 'var(--color-bg-brand)'

export default function TrendChart({ points, empty }: { points: TrendPoint[]; empty: boolean }) {
  const id = useId().replace(/:/g, '')
  const values = points.map((p) => p.value)
  // 세로축을 보여 주지 않으므로 0이 아니라 가장 작은 값의 70% ~ 가장 큰 값의 115%로 흐름을 크게 보여 준다
  const domain: [number, number] = empty
    ? [0, 1]
    : [Math.min(...values) * 0.7, Math.max(...values) * 1.15]

  return (
    <ResponsiveContainer width="100%" height={220}>
      <AreaChart data={points} margin={{ top: 16, right: 12, bottom: 0, left: 12 }}>
        <defs>
          <linearGradient id={`${id}area`} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={BRAND} stopOpacity={0.2} />
            <stop offset="100%" stopColor={BRAND} stopOpacity={0} />
          </linearGradient>
          <linearGradient id={`${id}stroke`} x1="0" y1="0" x2="1" y2="0">
            <stop offset="0%" stopColor={BRAND} stopOpacity={0.35} />
            <stop offset="25%" stopColor={BRAND} stopOpacity={1} />
            <stop offset="100%" stopColor={BRAND} stopOpacity={1} />
          </linearGradient>
        </defs>
        <CartesianGrid
          horizontal={false}
          vertical
          stroke="var(--color-border-default)"
          strokeOpacity={0.6}
        />
        <XAxis
          dataKey="date"
          axisLine={false}
          tickLine={false}
          // 7일이면 날마다, 30일이면 7일마다
          interval={points.length > 14 ? 6 : 0}
          tick={<DateTick />}
          height={32}
        />
        <YAxis hide domain={domain} />
        {!empty && (
          <>
            <Tooltip content={TrendTooltip} cursor={false} />
            <Area
              type="monotone"
              dataKey="value"
              stroke={`url(#${id}stroke)`}
              strokeWidth={2.5}
              fill={`url(#${id}area)`}
              activeDot={{ r: 6, fill: 'var(--color-bg-surface)', stroke: BRAND, strokeWidth: 3 }}
              isAnimationActive={false}
            />
          </>
        )}
      </AreaChart>
    </ResponsiveContainer>
  )
}

type TickProps = { x?: number; y?: number; payload?: { value: string } }

/** 날짜 글자. 마우스·키보드로 고른 날짜만 작은 알약 배지로 보여 준다 */
function DateTick({ x = 0, y = 0, payload }: TickProps) {
  const active = useActiveTooltipLabel()
  if (!payload) return null
  const text = formatStatsDay(payload.value)
  const selected = active === payload.value
  return (
    <g transform={`translate(${x},${y + 4})`}>
      {selected && (
        <rect x={-22} y={0} width={44} height={22} rx={11} fill="var(--color-bg-brand-subtle)" />
      )}
      <text
        x={0}
        y={15}
        textAnchor="middle"
        fontSize={12}
        fontWeight={selected ? 600 : 400}
        fill={selected ? 'var(--color-text-brand)' : 'var(--color-text-tertiary)'}
      >
        {text}
      </text>
    </g>
  )
}

function TrendTooltip({ active, payload }: TooltipContentProps<TooltipValueType, number | string>) {
  const point = payload?.[0]?.payload as TrendPoint | undefined
  if (!active || !point) return null
  return (
    <div className="flex flex-col gap-0.5 rounded-md border border-border-default bg-bg-surface px-3 py-2 shadow-md">
      <span className="text-caption text-text-secondary">
        {formatStatsDayWithWeekday(point.date)}
      </span>
      <span className="text-label-strong">{formatNumber(point.value)}명</span>
    </div>
  )
}
