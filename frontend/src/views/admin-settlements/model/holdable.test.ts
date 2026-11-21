import { describe, expect, it } from 'vitest'

import type { payoutModel } from '@/shared/api'

import { holdBlockLabel } from './holdable'

const payout = (status: payoutModel.PayoutStatus) => ({ status }) as payoutModel.PayoutDto
const hold = {} as payoutModel.PayoutHoldDto

describe('holdBlockLabel', () => {
  it('지급 예정이고 진행 중인 보류가 없으면 막지 않는다', () => {
    expect(holdBlockLabel(payout('지급_예정'), [])).toBeNull()
  })

  it('진행 중인 보류가 있으면 보류 중으로 막는다', () => {
    expect(holdBlockLabel(payout('지급_예정'), [hold])).toBe('보류 중')
  })

  it('지급보류 상태면 보류 목록이 비어 있어도 보류 중으로 막는다 (정산 ID로 찾은 줄은 보류 목록이 없다)', () => {
    expect(holdBlockLabel(payout('지급보류'), [])).toBe('보류 중')
  })

  it('지급 전이 아닌 상태는 백엔드가 거절하므로 상태 문구로 막는다', () => {
    expect(holdBlockLabel(payout('지급_중'), [])).toBe('지급 중')
    expect(holdBlockLabel(payout('지급_완료'), [])).toBe('지급 완료')
    expect(holdBlockLabel(payout('취소'), [])).toBe('취소')
    expect(holdBlockLabel(payout('지급_불일치'), [])).toBe('지급 불일치')
    expect(holdBlockLabel(payout('지급_확인_필요'), [])).toBe('지급 처리 중')
  })
})
