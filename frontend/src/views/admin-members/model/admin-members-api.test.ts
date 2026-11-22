import { describe, expect, it } from 'vitest'

import { ApiError } from '@/shared/api'

import {
  ADMIN_MEMBER_ERRORS,
  loadErrorMessage,
  roParticle,
  toDateTime,
  toShortDate,
} from './admin-members-api'

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

  it('일반 문구 대신 화면이 준 문구를 쓸 수 있다', () => {
    expect(loadErrorMessage(apiError('SOMETHING_ELSE'), '회원을 불러오지 못했어요')).toBe(
      '회원을 불러오지 못했어요',
    )
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

describe('toShortDate', () => {
  it('날짜만 앞자리 0 없이 보인다', () => {
    expect(toShortDate('2025-03-02T09:00:00')).toBe('2025. 3. 2')
    expect(toShortDate('2026-10-30T09:00:00')).toBe('2026. 10. 30')
  })
})

describe('roParticle', () => {
  it.each([
    ['달리는곰', '으로'],
    ['달리는곰3', '으로'],
    ['러너', '로'],
    ['달', '로'],
    ['곰2', '로'],
    ['runner', '로'],
  ])('%s → %s', (word, expected) => {
    expect(roParticle(word)).toBe(expected)
  })
})
