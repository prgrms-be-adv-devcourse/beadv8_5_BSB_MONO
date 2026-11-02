'use client'

import type { Route } from 'next'
import { useRouter } from 'next/navigation'

import { AppHeader } from '@/widgets/app-header'
import {
  useConfirmChargeReturn,
  type ChargeOutcome,
  type ChargeReturn,
} from '@/features/deposit-charge'
import { trackEvent } from '@/shared/lib'
import { Skeleton } from '@/shared/ui'

/**
 * 결과마다 갈 곳. 시안에 충전 결과 화면이 없어 새 문구 없이 기존 화면으로 보낸다.
 * - 결제 중 충전: 성공하면 결제 화면이 같은 주문으로 다시 결제(charged=1), 실패하면 결제 실패(M07-3), 닫았으면 결제 화면 그대로
 * - 예치금에서 충전: 성공하면 예치금(M17)에서 늘어난 잔액을 보고, 실패·닫음이면 충전 화면(M21)으로
 */
function nextHref(outcome: ChargeOutcome, returnOrderId: number | undefined): Route {
  if (returnOrderId === undefined)
    return outcome === 'completed' ? '/me/deposit' : '/me/deposit/charge'
  const checkout = `/checkout?orderId=${returnOrderId}`
  if (outcome === 'completed') return `${checkout}&charged=1` as Route
  if (outcome === 'failed') return `${checkout}&charge=failed` as Route
  return checkout as Route
}

// 토스 결제창이 successUrl·failUrl로 돌려보내는 화면. 충전을 확정하는 동안 로딩만 보여 준다.
export function DepositChargeReturnPage(params: ChargeReturn) {
  const router = useRouter()
  useConfirmChargeReturn(params, ({ outcome, returnOrderId }) => {
    if (outcome === 'completed') {
      trackEvent('deposit_charge_done', {
        amount: params.amount ?? 0,
        duringCheckout: returnOrderId !== undefined,
      })
    }
    router.replace(nextHref(outcome, returnOrderId))
  })
  return (
    <>
      <AppHeader type="sub" title="예치금 충전" />
      <main aria-busy className="flex flex-1 flex-col gap-4 px-4 py-6">
        <Skeleton shape="rect" className="h-64" />
      </main>
    </>
  )
}
