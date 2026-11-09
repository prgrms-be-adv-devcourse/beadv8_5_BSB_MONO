'use client'

import { useId } from 'react'
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  useActiveTooltipLabel,
  usePlotArea,
  useXAxisScale,
  useYAxisScale,
  XAxis,
  YAxis,
  type TooltipContentProps,
  type TooltipValueType,
} from 'recharts'

import { formatStatsDay, formatStatsDayWithWeekday, type TrendPoint } from '@/entities/usage-stats'
import { formatNumber } from '@/shared/lib'

// 일별 추이 면적 그래프 (디자인 계약 A-STATS 「일별 추이 카드 · Recharts 설정」, 11/2 지훈 님 확정).
// 선 하나(이번 기간)만 그리고, 지난 기간은 카드의 배지 숫자로만 보여 준다. 세로 격자만 있고 세로축 숫자는 없다.
// 선과 영역의 그라데이션은 고른 날짜(마우스 · 키보드)를 중심으로 진해지고, 고른 날짜가 없으면 가운데가 중심이다.
// Recharts는 이 파일에서만 불러오고, 이 파일은 TrendSection이 next/dynamic으로 불러온다.
// 그래서 다른 화면 번들에는 Recharts가 들어가지 않는다.
// 키보드: Recharts의 accessibilityLayer(기본 켜짐)가 그래프에 초점이 오면 왼쪽·오른쪽 화살표로 날짜를 옮긴다.

const BRAND = 'var(--color-bg-brand)'
/** 그래프 영역 높이와 날짜 줄 높이 (카드 안 폭 1054px 기준) */
const PLOT_HEIGHT = 400
const DATE_ROW_HEIGHT = 44
/** 세로 격자선은 그래프 영역 위에서 이만큼 띄우고 시작한다 */
const GRID_TOP_GAP = 40
/** 왼쪽 · 오른쪽 끝 점을 카드 안쪽으로 들이는 거리 */
const EDGE_INSET = 24

export default function TrendChart({ points, empty }: { points: TrendPoint[]; empty: boolean }) {
  const id = useId().replace(/:/g, '')
  const values = points.map((p) => p.value)
  // 세로축을 보여 주지 않으므로 0이 아니라 가장 작은 값의 60% ~ 가장 큰 값의 110%로 오르내림을 크게 보여 준다
  const domain: [number, number] = empty
    ? [0, 1]
    : [Math.min(...values) * 0.6, Math.max(...values) * 1.1]

  return (
    <ResponsiveContainer width="100%" height={PLOT_HEIGHT + DATE_ROW_HEIGHT}>
      <AreaChart data={points} margin={{ top: 0, right: 0, bottom: 0, left: 0 }}>
        <Gradients id={id} points={points} />
        <VerticalGrid />
        <XAxis
          dataKey="date"
          axisLine={false}
          tickLine={false}
          padding={{ left: EDGE_INSET, right: EDGE_INSET }}
          // 7일이면 날마다, 30일이면 7일마다
          interval={points.length > 14 ? 6 : 0}
          tick={<DateTick first={points[0]?.date} last={points.at(-1)?.date} />}
          height={DATE_ROW_HEIGHT}
        />
        <YAxis hide domain={domain} />
        {!empty && (
          <>
            <PointTooltip points={points} />
            <Area
              type="natural"
              dataKey="value"
              stroke={`url(#${id}stroke)`}
              strokeWidth={4}
              strokeLinecap="round"
              strokeLinejoin="round"
              fill={`url(#${id}area)`}
              activeDot={{ r: 7, fill: 'var(--color-bg-surface)', stroke: BRAND, strokeWidth: 3 }}
              isAnimationActive={false}
            />
          </>
        )}
      </AreaChart>
    </ResponsiveContainer>
  )
}

/** 고른 날짜의 가로 위치(0~1). 고른 날짜가 없으면 가운데 */
function useFocusRatio(points: TrendPoint[]) {
  const active = useActiveTooltipLabel()
  const index = points.findIndex((p) => p.date === active)
  return index < 0 || points.length < 2 ? 0.5 : index / (points.length - 1)
}

/** 선과 영역의 그라데이션. 고른 날짜가 바뀌면 다시 계산한다 */
function Gradients({ id, points }: { id: string; points: TrendPoint[] }) {
  const focus = useFocusRatio(points)
  const clamp = (n: number) => Math.min(1, Math.max(0, n))
  return (
    <defs>
      {/* 선: 왼쪽 끝 15% → 고른 날짜 45% 왼쪽 45% → 고른 날짜 100% → 10% 오른쪽 70% → 오른쪽 끝 30% */}
      <linearGradient id={`${id}stroke`} x1="0" y1="0" x2="1" y2="0">
        <stop offset={0} stopColor={BRAND} stopOpacity={0.15} />
        <stop offset={clamp(focus - 0.45)} stopColor={BRAND} stopOpacity={0.45} />
        <stop offset={focus} stopColor={BRAND} stopOpacity={1} />
        <stop offset={clamp(focus + 0.1)} stopColor={BRAND} stopOpacity={0.7} />
        <stop offset={1} stopColor={BRAND} stopOpacity={0.3} />
      </linearGradient>
      {/* 영역: 고른 날짜 위쪽이 중심인 타원(가로 반지름 75%, 세로 110%). 24% → 10% → 0% */}
      <radialGradient
        id={`${id}area`}
        cx={focus}
        cy={0}
        r={1}
        gradientTransform={`translate(${focus} 0) scale(0.75 1.1) translate(${-focus} 0)`}
      >
        <stop offset="0%" stopColor={BRAND} stopOpacity={0.24} />
        <stop offset="50%" stopColor={BRAND} stopOpacity={0.1} />
        <stop offset="100%" stopColor={BRAND} stopOpacity={0} />
      </radialGradient>
    </defs>
  )
}

/** 날짜마다 세로선만. 그래프 영역 위에서 40px 띄우고 바닥까지 그린다 */
function VerticalGrid() {
  const area = usePlotArea()
  if (!area) return null
  return (
    <CartesianGrid
      horizontal={false}
      vertical
      y={area.y + GRID_TOP_GAP}
      height={area.height - GRID_TOP_GAP}
      stroke="var(--color-border-default)"
      strokeOpacity={0.55}
    />
  )
}

type TickProps = {
  x?: number
  y?: number
  payload?: { value: string }
  first?: string
  last?: string
}

/** 날짜 글자. 고른 날짜만 작은 배지로 보이고, 첫 · 마지막 날짜는 더 옅게 보인다 */
function DateTick({ x = 0, y = 0, payload, first, last }: TickProps) {
  const active = useActiveTooltipLabel()
  if (!payload) return null
  const selected = active === payload.value
  const edge = payload.value === first || payload.value === last
  return (
    <g transform={`translate(${x},${y + 10})`} opacity={edge && !selected ? 0.5 : 1}>
      {selected && (
        <rect x={-21} y={0} width={42} height={22} rx={6} fill="var(--color-bg-brand-subtle)" />
      )}
      <text
        x={0}
        y={15}
        textAnchor="middle"
        fontSize={12}
        fontWeight={selected ? 600 : 400}
        fill={selected ? 'var(--color-text-brand)' : 'var(--color-text-tertiary)'}
      >
        {formatStatsDay(payload.value)}
      </text>
    </g>
  )
}

/** 설명 상자를 마우스가 아니라 고른 날짜의 점 바로 위에 띄운다 */
function PointTooltip({ points }: { points: TrendPoint[] }) {
  const active = useActiveTooltipLabel()
  const xScale = useXAxisScale()
  const yScale = useYAxisScale()
  const point = points.find((p) => p.date === active)
  const x = point && xScale?.(point.date)
  const y = point && yScale?.(point.value)
  return (
    <Tooltip
      content={TrendTooltip}
      cursor={false}
      offset={0}
      allowEscapeViewBox={{ x: true, y: true }}
      position={typeof x === 'number' && typeof y === 'number' ? { x, y } : undefined}
    />
  )
}

function TrendTooltip({ active, payload }: TooltipContentProps<TooltipValueType, number | string>) {
  const point = payload?.[0]?.payload as TrendPoint | undefined
  if (!active || !point) return null
  return (
    // 상자 아래 가운데가 점 위 16px에 오도록 옮긴다
    <div className="flex -translate-x-1/2 -translate-y-[calc(100%+16px)] flex-col gap-0.5 rounded-md border border-border-default bg-bg-surface px-3 py-2 shadow-md">
      <span className="text-caption-regular text-text-secondary">
        {formatStatsDayWithWeekday(point.date)}
      </span>
      <span className="text-body2-bold">{formatNumber(point.value)}명</span>
    </div>
  )
}
