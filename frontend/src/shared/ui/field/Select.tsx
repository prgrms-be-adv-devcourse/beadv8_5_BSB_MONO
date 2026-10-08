'use client'

import { Field } from '@base-ui/react/field'
import { Select as BaseSelect } from '@base-ui/react/select'
import type { ReactNode } from 'react'

import { cn } from '@/shared/lib'

import { Icon } from '../icon/icons'
import { FIELD_BOX, FIELD_HELPER, FIELD_LABEL, FIELD_ROOT } from './field-styles'

export type SelectItem<Value extends string> = {
  value: Value
  label: ReactNode
  disabled?: boolean
}

export type SelectProps<Value extends string> = {
  items: SelectItem<Value>[]
  label?: ReactNode
  helper?: ReactNode
  error?: ReactNode
  placeholder?: ReactNode
  value?: Value | null
  defaultValue?: Value | null
  onValueChange?: (value: Value | null) => void
  name?: string
  disabled?: boolean
  className?: string
}

// Select/Option 한 줄. BottomSheet 안 선택지도 같은 모양을 쓴다.
export const OPTION =
  'flex h-11 w-full cursor-default items-center gap-2 rounded-sm px-4 py-3 text-left text-body-m text-text-primary outline-none select-none'

// 하나를 고르는 필드. 높이 46. 선택지가 4개 이하라면 Chip이나 Radio를 쓴다.
export function Select<Value extends string>({
  items,
  label,
  helper,
  error,
  placeholder,
  value,
  defaultValue,
  onValueChange,
  name,
  disabled,
  className,
}: SelectProps<Value>) {
  const description = error ?? helper
  return (
    <Field.Root
      invalid={Boolean(error)}
      disabled={disabled}
      name={name}
      className={cn(FIELD_ROOT, className)}
    >
      {label && <Field.Label className={FIELD_LABEL}>{label}</Field.Label>}
      <BaseSelect.Root
        items={items}
        value={value}
        defaultValue={defaultValue}
        onValueChange={(next) => onValueChange?.(next as Value | null)}
      >
        <BaseSelect.Trigger
          className={cn(
            FIELD_BOX,
            'group/trigger flex items-center gap-2 data-popup-open:inset-ring-2 data-popup-open:inset-ring-border-focus',
          )}
        >
          <BaseSelect.Value
            placeholder={placeholder}
            className="min-w-0 flex-1 truncate text-left data-placeholder:text-text-tertiary"
          />
          <BaseSelect.Icon className="flex">
            <Icon
              name="ChevronDown"
              className="size-5 text-text-tertiary group-data-disabled:text-text-disabled group-data-popup-open/trigger:rotate-180 group-data-popup-open/trigger:text-text-brand"
            />
          </BaseSelect.Icon>
        </BaseSelect.Trigger>
        <BaseSelect.Portal>
          <BaseSelect.Positioner
            alignItemWithTrigger={false}
            sideOffset={4}
            className="z-50 outline-none"
          >
            <BaseSelect.Popup className="w-(--anchor-width) rounded-md bg-bg-surface p-1 shadow-md inset-ring inset-ring-border-default outline-none">
              <BaseSelect.List className="max-h-(--available-height) overflow-y-auto">
                {items.map((item) => (
                  <BaseSelect.Item
                    key={item.value}
                    value={item.value}
                    disabled={item.disabled}
                    className={cn(
                      OPTION,
                      'data-selected:bg-bg-brand-subtle data-selected:text-text-brand',
                      'data-disabled:text-text-disabled data-highlighted:bg-bg-subtle',
                    )}
                  >
                    <BaseSelect.ItemText className="min-w-0 flex-1 truncate">
                      {item.label}
                    </BaseSelect.ItemText>
                    <BaseSelect.ItemIndicator className="flex">
                      <Icon name="Check" className="size-5" />
                    </BaseSelect.ItemIndicator>
                  </BaseSelect.Item>
                ))}
              </BaseSelect.List>
            </BaseSelect.Popup>
          </BaseSelect.Positioner>
        </BaseSelect.Portal>
      </BaseSelect.Root>
      {description && <Field.Description className={FIELD_HELPER}>{description}</Field.Description>}
    </Field.Root>
  )
}
