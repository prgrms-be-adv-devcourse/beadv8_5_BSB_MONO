'use client'

import { cn } from '@/shared/lib'

import { FOCUS_RING } from '../focus-ring'
import { Icon } from '../icon/icons'

export type QuantityStepperProps = {
  value: number
  onValueChange: (value: number) => void
  min?: number
  /** 재고·구매 제한. 여기에 닿으면 + 를 막는다. */
  max?: number
  size?: 's' | 'm'
  disabled?: boolean
  className?: string
}

const SIZE = {
  s: { button: 'size-8 [&_svg]:size-4', value: 'w-10 text-label-m' },
  m: { button: 'size-11 [&_svg]:size-5', value: 'w-13 text-label-l' },
}

// − 숫자 + 로 굿즈 수량을 조절한다. S 32 / M 44(기본).
export function QuantityStepper({
  value,
  onValueChange,
  min = 1,
  max,
  size = 'm',
  disabled = false,
  className,
}: QuantityStepperProps) {
  const canDecrease = !disabled && value > min
  const canIncrease = !disabled && (max === undefined || value < max)
  const button = cn(
    'flex items-center justify-center rounded-md text-text-primary disabled:cursor-not-allowed disabled:text-text-disabled',
    FOCUS_RING,
    SIZE[size].button,
  )

  return (
    <div
      role="group"
      aria-label="수량"
      className={cn(
        'inline-flex w-fit items-center rounded-md inset-ring',
        disabled
          ? 'bg-bg-disabled inset-ring-border-disabled'
          : 'bg-bg-surface inset-ring-border-strong',
        className,
      )}
    >
      <button
        type="button"
        aria-label="수량 줄이기"
        disabled={!canDecrease}
        onClick={() => onValueChange(value - 1)}
        className={button}
      >
        <Icon name="Minus" />
      </button>
      <output
        aria-live="polite"
        className={cn(
          'text-center',
          SIZE[size].value,
          disabled ? 'text-text-disabled' : 'text-text-primary',
        )}
      >
        {value}
      </output>
      <button
        type="button"
        aria-label="수량 늘리기"
        disabled={!canIncrease}
        onClick={() => onValueChange(value + 1)}
        className={button}
      >
        <Icon name="Plus" />
      </button>
    </div>
  )
}
