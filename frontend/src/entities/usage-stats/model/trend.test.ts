import { describe, expect, it } from 'vitest'

import { toTrendStats, trendDeltaText } from './trend'

const PERIOD = {
  current: { start: '2026-10-30', end: '2026-11-01' },
  previous: { start: '2026-10-27', end: '2026-10-29' },
}

describe('toTrendStats', () => {
  it('이번 기간의 모든 날짜를 늘어놓고, 기록이 없는 날은 0명으로 채운다', () => {
    expect(toTrendStats(PERIOD, { current: { '2026-10-30': 12, '2026-11-01': 5 } }).points).toEqual(
      [
        { date: '2026-10-30', value: 12 },
        { date: '2026-10-31', value: 0 },
        { date: '2026-11-01', value: 5 },
      ],
    )
  })

  it('큰 숫자와 배지는 두 기간의 날짜별 사람 수 합계다', () => {
    const stats = toTrendStats(PERIOD, {
      current: { '2026-10-30': 12, '2026-11-01': 5 },
      // 지난 기간 밖의 날짜는 더하지 않는다
      previous: { '2026-10-27': 3, '2026-10-29': 4, '2026-10-30': 99 },
    })

    expect(stats).toMatchObject({ total: 17, previousTotal: 7 })
  })

  it('GA4가 기간을 통째로 비워 줘도 0명 날짜가 그려진다', () => {
    expect(toTrendStats(PERIOD, {}).points.map((p) => p.value)).toEqual([0, 0, 0])
  })
})

describe('trendDeltaText', () => {
  it('지난 기간보다 늘어난 사람 수를 부호와 함께 쓴다', () => {
    expect(trendDeltaText(1180, 1169)).toBe('+11')
    expect(trendDeltaText(7, 10)).toBe('\u22123')
    expect(trendDeltaText(5, 5)).toBe('0')
    expect(trendDeltaText(3200, 1000)).toBe('+2,200')
  })
})
