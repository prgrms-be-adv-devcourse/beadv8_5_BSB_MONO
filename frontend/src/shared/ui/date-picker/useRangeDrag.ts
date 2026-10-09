'use client'

import { isSameDay } from 'date-fns'
import { useEffect, useRef, useState, type MouseEvent, type PointerEvent } from 'react'

import { fromIsoDate } from '@/shared/lib'

import { dragRange, type DateRange } from './selection'

/** 고를 수 있는 날짜 칸(DayCell)이면 그 날짜를 돌려준다. */
function dayOf(target: EventTarget | null | undefined): Date | undefined {
  const button = target instanceof Element ? target.closest('button[data-day]') : null
  if (!(button instanceof HTMLButtonElement) || button.disabled || !button.dataset.day) {
    return undefined
  }
  return fromIsoDate(button.dataset.day)
}

type Options = {
  /** 기간 모드이고 읽기 전용이 아닐 때만 켠다. */
  enabled: boolean
  onCommit: (range: DateRange) => void
}

// 기간 모드에서 날짜를 누른 채 끌면 누른 날부터 놓은 날까지를 기간으로 고른다.
// 움직이지 않고 떼면 아무것도 하지 않고, 원래 click(nextRange)이 두 번 눌러 고르는 방식을 그대로 처리한다.
// 터치는 누른 칸이 포인터를 붙잡아서(implicit capture) 이벤트가 그 칸으로만 오므로, 좌표로 손가락 아래 칸을 찾는다.
export function useRangeDrag({ enabled, onCommit }: Options) {
  // 끄는 동안 보여 줄 기간. 놓을 때 onCommit으로 확정하고 지운다.
  const [preview, setPreview] = useState<DateRange>()
  const suppressClick = useRef(false)
  const stopDrag = useRef<() => void>(undefined)

  useEffect(() => () => stopDrag.current?.(), [])

  function onPointerDown(event: PointerEvent<HTMLElement>) {
    if (!enabled || event.button !== 0) {
      return
    }
    const anchor = dayOf(event.target)
    if (!anchor) {
      return
    }
    // 바텀시트 안에서 끌 때 시트가 같이 끌려 내려가지 않게 한다
    event.stopPropagation()
    const pointerId = event.pointerId
    let range: DateRange | undefined

    function move(e: globalThis.PointerEvent) {
      if (e.pointerId !== pointerId) {
        return
      }
      // 고를 수 없는 날 위에서는 직전 기간을 유지한다
      const date = dayOf(document.elementFromPoint?.(e.clientX, e.clientY))
      if (!date || (!range && isSameDay(date, anchor!))) {
        return
      }
      range = dragRange(anchor!, date)
      setPreview(range)
    }

    function end(e: globalThis.PointerEvent) {
      if (e.pointerId !== pointerId) {
        return
      }
      stop()
      setPreview(undefined)
      // 스크롤 등으로 브라우저가 끌기를 가로채면(pointercancel) 고르지 않은 것으로 한다
      if (!range || e.type === 'pointercancel') {
        return
      }
      onCommit(range)
      // 손을 뗀 칸에 이어서 오는 click이 nextRange로 값을 또 바꾸지 않게 한 번 막는다.
      // click이 오지 않는 경우(칸 밖에서 뗌)에 다음 진짜 click을 막지 않도록 바로 푼다.
      suppressClick.current = true
      setTimeout(() => {
        suppressClick.current = false
      })
    }

    function stop() {
      window.removeEventListener('pointermove', move)
      window.removeEventListener('pointerup', end)
      window.removeEventListener('pointercancel', end)
      stopDrag.current = undefined
    }

    stopDrag.current?.()
    stopDrag.current = stop
    window.addEventListener('pointermove', move)
    window.addEventListener('pointerup', end)
    window.addEventListener('pointercancel', end)
  }

  function onClickCapture(event: MouseEvent<HTMLElement>) {
    if (suppressClick.current) {
      suppressClick.current = false
      event.stopPropagation()
      event.preventDefault()
    }
  }

  return { preview, handlers: enabled ? { onPointerDown, onClickCapture } : {} }
}
