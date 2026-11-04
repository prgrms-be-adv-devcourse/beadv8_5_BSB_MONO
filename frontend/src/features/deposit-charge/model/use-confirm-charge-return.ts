'use client'

import { useEffect, useRef } from 'react'

import { ApiError, cashApi } from '@/shared/api'

import { clearPendingCharge, readPendingCharge, savePendingCharge } from '../lib/pending-charge'
import { isChargeCanceled } from './request-charge'

/** 토스 결제창이 돌아오며 붙여 준 값 (successUrl·failUrl의 쿼리) */
export type ChargeReturn = {
  result?: string
  paymentKey?: string
  orderId?: string
  amount?: number
  /** failUrl에만 붙는 토스 오류 코드 */
  code?: string
}

/**
 * completed: 잔액에 들어감 · failed: 승인 거절·확정 실패(돈이 빠지지 않음) · canceled: 사용자가 결제창을 닫음
 * uncertain: 승인됐는지 아직 모름. 카드에서 돈이 빠졌으면 서버가 나중에 예치금에 넣는다
 */
export type ChargeOutcome = 'completed' | 'failed' | 'canceled' | 'uncertain'

/** 충전 확정 응답을 이만큼 기다리고, 넘으면 결과를 모르는 것으로 본다 */
const CONFIRM_TIMEOUT_MS = 30_000

type ChargeResult = {
  outcome: ChargeOutcome
  /**
   * 돌아가 다시 결제할 주문. 주소가 아니라 떠나기 전에 적어 둔 값(같은 충전 orderId일 때만)을 쓴다.
   * 주소를 고치거나 다른 충전의 주소를 다시 열어 상관없는 주문이 저절로 결제되지 않게 한다.
   */
  returnOrderId?: number
  /** 서버가 알려 준 거절 사유. 충전 확정 응답이 승인 거절(FAILED)일 때만 있다 */
  message?: string
}

async function confirm({
  result,
  paymentKey,
  orderId,
  amount,
  code,
}: ChargeReturn): Promise<ChargeResult> {
  const pending = readPendingCharge()
  const sameCharge = pending !== null && pending.orderId === orderId
  const returnOrderId = sameCharge ? pending.returnOrderId : undefined
  if (result !== 'success') {
    return { outcome: isChargeCanceled(code) ? 'canceled' : 'failed', returnOrderId }
  }
  // 떠나기 전에 적어 둔 충전과 다르면(주소를 고친 경우 등) 확정하지 않는다
  if (!paymentKey || !sameCharge || pending.amount !== amount) {
    return { outcome: 'failed', returnOrderId }
  }
  // 이미 결과를 모르는 채로 끝난 충전이면(새로고침 · 뒤로 가기) 확정을 다시 부르지 않는다.
  // 다시 부르면 서버가 토스에 승인을 한 번 더 요청한다. 결과는 잔액 조회로 확인한다.
  if (pending.uncertainAt !== undefined) return { outcome: 'uncertain', returnOrderId }
  // 보내기 전에 적는다. 응답을 기다리는 중에 새로고침해도 확정을 다시 부르지 않게 한다.
  savePendingCharge({ ...pending, uncertainAt: Date.now() })
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), CONFIRM_TIMEOUT_MS)
  try {
    const body = await cashApi.confirmWalletCharge(
      { paymentKey, orderId: pending.orderId, amount: pending.amount },
      { signal: controller.signal },
    )
    const outcome = body.data?.outcome
    if (outcome === 'COMPLETED') return { outcome: 'completed', returnOrderId }
    if (outcome === 'FAILED') return { outcome: 'failed', returnOrderId, message: body.message }
  } catch (error) {
    // 409(먼저 보낸 확정이 처리 중)를 뺀 4xx는 서버가 처리하기 전에 거절한 요청이라 돈이 빠지지 않았다
    if (error instanceof ApiError && error.status < 500 && error.status !== 409) {
      return { outcome: 'failed', returnOrderId }
    }
  } finally {
    clearTimeout(timer)
  }
  // 확인 중(UNCERTAIN) · 409 · 5xx · 네트워크 오류 · 응답 없음: 서버가 끝까지 처리했는지 모른다.
  // 다시 결제하면 새 주문번호라 카드에서 두 번 빠질 수 있어 기다리라고만 안내한다. 충전 기록은 남겨 둔다.
  return { outcome: 'uncertain', returnOrderId }
}

/**
 * 결제창에서 돌아오면 충전 확정(POST /api/v1/cash/charges/confirm)을 한 번 부르고 결과를 알린다.
 * 같은 orderId로 다시 불러도 서버가 두 번 적립하지 않는다(멱등). 결과를 모르면 충전 기록을 남겨 다시 부르지 않는다.
 */
export function useConfirmChargeReturn(
  params: ChargeReturn,
  onDone: (result: ChargeResult) => void,
) {
  const started = useRef(false)
  const latest = useRef({ params, onDone })
  useEffect(() => {
    latest.current = { params, onDone }
  })
  useEffect(() => {
    // 개발 모드의 effect 두 번 실행에도 한 번만 부른다
    if (started.current) return
    started.current = true
    const { params: current } = latest.current
    void confirm(current).then((result) => {
      // 결과를 아는 같은 충전만 지운다. 다른 충전의 주소를 열었을 때 결과를 모르는 충전 기록을 지우지 않는다.
      if (result.outcome !== 'uncertain' && readPendingCharge()?.orderId === current.orderId) {
        clearPendingCharge()
      }
      latest.current.onDone(result)
    })
  }, [])
}
