'use client'

import { Field } from '@base-ui/react/field'
import { useState, type ChangeEvent, type ComponentProps, type ReactNode } from 'react'

import { cn } from '@/shared/lib'

import { FIELD_BOX, FIELD_BOX_FOCUS, FIELD_HELPER, FIELD_LABEL, FIELD_ROOT } from './field-styles'

export type TextareaProps = Omit<ComponentProps<'textarea'>, 'children'> & {
  label?: ReactNode
  helper?: ReactNode
  error?: ReactNode
  /** maxLength와 함께 쓰면 오른쪽 아래에 '글자 수/최대'를 보여 준다. */
  showCounter?: boolean
}

// 여러 줄 입력. 입력 영역 높이 120.
export function Textarea({
  label,
  helper,
  error,
  showCounter = false,
  disabled,
  maxLength,
  value,
  defaultValue,
  onChange,
  className,
  ...props
}: TextareaProps) {
  const [uncontrolledLength, setUncontrolledLength] = useState(String(defaultValue ?? '').length)
  const length = value === undefined ? uncontrolledLength : String(value).length
  const description = error ?? helper

  function handleChange(event: ChangeEvent<HTMLTextAreaElement>) {
    setUncontrolledLength(event.target.value.length)
    onChange?.(event)
  }

  return (
    <Field.Root invalid={Boolean(error)} disabled={disabled} className={cn(FIELD_ROOT, className)}>
      {label && <Field.Label className={FIELD_LABEL}>{label}</Field.Label>}
      <Field.Control
        render={
          <textarea
            maxLength={maxLength}
            value={value}
            defaultValue={defaultValue}
            onChange={handleChange}
            className={cn(FIELD_BOX, FIELD_BOX_FOCUS, 'h-30 resize-none')}
            {...props}
          />
        }
      />
      {(description || showCounter) && (
        <div className="flex justify-between gap-2">
          <Field.Description className={FIELD_HELPER}>{description}</Field.Description>
          {showCounter && maxLength !== undefined && (
            <span className={cn(FIELD_HELPER, 'shrink-0')}>
              {length}/{maxLength}
            </span>
          )}
        </div>
      )}
    </Field.Root>
  )
}
