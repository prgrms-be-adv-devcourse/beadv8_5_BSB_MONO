import { cva } from 'class-variance-authority'
import type { ButtonHTMLAttributes } from 'react'

import { cn } from '@/shared/lib'

import { FOCUS_RING } from '../focus-ring'

export type ChipProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  selected?: boolean
  /** 건수(예: 「답변 대기」 3). 선택된 칩은 글자와 같은 크기, 아닌 칩은 12 작은 글자(2070:70498) */
  count?: number | string
}

const chip = cva(
  `inline-flex h-10 shrink-0 items-center gap-1 rounded-full px-4 whitespace-nowrap disabled:cursor-not-allowed disabled:opacity-50 ${FOCUS_RING}`,
  {
    variants: {
      selected: {
        true: 'bg-bg-inverse text-body1-bold text-text-inverse',
        false:
          'bg-bg-surface text-body1-medium text-text-primary inset-ring inset-ring-border-default enabled:hover:bg-bg-subtle disabled:inset-ring disabled:inset-ring-border-disabled',
      },
    },
  },
)

// 종목·사이즈처럼 선택지를 버튼으로 나열한다. 단일·복수 선택 모두 aria-pressed로 상태를 알린다.
// v4 판매자 시안: 높이 40, 좌우 16, 글자 16(선택하면 굵게), 선택 안 한 칩은 1px 선.
export function Chip({
  selected = false,
  count,
  type = 'button',
  className,
  children,
  ...props
}: ChipProps) {
  return (
    <button
      type={type}
      aria-pressed={selected}
      className={cn(chip({ selected }), className)}
      {...props}
    >
      {children}
      {count !== undefined && (
        <span className={cn(!selected && 'text-caption-medium text-text-secondary')}>{count}</span>
      )}
    </button>
  )
}
