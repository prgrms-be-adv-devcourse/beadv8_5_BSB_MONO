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
  /** brand(기본): 선택하면 브랜드색 테두리. inverse: 검정 테두리(신고 사유 시트 등) */
  tone?: 'brand' | 'inverse'
}

// 원 24. 선택되면 두께 7의 브랜드 테두리로 가운데 점을 만든다. 테두리 1.5는 피그마 값 그대로다.
export function Radio({ label, className, tone = 'brand', ...props }: RadioProps) {
  return (
    <label
      className={cn(
        'inline-flex items-center gap-2 text-body1-regular text-text-primary has-data-disabled:cursor-not-allowed has-data-disabled:text-text-disabled',
        className,
      )}
    >
      <BaseRadio.Root
        className={cn(
          'size-6 shrink-0 rounded-full bg-bg-surface inset-ring-[1.5px] inset-ring-border-strong',
          'data-checked:inset-ring-7',
          tone === 'inverse'
            ? 'data-checked:inset-ring-bg-inverse'
            : 'data-checked:inset-ring-border-brand',
          'data-disabled:bg-bg-disabled data-disabled:inset-ring-border-disabled data-disabled:data-checked:inset-ring-text-disabled',
          FOCUS_RING,
        )}
        {...props}
      />
      {label}
    </label>
  )
}
