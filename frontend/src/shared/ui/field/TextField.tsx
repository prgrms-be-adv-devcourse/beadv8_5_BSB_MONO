'use client'

import { Field } from '@base-ui/react/field'
import type { ComponentProps, ReactNode } from 'react'

import { cn } from '@/shared/lib'

import { FIELD_BOX, FIELD_BOX_FOCUS, FIELD_HELPER, FIELD_LABEL, FIELD_ROOT } from './field-styles'

export type TextFieldProps = Omit<ComponentProps<'input'>, 'children'> & {
  label?: ReactNode
  /** 입력 아래 안내 문구 */
  helper?: ReactNode
  /** 있으면 오류 상태가 되고 helper 자리에 이 문구를 보여 준다. */
  error?: ReactNode
}

// 한 줄 입력. 높이 46.
export function TextField({ label, helper, error, disabled, className, ...props }: TextFieldProps) {
  const description = error ?? helper
  return (
    <Field.Root invalid={Boolean(error)} disabled={disabled} className={cn(FIELD_ROOT, className)}>
      {label && <Field.Label className={FIELD_LABEL}>{label}</Field.Label>}
      <Field.Control className={cn(FIELD_BOX, FIELD_BOX_FOCUS)} {...props} />
      {description && <Field.Description className={FIELD_HELPER}>{description}</Field.Description>}
    </Field.Root>
  )
}
