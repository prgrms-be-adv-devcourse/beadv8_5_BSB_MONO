import { format, isBefore, isSameDay } from 'date-fns'
import { ko } from 'date-fns/locale'
import type { DateRange } from 'react-day-picker'

export type { DateRange }

export type DatePickerSelection =
  | { mode: 'single'; selected: Date | undefined }
  | { mode: 'range'; selected: DateRange | undefined }
  | { mode: 'multiple'; selected: Date[] | undefined }

// 시안의 기간 고르기 규칙. react-day-picker 기본값(앞을 누르면 기간을 넓힘)과 달라서 직접 계산한다.
// - 처음 누른 날이 시작일
// - 시작일 뒤(같은 날 포함)를 누르면 종료일, 앞을 누르면 그날이 새 시작일
// - 기간이 정해진 뒤 다시 누르면 처음부터 새로 고른다
export function nextRange(current: DateRange | undefined, date: Date): DateRange {
  if (!current?.from || current.to) {
    return { from: date, to: undefined }
  }
  if (isBefore(date, current.from)) {
    return { from: date, to: undefined }
  }
  return { from: current.from, to: date }
}

/**
 * 끌어서 기간 고르기: 처음 누른 날(anchor)과 지금 손가락이 있는 날 중 앞이 시작, 뒤가 종료.
 * 누른 날로 돌아오면 시작일만 남긴다(한 번 누른 것과 같은 상태).
 */
export function dragRange(anchor: Date, date: Date): DateRange {
  if (isSameDay(anchor, date)) {
    return { from: anchor, to: undefined }
  }
  return isBefore(date, anchor) ? { from: date, to: anchor } : { from: anchor, to: date }
}

/** 여러 날 고르기: 누를 때마다 켜고 끈다. */
export function toggleDate(current: Date[] | undefined, date: Date): Date[] {
  const list = current ?? []
  return list.some((d) => isSameDay(d, date))
    ? list.filter((d) => !isSameDay(d, date))
    : [...list, date]
}

const DAY = 'M월 d일 (EEE)'

/** 하단 영역의 고른 값 문구. 예: 10월 18일 (일) / 10월 12일 (월) ~ 10월 16일 (금) / 3일 선택 */
export function selectionText(selection: DatePickerSelection): string {
  const fmt = (date: Date) => format(date, DAY, { locale: ko })
  switch (selection.mode) {
    case 'single':
      return selection.selected ? fmt(selection.selected) : ''
    case 'range': {
      const { from, to } = selection.selected ?? {}
      if (!from) {
        return ''
      }
      return to ? `${fmt(from)} ~ ${fmt(to)}` : fmt(from)
    }
    case 'multiple':
      return selection.selected?.length ? `${selection.selected.length}일 선택` : ''
  }
}
