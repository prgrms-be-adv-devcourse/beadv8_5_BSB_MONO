import { describe, expect, it } from 'vitest'

import { HOLD_ERRORS, holdFormSchema } from './hold-form'

const reasonType = '대회_취소_처리'

describe('holdFormSchema', () => {
  it('앞뒤 공백을 빼고 번호를 숫자로 바꾼다', () => {
    expect(holdFormSchema.parse({ payoutId: ' 12 ', reasonType })).toEqual({
      payoutId: 12,
      reasonType,
    })
  })

  it('양의 정수가 아니거나 너무 큰 번호는 거절한다', () => {
    for (const payoutId of ['', '0', '012', '1.5', '-3', 'ST-1', '99999999999999999999']) {
      const result = holdFormSchema.safeParse({ payoutId, reasonType })
      expect(result.success).toBe(false)
      expect(result.error?.issues[0].message).toBe(HOLD_ERRORS.payoutId)
    }
  })

  it('없는 보류 사유는 거절한다', () => {
    expect(holdFormSchema.safeParse({ payoutId: '1', reasonType: '기타' }).success).toBe(false)
  })
})
