'use client'

import { Switch as BaseSwitch } from '@base-ui/react/switch'
import type { ReactNode } from 'react'

import { cn } from '@/shared/lib'

import { FOCUS_RING } from '../focus-ring'

export type SwitchProps = Omit<BaseSwitch.Root.Props, 'children' | 'className'> & {
  label?: ReactNode
  className?: string
}

// 누르는 즉시 적용되는 설정(알림 받기·크루 공개). 저장 버튼이 따로 있는 폼에서는 Checkbox를 쓴다.
// 44×24, 라벨은 왼쪽.
export function Switch({ label, className, ...props }: SwitchProps) {
  return (
    <label
      className={cn(
        'inline-flex items-center gap-3 text-body-m text-text-primary has-data-disabled:cursor-not-allowed has-data-disabled:text-text-disabled',
        className,
      )}
    >
      {label}
      <BaseSwitch.Root
        className={cn(
          'flex h-6 w-11 shrink-0 items-center rounded-full bg-bg-control-off px-0.5',
          'data-checked:bg-bg-brand data-disabled:bg-bg-disabled data-disabled:data-checked:bg-bg-brand-subtle',
          FOCUS_RING,
        )}
        {...props}
      >
        <BaseSwitch.Thumb className="size-5 rounded-full bg-bg-control-thumb shadow-sm transition-transform data-checked:translate-x-5 data-disabled:shadow-none data-disabled:not-data-checked:bg-bg-surface" />
      </BaseSwitch.Root>
    </label>
  )
}
