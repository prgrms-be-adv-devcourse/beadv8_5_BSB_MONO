import { describe, expect, it } from 'vitest'

import {
  formatDateTimeWithWeekday,
  formatDayWithWeekday,
  formatDeduction,
  formatMonthDayTime,
  formatSupplyMonth,
  supplyMonthTitle,
} from './format'

describe('정산 화면 날짜 형식', () => {
  it('날짜 → 「11/1 (일)」', () => {
    expect(formatDayWithWeekday('2026-11-01')).toBe('11/1 (일)')
    expect(formatDayWithWeekday('2026-11-01T06:10:00')).toBe('11/1 (일)')
  })

  it('날짜시간 → 「11/1 (일) 06:10」', () => {
    expect(formatMonthDayTime('2026-11-01T06:10:02')).toBe('11/1 (일) 06:10')
  })

  it('날짜시간 → 「2026-10-01 (목) 06:10」', () => {
    expect(formatDateTimeWithWeekday('2026-10-01T06:10:00')).toBe('2026-10-01 (목) 06:10')
  })

  it('공급 월 → 「2026.10」, 「2026년 10월 공급분」', () => {
    expect(formatSupplyMonth('2026-10')).toBe('2026.10')
    expect(supplyMonthTitle('2026-09')).toBe('2026년 9월 공급분')
  })
})

describe('formatDeduction', () => {
  it('빼는 금액은 응답 값 앞에 빼기 기호(−)를 붙인다', () => {
    expect(formatDeduction(440_000)).toBe('−440,000원')
  })

  it('0원은 기호 없이 0원', () => {
    expect(formatDeduction(0)).toBe('0원')
  })
})
