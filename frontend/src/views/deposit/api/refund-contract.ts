// shortcut: 환급 화면용 충전 내역 조회(GET /wallets/refundable-charges)가 openapi/cash.json에 아직 없어 손으로 쓴 스키마다.
// 명세를 다시 받으면 orval로 만든 cashApi · cashZod로 바꾸고 이 파일을 지운다. 가짜 응답은 shared/mocks/wallet.ts.
import { queryOptions } from '@tanstack/react-query'
import { z, ZodError } from 'zod'

import type { WalletLedger } from '@/entities/wallet'
import { cashApi, customFetch, unwrapData, type RsData } from '@/shared/api'

/** 돌려받기 화면 조회 주소 (백엔드 BUK-21) */
export const REFUND_PREVIEW_URL = '/api/v1/cash/wallets/refundable-charges'

/** 「2027-10-12」처럼 한국 시간 날짜. 그날 24:00까지. 일시(chargedAt)도 한국 시간이라 글자를 그대로 쓴다 */
const kstDate = z.string().regex(/^\d{4}-\d{2}-\d{2}$/)

/**
 * 조회 응답을 화면이 쓰는 환급 계획으로 바꾼다.
 * 카드 몫 = 기한이 지나지 않은(expired=false) 충전 건의 남은 금액 합, 계좌 몫 = 잔액 − 카드 몫.
 * 계좌 몫을 expired=true 건의 합으로 구하면 안 된다. 판매자 회원의 정산 대금처럼 충전 건이 없는 잔액은
 * charges에 나오지 않지만 계좌로 보내야 하기 때문이다(백엔드 환급 신청도 같은 식으로 나눈다).
 */
export const refundPlanSchema = z
  .object({
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
  })
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
    queryKey: [REFUND_PREVIEW_URL],
    queryFn: async ({ signal }) =>
      refundPlanSchema.parse(
        unwrapData(await customFetch<RsData<unknown>>(REFUND_PREVIEW_URL, { signal })),
      ),
    // 응답 모양이 틀린 것은 다시 받아도 같으니 바로 안내한다
    retry: (count, error) => !(error instanceof ZodError) && count < 3,
  })

export type RefundAccount = { bankCode: string; accountNumber: string; accountHolder: string }

/**
 * 환급 신청. 계좌로 보낼 몫이 없으면(accountRequired=false) 계좌 정보를 빼고 보낸다.
 * 지금 생성된 요청 타입은 계좌 정보가 필수라 그 경우만 타입을 맞춘다(백엔드가 선택으로 바꾸면 지운다).
 */
export const requestRefund = (account: RefundAccount | null, idempotencyKey: string) =>
  cashApi.requestWithdrawal(account ?? ({} as RefundAccount), { idempotencyKey })

/**
 * 예치금 내역 충전 줄에 더해 달라고 한 두 값. 모두 쓴 충전은 둘 다 null.
 * 내역 응답을 그대로 쓰므로 읽을 수 있다. 내역을 zod로 걸러 모르는 값을 버리게 되면 여기도 바꾼다.
 */
type ChargeRemaining = { remainingAmount?: number | null; cardCancelDate?: string | null }

/** 남은 금액이 있는 충전 줄이면 남은 금액과 카드 취소 기한, 아니면 null */
export function chargeRemaining(row: WalletLedger) {
  const { remainingAmount, cardCancelDate } = row as WalletLedger & ChargeRemaining
  if (row.category !== 'CHARGE' || !remainingAmount || !cardCancelDate) return null
  return { remainingAmount, cardCancelDate }
}
