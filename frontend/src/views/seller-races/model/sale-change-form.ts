import { z } from 'zod'

import { isSaleStarted, type RaceForEdit } from '@/entities/race'
import type { productModel } from '@/shared/api'

// 대회 수정 화면 규칙. 일정(대회 일시·장소·크루 예매·일반 판매·접수 마감·환불 기한)은
// 일정 변경 화면이 맡고, 여기서는 정원·참가비 같은 일정이 아닌 칸만 다룬다.
export const SALE_CHANGE_ERRORS = {
  number: '숫자로 입력해 주세요.',
  quotaDecreased: '판매가 시작된 종목은 정원을 줄일 수 없어요.',
  priceChanged: '판매가 시작된 종목은 참가비를 바꿀 수 없어요.',
  quotaSum: '크루 정원과 일반 정원을 합쳐 1명 이상이어야 해요.',
} as const

export type SaleChangeValues = {
  courses: { individualQuota: string; price: string }[]
}

const wholeNumber = z.string().trim().regex(/^\d+$/, SALE_CHANGE_ERRORS.number)

/** 지금 값으로 입력 칸을 채운다. */
export function toSaleChangeValues({ request }: RaceForEdit): SaleChangeValues {
  return {
    courses: request.courses.map((c) => ({
      individualQuota: String(c.individualQuota),
      price: String(c.price),
    })),
  }
}

/**
 * 판매 중 변경 검사. 판매가 시작된 대회는 백엔드 규칙(PRO-141)대로
 * 정원은 늘리기만, 참가비는 그대로다. 서버도 같은 규칙으로 409를 준다.
 */
export function saleChangeSchema({ race, request }: RaceForEdit) {
  const started = isSaleStarted(race.saleStatus)
  return z
    .object({
      courses: z.array(z.object({ individualQuota: wholeNumber, price: wholeNumber })),
    })
    .superRefine((values, ctx) => {
      values.courses.forEach((course, i) => {
        const before = request.courses[i]
        if (!before) return
        // 서버 validateCourse: 크루 정원과 일반 정원을 합쳐 1명 이상이어야 한다
        if (
          /^\d+$/.test(course.individualQuota) &&
          before.crewQuota + Number(course.individualQuota) < 1
        ) {
          ctx.addIssue({
            code: 'custom',
            path: ['courses', i, 'individualQuota'],
            message: SALE_CHANGE_ERRORS.quotaSum,
          })
        }
        if (!started) return
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
    })
}

/** 지금 상태의 수정 요청에 바꾼 값만 덮어쓴다. 일정은 지금 값 그대로 보낸다. */
export function toUpdateRequest(
  { request }: RaceForEdit,
  values: SaleChangeValues,
): productModel.RaceSaveRequestDto {
  return {
    ...request,
    courses: request.courses.map((course, i) => ({
      ...course,
      individualQuota: Number(values.courses[i]?.individualQuota ?? course.individualQuota),
      price: Number(values.courses[i]?.price ?? course.price),
    })),
  }
}
