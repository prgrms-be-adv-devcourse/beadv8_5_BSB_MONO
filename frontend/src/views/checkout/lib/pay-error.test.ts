import { describe, expect, it } from 'vitest'

import { ApiError } from '@/shared/api'

import { toPayFailure } from './pay-error'

const conflict = (code: string, data: unknown = null) =>
  new ApiError(
    409,
    { status: 409, code, message: '아무 문구', data },
    '/api/v1/order/orders/1/payment',
  )

describe('toPayFailure', () => {
  it('서버 문구가 아니라 409 오류 코드로 잔액 부족 · 기한 지남 · 매진을 나눈다', () => {
    expect(toPayFailure(conflict('ORDER_INSUFFICIENT_BALANCE', { shortage: 12000 }))).toEqual({
      kind: 'insufficient',
      shortage: 12000,
    })
    expect(toPayFailure(conflict('ORDER_PAYMENT_DEADLINE_PASSED')).kind).toBe('timeout')
    expect(toPayFailure(conflict('ORDER_NOT_PAYABLE')).kind).toBe('timeout')
    expect(toPayFailure(conflict('ORDER_SOLD_OUT')).kind).toBe('soldout')
    // 코드 없이 문구만 같으면 나누지 않는다
    const messageOnly = new ApiError(
      409,
      { status: 409, message: '결제 기한이 지났습니다.', data: null },
      '/x',
    )
    expect(toPayFailure(messageOnly).kind).toBe('failed')
  })

  it('부족 금액이 빠져 오면 0으로 본다 (최소 충전 금액으로 충전한다)', () => {
    expect(toPayFailure(conflict('ORDER_INSUFFICIENT_BALANCE'))).toEqual({
      kind: 'insufficient',
      shortage: 0,
    })
  })

  it('대기열 입장 시간이 끝나 막히면(403 ORDER_QUEUE_NOT_ADMITTED) 문구와 상관없이 시간 초과로 본다', () => {
    const error = new ApiError(
      403,
      { status: 403, code: 'ORDER_QUEUE_NOT_ADMITTED', message: '아무 문구', data: null },
      '/api/v1/order/orders/1/payment',
    )
    expect(toPayFailure(error)).toEqual({ kind: 'timeout' })
  })

  it('409가 아니거나 서버에 닿지 못하면 실패로 본다', () => {
    expect(toPayFailure(new ApiError(500, null, '/x')).kind).toBe('failed')
    expect(toPayFailure(new TypeError('Failed to fetch')).kind).toBe('failed')
  })

  it('키트 옵션 오류(400 · 409)는 서버 문구를 담은 kit으로 나눈다', () => {
    const invalid = new ApiError(
      400,
      { status: 400, code: 'ORDER_KIT_OPTION_INVALID', message: '옵션을 골라 주세요.', data: null },
      '/x',
    )
    expect(toPayFailure(invalid)).toEqual({ kind: 'kit', message: '옵션을 골라 주세요.' })
    const soldOut = new ApiError(
      409,
      { status: 409, code: 'ORDER_KIT_OPTION_SOLD_OUT', message: '소진됐어요.', data: null },
      '/x',
    )
    expect(toPayFailure(soldOut)).toEqual({ kind: 'kit', message: '소진됐어요.' })
  })
})
