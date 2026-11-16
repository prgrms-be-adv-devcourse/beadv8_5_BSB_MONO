import { describe, expect, it } from 'vitest'

import { courseSales, formatBaseDate, formatKoreanMonthDay, soldRate } from './dashboard'

describe('formatBaseDate', () => {
  it('월 · 일 · 요일 기준 문구를 만든다', () => {
    expect(formatBaseDate(new Date(2026, 10, 3))).toBe('11월 3일(화) 기준')
  })
})

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

describe('courseSales', () => {
  it('판매 · 결제 대기 · 남은 자리를 더하고, 막대는 가장 큰 정원을 기준으로 잰다', () => {
    const result = courseSales([
      { raceCourseId: 1, name: '하프 21K', quota: 2000, sold: 1620, reserved: 30 },
      { raceCourseId: 2, name: '10K', quota: 1000, sold: 500, reserved: 10 },
    ])
    expect(result.sold).toBe(2120)
    expect(result.reserved).toBe(40)
    expect(result.remaining).toBe(840)
    expect(result.bars[0]).toMatchObject({ quotaPct: 100, fillPct: 81 })
    expect(result.bars[1]).toMatchObject({ quotaPct: 50, fillPct: 50 })
  })

  it('코스가 없거나 정원이 0이어도 0으로 나누지 않는다', () => {
    expect(courseSales([])).toEqual({ sold: 0, reserved: 0, remaining: 0, bars: [] })
    expect(courseSales([{ raceCourseId: 1, quota: 0, sold: 0 }]).bars[0].fillPct).toBe(0)
  })

  it('정원보다 많이 잡혀도 남은 자리는 0 밑으로 내려가지 않는다', () => {
    expect(courseSales([{ quota: 10, sold: 9, reserved: 3 }]).remaining).toBe(0)
  })
})
