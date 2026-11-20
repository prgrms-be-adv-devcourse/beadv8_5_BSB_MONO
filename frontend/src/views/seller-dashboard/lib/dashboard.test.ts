import { describe, expect, it } from 'vitest'

import { formatKoreanMonthDay, soldRate } from './dashboard'

describe('formatKoreanMonthDay', () => {
  it('날짜 글자에서 월 · 일을 읽는다', () => {
    expect(formatKoreanMonthDay('2026-11-10')).toBe('11월 10일')
    expect(formatKoreanMonthDay('2026-01-05T00:00:00')).toBe('1월 5일')
  })
})

describe('soldRate', () => {
  it('정원 대비 판매 비율을 반올림한다', () => {
    expect(soldRate(1620, 2000)).toBe(81)
  })
  it('정원이 0이면 0이다', () => {
    expect(soldRate(10, 0)).toBe(0)
  })
})
