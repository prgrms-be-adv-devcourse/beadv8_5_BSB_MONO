import { z } from 'zod'

/** 줄마다 넣는 택배사 · 송장 번호. 키는 rowKey(주문 번호) */
export const shipFormSchema = z.object({
  carrier: z.record(z.string(), z.string()),
  tracking: z.record(z.string(), z.string().trim().max(40)),
})

export type ShipFormValues = z.infer<typeof shipFormSchema>

/** 숫자만 있는 키는 react-hook-form 경로에서 배열 칸으로 읽혀서 글자를 붙인다 */
export const rowKey = (orderItemId: number) => `k${orderItemId}`

/** 고른 주문 중 송장 번호가 빈 것이 있는지 */
export function missingTracking(ids: number[], tracking: Record<string, string>): boolean {
  return ids.some((id) => !(tracking[rowKey(id)] ?? '').trim())
}
