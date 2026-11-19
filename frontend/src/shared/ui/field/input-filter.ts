'use client'

import { useState, type ChangeEvent, type CompositionEvent } from 'react'

/** 숫자가 아닌 글자를 모두 뺀다. TextField의 filter로 쓴다. */
export const digitsOnly = (value: string) => value.replace(/\D/g, '')

export const NUMERIC_NOTICE = '숫자만 입력할 수 있어요'

/**
 * 입력 칸이 막은 글자를 알려 주는 훅. 칸의 onChange 맨 앞에서 apply(event)를, onCompositionEnd에서 finish(event)를 부른다.
 * filter를 거치며 글자가 빠지면 칸 값을 걸러진 값으로 되돌리고 notice에 message를 둔다.
 * 다음 입력에서 막힌 글자가 없으면 notice는 사라진다.
 * 한글 조합 중에는 거르지 않는다(조합 중인 글자를 지우면 입력이 깨진다). 조합이 끝날 때 한 번 거른다.
 */
export function useInputFilter(filter: ((value: string) => string) | undefined, message: string) {
  const [blocked, setBlocked] = useState(false)

  function run(input: HTMLInputElement, notify = false) {
    if (!filter) {
      return false
    }
    const raw = input.value
    const filtered = filter(raw)
    if (filtered === raw) {
      setBlocked(false)
      return false
    }
    // 커서 앞부분에서 빠진 글자 수만 센다. 커서 뒤에서 잘린 글자는 커서에 영향이 없다
    const start = input.selectionStart ?? raw.length
    const caret = filter(raw.slice(0, start)).length
    if (notify) {
      // React가 값 바뀜으로 읽도록 추적값을 건드리지 않는 원래 setter로 쓰고, 폼에 input 이벤트를 다시 알린다
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set?.call(
        input,
        filtered,
      )
      input.setSelectionRange(caret, caret)
      input.dispatchEvent(new Event('input', { bubbles: true }))
    } else {
      input.value = filtered
      input.setSelectionRange(caret, caret)
    }
    setBlocked(true)
    return true
  }

  function apply(event: ChangeEvent<HTMLInputElement>) {
    if ((event.nativeEvent as InputEvent).isComposing) {
      return
    }
    run(event.currentTarget)
  }

  function finish(event: CompositionEvent<HTMLInputElement>) {
    run(event.currentTarget, true)
  }

  return { notice: blocked ? message : undefined, apply, finish }
}
