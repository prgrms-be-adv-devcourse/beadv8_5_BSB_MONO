import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { ApiError, MOCK_VERIFICATION_CODE } from '@/shared/api'
import { server } from '@/shared/testing'

import { confirmPhoneCode, requestPhoneCode } from './identity-verification'

describe('본인 확인 요청 경로', () => {
  it('가짜 응답 모드에서는 /api로 보내 MSW 핸들러가 답한다', async () => {
    let called = false
    server.use(
      http.post('*/api/v1/auth/phone-verifications', () => {
        called = true
        return HttpResponse.json(
          { status: 201, message: '보냄', data: { verificationId: 'id-1', expiresInSeconds: 60 } },
          { status: 201 },
        )
      }),
    )
    const sent = await requestPhoneCode('010-1234-5678', { mocking: true })
    expect(called).toBe(true)
    expect(sent).toEqual({ verificationId: 'id-1', expiresInSeconds: 60 })
  })

  it('실제 백엔드 모드에서는 요청을 보내지 않고 브라우저 안에서 같은 규칙으로 답한다', async () => {
    let called = false
    server.use(
      http.post('*/api/v1/auth/phone-verifications', () => {
        called = true
        return new HttpResponse(null, { status: 404 })
      }),
    )
    const { verificationId } = await requestPhoneCode('010-1234-5678', { mocking: false })
    expect(called).toBe(false)

    await expect(confirmPhoneCode(verificationId, '000000', { mocking: false })).rejects.toSatisfy(
      (error) => error instanceof ApiError && error.code === 'AUTH_VERIFICATION_CODE_MISMATCH',
    )
    const verified = await confirmPhoneCode(verificationId, MOCK_VERIFICATION_CODE, {
      mocking: false,
    })
    expect(verified).toMatchObject({ method: 'PHONE', phone: '010-1234-5678' })
  })
})
