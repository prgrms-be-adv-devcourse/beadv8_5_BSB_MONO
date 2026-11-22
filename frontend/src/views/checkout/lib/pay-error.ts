import { z } from 'zod'

import { isQueueNotAdmitted } from '@/features/queue-admission'
import { ApiError } from '@/shared/api'

/**
 * 결제(POST /orders/{orderId}/payment)가 안 된 이유. 서버 문구가 아니라 오류 코드로 나눈다.
 * - insufficient: 예치금이 모자람(409 ORDER_INSUFFICIENT_BALANCE) → 모자란 금액(shortage)만큼 충전하고 같은 주문으로 다시 결제
 * - timeout: 결제 기한이 지남 · 대기열 입장 시간이 끝남(403 ORDER_QUEUE_NOT_ADMITTED) → 시간 초과 화면(M07-1)
 * - soldout: 남은 자리가 없음 → 자리 마감 화면(M07-2)
 * - failed: 그 밖의 실패 → 결제 실패 화면(M07-3)
 */
export type PayFailure =
  | { kind: 'insufficient'; /** 모자란 금액(원) */ shortage: number }
  | { kind: 'kit'; /** 서버 문구. 결제 화면에 남아 키트 칸 위에 보여 준다 */ message: string }
  | { kind: 'timeout' | 'soldout' | 'failed' }

// 키트 옵션을 못 골랐거나(400) 고른 옵션이 소진됨(409) → 실패 화면 없이 키트 칸에서 다시 고른다
const KIT_CODES = new Set(['ORDER_KIT_OPTION_INVALID', 'ORDER_KIT_OPTION_SOLD_OUT'])
const KIT_FALLBACK_MESSAGE = '키트 옵션을 다시 확인해 주세요.'

// 결제를 못 하게 된 주문(ORDER_NOT_PAYABLE)은 결제 화면에서는 거의 항상 기한이 지나 풀린 주문이라 시간 초과로 본다.
const TIMEOUT_CODES = new Set(['ORDER_PAYMENT_DEADLINE_PASSED', 'ORDER_NOT_PAYABLE'])
const SOLDOUT_CODES = new Set(['ORDER_SOLD_OUT', 'ORDER_APPLICATION_CLOSED'])

const InsufficientBody = z.object({ data: z.object({ shortage: z.number().int().nonnegative() }) })

export function toPayFailure(error: unknown): PayFailure {
  if (isQueueNotAdmitted(error)) return { kind: 'timeout' }
  if (!(error instanceof ApiError)) return { kind: 'failed' }
  if (error.code === 'ORDER_INSUFFICIENT_BALANCE') {
    const body = InsufficientBody.safeParse(error.body)
    // 부족 금액이 빠져 와도 최소 충전 금액(1만 원)으로 충전하면 된다
    return { kind: 'insufficient', shortage: body.success ? body.data.data.shortage : 0 }
  }
  if (error.code !== undefined && KIT_CODES.has(error.code)) {
    return { kind: 'kit', message: error.serverMessage ?? KIT_FALLBACK_MESSAGE }
  }
  if (error.code !== undefined && TIMEOUT_CODES.has(error.code)) return { kind: 'timeout' }
  if (error.code !== undefined && SOLDOUT_CODES.has(error.code)) return { kind: 'soldout' }
  return { kind: 'failed' }
}
