import type { ButtonHTMLAttributes } from 'react'

import { cn } from '@/shared/lib'

import { FOCUS_RING } from '../focus-ring'

export type ChipProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  selected?: boolean
}

// 종목·사이즈처럼 선택지를 버튼으로 나열한다. 단일·복수 선택 모두 aria-pressed로 상태를 알린다.
// 시안 높이는 32지만, 손가락으로 누르는 영역은 위아래로 넓혀 44로 둔다(before 가상 요소).
export function Chip({ selected = false, type = 'button', className, ...props }: ChipProps) {
  return (
    <button
      type={type}
      aria-pressed={selected}
      className={cn(
        'relative inline-flex h-8 shrink-0 items-center gap-1 rounded-full px-3 whitespace-nowrap before:absolute before:inset-x-0 before:-inset-y-1.5 disabled:cursor-not-allowed disabled:opacity-50',
        FOCUS_RING,
        selected
          ? 'bg-bg-inverse text-body2-bold text-text-inverse'
          : 'bg-bg-surface text-body2-medium text-text-secondary inset-ring inset-ring-border-default enabled:hover:bg-bg-subtle disabled:inset-ring-border-disabled',
        className,
      )}
      {...props}
    />
  )
}
