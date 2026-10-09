import type { ButtonHTMLAttributes, MouseEvent } from 'react'

import { cn } from '@/shared/lib'

import { FOCUS_RING } from '../focus-ring'
import { Spinner } from '../icon/icons'

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'accent'
export type ButtonSize = 's' | 'm' | 'l'

// 피그마 Button. Accent(라임)는 네이비 Hero 안에서만 쓰고 코발트 버튼과 한 화면에 두지 않는다.
// 테두리는 피그마처럼 안쪽 선(inset-ring)이라 크기가 바뀌지 않는다.
const VARIANT: Record<ButtonVariant, string> = {
  primary:
    'bg-bg-brand text-text-on-brand enabled:hover:bg-bg-brand-hover disabled:bg-bg-disabled disabled:text-text-disabled',
  secondary:
    'bg-bg-surface text-text-primary inset-ring inset-ring-border-strong enabled:hover:bg-bg-subtle disabled:bg-bg-disabled disabled:text-text-disabled disabled:inset-ring-border-disabled',
  ghost: 'text-text-primary enabled:hover:bg-bg-subtle disabled:text-text-disabled',
  danger:
    'bg-bg-danger text-text-on-danger enabled:hover:bg-bg-danger-hover disabled:bg-bg-disabled disabled:text-text-disabled',
  accent:
    'bg-bg-accent text-text-on-accent enabled:hover:bg-bg-accent-hover disabled:bg-bg-disabled disabled:text-text-disabled',
}

const SIZE: Record<ButtonSize, string> = {
  s: 'gap-1 px-3 py-2 text-label-s',
  m: 'gap-2 px-4 py-3 text-label-m',
  l: 'gap-2 px-5 py-4 text-label-l',
}

// 피그마 Loading 상태의 Spinner 색. Accent는 라임 위에서 보이도록 네이비(on-accent)다.
const SPINNER_COLOR: Record<ButtonVariant, string> = {
  primary: 'text-text-on-brand',
  secondary: 'text-text-primary',
  ghost: 'text-text-primary',
  danger: 'text-text-on-brand',
  accent: 'text-text-on-accent',
}

export type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant
  size?: ButtonSize
  /** 요청 중 중복 클릭을 막는다. 색은 그대로 두고 앞에 Spinner를 붙인다. */
  loading?: boolean
}

export function Button({
  variant = 'primary',
  size = 'm',
  loading = false,
  type = 'button',
  className,
  children,
  onClick,
  ...props
}: ButtonProps) {
  function handleClick(event: MouseEvent<HTMLButtonElement>) {
    if (loading) {
      event.preventDefault()
      return
    }
    onClick?.(event)
  }

  return (
    <button
      type={type}
      aria-busy={loading || undefined}
      aria-disabled={loading || undefined}
      className={cn(
        'inline-flex shrink-0 items-center justify-center rounded-md whitespace-nowrap select-none disabled:cursor-not-allowed',
        FOCUS_RING,
        VARIANT[variant],
        SIZE[size],
        className,
      )}
      onClick={handleClick}
      {...props}
    >
      {loading && (
        <Spinner className={cn(size === 's' ? 'size-4' : 'size-5', SPINNER_COLOR[variant])} />
      )}
      {children}
    </button>
  )
}
