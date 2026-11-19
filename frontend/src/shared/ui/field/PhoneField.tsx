'use client'

import { useRef, type ChangeEvent } from 'react'

import { formatPhoneInput } from '@/shared/lib'

import { TextField, type TextFieldProps } from './TextField'

export type PhoneFieldProps = Omit<TextFieldProps, 'type' | 'inputMode'>

// 휴대폰 번호 칸. 숫자만 쳐도 010-1234-5678처럼 하이픈을 넣는다.
// react-hook-form의 register처럼 제어하지 않는 입력에 맞춰, 칸의 값을 직접 바꾼 뒤 onChange를 넘긴다.
export function PhoneField({ onChange, onFocus, onKeyDown, ...props }: PhoneFieldProps) {
  // 하이픈만 지웠는지 알려면 바뀌기 전 값이 필요하다. 키를 누르기 직전 값을 적어 둔다.
  const before = useRef('')

  function handleChange(event: ChangeEvent<HTMLInputElement>) {
    const input = event.currentTarget
    const inputType = (event.nativeEvent as InputEvent).inputType
    const next = formatPhoneInput(
      before.current,
      input.value,
      input.selectionStart ?? input.value.length,
      inputType,
    )
    input.value = next.value
    input.setSelectionRange(next.caret, next.caret)
    before.current = next.value
    onChange?.(event)
  }

  return (
    <TextField
      // 하이픈·공백·+(국가번호)는 붙여넣기로 들어올 수 있어 허용하고, 그 밖의 글자는 막는다
      filter={(value) => value.replace(/[^\d\s+-]/g, '')}
      filterMessage="숫자만 입력할 수 있어요"
      type="tel"
      inputMode="numeric"
      autoComplete="tel"
      onFocus={(event) => {
        before.current = event.currentTarget.value
        onFocus?.(event)
      }}
      onKeyDown={(event) => {
        before.current = event.currentTarget.value
        onKeyDown?.(event)
      }}
      onChange={handleChange}
      {...props}
    />
  )
}
