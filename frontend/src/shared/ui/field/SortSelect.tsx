'use client'

import { Select as BaseSelect } from '@base-ui/react/select'

import { cn } from '@/shared/lib'

import { FOCUS_RING } from '../focus-ring'
import { Icon } from '../icon/icons'
import { SelectPopup, type SelectItem } from './Select'

export type SortSelectProps<Value extends string> = {
  items: SelectItem<Value>[]
  value?: Value
  defaultValue?: Value
  onValueChange?: (value: Value) => void
  // 미리보기용: 처음부터 메뉴를 펼쳐 둔다
  defaultOpen?: boolean
  'aria-label'?: string
  className?: string
}

// 목록 정렬 드롭다운. 글자 버튼 아래 오른쪽 끝에 Select/Menu를 붙인다 (피그마 M18-1).
export function SortSelect<Value extends string>({
  items,
  value,
  defaultValue,
  onValueChange,
  defaultOpen,
  'aria-label': ariaLabel = '정렬',
  className,
}: SortSelectProps<Value>) {
  return (
    <BaseSelect.Root
      items={items}
      value={value}
      defaultValue={defaultValue ?? items[0]?.value}
      defaultOpen={defaultOpen}
      onValueChange={(next) => next !== null && onValueChange?.(next as Value)}
    >
      <BaseSelect.Trigger
        aria-label={ariaLabel}
        className={cn(
          'group/trigger flex items-center gap-1 rounded-sm text-body2-medium',
          FOCUS_RING,
          className,
        )}
      >
        <BaseSelect.Value />
        <Icon name="ChevronDown" className="size-4 group-data-popup-open/trigger:rotate-180" />
      </BaseSelect.Trigger>
      <SelectPopup items={items} align="end" className="w-42" />
    </BaseSelect.Root>
  )
}
