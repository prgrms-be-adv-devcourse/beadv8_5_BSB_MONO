import { describe, expect, it } from 'vitest'

import { ApiError } from '@/shared/api'

import { ADMIN_MEMBER_ERRORS, loadErrorMessage, toDateTime } from './admin-members-api'

const apiError = (code: string) =>
  new ApiError(403, { status: 403, code, message: '서버 문구', data: null }, '/api/x')

describe('loadErrorMessage', () => {
  it.each([
    ['CASH_ADMIN_FORBIDDEN', ADMIN_MEMBER_ERRORS.notAdmin],
    ['MEMBER_NOT_ADMIN', ADMIN_MEMBER_ERRORS.notAdmin],
    ['CASH_MEMBER_SERVICE_UNAVAILABLE', ADMIN_MEMBER_ERRORS.memberUnavailable],
    ['SOMETHING_ELSE', ADMIN_MEMBER_ERRORS.loadFailed],
  ])('%s는 정해진 문구로 바꾼다', (code, message) => {
    expect(loadErrorMessage(apiError(code))).toBe(message)
  })

  it('ApiError가 아니면 일반 문구', () => {
    expect(loadErrorMessage(new Error('x'))).toBe(ADMIN_MEMBER_ERRORS.loadFailed)
  })
})

describe('toDateTime', () => {
  it('시간대를 바꾸지 않고 문자열을 잘라 보인다', () => {
    expect(toDateTime('2026-11-12T03:21:07')).toBe('2026. 11. 12 03:21')
  })
})
