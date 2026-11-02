import { describe, expect, it } from 'vitest'

import { answerMockPass, readMockVerificationToken } from './identity-verification'

const NOW = Date.UTC(2026, 10, 2, 3, 0, 0)
const BODY = { carrier: 'SKT', name: '김러너', birthDate: '1995-04-12', phone: '010-1234-5678' }

describe('체험용 PASS 본인인증 규칙', () => {
  it('휴대폰 번호 형식이 틀리면 400이다', () => {
    expect(answerMockPass({ ...BODY, phone: '02-123-4567' }, NOW).status).toBe(400)
  })

  it('받은 토큰에는 확인한 정보가 담기고, 15분이 지나면 쓸 수 없다', () => {
    const token = answerMockPass(BODY, NOW).body.data?.verificationToken
    expect(readMockVerificationToken(token, NOW)).toMatchObject({
      name: '김러너',
      birthDate: '1995-04-12',
      phone: '010-1234-5678',
    })
    expect(readMockVerificationToken(token, NOW + 15 * 60 * 1000)).toBeNull()
    expect(readMockVerificationToken('crewrun-iv-real-token', NOW)).toBeNull()
  })
})
