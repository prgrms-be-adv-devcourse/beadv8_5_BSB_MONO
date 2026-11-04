import { describe, expect, it } from 'vitest'

import { isProtectedPath } from './protected-path'

describe('isProtectedPath', () => {
  it.each([
    '/me',
    '/me/deposit',
    '/me/withdraw',
    '/tickets',
    '/tickets/1',
    '/cart',
    '/checkout/complete',
    '/races/1/apply',
    '/crews',
    '/crews/1/invites',
    '/invite/q7Xk2LmZ9vBn4RtW8yCp1A',
  ])('%s는 로그인이 필요하다', (path) => {
    expect(isProtectedPath(path)).toBe(true)
  })

  it.each(['/', '/races', '/races/1', '/login', '/signup', '/mercury', '/terms/service'])(
    '%s는 로그인 없이 열린다',
    (path) => {
      expect(isProtectedPath(path)).toBe(false)
    },
  )

  // 탈퇴에 성공하면 토큰을 비운 뒤 이 화면으로 온다. 로그인을 요구하면 완료 화면 대신 로그인 화면으로 간다.
  it('탈퇴 완료 화면은 로그아웃 상태로 열린다', () => {
    expect(isProtectedPath('/me/withdraw/done')).toBe(false)
  })
})
