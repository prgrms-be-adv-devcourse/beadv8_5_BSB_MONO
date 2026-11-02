import { describe, expect, it } from 'vitest'

import {
  answerPassVerification,
  answerPhoneVerificationConfirm,
  answerPhoneVerificationRequest,
  MOCK_CODE_TTL_SECONDS,
  MOCK_VERIFICATION_CODE,
  readMockVerificationToken,
} from './identity-verification-mock'

const NOW = Date.UTC(2026, 10, 2, 3, 0, 0)

function sendCode(phone = '010-1234-5678') {
  const answer = answerPhoneVerificationRequest({ phone }, NOW)
  return answer.body.data?.verificationId ?? ''
}

describe('휴대폰 인증 목 규칙', () => {
  it('번호 형식이 틀리면 인증번호를 보내지 않는다', () => {
    expect(answerPhoneVerificationRequest({ phone: '02-123-4567' }, NOW).status).toBe(400)
  })

  it('3분이 지나면 맞는 인증번호여도 410 AUTH_VERIFICATION_EXPIRED다', () => {
    const id = sendCode()
    const late = NOW + MOCK_CODE_TTL_SECONDS * 1000
    const answer = answerPhoneVerificationConfirm(id, { code: MOCK_VERIFICATION_CODE }, late)
    expect(answer.status).toBe(410)
    expect(answer.body.code).toBe('AUTH_VERIFICATION_EXPIRED')
  })

  it('3분 안에 맞는 인증번호를 넣으면 그 번호의 인증 토큰을 준다', () => {
    const answer = answerPhoneVerificationConfirm(
      sendCode(),
      { code: MOCK_VERIFICATION_CODE },
      NOW + 179_000,
    )
    expect(answer.status).toBe(200)
    const token = answer.body.data?.verificationToken
    // 하이픈 유무가 달라도 같은 번호다
    expect(readMockVerificationToken(token, { phone: '01012345678' }, NOW)).not.toBeNull()
    expect(readMockVerificationToken(token, { phone: '010-0000-0000' }, NOW)).toBeNull()
  })
})

describe('PASS 본인인증 목 규칙', () => {
  const body = {
    carrier: 'SKT',
    name: '김러너',
    birthDate: '1995-04-12',
    phone: '010-1234-5678',
  } as const

  it('통신사를 고르지 않았으면 400이다', () => {
    expect(answerPassVerification({ ...body, carrier: undefined }, NOW).status).toBe(400)
  })

  it('PASS로 받은 토큰은 이름 · 생년월일까지 가입 요청과 같아야 쓸 수 있다', () => {
    const token = answerPassVerification(body, NOW).body.data?.verificationToken
    expect(readMockVerificationToken(token, body, NOW)).toMatchObject({ method: 'PASS' })
    expect(readMockVerificationToken(token, { ...body, name: '박러너' }, NOW)).toBeNull()
    expect(readMockVerificationToken(token, { ...body, birthDate: '1995-04-13' }, NOW)).toBeNull()
  })

  it('인증 토큰은 30분이 지나면 쓸 수 없다', () => {
    const token = answerPassVerification(body, NOW).body.data?.verificationToken
    expect(readMockVerificationToken(token, body, NOW + 30 * 60 * 1000)).toBeNull()
  })
})
