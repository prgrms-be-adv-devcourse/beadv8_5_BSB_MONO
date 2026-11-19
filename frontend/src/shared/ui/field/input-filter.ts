'use client'

import { useState, type ChangeEvent } from 'react'

/** 숫자가 아닌 글자를 모두 뺀다. TextField의 filter로 쓴다. */
export const digitsOnly = (value: string) => value.replace(/\D/g, '')

export const NUMERIC_NOTICE = '숫자만 입력할 수 있어요'

/**
 * 입력 칸이 막은 글자를 알려 주는 훅. 칸의 onChange 맨 앞에서 apply(event)를 부른다.
 * filter를 거치며 글자가 빠지면 칸 값을 걸러진 값으로 되돌리고 notice에 message를 둔다.
 * 다음 입력에서 막힌 글자가 없으면 notice는 사라진다.
 */
export function useInputFilter(filter: ((value: string) => string) | undefined, message: string) {
  const [blocked, setBlocked] = useState(false)

  function apply(event: ChangeEvent<HTMLInputElement>) {
    if (!filter) {
      return
    }
    const input = event.currentTarget
    const raw = input.value
    const filtered = filter(raw)
    if (filtered === raw) {
      setBlocked(false)
      return
    }
    const caret = Math.max(0, (input.selectionStart ?? raw.length) - (raw.length - filtered.length))
    input.value = filtered
    input.setSelectionRange(caret, caret)
    setBlocked(true)
  }

  return { notice: blocked ? message : undefined, apply }
}
