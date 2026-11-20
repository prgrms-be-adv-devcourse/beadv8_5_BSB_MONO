import { beforeEach, describe, expect, it } from 'vitest'

import { saveChargeNotice, takeChargeNotice } from './charge-notice'

beforeEach(() => sessionStorage.clear())

describe('충전 결과 넘기기', () => {
  it('한 번만 읽힌다', () => {
    saveChargeNotice({ kind: 'failed', message: '일일 충전 한도는 1,000,000원입니다.' }, 1_000)

    expect(takeChargeNotice(2_000)).toEqual({
      kind: 'failed',
      message: '일일 충전 한도는 1,000,000원입니다.',
    })
    expect(takeChargeNotice(2_000)).toBeNull()
  })

  it('1분이 지난 결과는 엉뚱한 곳에 남은 것이라 버린다', () => {
    saveChargeNotice({ kind: 'completed' }, 1_000)

    expect(takeChargeNotice(1_000 + 60_001)).toBeNull()
  })
})
