import { describe, expect, it } from 'vitest'

import type { WalletLedger } from '@/entities/wallet'

import { ledgerTitle } from './ledger'

const row = (ledgerType: WalletLedger['ledgerType']) => ({ ledgerType, refId: 7 }) as WalletLedger

describe('ledgerTitle', () => {
  it('카드 결제취소 환급 줄과 거절돼 되돌린 줄을 환급으로 보여 준다 (백엔드 BUK-22)', () => {
    expect(ledgerTitle(row('출금__PG결제취소_토스페이먼츠'))).toBe('환급 · 결제한 카드로 결제취소')
    expect(ledgerTitle(row('출금취소__PG결제취소_토스페이먼츠'))).toBe(
      '환급 취소 · 예치금으로 되돌림',
    )
  })
})
