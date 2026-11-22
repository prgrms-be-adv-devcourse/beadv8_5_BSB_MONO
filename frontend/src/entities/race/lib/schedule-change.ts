import { format } from 'date-fns'
import { z } from 'zod'

import type { productModel } from '@/shared/api'

import type { RaceForEdit } from '../api/seller-race-api'
import { isSaleStarted } from '../model/seller-race'
import { parseKstDateTime, parseRaceDateInput } from './race-date'
import {
  checkRefundAfterClose,
  checkScheduleOrder,
  earliestSaleOpenDate,
  fromLocalDateTime,
  parseDateTimeInput,
  saleOpenTooEarlyMessage,
  SCHEDULE_ERRORS,
  toLocalDateTime,
} from './race-schedule'

// 대회 일정 변경(일정 변경 · 대회 취소 화면) 규칙. 서버 Race.update · checkScheduleChange와 같다.
// 일정이 아닌 칸(정원·참가비 등)은 대회 수정 화면이 맡는다. 두 화면은 같은 수정 요청을 쓰고 자기 칸만 바꾼다.

export const SCHEDULE_CHANGE_ERRORS = {
  ...SCHEDULE_ERRORS,
  dateFormat: '2026-10-31처럼 입력해 주세요.',
  locationRequired: '장소를 검색해 골라 주세요.',
  refundAdvanced: '판매가 시작된 대회는 환불 기한을 앞당길 수 없어요.',
  refundBeforeRace: '환불 기한은 대회 날짜보다 늦을 수 없어요.',
  scheduleLocked: '이미 지났거나 판매가 시작돼 바꿀 수 없는 일정이에요.',
  scheduleInPast: '지난 시각으로는 바꿀 수 없어요.',
  crewOpenLocked: '판매가 시작됐거나 크루 예매가 시작된 대회는 크루 선예매를 바꿀 수 없어요.',
  quotaSum: '크루 정원과 일반 정원을 합쳐 1명 이상이어야 해요.',
} as const

const SCHEDULE_FIELDS = [
  'crewSaleStartAt',
  'crewSaleEndAt',
  'individualOpenAt',
  'applicationCloseAt',
] as const
export type ScheduleChangeField = (typeof SCHEDULE_FIELDS)[number]

export type ScheduleChangeValues = {
  /** 대회 일시. 'yyyy-MM-dd HH:mm' */
  raceDateTime: string
  location: string
  regionSido: string
  regionSigungu: string
  /** 크루 선예매를 열 때만 크루 예매 칸을 본다. 일정 칸은 'yyyy-MM-dd HH:mm' */
  crewOpen: boolean
  crewSaleStartAt: string
  crewSaleEndAt: string
  /** 비우면 인원 제한이 없다 */
  crewMaxCount: string
  individualOpenAt: string
  applicationCloseAt: string
  /** 'yyyy-MM-dd' */
  refundDeadline: string
}

/** 지금 값으로 입력 칸을 채운다. */
export function toScheduleChangeValues({ request }: RaceForEdit): ScheduleChangeValues {
  return {
    raceDateTime: fromLocalDateTime(request.raceDate),
    location: request.location,
    regionSido: request.regionSido,
    regionSigungu: request.regionSigungu,
    crewOpen: Boolean(request.crewSaleStartAt),
    crewSaleStartAt: fromLocalDateTime(request.crewSaleStartAt),
    crewSaleEndAt: fromLocalDateTime(request.crewSaleEndAt),
    crewMaxCount: request.crewMaxCount ? String(request.crewMaxCount) : '',
    individualOpenAt: fromLocalDateTime(request.individualOpenAt),
    applicationCloseAt: fromLocalDateTime(request.applicationCloseAt),
    refundDeadline: request.refundDeadline,
  }
}

/**
 * 읽기 전용으로 둘 일정.
 * 이미 지난 일정은 바꿀 수 없고, 판매가 시작된 대회는 판매 시작일을 정하는 일정
 * (크루 예매가 있으면 크루 예매 시작, 없으면 일반 판매 시작)도 바꿀 수 없다.
 * 크루 선예매 열기·열지 않기는 판매가 시작됐거나 크루 예매 시작이 지났으면 바꿀 수 없다.
 */
export function scheduleLocks({ race, request }: RaceForEdit, now: Date) {
  const passed = (value: string | null | undefined) => !!value && parseKstDateTime(value) <= now
  const started = isSaleStarted(race.saleStatus)
  const hasCrew = Boolean(request.crewSaleStartAt)
  return {
    crewOpen: started || passed(request.crewSaleStartAt),
    crewSaleStartAt: passed(request.crewSaleStartAt) || (started && hasCrew),
    crewSaleEndAt: passed(request.crewSaleEndAt),
    individualOpenAt: passed(request.individualOpenAt) || (started && !hasCrew),
    applicationCloseAt: passed(request.applicationCloseAt),
  }
}

/** 일정 변경 검사. 칸 형식은 zod로, 칸끼리의 순서와 서버가 막는 변경은 여기서 본다. */
export function scheduleChangeSchema(edit: RaceForEdit, now: Date) {
  const { race, request } = edit
  const started = isSaleStarted(race.saleStatus)
  const dateTime = z
    .string()
    .refine((v) => parseDateTimeInput(v) !== null, SCHEDULE_CHANGE_ERRORS.dateTimeRequired)
  return z
    .object({
      raceDateTime: dateTime,
      location: z.string().trim().min(1, SCHEDULE_CHANGE_ERRORS.locationRequired),
      regionSido: z.string(),
      regionSigungu: z.string(),
      crewOpen: z.boolean(),
      crewSaleStartAt: z.string(),
      crewSaleEndAt: z.string(),
      crewMaxCount: z.string(),
      individualOpenAt: dateTime,
      applicationCloseAt: dateTime,
      refundDeadline: z
        .string()
        .refine((v) => parseRaceDateInput(v) !== null, SCHEDULE_CHANGE_ERRORS.dateFormat),
    })
    .superRefine((values, ctx) => {
      const issue = (path: string, message: string) =>
        ctx.addIssue({ code: 'custom', path: [path], message })
      // 서버 RaceRequestValidator: 크루 최대 인원은 크루 최소 인원 이상이다. 비우면 제한이 없다
      const min = request.crewMinCount ?? 4
      const max = values.crewMaxCount.trim()
      if (values.crewOpen && max !== '' && !(/^\d+$/.test(max) && Number(max) >= min)) {
        issue('crewMaxCount', `크루 최대 인원은 ${min}명 이상의 숫자로 입력해 주세요.`)
      }
      checkSchedule(edit, values, now, issue)
      // 서버 validateCourse: 크루 예매가 없으면 크루 정원은 0이라, 일반 정원이 1명 이상이어야 한다
      const crewOff = request.courses.some(
        (course) => (values.crewOpen ? course.crewQuota : 0) + course.individualQuota < 1,
      )
      if (crewOff) issue('crewOpen', SCHEDULE_CHANGE_ERRORS.quotaSum)
      // 판매가 시작된 대회는 환불 기한을 앞당길 수 없다 (구매자에게 유리한 변경만)
      const refund = parseRaceDateInput(values.refundDeadline)
      if (started && refund && refund < request.refundDeadline) {
        issue('refundDeadline', SCHEDULE_CHANGE_ERRORS.refundAdvanced)
      }
    })
}

/** 일정 칸끼리의 순서와, 서버가 막는 일정 변경(지난 일정·판매 시작 뒤·지난 시각·판매 시작일)을 본다. */
function checkSchedule(
  edit: RaceForEdit,
  values: ScheduleChangeValues,
  now: Date,
  issue: (path: string, message: string) => void,
) {
  const { request } = edit
  const locks = scheduleLocks(edit, now)
  const closeChanged = values.applicationCloseAt !== fromLocalDateTime(request.applicationCloseAt)
  const raceChanged = values.raceDateTime !== fromLocalDateTime(request.raceDate)
  // 대회 일시나 접수 마감을 바꿀 때만 3주 간격을 본다 (서버와 같다)
  checkScheduleOrder(values, parseDateTimeInput(values.raceDateTime), issue, {
    racePath: 'raceDateTime',
    checkGap: closeChanged || raceChanged,
  })
  // 환불 기한은 접수 마감 다음 날부터 대회 날짜까지. 환불 기한이나 접수 마감을 바꿀 때만 본다
  const refund = parseRaceDateInput(values.refundDeadline)
  if (values.refundDeadline !== request.refundDeadline || closeChanged) {
    checkRefundAfterClose(refund, parseDateTimeInput(values.applicationCloseAt), (message) =>
      issue('refundDeadline', message),
    )
    const raceDay = parseDateTimeInput(values.raceDateTime)?.slice(0, 10)
    if (refund && raceDay && refund > raceDay) {
      issue('refundDeadline', SCHEDULE_CHANGE_ERRORS.refundBeforeRace)
    }
  }
  if (values.crewOpen !== Boolean(request.crewSaleStartAt) && locks.crewOpen) {
    issue('crewOpen', SCHEDULE_CHANGE_ERRORS.crewOpenLocked)
  }
  const current = (field: ScheduleChangeField) => fromLocalDateTime(request[field])
  const changedFields = SCHEDULE_FIELDS.filter((field) => {
    if (field.startsWith('crew') && !values.crewOpen) return false
    const text = parseDateTimeInput(values[field])
    return text !== null && text !== current(field)
  })
  for (const field of changedFields) {
    const requested = parseDateTimeInput(values[field]) as string
    if (locks[field]) {
      issue(field, SCHEDULE_CHANGE_ERRORS.scheduleLocked)
    } else if (parseKstDateTime(toLocalDateTime(requested)) <= now) {
      issue(field, SCHEDULE_CHANGE_ERRORS.scheduleInPast)
    }
  }
  // 판매 시작일(크루 예매 시작일, 없으면 일반 판매 시작일)이 바뀌면 오늘로부터 이틀 뒤부터
  const first: ScheduleChangeField = values.crewOpen ? 'crewSaleStartAt' : 'individualOpenAt'
  const firstText = parseDateTimeInput(values[first])
  const currentFirst = current(request.crewSaleStartAt ? 'crewSaleStartAt' : 'individualOpenAt')
  if (firstText && !locks[first] && firstText.slice(0, 10) !== currentFirst.slice(0, 10)) {
    const earliest = earliestSaleOpenDate(now)
    if (firstText.slice(0, 10) < format(earliest, 'yyyy-MM-dd')) {
      issue(first, saleOpenTooEarlyMessage(earliest))
    }
  }
}

/** 지금 상태의 수정 요청에 일정 칸만 바꿔 덮어쓴다. 바꾸지 않은 일정은 저장된 값을 그대로 보낸다(서버는 지난 일정이 달라졌는지 초 단위까지 비교한다). */
export function toScheduleRequest(
  { request }: RaceForEdit,
  values: ScheduleChangeValues,
): productModel.RaceSaveRequestDto {
  const keep = (field: ScheduleChangeField | 'raceDate', text: string): string =>
    text === fromLocalDateTime(request[field]) ? (request[field] as string) : toLocalDateTime(text)
  return {
    ...request,
    raceDate: keep('raceDate', values.raceDateTime),
    location: values.location.trim(),
    regionSido: values.regionSido,
    regionSigungu: values.regionSigungu,
    refundDeadline: parseRaceDateInput(values.refundDeadline) ?? request.refundDeadline,
    crewSaleStartAt: values.crewOpen ? keep('crewSaleStartAt', values.crewSaleStartAt) : undefined,
    crewSaleEndAt: values.crewOpen ? keep('crewSaleEndAt', values.crewSaleEndAt) : undefined,
    crewMaxCount:
      values.crewOpen && values.crewMaxCount.trim() ? Number(values.crewMaxCount) : undefined,
    individualOpenAt: keep('individualOpenAt', values.individualOpenAt),
    applicationCloseAt: keep('applicationCloseAt', values.applicationCloseAt),
    // 크루 선예매를 열지 않으면 크루 정원은 0 (서버 RaceRequestValidator)
    courses: request.courses.map((course) => ({
      ...course,
      crewQuota: values.crewOpen ? course.crewQuota : 0,
    })),
  }
}
