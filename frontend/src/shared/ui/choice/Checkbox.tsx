'use client'

import { Checkbox as BaseCheckbox } from '@base-ui/react/checkbox'
import type { ReactNode } from 'react'

import { cn } from '@/shared/lib'

import { FOCUS_RING } from '../focus-ring'
import { Icon } from '../icon/icons'

export type CheckboxProps = Omit<BaseCheckbox.Root.Props, 'children' | 'className'> & {
  label?: ReactNode
  className?: string
}

// 여러 개를 고르거나 동의 여부를 체크한다. 상자 20. 하위 항목이 일부만 체크됐으면 indeterminate.
// 테두리 1.5는 피그마 값 그대로다.
export function Checkbox({ label, className, ...props }: CheckboxProps) {
  return (
    <label
      className={cn(
        'inline-flex items-center gap-2 text-body-m text-text-primary has-data-disabled:cursor-not-allowed has-data-disabled:text-text-disabled',
        className,
      )}
    >
      <BaseCheckbox.Root
        className={cn(
          'flex size-5 shrink-0 items-center justify-center rounded-sm bg-bg-surface text-text-on-brand inset-ring-[1.5px] inset-ring-border-strong',
          'data-checked:bg-bg-brand data-checked:inset-ring-0 data-indeterminate:bg-bg-brand data-indeterminate:inset-ring-0',
          'data-disabled:bg-bg-disabled data-disabled:text-text-disabled data-disabled:inset-ring-border-disabled',
          FOCUS_RING,
        )}
        {...props}
      >
        <BaseCheckbox.Indicator
          className="flex data-unchecked:hidden"
          render={(indicatorProps, state) => (
            <span {...indicatorProps}>
              <Icon name={state.indeterminate ? 'Minus' : 'Check'} className="size-4" />
            </span>
          )}
        />
      </BaseCheckbox.Root>
      {label}
    </label>
  )
}
