import type { ButtonHTMLAttributes, Ref } from 'react'

import { cn } from '@/shared/lib'

export type DayCellState = {
  /** Selected·Range Start·Range End: 코발트 원 */
  selected?: boolean
  rangeStart?: boolean
  rangeEnd?: boolean
  /** In Range: 옅은 코발트 띠 */
  rangeMiddle?: boolean
  today?: boolean
  /** 지난 날·접수 불가일: 흐린 글자 + 취소선 */
  disabled?: boolean
  /** 확정되어 못 바꾸는 값: 톤 낮춘 코발트, 눌러도 반응하지 않음 */
  readOnly?: boolean
}

export type DayCellProps = ButtonHTMLAttributes<HTMLButtonElement> &
  DayCellState & { date: Date; ref?: Ref<HTMLButtonElement> }

// 피그마 DatePicker/DayCell. 칸 44, 원 40. Hover·Focused는 데스크톱에서만 생긴다(hover: 는 마우스가 있을 때만 동작).
export function DayCell({
  date,
  selected,
  rangeStart,
  rangeEnd,
  rangeMiddle,
  today,
  disabled,
  readOnly,
  className,
  ...props
}: DayCellProps) {
  const filled = (selected || rangeStart || rangeEnd) && !rangeMiddle
  // 시작·끝이 같은 날이면 띠 없이 원만 그린다
  const band = rangeMiddle
    ? 'inset-x-0'
    : rangeStart && !rangeEnd
      ? 'right-0 left-1/2'
      : rangeEnd && !rangeStart
        ? 'right-1/2 left-0'
        : null
  const interactive = !disabled && !readOnly && !filled && !rangeMiddle

  return (
    <button
      type="button"
      disabled={disabled}
      aria-disabled={readOnly || undefined}
      className={cn(
        'group/day relative flex h-11 w-full items-center justify-center outline-none',
        disabled && 'cursor-not-allowed',
        readOnly && 'pointer-events-none',
        className,
      )}
      {...props}
    >
      {band && <span aria-hidden className={cn('absolute inset-y-0.5 bg-bg-brand-subtle', band)} />}
      <span
        className={cn(
          'relative flex size-10 items-center justify-center rounded-full text-label-l text-text-secondary',
          'group-focus-visible/day:inset-ring-2 group-focus-visible/day:inset-ring-border-focus',
          today && 'bg-bg-subtle font-bold text-text-primary',
          rangeMiddle && 'text-text-primary',
          interactive && 'group-hover/day:bg-bg-default group-active/day:bg-bg-track',
          filled && (readOnly ? 'bg-bg-brand/40' : 'bg-bg-brand'),
          filled && 'font-bold text-text-on-brand',
          disabled && 'text-text-disabled line-through',
        )}
      >
        {date.getDate()}
      </span>
    </button>
  )
}
