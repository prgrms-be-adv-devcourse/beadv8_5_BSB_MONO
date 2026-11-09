import type { ButtonHTMLAttributes } from 'react'

import { cn } from '@/shared/lib'

import { FOCUS_RING } from '../focus-ring'

export type IconButtonVariant = 'primary' | 'secondary' | 'ghost'
export type IconButtonSize = 's' | 'm'

const VARIANT: Record<IconButtonVariant, string> = {
  primary: 'bg-bg-brand text-text-on-brand enabled:hover:bg-bg-brand-hover',
  secondary: 'bg-bg-subtle text-text-primary',
  ghost: 'text-text-primary enabled:hover:bg-bg-subtle',
}

// S 32 / M 44(기본). 아이콘은 S 16, M 20.
const SIZE: Record<IconButtonSize, string> = {
  s: 'size-8 [&_svg]:size-4',
  m: 'size-11 [&_svg]:size-5',
}

export type IconButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: IconButtonVariant
  size?: IconButtonSize
  /** 아이콘만 있어서 화면 낭독기가 읽을 이름이 꼭 필요하다. */
  'aria-label': string
}

export function IconButton({
  variant = 'ghost',
  size = 'm',
  type = 'button',
  className,
  ...props
}: IconButtonProps) {
  return (
    <button
      type={type}
      className={cn(
        // 피그마 v2/IconButton: 완전히 둥글고, 누를 수 없으면 원래 색 그대로 반만 보인다
        'inline-flex shrink-0 items-center justify-center rounded-full disabled:cursor-not-allowed disabled:opacity-50',
        FOCUS_RING,
        VARIANT[variant],
        SIZE[size],
        className,
      )}
      {...props}
    />
  )
}
