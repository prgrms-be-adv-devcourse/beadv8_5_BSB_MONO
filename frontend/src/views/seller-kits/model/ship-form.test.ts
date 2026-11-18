import { describe, expect, it } from 'vitest'

import { missingTracking, rowKey, shipFormSchema } from './ship-form'

describe('rowKey', () => {
  it('폼 경로에서 배열 칸으로 읽히지 않게 글자를 붙인다', () => {
    expect(rowKey(101)).toBe('k101')
  })
})

describe('missingTracking', () => {
  it('고른 주문 중 송장 번호가 비었거나 공백뿐인 것이 있으면 참이다', () => {
    expect(missingTracking([1, 2], { k1: '111', k2: '   ' })).toBe(true)
    expect(missingTracking([1, 2], { k1: '111' })).toBe(true)
  })

  it('모두 채워져 있으면 거짓이다', () => {
    expect(missingTracking([1, 2], { k1: '111', k2: '222' })).toBe(false)
  })
})

describe('shipFormSchema', () => {
  it('송장 번호의 앞뒤 공백을 빼서 돌려준다', () => {
    const result = shipFormSchema.parse({
      carrier: { k1: 'CJ대한통운' },
      tracking: { k1: '  111 ' },
    })
    expect(result.tracking.k1).toBe('111')
  })

  it('송장 번호가 40자를 넘으면 거절한다', () => {
    expect(
      shipFormSchema.safeParse({ carrier: {}, tracking: { k1: 'a'.repeat(41) } }).success,
    ).toBe(false)
  })
})
