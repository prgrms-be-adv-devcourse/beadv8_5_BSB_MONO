import { describe, expect, it } from 'vitest'

import { RECOVERY_REASON, RECOVERY_STATUS, TAX_INVOICE_STATUS } from './labels'

describe('회수 · 세금계산서 문구', () => {
  it('회수 상태는 판매자가 할 일로 읽히게 바꾼다', () => {
    expect(RECOVERY_STATUS.회수_요청).toEqual({ label: '입금 대기', tone: 'neutral' })
    expect(RECOVERY_STATUS.회수_완료).toEqual({ label: '입금 완료', tone: 'success' })
    expect(RECOVERY_STATUS.회수_취소.label).toBe('회수 취소')
  })

  it('회수 사유와 세금계산서 상태', () => {
    expect(RECOVERY_REASON.대회_취소).toBe('대회 취소')
    expect(RECOVERY_REASON.일정_변경).toBe('일정 변경')
    expect(TAX_INVOICE_STATUS.발행_완료).toEqual({ label: '발행 완료', tone: 'success' })
    expect(TAX_INVOICE_STATUS.발행_실패).toEqual({ label: '발행 실패', tone: 'danger' })
  })
})
