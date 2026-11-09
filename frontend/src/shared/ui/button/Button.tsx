import type { ButtonHTMLAttributes, MouseEvent } from 'react'

import { cn } from '@/shared/lib'

import { FOCUS_RING } from '../focus-ring'
import { Spinner } from '../icon/icons'

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'text' | 'danger' | 'accent'
export type ButtonSize = 's' | 'm' | 'l'

// 피그마 v2/Button. 보조(secondary)는 회색 바탕, 선(ghost)은 테두리만, 글자(text)는 바탕·테두리 없이 글자만이다.
// 테두리는 피그마처럼 안쪽 선(inset-ring)이라 크기가 바뀌지 않는다.
// Accent는 v2에서 Primary와 같은 파랑이다(옛 화면 호환으로 이름만 남김).
const BRAND_DISABLED = 'disabled:bg-bg-brand-disabled disabled:text-text-on-brand'
const VARIANT: Record<ButtonVariant, string> = {
  primary: `bg-bg-brand text-text-on-brand enabled:hover:bg-bg-brand-hover ${BRAND_DISABLED}`,
  secondary:
    'bg-bg-subtle text-text-primary enabled:hover:bg-bg-subtle-hover disabled:bg-bg-disabled disabled:text-text-disabled',
  ghost:
    'text-text-primary inset-ring inset-ring-border-default enabled:hover:bg-bg-subtle focus-visible:inset-ring-border-strong disabled:text-text-disabled disabled:inset-ring-border-disabled',
  text: 'text-text-secondary enabled:hover:bg-bg-subtle focus-visible:text-text-primary disabled:text-text-disabled',
  danger:
    'bg-bg-danger text-text-on-danger enabled:hover:bg-bg-danger-hover disabled:bg-bg-disabled disabled:text-text-disabled',
  accent: `bg-bg-accent text-text-on-accent enabled:hover:bg-bg-accent-hover ${BRAND_DISABLED}`,
}

const SIZE: Record<ButtonSize, string> = {
  s: 'gap-1 px-3 py-2 text-body2-medium',
  m: 'gap-2 px-4 py-3 text-body2-medium',
  l: 'h-13 gap-2 px-5 text-body1-medium',
}

// 피그마 Loading 상태의 Spinner 색
const SPINNER_COLOR: Record<ButtonVariant, string> = {
  primary: 'text-text-on-brand',
  secondary: 'text-text-primary',
  ghost: 'text-text-primary',
  text: 'text-text-secondary',
  danger: 'text-text-on-brand',
  accent: 'text-text-on-accent',
}

/** 링크처럼 버튼이 아닌 요소에 버튼 모양을 입힐 때 쓴다. */
export function buttonClassName({
  variant = 'primary',
  size = 'm',
  className,
}: { variant?: ButtonVariant; size?: ButtonSize; className?: string } = {}) {
  return cn(
    'inline-flex shrink-0 items-center justify-center rounded-sm whitespace-nowrap select-none disabled:cursor-not-allowed',
    FOCUS_RING,
    VARIANT[variant],
    SIZE[size],
    className,
  )
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
      className={buttonClassName({ variant, size, className })}
      onClick={handleClick}
      {...props}
    >
      {loading && (
        <Spinner
          label={null}
          className={cn(size === 's' ? 'size-4' : 'size-5', SPINNER_COLOR[variant])}
          trackClassName="stroke-current/25"
        />
      )}
      {children}
    </button>
  )
}
