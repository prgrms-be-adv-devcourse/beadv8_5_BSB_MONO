'use client'

import { Field } from '@base-ui/react/field'
import type { ComponentProps, ReactNode } from 'react'

import { cn } from '@/shared/lib'

import { FIELD_BOX, FIELD_BOX_FOCUS, FIELD_HELPER, FIELD_LABEL, FIELD_ROOT } from './field-styles'
import { NUMERIC_NOTICE, useInputFilter } from './input-filter'

export type TextFieldProps = Omit<ComponentProps<'input'>, 'children'> & {
  label?: ReactNode
  /** 입력 아래 안내 문구 */
  helper?: ReactNode
  /** 있으면 오류 상태가 되고 helper 자리에 이 문구를 보여 준다. */
  error?: ReactNode
  /** 입력값에서 허용하지 않는 글자를 뺀 값을 돌려준다(예: digitsOnly). 글자가 빠지면 filterMessage를 칸 아래에 보여 준다. */
  filter?: (value: string) => string
  /** filter가 글자를 막았을 때 보여 줄 이유. 오류 문구가 있으면 오류가 우선이다. */
  filterMessage?: string
  /** true면 오류여도 빨간 2px 테두리를 그리지 않는다(오류 문구만 보인다). 로그인 화면 시안용 */
  hideErrorBorder?: boolean
  /** true면 높이 40의 작은 칸(표 안·목록 검색). 기본은 52 */
  compact?: boolean
  /** 입력 칸 앞에 놓는 아이콘(예: 검색 돋보기 `<Icon name="Search" />`). 칸 안쪽 왼쪽에 겹쳐 그린다. */
  leading?: ReactNode
  /** 입력 칸 안쪽 오른쪽에 놓는 단위(예: 명 · 원 · 분). 글자는 오른쪽 정렬하고 화면 낭독기는 읽지 않는다 */
  trailing?: ReactNode
}

// 한 줄 입력. 높이 52.
export function TextField({
  label,
  helper,
  error,
  filter,
  filterMessage = NUMERIC_NOTICE,
  compact = false,
  hideErrorBorder = false,
  leading,
  trailing,
  disabled,
  className,
  onChange,
  onCompositionEnd,
  ...props
}: TextFieldProps) {
  const { notice, apply, finish } = useInputFilter(filter, filterMessage)
  const shownNotice = error ? undefined : notice
  const description = error ?? shownNotice ?? helper
  return (
    <Field.Root invalid={Boolean(error)} disabled={disabled} className={cn(FIELD_ROOT, className)}>
      {label && <Field.Label className={FIELD_LABEL}>{label}</Field.Label>}
      <div className="relative">
        <Field.Control
          className={cn(
            FIELD_BOX,
            FIELD_BOX_FOCUS,
            compact && 'min-h-10 py-2',
            hideErrorBorder &&
              'group-data-invalid:inset-ring-1 group-data-invalid:inset-ring-border-strong',
            leading && 'pl-11',
            trailing && 'pr-12 text-right',
          )}
          onChange={(event) => {
            apply(event)
            onChange?.(event)
          }}
          onCompositionEnd={(event) => {
            finish(event)
            onCompositionEnd?.(event)
          }}
          {...props}
        />
        {trailing && (
          <span
            aria-hidden
            className="pointer-events-none absolute inset-y-0 right-4 flex items-center text-body1-regular text-text-secondary"
          >
            {trailing}
          </span>
        )}
        {leading && (
          <span className="pointer-events-none absolute inset-y-0 left-4 flex items-center text-text-primary [&_svg]:size-5">
            {leading}
          </span>
        )}
      </div>
      {/* 안내가 새로 생길 때 스크린리더가 읽도록 늘 있는 알림 자리 */}
      <span role="status" className="sr-only">
        {shownNotice}
      </span>
      {description && <Field.Description className={FIELD_HELPER}>{description}</Field.Description>}
    </Field.Root>
  )
}
