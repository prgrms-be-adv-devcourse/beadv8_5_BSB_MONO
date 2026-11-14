// 환급 화면용 충전 내역 조회(GET /wallets/refundable-charges)를 부르고, 화면이 쓰는 카드 몫 · 계좌 몫으로 나눈다.
// 요청 함수와 응답 타입은 orval 생성 코드(cashApi · cashModel)다. 가짜 응답은 shared/mocks/wallet.ts.
import { queryOptions } from '@tanstack/react-query'
import { z, ZodError } from 'zod'

import type { WalletLedger } from '@/entities/wallet'
import { cashApi, unwrapData, type cashModel } from '@/shared/api'

/** 「2027-10-12」처럼 한국 시간 날짜. 그날 24:00까지. 일시(chargedAt)도 한국 시간이라 글자를 그대로 쓴다 */
const kstDate = z.string().regex(/^\d{4}-\d{2}-\d{2}$/)

/**
 * 조회 응답을 화면이 쓰는 환급 계획으로 바꾼다.
 * 카드 몫 = 기한이 지나지 않은(expired=false) 충전 건의 남은 금액 합, 계좌 몫 = 잔액 − 카드 몫.
 * 계좌 몫을 expired=true 건의 합으로 구하면 안 된다. 판매자 회원의 정산 대금처럼 충전 건이 없는 잔액은
 * charges에 나오지 않지만 계좌로 보내야 하기 때문이다(백엔드 환급 신청도 같은 식으로 나눈다).
 */
// shortcut: 생성된 cashZod.GetRefundableChargesResponse는 일시에 시간대를 요구해 서버의 「2026-10-13T09:00:00」을 거부한다.
// 그래서 같은 모양을 여기서 검사하고 생성 타입(RefundableChargesDto)과 맞는지 satisfies로 묶는다. orval이 시간대 없는 일시를 받게 되면 cashZod로 바꾼다.
const refundableChargesSchema = z.object({
  balance: z.number().int().nonnegative(),
  earliestLastCancelableDate: kstDate.nullable(),
  charges: z.array(
    z.object({
      chargeId: z.number().int(),
      chargedAt: z.string().regex(/^\d{4}-\d{2}-\d{2}T/),
      amount: z.number().int().nonnegative(),
      remainingAmount: z.number().int().nonnegative(),
      lastCancelableDate: kstDate,
      expired: z.boolean(),
    }),
  ),
}) satisfies z.ZodType<cashModel.RefundableChargesDto>

export const refundPlanSchema = refundableChargesSchema
  .transform((res) => {
    const charges = res.charges
      .filter((charge) => charge.remainingAmount > 0)
      .map((charge) => ({
        chargeId: charge.chargeId,
        approvedAt: charge.chargedAt,
        chargedAmount: charge.amount,
        remainingAmount: charge.remainingAmount,
        cardCancelDate: charge.lastCancelableDate,
        refundTo: charge.expired ? ('ACCOUNT' as const) : ('CARD' as const),
      }))
    const cardRefundAmount = charges
      .filter((charge) => charge.refundTo === 'CARD')
      .reduce((sum, charge) => sum + charge.remainingAmount, 0)
    const accountRefundAmount = res.balance - cardRefundAmount
    return {
      balance: res.balance,
      cardRefundAmount,
      accountRefundAmount,
      accountRequired: accountRefundAmount > 0,
      earliestCardCancelDate: res.earliestLastCancelableDate,
      charges,
    }
  })
  // 서로 어긋난 값이 오면 금액을 잘못 안내하게 되므로 신청을 막는다
  .refine((plan) => {
    // 카드 몫이 있으면 맨 위 문장에 기한이 꼭 들어가야 한다
    const hasCardDate = plan.cardRefundAmount === 0 || plan.earliestCardCancelDate !== null
    // 카드 몫이 잔액보다 크면 계좌 몫이 음수가 된다
    return hasCardDate && plan.accountRefundAmount >= 0
  })

export type RefundPlan = z.output<typeof refundPlanSchema>

/** 돌려받기 화면 조회. 응답이 계약과 다르면 ZodError로 실패해 화면이 신청을 막는다 */
export const refundPlanQueryOptions = () =>
  queryOptions({
    queryKey: cashApi.getGetRefundableChargesQueryKey(),
    queryFn: async ({ signal }) =>
      refundPlanSchema.parse(unwrapData(await cashApi.getRefundableCharges({ signal }))),
    // 응답 모양이 틀린 것은 다시 받아도 같으니 바로 안내한다
    retry: (count, error) => !(error instanceof ZodError) && count < 3,
  })

export type RefundAccount = { bankCode: string; accountNumber: string; accountHolder: string }

/** 환급 신청. 계좌로 보낼 몫이 없으면(accountRequired=false) 계좌 정보를 빼고 보낸다 */
export const requestRefund = (account: RefundAccount | null, idempotencyKey: string) =>
  cashApi.requestWithdrawal(account ?? {}, { idempotencyKey })

/** 남은 금액이 있는 충전 줄이면 남은 금액 · 카드 취소 마지막 날 · 기한 지남 여부, 아니면 null */
export function chargeRemaining(row: WalletLedger) {
  const charge = row.charge
  if (row.category !== 'CHARGE' || !charge || charge.remainingAmount <= 0) return null
  return {
    remainingAmount: charge.remainingAmount,
    cardCancelDate: charge.lastCancelableDate,
    expired: charge.expired,
  }
}
