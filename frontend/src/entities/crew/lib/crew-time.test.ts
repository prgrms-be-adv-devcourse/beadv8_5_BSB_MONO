import { describe, expect, it } from 'vitest'

import {
  formatKstDateTime,
  formatRemaining,
  inviteCreatedLabel,
  parseKstDateTime,
} from './crew-time'

const NOW = parseKstDateTime('2026-10-30T15:08:00')

describe('crew-time', () => {
  it('한국 시각을 월/일 (요일) 시:분으로 쓴다', () => {
    expect(formatKstDateTime('2026-10-31T14:20:00')).toBe('10/31 (토) 14:20')
  })

  it('남은 시간을 시간과 분으로 쓰고, 지났으면 null이다', () => {
    expect(formatRemaining('2026-10-31T14:20:00', NOW)).toBe('23시간 12분')
    expect(formatRemaining('2026-10-30T16:08:00', NOW)).toBe('1시간')
    expect(formatRemaining('2026-10-30T15:08:30', NOW)).toBe('1분')
    expect(formatRemaining('2026-10-30T15:00:00', NOW)).toBeNull()
  })

  it('5분 안에 만든 초대는 방금 만든 링크다', () => {
    expect(inviteCreatedLabel('2026-10-30T15:06:00', NOW)).toBe('방금 만든 링크')
    expect(inviteCreatedLabel('2026-10-29T09:10:00', NOW)).toBe('10/29 (목) 09:10에 만든 링크')
  })
})
