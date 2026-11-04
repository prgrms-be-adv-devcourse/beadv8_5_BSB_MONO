'use client'

import { useEffect, useRef, useState, type ReactNode, type Ref } from 'react'

import { DateTimeField } from '@/shared/date-picker'

import { BIRTH_MIN, isJoinableAge, latestJoinableBirth, parseBirth } from '../model/birth'

type FieldEvent = { target: { name: string; value: string } }

export type BirthDateFieldProps = {
  label?: ReactNode
  helper?: ReactNode
  error?: ReactNode
  disabled?: boolean
  /** react-hook-form register()가 주는 값. {...register('birth')}로 그대로 넘긴다 */
  name: string
  onChange: (event: FieldEvent) => unknown
  onBlur?: (event: FieldEvent) => unknown
  ref?: Ref<HTMLInputElement>
}

// 생년월일 칸. 공용 달력(DateTimeField)을 열어 연·월 휠로 해를 고른 뒤 날짜를 누른다.
// 값은 "YYYY-MM-DD"로 API birthDate와 같다. 고를 수 있는 날은 1900-01-01부터 만 14세가 되는 날까지다.
// register()를 그대로 받으려고 숨은 input에 ref를 건다. 폼의 기본값(정보 수정 화면)은 그 input에 들어오므로
// 처음 그린 뒤 한 번 읽어 달력의 처음 값으로 쓴다. 만 14세 검사는 폼 스키마가 그대로 한다.
export function BirthDateField({
  label,
  helper,
  error,
  disabled,
  name,
  onChange,
  onBlur,
  ref,
}: BirthDateFieldProps) {
  const inputRef = useRef<HTMLInputElement | null>(null)
  const [initial, setInitial] = useState('')
  useEffect(() => {
    setInitial(inputRef.current?.value ?? '')
  }, [])

  const latest = parseBirth(latestJoinableBirth()) ?? new Date()
  const initialDate = parseBirth(initial) ?? undefined

  return (
    <>
      <input
        type="hidden"
        name={name}
        ref={(element) => {
          inputRef.current = element
          if (typeof ref === 'function') ref(element)
          else if (ref) ref.current = element
        }}
      />
      <DateTimeField
        // 기본값을 읽은 뒤 달력을 다시 그려 처음 값으로 보여 준다
        key={initial}
        mode="single"
        withTime={false}
        label={label}
        helper={helper}
        error={error}
        disabled={disabled}
        placeholder="생년월일을 골라 주세요"
        defaultValue={initialDate}
        startMonth={parseBirth(BIRTH_MIN) ?? undefined}
        endMonth={latest}
        // 값이 없으면 고를 수 있는 마지막 달(만 14세가 되는 달)부터 보여 준다. 해는 연·월 휠로 바꾼다
        defaultMonth={initialDate ?? latest}
        isDateDisabled={(date) => !isJoinableAge(date)}
        onValueChange={(value) => {
          const event = { target: { name, value } }
          onChange(event)
          // 고르면 바로 검사하도록 칸을 떠난 것으로 알린다(폼 mode가 onTouched)
          onBlur?.(event)
        }}
      />
    </>
  )
}
