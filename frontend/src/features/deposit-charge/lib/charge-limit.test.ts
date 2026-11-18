import { describe, expect, it } from 'vitest'

import { chargeLimitMessage, koreaToday, todayChargedTotal } from './charge-limit'

// 2026-11-18 00:30 한국 시간 = 2026-11-17 15:30 UTC
const NOW = Date.parse('2026-11-17T15:30:00Z')

describe('충전 한도', () => {
  it('오늘 날짜는 한국 시간 자정으로 나눈다', () => {
    expect(koreaToday(NOW)).toBe('2026-11-18')
  })

  it('오늘 PG 충전만 더한다 (어제 충전 · 다른 종류는 빼고)', () => {
    const rows = [
      {
        ledgerType: '충전__PG결제_토스페이먼츠',
        amount: 300_000,
        createdAt: '2026-11-18T00:10:00',
      },
      {
        ledgerType: '충전__PG결제_토스페이먼츠',
        amount: 200_000,
        createdAt: '2026-11-17T23:59:59',
      },
      { ledgerType: '충전취소__PG결제취소', amount: -50_000, createdAt: '2026-11-18T00:20:00' },
    ]
    expect(todayChargedTotal(rows, NOW)).toBe(300_000)
  })

  it('1만 원 미만 · 1회 30만 원 초과를 막는다', () => {
    expect(chargeLimitMessage(9_000, 0)).toBe('1만 원부터 충전할 수 있어요.')
    expect(chargeLimitMessage(300_000, 0)).toBeNull()
    expect(chargeLimitMessage(310_000, 0)).toBe('한 번에 300,000원까지 충전할 수 있어요.')
  })

  it('오늘 합계와 더해 100만 원을 넘으면 남은 금액을 알려 준다', () => {
    expect(chargeLimitMessage(300_000, 700_000)).toBeNull()
    expect(chargeLimitMessage(300_000, 800_000)).toBe('오늘은 200,000원까지 더 충전할 수 있어요.')
    expect(chargeLimitMessage(10_000, 1_000_000)).toBe(
      '오늘은 더 충전할 수 없어요. 하루 1,000,000원까지 충전할 수 있어요.',
    )
  })

  it('오늘 합계를 모르면 일일 한도는 서버에 맡긴다', () => {
    expect(chargeLimitMessage(300_000, undefined)).toBeNull()
  })
})
