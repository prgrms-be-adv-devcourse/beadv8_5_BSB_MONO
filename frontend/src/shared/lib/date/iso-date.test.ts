import { describe, expect, it } from 'vitest'

import { fromIsoDate, toIsoDate } from './iso-date'

describe('toIsoDate', () => {
  it('로컬 날짜를 그대로 쓴다 (자정 직전에도 하루가 밀리지 않는다)', () => {
    expect(toIsoDate(new Date(2026, 10, 15, 23, 59))).toBe('2026-11-15')
    expect(toIsoDate(new Date(2026, 0, 1, 0, 0))).toBe('2026-01-01')
  })
})

describe('fromIsoDate', () => {
  it('로컬 자정의 Date로 바꾼다', () => {
    const date = fromIsoDate('2026-11-15')

    expect([date.getFullYear(), date.getMonth(), date.getDate(), date.getHours()]).toEqual([
      2026, 10, 15, 0,
    ])
    expect(toIsoDate(date)).toBe('2026-11-15')
  })

  it('형식이 틀리거나 없는 날짜면 오류를 던진다', () => {
    expect(() => fromIsoDate('2026-2-3')).toThrow()
    expect(() => fromIsoDate('2026-02-30')).toThrow()
  })
})
