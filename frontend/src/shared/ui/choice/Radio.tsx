'use client'

import { Radio as BaseRadio } from '@base-ui/react/radio'
import { RadioGroup as BaseRadioGroup } from '@base-ui/react/radio-group'
import type { ReactNode } from 'react'

import { cn } from '@/shared/lib'

import { FOCUS_RING } from '../focus-ring'

// 여러 개 중 하나만 고른다(결제 수단·배송 방법). 같은 묶음은 세로 간격 12.
export function RadioGroup({ className, ...props }: BaseRadioGroup.Props) {
  return <BaseRadioGroup className={cn('flex flex-col gap-3', className)} {...props} />
}

export type RadioProps = Omit<BaseRadio.Root.Props, 'children' | 'className'> & {
  label?: ReactNode
  className?: string
}

// 원 20. 선택되면 두께 6의 브랜드 테두리로 가운데 점을 만든다. 테두리 1.5는 피그마 값 그대로다.
export function Radio({ label, className, ...props }: RadioProps) {
  return (
    <label
      className={cn(
        'inline-flex items-center gap-2 text-body-m text-text-primary has-data-disabled:cursor-not-allowed has-data-disabled:text-text-disabled',
        className,
      )}
    >
      <BaseRadio.Root
        className={cn(
          'size-5 shrink-0 rounded-full bg-bg-surface inset-ring-[1.5px] inset-ring-border-strong',
          'data-checked:inset-ring-6 data-checked:inset-ring-border-brand',
          'data-disabled:bg-bg-disabled data-disabled:inset-ring-border-disabled data-disabled:data-checked:inset-ring-text-disabled',
          FOCUS_RING,
        )}
        {...props}
      />
      {label}
    </label>
  )
}
