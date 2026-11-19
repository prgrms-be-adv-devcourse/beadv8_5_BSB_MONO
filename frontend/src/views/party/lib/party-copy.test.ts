import { describe, expect, it } from 'vitest'

import { CREATE_JOINED_LATE, JOIN_FAILURE } from './party-copy'

describe('파티 막힘 문구', () => {
  it('크루 가입이 늦은 경우 「합류할 수 있어요」로 안내한다', () => {
    for (const copy of [JOIN_FAILURE.JOINED_LATE, CREATE_JOINED_LATE]) {
      expect(copy.description).toBe('크루 예매 시작 7일 전까지 가입한 크루원만 합류할 수 있어요')
    }
  })
})
