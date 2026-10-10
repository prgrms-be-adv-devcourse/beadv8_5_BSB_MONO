'use client'

import {
  addDays,
  addMonths,
  addWeeks,
  addYears,
  endOfMonth,
  format,
  isAfter,
  isBefore,
  isSameDay,
  isToday,
  startOfMonth,
  startOfWeek,
} from 'date-fns'
import { ko } from 'date-fns/locale'
import {
  createContext,
  useContext,
  useEffect,
  useRef,
  useState,
  type KeyboardEvent,
  type ReactNode,
} from 'react'
import { DayPicker, type DayButtonProps } from 'react-day-picker'
import { ko as dayPickerKo } from 'react-day-picker/locale'

import { cn } from '@/shared/lib'

import { Button } from '../button/Button'
import { IconButton } from '../button/IconButton'
import { FOCUS_RING } from '../focus-ring'
import { Icon } from '../icon/icons'
import { DayCell, type DayCellState } from './DayCell'
import { MonthYearWheel } from './MonthYearWheel'
import {
  nextRange,
  selectionText,
  toggleDate,
  type DatePickerSelection,
  type DateRange,
} from './selection'
import { useRangeDrag } from './useRangeDrag'

// 한국은 일요일이 주 시작이다.
const WEEK_STARTS_ON = 0
// 범위를 주지 않았을 때 연·월 휠에 넣는 기간(오늘 기준 앞뒤 10년)
const DEFAULT_YEARS = 10

type CommonProps = {
  /** month(한 달) / week(한 주, 좁은 자리) */
  view?: 'month' | 'week'
  /** 고를 수 없는 날(지난 날·접수 불가일). 흐린 글자에 취소선으로 보여 준다. */
  isDateDisabled?: (date: Date) => boolean
  /** 고를 수 있는 첫 달·마지막 달. 이전·다음 버튼과 연·월 휠이 이 범위를 넘지 않는다. */
  startMonth?: Date
  endMonth?: Date
  /** 이미 확정돼 바꿀 수 없는 값. 고른 날을 톤 낮춘 코발트로 보여 주고 눌러도 반응하지 않는다. */
  readOnly?: boolean
  /** 처음 보여 줄 달. 없으면 고른 날, 그것도 없으면 오늘이 있는 달 */
  defaultMonth?: Date
  /**
   * 팝오버·시트 안에서는 하단 영역을 켜고 '선택 완료'로 확정한다.
   * 화면 안(Inline)에서는 넘기지 않고, 누르는 즉시 onSelect로 반영한다.
   */
  footer?: { onConfirm: () => void; confirmLabel?: ReactNode }
  className?: string
}

export type DatePickerProps = CommonProps &
  (
    | {
        mode: 'single'
        selected?: Date
        defaultSelected?: Date
        onSelect?: (date: Date) => void
      }
    | {
        mode: 'range'
        selected?: DateRange
        defaultSelected?: DateRange
        onSelect?: (range: DateRange) => void
      }
    | {
        mode: 'multiple'
        selected?: Date[]
        defaultSelected?: Date[]
        onSelect?: (dates: Date[]) => void
      }
  )

function firstSelectedDate(selection: DatePickerSelection): Date | undefined {
  switch (selection.mode) {
    case 'single':
      return selection.selected
    case 'range':
      return selection.selected?.from
    case 'multiple':
      return selection.selected?.[0]
  }
}

function cellState(selection: DatePickerSelection, date: Date): DayCellState {
  switch (selection.mode) {
    case 'single':
      return { selected: Boolean(selection.selected && isSameDay(selection.selected, date)) }
    case 'multiple':
      return { selected: Boolean(selection.selected?.some((d) => isSameDay(d, date))) }
    case 'range': {
      const { from, to } = selection.selected ?? {}
      const rangeStart = Boolean(from && isSameDay(from, date))
      const rangeEnd = Boolean(to ? isSameDay(to, date) : rangeStart)
      const rangeMiddle = Boolean(from && to && isAfter(date, from) && isBefore(date, to))
      return { selected: rangeStart || rangeEnd || rangeMiddle, rangeStart, rangeEnd, rangeMiddle }
    }
  }
}

// DayPicker의 날짜 버튼을 시안 DayCell로 바꿔 끼운다. components에는 매번 같은 함수를 넘겨야
// 키보드로 옮길 때 버튼이 다시 만들어지지 않으므로, readOnly는 context로 받는다.
const ReadOnlyContext = createContext(false)

function PickerDayButton({ day, modifiers, ...props }: DayButtonProps) {
  const readOnly = useContext(ReadOnlyContext)
  const ref = useRef<HTMLButtonElement>(null)
  useEffect(() => {
    if (modifiers.focused) {
      ref.current?.focus()
    }
  }, [modifiers.focused])
  return (
    <DayCell
      ref={ref}
      date={day.date}
      selected={modifiers.selected}
      rangeStart={modifiers.range_start}
      rangeEnd={modifiers.range_end}
      rangeMiddle={modifiers.range_middle}
      today={modifiers.today}
      disabled={modifiers.disabled}
      readOnly={readOnly && modifiers.selected}
      {...props}
    />
  )
}

const DAY_PICKER_CLASSES = {
  root: 'w-full',
  months: 'w-full',
  month: 'w-full',
  month_caption: 'sr-only',
  month_grid: 'w-full table-fixed border-collapse',
  // 요일 줄 32 + 날짜 칸과의 간격 8
  weekday: 'h-10 pb-2 text-label-m text-text-tertiary',
  day: 'p-0',
}

type HeaderProps = {
  label: string
  wheelOpen: boolean
  onToggleWheel: () => void
  onPrev: () => void
  onNext: () => void
  prevDisabled: boolean
  nextDisabled: boolean
  prevLabel: string
  nextLabel: string
}

// 피그마 DatePicker/Header. 연·월을 누르면 휠이 열리고, 고를 수 있는 첫 달에서는 이전 버튼을 막는다.
// 모서리 8은 피그마 Radius 변수에 없는 값이라 그대로 적었다.
function Header({
  label,
  wheelOpen,
  onToggleWheel,
  onPrev,
  onNext,
  prevDisabled,
  nextDisabled,
  prevLabel,
  nextLabel,
}: HeaderProps) {
  return (
    <div className="flex items-center justify-between">
      <button
        type="button"
        aria-expanded={wheelOpen}
        aria-label={`${label}, 연·월 고르기`}
        onClick={onToggleWheel}
        className={cn(
          'flex items-center gap-1 rounded-[8px] px-1 py-2.5 text-heading-m text-text-primary',
          FOCUS_RING,
        )}
      >
        <span aria-live="polite">{label}</span>
        <Icon name="ChevronDown" className={cn('size-5', wheelOpen && 'rotate-180')} />
      </button>
      <div className="flex gap-1">
        <IconButton aria-label={prevLabel} disabled={prevDisabled} onClick={onPrev}>
          <Icon name="ChevronLeft" />
        </IconButton>
        <IconButton aria-label={nextLabel} disabled={nextDisabled} onClick={onNext}>
          <Icon name="ChevronRight" />
        </IconButton>
      </div>
    </div>
  )
}

type WeekRowProps = {
  weekStart: Date
  onWeekChange: (weekStart: Date) => void
  selection: DatePickerSelection
  onPick: (date: Date) => void
  isDateDisabled?: (date: Date) => boolean
  readOnly: boolean
}

// 한 주 보기. react-day-picker에 없는 보기라 직접 그린다.
// 키보드: 좌우 하루, 위아래 한 주, Home·End 주의 처음·끝. 주 밖으로 나가면 그 주로 넘긴다.
function WeekRow({
  weekStart,
  onWeekChange,
  selection,
  onPick,
  isDateDisabled,
  readOnly,
}: WeekRowProps) {
  const days = Array.from({ length: 7 }, (_, i) => addDays(weekStart, i))
  const initialFocus =
    days.find((d) => cellState(selection, d).selected) ?? days.find((d) => isToday(d)) ?? weekStart
  const [focused, setFocused] = useState(initialFocus)
  // 키보드로 옮긴 뒤 그린 다음에 새 칸으로 포커스를 옮긴다
  const pendingFocus = useRef(false)
  const buttons = useRef(new Map<string, HTMLButtonElement>())
  const focusInWeek = days.some((d) => isSameDay(d, focused)) ? focused : initialFocus

  useEffect(() => {
    if (pendingFocus.current) {
      pendingFocus.current = false
      buttons.current.get(focusInWeek.toDateString())?.focus()
    }
  })

  function handleKeyDown(event: KeyboardEvent<HTMLButtonElement>, date: Date) {
    const offsets: Record<string, number> = {
      ArrowLeft: -1,
      ArrowRight: 1,
      ArrowUp: -7,
      ArrowDown: 7,
      Home: -date.getDay(),
      End: 6 - date.getDay(),
    }
    const offset = offsets[event.key]
    if (offset === undefined) {
      return
    }
    event.preventDefault()
    const next = addDays(date, offset)
    const nextWeek = startOfWeek(next, { weekStartsOn: WEEK_STARTS_ON })
    if (!isSameDay(nextWeek, weekStart)) {
      onWeekChange(nextWeek)
    }
    pendingFocus.current = true
    setFocused(next)
  }

  return (
    <table role="grid" className="w-full table-fixed border-collapse">
      <thead>
        <tr>
          {days.map((d) => (
            <th
              key={d.toDateString()}
              scope="col"
              className="h-10 pb-2 text-label-m text-text-tertiary"
            >
              {format(d, 'EEEEE', { locale: ko })}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        <tr>
          {days.map((date) => {
            const state = cellState(selection, date)
            const disabled = isDateDisabled?.(date) ?? false
            return (
              <td
                key={date.toDateString()}
                role="gridcell"
                aria-selected={state.selected}
                className="p-0"
              >
                <DayCell
                  ref={(el) => {
                    if (el) {
                      buttons.current.set(date.toDateString(), el)
                    } else {
                      buttons.current.delete(date.toDateString())
                    }
                  }}
                  date={date}
                  {...state}
                  today={isToday(date)}
                  disabled={disabled}
                  readOnly={readOnly && state.selected}
                  aria-label={format(date, 'yyyy년 M월 d일 EEEE', { locale: ko })}
                  tabIndex={isSameDay(date, focusInWeek) ? 0 : -1}
                  onFocus={() => setFocused(date)}
                  onClick={() => onPick(date)}
                  onKeyDown={(event) => handleKeyDown(event, date)}
                />
              </td>
            )
          })}
        </tr>
      </tbody>
    </table>
  )
}

// 피그마 DatePicker. 하루(single)·기간(range)·여러 날(multiple)을 고른다.
// 한 달 보기는 react-day-picker가 표·키보드 이동·접근성을 맡고, 시안과 다른 기간 규칙·한 주 보기·연·월 휠은 직접 만든다.
export function DatePicker(props: DatePickerProps) {
  const { view = 'month', isDateDisabled, readOnly = false, footer, className } = props
  const today = new Date()
  const startMonth = props.startMonth ?? startOfMonth(addYears(today, -DEFAULT_YEARS))
  const endMonth = props.endMonth ?? endOfMonth(addYears(today, DEFAULT_YEARS))

  const [innerSelected, setInnerSelected] = useState<Date | DateRange | Date[] | undefined>(
    props.defaultSelected,
  )
  const rangeDrag = useRangeDrag({
    enabled: props.mode === 'range' && !readOnly,
    onCommit: (range) => {
      setInnerSelected(range)
      if (props.mode === 'range') {
        props.onSelect?.(range)
      }
    },
  })
  const selection = {
    mode: props.mode,
    // 끄는 동안에는 미리 보기 기간을 그린다
    selected: rangeDrag.preview ?? (props.selected !== undefined ? props.selected : innerSelected),
  } as DatePickerSelection
  const anchor = props.defaultMonth ?? firstSelectedDate(selection) ?? today

  const [month, setMonth] = useState(() => startOfMonth(anchor))
  const [weekStart, setWeekStart] = useState(() =>
    startOfWeek(anchor, { weekStartsOn: WEEK_STARTS_ON }),
  )
  const [wheelOpen, setWheelOpen] = useState(false)

  function pick(date: Date) {
    if (readOnly) {
      return
    }
    if (props.mode === 'single') {
      setInnerSelected(date)
      props.onSelect?.(date)
    } else if (props.mode === 'range' && selection.mode === 'range') {
      const next = nextRange(selection.selected, date)
      setInnerSelected(next)
      props.onSelect?.(next)
    } else if (props.mode === 'multiple' && selection.mode === 'multiple') {
      const next = toggleDate(selection.selected, date)
      setInnerSelected(next)
      props.onSelect?.(next)
    }
  }

  // 휠에서 달을 바꾸면 한 주 보기는 그 달 1일이 있는 주로 옮긴다
  function changeMonth(next: Date) {
    setMonth(next)
    setWeekStart(startOfWeek(next, { weekStartsOn: WEEK_STARTS_ON }))
  }

  const isWeek = view === 'week'
  // 한 주가 두 달에 걸치면 목요일이 있는 달로 보여 준다
  const shownMonth = isWeek ? startOfMonth(addDays(weekStart, 3)) : month
  const prevDisabled = isWeek
    ? isBefore(addDays(weekStart, -1), startOfMonth(startMonth))
    : !isAfter(month, startOfMonth(startMonth))
  const nextDisabled = isWeek
    ? isAfter(addDays(weekStart, 7), endOfMonth(endMonth))
    : !isBefore(month, startOfMonth(endMonth))

  const dayPickerCommon = {
    locale: dayPickerKo,
    weekStartsOn: WEEK_STARTS_ON,
    month,
    onMonthChange: setMonth,
    startMonth,
    endMonth,
    hideNavigation: true,
    disabled: isDateDisabled,
    classNames: DAY_PICKER_CLASSES,
    components: { DayButton: PickerDayButton },
  } as const

  const text = selectionText(selection)

  return (
    <div
      {...rangeDrag.handlers}
      className={cn(
        'relative flex w-full flex-col gap-2 rounded-lg bg-bg-surface p-4',
        // 기간 모드에서는 날짜 칸을 끌어도 화면이 스크롤되거나 글자가 선택되지 않게 한다
        props.mode === 'range' && !readOnly && 'select-none [&_[data-day]]:touch-none',
        className,
      )}
    >
      <Header
        label={format(shownMonth, 'yyyy년 M월')}
        wheelOpen={wheelOpen}
        onToggleWheel={() => setWheelOpen((open) => !open)}
        onPrev={() =>
          isWeek ? setWeekStart(addWeeks(weekStart, -1)) : setMonth(addMonths(month, -1))
        }
        onNext={() =>
          isWeek ? setWeekStart(addWeeks(weekStart, 1)) : setMonth(addMonths(month, 1))
        }
        prevDisabled={prevDisabled}
        nextDisabled={nextDisabled}
        prevLabel={isWeek ? '이전 주' : '이전 달'}
        nextLabel={isWeek ? '다음 주' : '다음 달'}
      />

      {wheelOpen && !isWeek ? (
        <MonthYearWheel
          month={month}
          onMonthChange={changeMonth}
          startMonth={startMonth}
          endMonth={endMonth}
        />
      ) : isWeek ? (
        <WeekRow
          weekStart={weekStart}
          onWeekChange={setWeekStart}
          selection={selection}
          onPick={pick}
          isDateDisabled={isDateDisabled}
          readOnly={readOnly}
        />
      ) : (
        <ReadOnlyContext value={readOnly}>
          {selection.mode === 'single' && (
            <DayPicker
              {...dayPickerCommon}
              mode="single"
              required
              selected={selection.selected}
              onSelect={(_, date) => pick(date)}
            />
          )}
          {selection.mode === 'range' && (
            <DayPicker
              {...dayPickerCommon}
              mode="range"
              selected={selection.selected}
              onSelect={(_, date) => pick(date)}
            />
          )}
          {selection.mode === 'multiple' && (
            <DayPicker
              {...dayPickerCommon}
              mode="multiple"
              selected={selection.selected}
              onSelect={(_, date) => pick(date)}
            />
          )}
        </ReadOnlyContext>
      )}

      {/* 한 주 보기에서는 휠이 달력 위에 뜬다. 모서리 16·Shadow/MD, 머리 줄 바로 아래 */}
      {wheelOpen && isWeek && (
        <div className="absolute top-16 left-3 z-10 w-64 rounded-lg bg-bg-surface p-2 shadow-md">
          <MonthYearWheel
            month={shownMonth}
            onMonthChange={changeMonth}
            startMonth={startMonth}
            endMonth={endMonth}
          />
        </div>
      )}

      {footer && (
        <div className="flex items-center gap-3 pt-2">
          <span className="min-w-0 flex-1 truncate text-label-strong text-text-primary">
            {text}
          </span>
          <Button onClick={footer.onConfirm}>{footer.confirmLabel ?? '선택 완료'}</Button>
        </div>
      )}
    </div>
  )
}
