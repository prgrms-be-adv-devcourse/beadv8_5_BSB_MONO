import { describe, expect, it } from 'vitest'

import { isProtectedPath } from './protected-path'

describe('isProtectedPath', () => {
  it.each([
    '/me',
    '/me/deposit',
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
})
