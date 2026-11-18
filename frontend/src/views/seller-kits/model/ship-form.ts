import { z } from 'zod'

/** 송장 번호를 줄마다 하나씩 읽는다 (앞뒤 공백을 빼고, 빈 줄은 건너뛴다) */
export function trackingLines(text: string): string[] {
  return text
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
}

export const shipCountMessage = (count: number) =>
  `송장 번호를 선택한 ${count}건만큼 줄마다 넣어 주세요.`

// 배송 중으로 바꿀 때 보내는 값. 송장 번호는 고른 건수와 줄 수가 같아야 한다 (줄은 표의 위에서부터 차례로 주문에 붙는다).
export function shipFormSchema(count: number) {
  return z.object({
    carrier: z.string().min(1),
    tracking: z
      .string()
      .transform(trackingLines)
      .refine((lines) => lines.length === count, shipCountMessage(count)),
  })
}

export type ShipFormInput = z.input<ReturnType<typeof shipFormSchema>>
export type ShipFormValues = z.output<ReturnType<typeof shipFormSchema>>
