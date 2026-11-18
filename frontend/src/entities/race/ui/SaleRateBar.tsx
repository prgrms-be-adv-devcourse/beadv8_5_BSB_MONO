import { cn } from '@/shared/lib'

/** 판매율 막대: 검정 굵은 막대 + 오른쪽 글자(판매율 % 또는 판매 / 정원). 값은 0~100 */
export function SaleRateBar({
  percent,
  label,
  className,
  ...aria
}: {
  percent: number
  label: string
  className?: string
  'aria-label': string
}) {
  const value = Math.min(100, Math.max(0, percent))
  return (
    <div className={cn('flex items-center gap-3', className)}>
      <div
        role="progressbar"
        aria-label={aria['aria-label']}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={value}
        className="h-2 w-24 shrink-0 overflow-hidden rounded-full bg-bg-track"
      >
        <div className="h-full rounded-full bg-bg-inverse" style={{ width: `${value}%` }} />
      </div>
      <span className="text-body2-regular tabular-nums">{label}</span>
    </div>
  )
}
