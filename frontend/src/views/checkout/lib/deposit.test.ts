import { describe, expect, it } from 'vitest'

import { splitPayment } from './deposit'

describe('splitPayment', () => {
  it('1만 원보다 적게 모자라면 1만 원을 충전한다', () => {
    expect(splitPayment(45_000, 40_000)).toEqual({
      fromDeposit: 40_000,
      charge: 10_000,
      balanceAfter: 5_000,
    })
  })

  it('1만 원 넘게 모자라면 모자란 만큼 충전한다', () => {
    expect(splitPayment(80_000, 40_000)).toEqual({
      fromDeposit: 40_000,
      charge: 40_000,
      balanceAfter: 0,
    })
  })

  it('1회 충전 한도(30만 원)를 넘게 모자라면 30만 원까지만 충전한다', () => {
    expect(splitPayment(300_000, 0).charge).toBe(300_000)
    expect(splitPayment(450_000, 0)).toEqual({
      fromDeposit: 0,
      charge: 300_000,
      balanceAfter: -150_000,
    })
  })

  it('예치금이 넉넉하면 충전하지 않는다', () => {
    expect(splitPayment(15_000, 40_000)).toEqual({
      fromDeposit: 15_000,
      charge: 0,
      balanceAfter: 25_000,
    })
  })
})
