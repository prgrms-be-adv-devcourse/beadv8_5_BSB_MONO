import { describe, expect, it } from 'vitest'

import { formatDateTime, formatRelative } from './format'

const NOW = new Date('2026-11-10T12:00:00')

describe('formatRelative', () => {
  it('시간 간격에 따라 방금 · 분 · 시간 · 어제 · 날짜로 쓴다', () => {
    expect(formatRelative('2026-11-10T11:59:40', NOW)).toBe('방금')
    expect(formatRelative('2026-11-10T11:48:00', NOW)).toBe('12분 전')
    expect(formatRelative('2026-11-10T09:00:00', NOW)).toBe('3시간 전')
    expect(formatRelative('2026-11-09T09:00:00', NOW)).toBe('어제')
    expect(formatRelative('2026-11-05T09:00:00', NOW)).toBe('11월 5일')
  })
})

describe('formatDateTime', () => {
  it('월 일 시:분으로 쓴다', () => {
    expect(formatDateTime('2026-11-09T21:04:00')).toBe('11월 9일 21:04')
  })
})
