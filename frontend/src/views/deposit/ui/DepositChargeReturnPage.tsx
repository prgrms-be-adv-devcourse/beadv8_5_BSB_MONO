'use client'

import type { Route } from 'next'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useState } from 'react'

import { AppHeader } from '@/widgets/app-header'
import {
  useConfirmChargeReturn,
  type ChargeOutcome,
  type ChargeReturn,
} from '@/features/deposit-charge'
import { trackEvent } from '@/shared/lib'
import { BottomCta, buttonClassName, EmptyState, Skeleton } from '@/shared/ui'

/**
 * 이 화면에서 바로 보여 주지 않는 결과마다 갈 곳(확인 중과 사유가 있는 승인 거절은 ChargeResult가 보여 준다).
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

/** 이 화면에서 바로 보여 주는 결과. 승인 거절은 서버가 알려 준 사유가 있을 때만 여기서 보여 준다 */
type ShownResult =
  | { kind: 'rejected'; message: string; returnOrderId?: number }
  | { kind: 'uncertain'; returnOrderId?: number }

// 토스 결제창이 successUrl·failUrl로 돌려보내는 화면. 충전을 확정하는 동안 로딩을 보여 주고, 결과에 따라 옮기거나 결과를 보여 준다.
export function DepositChargeReturnPage(params: ChargeReturn) {
  const router = useRouter()
  const [shown, setShown] = useState<ShownResult | null>(null)
  useConfirmChargeReturn(params, ({ outcome, returnOrderId, message }) => {
    if (outcome === 'uncertain') return setShown({ kind: 'uncertain', returnOrderId })
    if (outcome === 'failed' && message) {
      return setShown({ kind: 'rejected', message, returnOrderId })
    }
    if (outcome === 'completed') {
      trackEvent('deposit_charge_done', {
        amount: params.amount ?? 0,
        duringCheckout: returnOrderId !== undefined,
      })
    }
    router.replace(nextHref(outcome, returnOrderId))
  })
  if (shown) return <ChargeResult result={shown} />
  return (
    <>
      <AppHeader type="sub" title="예치금 충전" />
      <main aria-busy className="flex flex-1 flex-col gap-4 px-4 py-6">
        <Skeleton shape="rect" className="h-64" />
      </main>
    </>
  )
}

// 결제 결과 화면(M07-1~3)과 같은 모양. 시안이 없어 문구만 새로 쓴다.
function ChargeResult({ result }: { result: ShownResult }) {
  // 결제 화면으로 돌아갈 때 charged=1을 붙이지 않는다. 붙이면 그 주문을 바로 다시 결제한다.
  const action =
    result.returnOrderId !== undefined
      ? {
          label: '결제 화면으로 돌아가기',
          href: `/checkout?orderId=${result.returnOrderId}` as Route,
        }
      : result.kind === 'uncertain'
        ? { label: '예치금 보기', href: '/me/deposit' as Route }
        : { label: '다시 충전하기', href: '/me/deposit/charge' as Route }
  // 확인 중일 때 응답의 잔액은 이 충전이 들어가기 전 값이라 보여 주지 않는다
  return (
    <>
      <AppHeader type="sub" title="예치금 충전" />
      <main className="flex flex-1 items-center justify-center px-4 py-6">
        {result.kind === 'uncertain' ? (
          <EmptyState
            title="충전 결과를 확인하고 있어요"
            description={
              <>
                카드 결제가 됐다면 예치금에 자동으로 들어가요.
                <br />
                다시 충전하지 말고 잠시 후 잔액을 확인해 주세요.
              </>
            }
          />
        ) : (
          <EmptyState
            title="충전하지 못했어요"
            description={
              <>
                {result.message}
                <br />
                결제된 금액은 없어요.
              </>
            }
          />
        )}
      </main>
      <BottomCta
        className="sticky bottom-0 z-10"
        primary={
          <Link href={action.href} className={buttonClassName({ size: 'l' })}>
            {action.label}
          </Link>
        }
      />
    </>
  )
}
