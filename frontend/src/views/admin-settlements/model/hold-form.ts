import { z } from 'zod'

import { payoutZod } from '@/shared/api-zod'

export const HOLD_ERRORS = {
  payoutId: '정산 내역 번호를 숫자로 넣어 주세요.',
} as const

// 정산 내역 번호는 입력 칸이 글자라 양의 정수로 바꿔 쓴다. 보류 사유는 서버 스키마를 그대로 쓴다.
export const holdFormSchema = z.object({
  payoutId: z
    .string()
    .trim()
    .regex(/^[1-9]\d*$/, HOLD_ERRORS.payoutId)
    .transform(Number)
    .refine(Number.isSafeInteger, HOLD_ERRORS.payoutId),
  reasonType: payoutZod.HoldBody.shape.reasonType,
})
