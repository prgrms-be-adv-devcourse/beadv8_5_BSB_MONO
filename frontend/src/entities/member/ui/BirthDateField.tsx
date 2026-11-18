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
  /** false면 만 14세 제한 없이 1900년 1월 1일부터 오늘까지 고른다. 참가자 칸처럼 나이를 서버가 보는 곳에서 끈다 (기본 true) */
  restrictAge?: boolean
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
  restrictAge = true,
}: BirthDateFieldProps) {
  const inputRef = useRef<HTMLInputElement | null>(null)
  const triggerRef = useRef<HTMLButtonElement | null>(null)
  // 처음 값(숨은 input의 DOM 값)과 오늘 날짜는 렌더 중에 읽을 수 없어 마운트 뒤 한 번 함께 읽는다
  const [mounted, setMounted] = useState<{ initial: string; today: Date }>()
  useEffect(() => {
    setMounted({ initial: inputRef.current?.value ?? '', today: new Date() })
  }, [])
  const initial = mounted?.initial ?? ''

  const latest =
    mounted &&
    (restrictAge
      ? (parseBirth(latestJoinableBirth(mounted.today)) ?? mounted.today)
      : mounted.today)
  const initialDate = parseBirth(initial) ?? undefined

  return (
    <>
      <input
        type="hidden"
        name={name}
        ref={(element) => {
          inputRef.current = element
          // 폼이 오류 칸으로 포커스를 옮길 때 숨은 input 대신 날짜 열기 버튼으로 간다
          if (element) element.focus = () => triggerRef.current?.focus()
          if (typeof ref === 'function') ref(element)
          else if (ref) ref.current = element
        }}
      />
      <DateTimeField
        // 기본값을 읽은 뒤 달력을 다시 그려 처음 값으로 보여 준다
        key={initial}
        ref={triggerRef}
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
        isDateDisabled={
          restrictAge ? (date) => !isJoinableAge(date) : (date) => !!mounted && date > mounted.today
        }
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
