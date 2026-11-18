import { describe, expect, it } from 'vitest'

import { orderZod } from '@/shared/api-zod'

import {
  shipCountMessage,
  shipFormSchema,
  TRACKING_TOO_LONG_MESSAGE,
  trackingLines,
} from './ship-form'

describe('trackingLines', () => {
  it('줄마다 앞뒤 공백을 빼고 빈 줄은 건너뛴다', () => {
    expect(trackingLines(' 111 \n\n  222\n   \n')).toEqual(['111', '222'])
  })
})

describe('shipFormSchema', () => {
  it('송장 번호 줄 수가 고른 건수와 같으면 통과한다', () => {
    expect(shipFormSchema(2).parse({ carrier: 'CJ대한통운', tracking: '111\n\n222' })).toEqual({
      carrier: 'CJ대한통운',
      tracking: ['111', '222'],
    })
  })

  it('줄 수가 모자라거나 남으면 건수를 알리며 거절한다', () => {
    for (const tracking of ['', '111', '1\n2\n3']) {
      const result = shipFormSchema(2).safeParse({ carrier: 'CJ대한통운', tracking })
      expect(result.error?.issues[0].message).toBe(shipCountMessage(2))
    }
  })

  it('송장 번호 한 줄이 서버 한도보다 길면 거절하고, 한도와 같으면 통과한다', () => {
    const max = orderZod.shipBodyTrackingNumberMax
    const tooLong = shipFormSchema(2).safeParse({
      carrier: 'CJ대한통운',
      tracking: `111\n${'9'.repeat(max + 1)}`,
    })
    expect(tooLong.success).toBe(false)
    expect(tooLong.error?.issues[0].message).toBe(TRACKING_TOO_LONG_MESSAGE)
    expect(
      shipFormSchema(1).safeParse({ carrier: 'CJ대한통운', tracking: '9'.repeat(max) }).success,
    ).toBe(true)
  })

  it('택배사가 비면 거절한다', () => {
    expect(shipFormSchema(1).safeParse({ carrier: '', tracking: '111' }).success).toBe(false)
  })
})
