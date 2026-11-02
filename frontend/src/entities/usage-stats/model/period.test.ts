import { describe, expect, it } from 'vitest'

import { resolveStatsPeriod, seoulToday, statsPeriodQuerySchema } from './period'

describe('resolveStatsPeriod', () => {
  it('지난 7일은 어제에서 끝나고, 앞 기간은 그 바로 앞 7일이다', () => {
    expect(resolveStatsPeriod({ period: '7d' }, '2026-11-02')).toEqual({
      current: { start: '2026-10-26', end: '2026-11-01' },
      previous: { start: '2026-10-19', end: '2026-10-25' },
    })
  })

  it('지난 30일은 달을 넘어 계산한다', () => {
    expect(resolveStatsPeriod({ period: '30d' }, '2026-03-01')).toEqual({
      current: { start: '2026-01-30', end: '2026-02-28' },
      previous: { start: '2025-12-31', end: '2026-01-29' },
    })
  })

  it('직접 고른 기간은 같은 길이의 앞 기간을 붙인다', () => {
    expect(
      resolveStatsPeriod(
        { period: 'custom', start: '2026-10-10', end: '2026-10-12' },
        '2026-11-02',
      ),
    ).toEqual({
      current: { start: '2026-10-10', end: '2026-10-12' },
      previous: { start: '2026-10-07', end: '2026-10-09' },
    })
  })

  it.each([
    ['거꾸로', '2026-10-12', '2026-10-10'],
    ['오늘 뒤', '2026-11-01', '2026-11-03'],
    ['366일 넘음', '2025-01-01', '2026-11-01'],
  ])('직접 고른 기간이 %s이면 null', (_, start, end) => {
    expect(resolveStatsPeriod({ period: 'custom', start, end }, '2026-11-02')).toBeNull()
  })
})

describe('statsPeriodQuerySchema', () => {
  it('직접 고르기는 날짜 형식이 아니면 받지 않는다', () => {
    expect(
      statsPeriodQuerySchema.safeParse({ period: 'custom', start: '2026-1-1', end: '2026-01-02' })
        .success,
    ).toBe(false)
    expect(statsPeriodQuerySchema.safeParse({ period: '90d' }).success).toBe(false)
  })
})

describe('seoulToday', () => {
  it('UTC로 전날 밤이어도 한국 날짜를 준다', () => {
    expect(seoulToday(new Date('2026-11-01T16:30:00Z'))).toBe('2026-11-02')
  })
})
