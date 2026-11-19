import { describe, expect, it } from 'vitest'

import { saleActionOf } from './sale-action'

describe('판매 상태에 맞는 버튼', () => {
  it('판매 예정은 지금 판매 시작, 판매 중은 판매 멈추기, 판매 중지는 판매 다시 열기', () => {
    expect(saleActionOf('SCHEDULED')).toMatchObject({ action: 'open', label: '지금 판매 시작' })
    expect(saleActionOf('ON_SALE')).toMatchObject({ action: 'stop', label: '판매 멈추기' })
    expect(saleActionOf('STOPPED')).toMatchObject({ action: 'resume', label: '판매 다시 열기' })
  })

  it('판매 종료 · 취소 · 모르는 상태는 버튼이 없다', () => {
    expect(saleActionOf('CLOSED')).toBeNull()
    expect(saleActionOf('CANCELED')).toBeNull()
    expect(saleActionOf(undefined)).toBeNull()
  })
})
