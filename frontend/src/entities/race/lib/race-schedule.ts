import { addDays, addWeeks, format, parseISO } from 'date-fns'

import { parseRaceDateInput } from './race-date'

// 판매 일정(크루 예매 · 일반 판매 · 접수 마감) 규칙. 대회 등록과 수정 화면이 함께 쓴다.
// 일정 칸은 'yyyy-MM-dd HH:mm' 글자라 글자 비교가 곧 시각 비교다.

export const SCHEDULE_ERRORS = {
  // 일반 판매 시작 · 접수 마감 · 크루 예매 시작·마감: 날짜와 시각을 함께 받는다
  dateTimeRequired: '날짜와 시각을 입력해 주세요.',
  crewEndAfterStart: '크루 예매 마감은 시작보다 늦어야 해요.',
  individualAfterCrew: '일반 판매는 크루 예매 마감 다음 날 0시부터 시작할 수 있어요.',
  closeAfterOpen: '접수 마감은 일반 판매 시작보다 늦어야 해요.',
  closeBeforeRace: '접수 마감은 대회 시작 전까지 정할 수 있어요.',
} as const

/** 대회 일시는 접수 마감 일시로부터 이 주 수 뒤부터 (서버 Race.RACE_AFTER_CLOSE_WEEKS와 같다) */
export const RACE_AFTER_CLOSE_WEEKS = 3

/** 접수 마감('yyyy-MM-dd HH:mm')으로부터 3주 뒤. 대회 일시를 이 시각보다 앞으로는 정할 수 없다 */
export function earliestRaceAt(closeAt: string): string {
  return format(
    addWeeks(parseISO(closeAt.replace(' ', 'T')), RACE_AFTER_CLOSE_WEEKS),
    'yyyy-MM-dd HH:mm',
  )
}

export function raceAfterCloseMessage(earliest: string): string {
  const text = format(parseISO(earliest.replace(' ', 'T')), 'yyyy년 M월 d일 HH:mm')
  return `대회 일시는 접수 마감 일시로부터 ${RACE_AFTER_CLOSE_WEEKS}주 뒤(${text})부터 정할 수 있어요.`
}

/** 판매 시작일(접수 시작)은 오늘로부터 이 날 수 뒤부터 (서버 Race.SALE_OPEN_MIN_DAYS와 같다) */
export const SALE_OPEN_MIN_DAYS = 2

/** 접수 시작으로 고를 수 있는 가장 이른 날 */
export function earliestSaleOpenDate(today: Date): Date {
  return addDays(today, SALE_OPEN_MIN_DAYS)
}

export function saleOpenTooEarlyMessage(earliest: Date): string {
  return `접수 시작은 오늘로부터 이틀 뒤(${format(earliest, 'M/d')})부터 정할 수 있어요.`
}

export const TIME_INPUT = /^([01]\d|2[0-3]):[0-5]\d$/

/** 날짜 · 시각 칸('yyyy-MM-dd HH:mm'). 날짜나 시각이 비었거나 틀리면 null */
export function parseDateTimeInput(text: string): string | null {
  const match = /^(\S+) (\S+)$/.exec(text.trim())
  const day = match && parseRaceDateInput(match[1])
  return day && TIME_INPUT.test(match[2]) ? `${day} ${match[2]}` : null
}

/** 크루 몫이 일반 몫으로 넘어가는 시각. 크루 예매 마감이 00:00이면 그 시각, 아니면 다음 날 00:00 (서버 Race.crewCloseAt) */
export function crewCloseAt(crewEnd: string): string {
  if (crewEnd.endsWith(' 00:00')) return crewEnd
  return `${format(addDays(parseISO(crewEnd.slice(0, 10)), 1), 'yyyy-MM-dd')} 00:00`
}

/** 'yyyy-MM-dd HH:mm' → 서버 LocalDateTime. 23:59는 그 날 끝(초까지 59)으로 본다 */
export function toLocalDateTime(text: string): string {
  const [day, clock] = text.trim().split(' ')
  return `${parseRaceDateInput(day) ?? day}T${clock}:${clock === '23:59' ? '59' : '00'}`
}

/** 서버 LocalDateTime(2026-12-04T23:59:59) → 'yyyy-MM-dd HH:mm'. 없으면 빈 글자 */
export function fromLocalDateTime(value: string | null | undefined): string {
  return value ? `${value.slice(0, 10)} ${value.slice(11, 16)}` : ''
}

export type ScheduleInput = {
  /** 크루 선예매를 열 때만 크루 예매 칸을 본다 */
  crewOpen: boolean
  crewSaleStartAt: string
  crewSaleEndAt: string
  individualOpenAt: string
  applicationCloseAt: string
}

/**
 * 일정 칸끼리의 순서 검사 (서버 RaceRequestValidator.validateSchedule과 같다).
 * raceAt('yyyy-MM-dd HH:mm')이 없으면 대회 시작 전 검사는 건너뛴다. 형식이 맞는 칸끼리만 본다.
 */
export function checkScheduleOrder(
  values: ScheduleInput,
  raceAt: string | null,
  issue: (path: string, message: string) => void,
  // 수정 화면은 대회 일시가 없어 접수 마감 칸에 오류를 보이고, 접수 마감을 바꿀 때만 3주 간격을 본다
  { racePath = 'raceDate', checkGap = true }: { racePath?: string; checkGap?: boolean } = {},
) {
  const openAt = parseDateTimeInput(values.individualOpenAt)
  const closeAt = parseDateTimeInput(values.applicationCloseAt)
  const crewStart = values.crewOpen ? parseDateTimeInput(values.crewSaleStartAt) : null
  const crewEnd = values.crewOpen ? parseDateTimeInput(values.crewSaleEndAt) : null
  if (values.crewOpen) {
    if (!crewStart) issue('crewSaleStartAt', SCHEDULE_ERRORS.dateTimeRequired)
    if (!crewEnd) issue('crewSaleEndAt', SCHEDULE_ERRORS.dateTimeRequired)
    if (crewStart && crewEnd && crewStart >= crewEnd) {
      issue('crewSaleEndAt', SCHEDULE_ERRORS.crewEndAfterStart)
    }
    if (crewEnd && openAt && openAt < crewCloseAt(crewEnd)) {
      issue('individualOpenAt', SCHEDULE_ERRORS.individualAfterCrew)
    }
  }
  if (openAt && closeAt && closeAt <= openAt) {
    issue('applicationCloseAt', SCHEDULE_ERRORS.closeAfterOpen)
  }
  if (closeAt && raceAt && closeAt > raceAt) {
    issue('applicationCloseAt', SCHEDULE_ERRORS.closeBeforeRace)
  } else if (checkGap && closeAt && raceAt && raceAt < earliestRaceAt(closeAt)) {
    issue(racePath, raceAfterCloseMessage(earliestRaceAt(closeAt)))
  }
}
