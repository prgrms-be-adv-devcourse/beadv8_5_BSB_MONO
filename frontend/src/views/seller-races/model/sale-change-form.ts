import { format } from 'date-fns'
import { z } from 'zod'

import {
  checkScheduleOrder,
  earliestSaleOpenDate,
  fromLocalDateTime,
  isSaleStarted,
  parseDateTimeInput,
  parseKstDateTime,
  parseRaceDateInput,
  saleOpenTooEarlyMessage,
  SCHEDULE_ERRORS,
  toLocalDateTime,
  type RaceForEdit,
} from '@/entities/race'
import type { productModel } from '@/shared/api'

export const SALE_CHANGE_ERRORS = {
  number: '숫자로 입력해 주세요.',
  quotaDecreased: '판매가 시작된 종목은 정원을 줄일 수 없어요.',
  priceChanged: '판매가 시작된 종목은 참가비를 바꿀 수 없어요.',
  dateFormat: '2026-10-31처럼 입력해 주세요.',
  refundAdvanced: '판매가 시작된 대회는 환불 기한을 앞당길 수 없어요.',
  ...SCHEDULE_ERRORS,
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
type ScheduleField = (typeof SCHEDULE_FIELDS)[number]

export type SaleChangeValues = {
  /** 크루 선예매를 열 때만 크루 예매 칸을 본다. 일정 칸은 'yyyy-MM-dd HH:mm' */
  crewOpen: boolean
  crewSaleStartAt: string
  crewSaleEndAt: string
  individualOpenAt: string
  applicationCloseAt: string
  courses: { individualQuota: string; price: string }[]
  refundDeadline: string
}

const wholeNumber = z.string().trim().regex(/^\d+$/, SALE_CHANGE_ERRORS.number)

/** 지금 값으로 입력 칸을 채운다. */
export function toSaleChangeValues({ request }: RaceForEdit): SaleChangeValues {
  return {
    crewOpen: Boolean(request.crewSaleStartAt),
    crewSaleStartAt: fromLocalDateTime(request.crewSaleStartAt),
    crewSaleEndAt: fromLocalDateTime(request.crewSaleEndAt),
    individualOpenAt: fromLocalDateTime(request.individualOpenAt),
    applicationCloseAt: fromLocalDateTime(request.applicationCloseAt),
    courses: request.courses.map((c) => ({
      individualQuota: String(c.individualQuota),
      price: String(c.price),
    })),
    refundDeadline: request.refundDeadline,
  }
}

/**
 * 읽기 전용으로 둘 일정 (서버 Race.update와 같다).
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

/**
 * 판매 중 변경 검사. 판매가 시작된 대회는 백엔드 규칙(PRO-141)대로
 * 정원은 늘리기만, 참가비는 그대로, 환불 기한은 늦추기만 된다. 서버도 같은 규칙으로 409를 준다.
 */
export function saleChangeSchema(edit: RaceForEdit, now: Date) {
  const { race, request } = edit
  const started = isSaleStarted(race.saleStatus)
  return z
    .object({
      crewOpen: z.boolean(),
      crewSaleStartAt: z.string(),
      crewSaleEndAt: z.string(),
      individualOpenAt: z
        .string()
        .refine((v) => parseDateTimeInput(v) !== null, SALE_CHANGE_ERRORS.dateTimeRequired),
      applicationCloseAt: z
        .string()
        .refine((v) => parseDateTimeInput(v) !== null, SALE_CHANGE_ERRORS.dateTimeRequired),
      courses: z.array(z.object({ individualQuota: wholeNumber, price: wholeNumber })),
      refundDeadline: z
        .string()
        .refine((value) => parseRaceDateInput(value) !== null, SALE_CHANGE_ERRORS.dateFormat),
    })
    .superRefine((values, ctx) => {
      checkSchedule(edit, values, now, (path, message) =>
        ctx.addIssue({ code: 'custom', path, message }),
      )
      values.courses.forEach((course, i) => {
        const crew = values.crewOpen ? (request.courses[i]?.crewQuota ?? 0) : 0
        if (/^\d+$/.test(course.individualQuota) && crew + Number(course.individualQuota) < 1) {
          ctx.addIssue({
            code: 'custom',
            path: ['courses', i, 'individualQuota'],
            message: SALE_CHANGE_ERRORS.quotaSum,
          })
        }
      })
      if (!started) return
      values.courses.forEach((course, i) => {
        const before = request.courses[i]
        if (!before) return
        if (
          /^\d+$/.test(course.individualQuota) &&
          Number(course.individualQuota) < before.individualQuota
        ) {
          ctx.addIssue({
            code: 'custom',
            path: ['courses', i, 'individualQuota'],
            message: SALE_CHANGE_ERRORS.quotaDecreased,
          })
        }
        if (/^\d+$/.test(course.price) && Number(course.price) !== before.price) {
          ctx.addIssue({
            code: 'custom',
            path: ['courses', i, 'price'],
            message: SALE_CHANGE_ERRORS.priceChanged,
          })
        }
      })
      const refund = parseRaceDateInput(values.refundDeadline)
      if (refund && refund < request.refundDeadline) {
        ctx.addIssue({
          code: 'custom',
          path: ['refundDeadline'],
          message: SALE_CHANGE_ERRORS.refundAdvanced,
        })
      }
    })
}

/** 일정 칸끼리의 순서와, 서버가 막는 일정 변경(지난 일정·판매 시작 뒤·지난 시각·판매 시작일)을 본다. */
function checkSchedule(
  edit: RaceForEdit,
  values: SaleChangeValues,
  now: Date,
  issue: (path: (string | number)[], message: string) => void,
) {
  const { request } = edit
  const locks = scheduleLocks(edit, now)
  const raceText = fromLocalDateTime(request.raceDate)
  checkScheduleOrder(values, parseDateTimeInput(raceText), (path, message) =>
    issue([path], message),
  )
  if (values.crewOpen !== Boolean(request.crewSaleStartAt) && locks.crewOpen) {
    issue(['crewOpen'], SALE_CHANGE_ERRORS.crewOpenLocked)
  }
  const current = (field: ScheduleField) => fromLocalDateTime(request[field])
  const changedFields = SCHEDULE_FIELDS.filter((field) => {
    if (field.startsWith('crew') && !values.crewOpen) return false
    const text = parseDateTimeInput(values[field])
    return text !== null && text !== current(field)
  })
  for (const field of changedFields) {
    const requested = parseDateTimeInput(values[field]) as string
    if (locks[field]) {
      issue([field], SALE_CHANGE_ERRORS.scheduleLocked)
    } else if (parseKstDateTime(toLocalDateTime(requested)) <= now) {
      issue([field], SALE_CHANGE_ERRORS.scheduleInPast)
    }
  }
  // 판매 시작일(크루 예매 시작일, 없으면 일반 판매 시작일)이 바뀌면 오늘로부터 이틀 뒤부터
  const first: ScheduleField = values.crewOpen ? 'crewSaleStartAt' : 'individualOpenAt'
  const firstText = parseDateTimeInput(values[first])
  const currentFirst = current(request.crewSaleStartAt ? 'crewSaleStartAt' : 'individualOpenAt')
  if (firstText && !locks[first] && firstText.slice(0, 10) !== currentFirst.slice(0, 10)) {
    const earliest = earliestSaleOpenDate(now)
    if (firstText.slice(0, 10) < format(earliest, 'yyyy-MM-dd')) {
      issue([first], saleOpenTooEarlyMessage(earliest))
    }
  }
}

/** 지금 상태의 수정 요청에 바꾼 값만 덮어쓴다. */
export function toUpdateRequest(
  { request }: RaceForEdit,
  values: SaleChangeValues,
): productModel.RaceSaveRequestDto {
  return {
    ...request,
    refundDeadline: parseRaceDateInput(values.refundDeadline) ?? request.refundDeadline,
    // 바꾸지 않은 일정은 저장된 값을 그대로 보낸다. 서버는 지난 일정이 달라졌는지 초 단위까지 비교한다
    crewSaleStartAt: values.crewOpen
      ? scheduleValue(request, values, 'crewSaleStartAt')
      : undefined,
    crewSaleEndAt: values.crewOpen ? scheduleValue(request, values, 'crewSaleEndAt') : undefined,
    individualOpenAt: scheduleValue(request, values, 'individualOpenAt'),
    applicationCloseAt: scheduleValue(request, values, 'applicationCloseAt'),
    courses: request.courses.map((course, i) => ({
      ...course,
      // 크루 선예매를 열지 않으면 크루 정원은 0 (서버 RaceRequestValidator)
      crewQuota: values.crewOpen ? course.crewQuota : 0,
      individualQuota: Number(values.courses[i]?.individualQuota ?? course.individualQuota),
      price: Number(values.courses[i]?.price ?? course.price),
    })),
  }
}

function scheduleValue(
  request: productModel.RaceSaveRequestDto,
  values: SaleChangeValues,
  field: ScheduleField,
): string {
  const text = values[field]
  return text === fromLocalDateTime(request[field])
    ? (request[field] as string)
    : toLocalDateTime(text)
}
