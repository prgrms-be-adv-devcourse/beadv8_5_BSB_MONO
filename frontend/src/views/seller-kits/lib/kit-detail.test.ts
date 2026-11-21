import { describe, expect, it } from 'vitest'

import { detailFormSchema, formatMonthDay, formatTrackingNumber } from './kit-detail'

describe('formatTrackingNumber', () => {
  it('네 자리씩 띄워 보여 준다', () => {
    expect(formatTrackingNumber('684912013344')).toBe('6849 1201 3344')
    expect(formatTrackingNumber('6849120133')).toBe('6849 1201 33')
  })

  it('숫자만 있는 번호가 아니면 그대로 둔다', () => {
    expect(formatTrackingNumber('AB-1234')).toBe('AB-1234')
    expect(formatTrackingNumber(null)).toBe('')
  })
})

describe('formatMonthDay', () => {
  it('서버 시각(한국 시간)을 「월/일 (요일)」로 보여 준다', () => {
    expect(formatMonthDay('2026-11-16T10:00:00')).toBe('11/16 (월)')
    expect(formatMonthDay('2026-10-14T23:59:00')).toBe('10/14 (수)')
  })

  it('값이 없으면 빈 글자', () => {
    expect(formatMonthDay(null)).toBe('')
  })
})

describe('detailFormSchema', () => {
  const parse = (carrier: string, trackingNumber: string) =>
    detailFormSchema.safeParse({ carrier, trackingNumber })

  it('송장 번호는 숫자 10~12자리만 받는다', () => {
    expect(parse('CJ대한통운', '6849120133').success).toBe(true)
    expect(parse('CJ대한통운', ' 684912013344 ').success).toBe(true)
    const wrong = parse('CJ대한통운', '6849-12')
    expect(wrong.success).toBe(false)
    expect(wrong.error?.issues[0].message).toBe('송장 번호는 숫자 10~12자리예요')
    expect(parse('CJ대한통운', '6849120133445').success).toBe(false)
  })

  it('택배사를 고르지 않으면 막는다', () => {
    const empty = parse('', '6849120133')
    expect(empty.success).toBe(false)
    expect(empty.error?.issues[0].message).toBe('택배사를 골라 주세요')
  })
})
