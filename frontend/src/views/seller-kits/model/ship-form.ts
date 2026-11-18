import { z } from 'zod'

import { orderZod } from '@/shared/api-zod'

/** 송장 번호를 줄마다 하나씩 읽는다 (앞뒤 공백을 빼고, 빈 줄은 건너뛴다) */
export function trackingLines(text: string): string[] {
  return text
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
}

/** 서버 KitShipmentRequestDto와 같은 송장 번호 한도 */
export const TRACKING_MAX = orderZod.shipBodyTrackingNumberMax

export const TRACKING_TOO_LONG_MESSAGE = `송장 번호는 줄마다 ${TRACKING_MAX}자 이하로 넣어 주세요.`

export const shipCountMessage = (count: number) =>
  `송장 번호를 선택한 ${count}건만큼 줄마다 넣어 주세요.`

// 배송 중으로 바꿀 때 보내는 값. 송장 번호는 고른 건수와 줄 수가 같아야 한다 (줄은 표의 위에서부터 차례로 주문에 붙는다).
export function shipFormSchema(count: number) {
  return z.object({
    carrier: z.string().min(1),
    tracking: z
      .string()
      .transform(trackingLines)
      .refine((lines) => lines.length === count, shipCountMessage(count))
      .refine(
        (lines) => lines.every((line) => line.length <= TRACKING_MAX),
        TRACKING_TOO_LONG_MESSAGE,
      ),
  })
}

export type ShipFormInput = z.input<ReturnType<typeof shipFormSchema>>
export type ShipFormValues = z.output<ReturnType<typeof shipFormSchema>>
