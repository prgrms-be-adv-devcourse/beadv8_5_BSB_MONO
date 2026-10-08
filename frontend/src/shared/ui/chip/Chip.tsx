import type { ButtonHTMLAttributes } from 'react'

import { cn } from '@/shared/lib'

import { FOCUS_RING } from '../focus-ring'

export type ChipProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  selected?: boolean
}

// 종목·사이즈처럼 선택지를 버튼으로 나열한다. 단일·복수 선택 모두 aria-pressed로 상태를 알린다.
// 모서리 8은 피그마 Radius 변수에 없는 값이라 그대로 적었다.
export function Chip({ selected = false, type = 'button', className, ...props }: ChipProps) {
  return (
    <button
      type={type}
      aria-pressed={selected}
      className={cn(
        'inline-flex h-9 shrink-0 items-center gap-1 rounded-[8px] px-3.5 whitespace-nowrap disabled:cursor-not-allowed',
        FOCUS_RING,
        selected
          ? 'bg-bg-brand text-label-strong text-text-on-brand'
          : 'bg-bg-surface text-label-m text-text-secondary inset-ring inset-ring-border-default disabled:inset-ring-border-disabled',
        'disabled:bg-bg-disabled disabled:text-text-disabled',
        className,
      )}
      {...props}
    />
  )
}
