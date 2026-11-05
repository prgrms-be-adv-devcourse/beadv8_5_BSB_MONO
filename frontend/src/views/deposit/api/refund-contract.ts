// shortcut: BUK-21 응답 확정 전 가정, 백엔드 계약이 생기면 openapi로 교체
// 이 파일이 아직 백엔드에 없는 계약을 가정하는 유일한 곳이다. 가짜 응답은 shared/mocks/wallet.ts.
// 응답 모양은 2026-11-05 「예치금 환급 시안 의견」 전달문의 응답 제안을 따른다.
// 명세가 생기면 openapi/cash.json을 받아 orval로 만든 cashApi · cashZod로 바꾸고 이 파일을 지운다.
import { queryOptions } from '@tanstack/react-query'
import { z } from 'zod'

import type { WalletLedger } from '@/entities/wallet'
import { cashApi, customFetch, unwrapData, type RsData } from '@/shared/api'

/** 돌려받기 화면 조회 주소. 백엔드가 주소를 정하면 바꾼다 */
export const REFUND_PREVIEW_URL = '/api/v1/cash/withdrawals/preview'

/** 「2027-10-12」처럼 한국 시간 날짜. 그날 24:00까지 */
const kstDate = z.string().regex(/^\d{4}-\d{2}-\d{2}$/)

export const refundPlanSchema = z
  .object({
    balance: z.number().int().nonnegative(),
    cardRefundAmount: z.number().int().nonnegative(),
    accountRefundAmount: z.number().int().nonnegative(),
    accountRequired: z.boolean(),
    earliestCardCancelDate: kstDate.nullable(),
    charges: z.array(
      z.object({
        chargeId: z.number(),
        approvedAt: z.string().regex(/^\d{4}-\d{2}-\d{2}T/),
        chargedAmount: z.number().int(),
        remainingAmount: z.number().int(),
        cardCancelDate: kstDate,
        refundTo: z.enum(['CARD', 'ACCOUNT']),
        paymentMethodName: z.string(),
      }),
    ),
  })
  // 카드 몫이 있으면 맨 위 문장에 기한이 꼭 들어가야 한다
  .refine((plan) => plan.cardRefundAmount === 0 || plan.earliestCardCancelDate !== null)

export type RefundPlan = z.infer<typeof refundPlanSchema>

/** 돌려받기 화면 조회. 응답이 계약과 다르면 ZodError로 실패해 화면이 신청을 막는다 */
export const refundPlanQueryOptions = () =>
  queryOptions({
    queryKey: [REFUND_PREVIEW_URL],
    queryFn: async ({ signal }) =>
      refundPlanSchema.parse(
        unwrapData(await customFetch<RsData<unknown>>(REFUND_PREVIEW_URL, { signal })),
      ),
  })

export type RefundAccount = { bankCode: string; accountNumber: string; accountHolder: string }

/**
 * 환급 신청. 계좌로 보낼 몫이 없으면(accountRequired=false) 계좌 정보를 빼고 보낸다.
 * 지금 생성된 요청 타입은 계좌 정보가 필수라 그 경우만 타입을 맞춘다(백엔드가 선택으로 바꾸면 지운다).
 */
export const requestRefund = (account: RefundAccount | null, idempotencyKey: string) =>
  cashApi.requestWithdrawal(account ?? ({} as RefundAccount), { idempotencyKey })

/** 예치금 내역 충전 줄에 더해 달라고 한 두 값. 모두 쓴 충전은 둘 다 null */
type ChargeRemaining = { remainingAmount?: number | null; cardCancelDate?: string | null }

/** 남은 금액이 있는 충전 줄이면 남은 금액과 카드 취소 기한, 아니면 null */
export function chargeRemaining(row: WalletLedger) {
  const { remainingAmount, cardCancelDate } = row as WalletLedger & ChargeRemaining
  if (row.category !== 'CHARGE' || !remainingAmount || !cardCancelDate) return null
  return { remainingAmount, cardCancelDate }
}
