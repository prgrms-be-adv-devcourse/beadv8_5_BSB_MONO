'use client'

import { cn } from '@/shared/lib'

import { FOCUS_RING } from '../focus-ring'

export type SegmentedItem<T extends string> = { value: T; label: string; count?: number }

export type SegmentedProps<T extends string> = {
  items: SegmentedItem<T>[]
  value: T
  onChange: (value: T) => void
  label: string
  /** fill: 칸이 너비를 똑같이 나눔(모바일) · hug: 글자만큼(데스크톱 탭) */
  layout?: 'fill' | 'hug'
  /** 칸 높이 m 40 · l 48 */
  size?: 'm' | 'l'
  className?: string
}

// 피그마 화면의 묶음 탭: 회색(bg-segment) 판 위에 고른 칸만 흰 바탕으로 떠 있다. 칸 높이 40.
export function Segmented<T extends string>({
  items,
  value,
  onChange,
  label,
  layout = 'fill',
  size = 'm',
  className,
}: SegmentedProps<T>) {
  return (
    <div
      role="tablist"
      aria-label={label}
      className={cn('flex gap-1 rounded-md bg-bg-segment p-1', className)}
    >
      {items.map((item) => {
        const selected = item.value === value
        return (
          <button
            key={item.value}
            type="button"
            role="tab"
            aria-selected={selected}
            onClick={() => onChange(item.value)}
            className={cn(
              'flex min-w-0 items-center justify-center gap-1 truncate rounded-sm text-body1-medium',
              size === 'm' ? 'h-10' : 'h-12',
              layout === 'fill' ? 'flex-1 px-2' : 'px-4',
              selected
                ? 'bg-bg-surface font-bold text-text-primary shadow-sm'
                : 'text-text-tertiary',
              FOCUS_RING,
            )}
          >
            {item.label}
            {item.count !== undefined && (
              <span className={cn('text-caption-medium', selected && 'text-text-brand')}>
                {item.count}
              </span>
            )}
          </button>
        )
      })}
    </div>
  )
}
