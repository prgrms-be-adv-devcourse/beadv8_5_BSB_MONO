'use client'

import {
  useEffect,
  useId,
  useRef,
  useState,
  type KeyboardEvent,
  type MouseEvent,
  type PointerEvent,
} from 'react'

import { cn } from '@/shared/lib'

const ROW = 44
const SETTLE_MS = 120
// 이만큼 움직이기 전까지는 끌기가 아니라 클릭으로 본다
const DRAG_THRESHOLD = 4

type Option = { value: number; label: string }

type WheelColumnProps = {
  label: string
  options: Option[]
  value: number
  onChange: (value: number) => void
  align: 'left' | 'right'
  className?: string
}

// 한 열(연도 또는 월). 브라우저 기본 스크롤에 칸마다 멈추는 scroll-snap을 걸고,
// 스크롤이 멈추면 가운데 줄의 값을 고른다. 키보드는 위·아래 화살표로 한 칸씩 옮긴다.
// 터치는 기본 스크롤로 굴러가지만 마우스는 끌어도 스크롤되지 않아서, 마우스 끌기만 직접 굴린다.
function WheelColumn({ label, options, value, onChange, align, className }: WheelColumnProps) {
  const ref = useRef<HTMLDivElement>(null)
  const settleTimer = useRef<ReturnType<typeof setTimeout>>(undefined)
  const mounted = useRef(false)
  const [dragging, setDragging] = useState(false)
  const draggingRef = useRef(false)
  const suppressClick = useRef(false)
  const stopDrag = useRef<() => void>(undefined)
  const id = useId()
  const index = Math.max(
    0,
    options.findIndex((o) => o.value === value),
  )

  // 값이 밖에서 바뀌면(클릭·키보드·다른 열 때문에 범위가 바뀜) 그 줄을 가운데로 맞춘다
  useEffect(() => {
    const el = ref.current
    if (el && Math.round(el.scrollTop / ROW) !== index) {
      // 처음 열 때는 움직임 없이 바로 맞추고, 그다음부터 부드럽게 굴린다
      el.scrollTo?.({ top: index * ROW, behavior: mounted.current ? 'smooth' : 'instant' })
    }
    mounted.current = true
  }, [index])

  useEffect(
    () => () => {
      clearTimeout(settleTimer.current)
      stopDrag.current?.()
    },
    [],
  )

  function handleScroll() {
    // 끄는 동안에는 손을 뗄 때 값을 정한다
    if (draggingRef.current) {
      return
    }
    clearTimeout(settleTimer.current)
    settleTimer.current = setTimeout(() => {
      const el = ref.current
      if (!el) {
        return
      }
      const next =
        options[Math.min(options.length - 1, Math.max(0, Math.round(el.scrollTop / ROW)))]
      if (next && next.value !== value) {
        onChange(next.value)
      }
    }, SETTLE_MS)
  }

  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    const step = event.key === 'ArrowDown' ? 1 : event.key === 'ArrowUp' ? -1 : 0
    if (!step) {
      return
    }
    event.preventDefault()
    const next = options[index + step]
    if (next) {
      onChange(next.value)
    }
  }

  function handlePointerDown(event: PointerEvent<HTMLDivElement>) {
    if (event.pointerType !== 'mouse' || event.button !== 0) {
      return
    }
    const pointerId = event.pointerId
    const startY = event.clientY
    // 휠은 늘 지금 값 줄에 멈춰 있으므로 그 줄 위치에서 시작한다
    const startTop = index * ROW
    let moved = false

    function rowAt(clientY: number) {
      const top = startTop - (clientY - startY)
      return Math.min(options.length - 1, Math.max(0, Math.round(top / ROW)))
    }

    function move(e: globalThis.PointerEvent) {
      if (e.pointerId !== pointerId) {
        return
      }
      if (!moved && Math.abs(e.clientY - startY) < DRAG_THRESHOLD) {
        return
      }
      if (!moved) {
        moved = true
        draggingRef.current = true
        // 칸마다 멈추는 snap이 끄는 손을 따라오지 못하게 하므로 끄는 동안은 끈다
        setDragging(true)
      }
      if (ref.current) {
        ref.current.scrollTop = startTop - (e.clientY - startY)
      }
    }

    function end(e: globalThis.PointerEvent) {
      if (e.pointerId !== pointerId) {
        return
      }
      stop()
      if (!moved) {
        return
      }
      draggingRef.current = false
      setDragging(false)
      const next = rowAt(e.clientY)
      // 값이 그대로면 index가 안 바뀌어 다시 맞추지 않으므로 여기서 가운데로 돌려놓는다
      ref.current?.scrollTo?.({ top: next * ROW, behavior: 'smooth' })
      if (options[next].value !== value) {
        onChange(options[next].value)
      }
      // 손을 뗀 줄에 이어서 오는 click이 그 줄을 또 고르지 않게 한 번 막는다
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

  function handleClickCapture(event: MouseEvent<HTMLDivElement>) {
    if (suppressClick.current) {
      suppressClick.current = false
      event.stopPropagation()
    }
  }

  return (
    <div
      ref={ref}
      role="listbox"
      aria-label={label}
      aria-activedescendant={`${id}-${value}`}
      tabIndex={0}
      onScroll={handleScroll}
      onKeyDown={handleKeyDown}
      onPointerDown={handlePointerDown}
      onClickCapture={handleClickCapture}
      className={cn(
        'h-55 cursor-grab [scrollbar-width:none] overflow-y-auto py-22 outline-none focus-visible:rounded-md focus-visible:inset-ring-2 focus-visible:inset-ring-border-focus [&::-webkit-scrollbar]:hidden',
        dragging ? 'cursor-grabbing snap-none select-none' : 'snap-y snap-mandatory',
        className,
      )}
    >
      {options.map((option, i) => {
        const distance = Math.abs(i - index)
        return (
          <div
            key={option.value}
            id={`${id}-${option.value}`}
            role="option"
            aria-selected={distance === 0}
            onClick={() => onChange(option.value)}
            className={cn(
              'flex h-11 snap-center items-center text-heading-l select-none',
              align === 'right' ? 'justify-end' : 'justify-start',
              distance === 0 ? 'text-text-primary' : 'font-medium text-text-disabled',
              distance >= 2 && 'opacity-50',
            )}
          >
            {option.label}
          </div>
        )
      })}
    </div>
  )
}

export type MonthYearWheelProps = {
  /** 지금 보고 있는 달(1일) */
  month: Date
  onMonthChange: (month: Date) => void
  /** 고를 수 있는 첫 달·마지막 달. 이 밖의 연·월은 목록에 넣지 않는다. */
  startMonth: Date
  endMonth: Date
  className?: string
}

// 피그마 DatePicker/MonthYearWheel. 높이 220(5줄), 가운데 회색 줄이 지금 고른 연·월.
export function MonthYearWheel({
  month,
  onMonthChange,
  startMonth,
  endMonth,
  className,
}: MonthYearWheelProps) {
  const year = month.getFullYear()
  const firstYear = startMonth.getFullYear()
  const lastYear = endMonth.getFullYear()
  const years = Array.from({ length: lastYear - firstYear + 1 }, (_, i) => ({
    value: firstYear + i,
    label: `${firstYear + i}년`,
  }))
  const firstMonth = year === firstYear ? startMonth.getMonth() : 0
  const lastMonth = year === lastYear ? endMonth.getMonth() : 11
  const months = Array.from({ length: lastMonth - firstMonth + 1 }, (_, i) => ({
    value: firstMonth + i,
    label: `${firstMonth + i + 1}월`,
  }))

  function change(nextYear: number, nextMonth: number) {
    // 연도를 바꿨을 때 그해에 없는 달이면 가장 가까운 달로 맞춘다
    const min = nextYear === firstYear ? startMonth.getMonth() : 0
    const max = nextYear === lastYear ? endMonth.getMonth() : 11
    onMonthChange(new Date(nextYear, Math.min(max, Math.max(min, nextMonth)), 1))
  }

  return (
    <div className={cn('relative h-55', className)}>
      <div aria-hidden className="absolute inset-x-0 top-22 h-11 rounded-md bg-bg-subtle" />
      <div className="relative flex justify-center gap-8">
        <WheelColumn
          label="연도"
          options={years}
          value={year}
          onChange={(y) => change(y, month.getMonth())}
          align="right"
          className="w-22"
        />
        <WheelColumn
          label="월"
          options={months}
          value={month.getMonth()}
          onChange={(m) => change(year, m)}
          align="left"
          className="w-16"
        />
      </div>
    </div>
  )
}
