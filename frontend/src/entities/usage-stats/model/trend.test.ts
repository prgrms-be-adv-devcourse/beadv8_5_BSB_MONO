import { describe, expect, it } from 'vitest'

import { toTrendPoints } from './trend'

const PERIOD = {
  current: { start: '2026-10-30', end: '2026-11-01' },
  previous: { start: '2026-10-27', end: '2026-10-29' },
}

describe('toTrendPoints', () => {
  it('기간의 모든 날짜를 늘어놓고, 기록이 없는 날은 0명으로 채운다', () => {
    expect(
      toTrendPoints(PERIOD, {
        current: { '2026-10-30': 12, '2026-11-01': 5 },
        previous: { '2026-10-28': 9 },
      }),
    ).toEqual([
      { date: '2026-10-30', previousDate: '2026-10-27', value: 12, previousValue: 0 },
      { date: '2026-10-31', previousDate: '2026-10-28', value: 0, previousValue: 9 },
      { date: '2026-11-01', previousDate: '2026-10-29', value: 5, previousValue: 0 },
    ])
  })

  it('GA4가 기간을 통째로 비워 줘도 0명 날짜가 그려진다', () => {
    expect(toTrendPoints(PERIOD, {}).map((p) => p.value)).toEqual([0, 0, 0])
  })
})
