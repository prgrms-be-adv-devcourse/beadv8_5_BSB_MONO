import { z } from 'zod'

import type { KitStatus } from '@/entities/seller-kit'

// 탭 · 상세 창 머리에 쓰는 배송 상태 이름
export const TAB_LABEL: Record<KitStatus, string> = {
  PREPARING: '송장 입력 대기',
  SHIPPING: '배송 중',
  DELIVERED: '배송 완료',
}

export const CARRIERS = ['CJ대한통운', '롯데택배', '한진택배', '우체국택배'].map((c) => ({
  value: c,
  label: c,
}))

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

/** 송장 번호 칸이 받는 글자: 영문 · 숫자 · - (서버 스키마는 길이만 막는다) */
export const trackingOnly = (value: string) => value.replace(/[^A-Za-z0-9-]/g, '')
export const TRACKING_NOTICE = '영문, 숫자, -만 입력할 수 있어요'
