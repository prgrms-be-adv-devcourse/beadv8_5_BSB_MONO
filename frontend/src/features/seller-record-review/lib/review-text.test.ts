import { describe, expect, it } from 'vitest'

import { formatNetTime, reviewReasonSentences, reviewReasonShort } from './review-text'

describe('formatNetTime', () => {
  it('초를 시:분:초로 바꾼다', () => {
    expect(formatNetTime(5405)).toBe('1:30:05')
    expect(formatNetTime(16980)).toBe('4:43:00')
    expect(formatNetTime(59)).toBe('0:00:59')
  })
})

describe('심사 사유', () => {
  it('판정 사유 뒤에 서버 확인 결과를 한 번씩만 붙인다', () => {
    const s = {
      reasons: ['TOO_FAST' as const],
      serverCheck: {
        NET_TIME: 'MISMATCH' as const,
        NET_LABEL: 'MISMATCH' as const,
        RACE_DATE: 'UNREADABLE' as const,
        COURSE: 'MATCH' as const,
      },
    }
    expect(reviewReasonShort(s)).toBe('기록 너무 빠름 · 읽은 값 다름 · 읽지 못함')
    expect(reviewReasonSentences(s)).toEqual([
      '풀 2시간보다 빠른 속도예요',
      '넷타임 · 서버가 사진에서 읽은 값과 달라요',
      '넷타임 표시 · 서버가 사진에서 읽은 값과 달라요',
      '대회일 · 서버가 사진에서 읽지 못했어요',
    ])
  })

  it('확인 중(빈 결과)이면 판정 사유만 보인다', () => {
    expect(reviewReasonShort({ reasons: [], serverCheck: {} })).toBe('')
  })
})
