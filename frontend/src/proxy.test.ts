import { NextRequest } from 'next/server'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { MOCK_LOGIN_MARK_COOKIE_NAME } from '@/shared/mocks/mock-session'

import { proxy } from './proxy'

const request = (path: string, cookie?: string) =>
  new NextRequest(`http://localhost${path}`, cookie ? { headers: { cookie } } : undefined)

const redirectOf = (response: Response) => response.headers.get('location')

afterEach(() => vi.unstubAllEnvs())

describe('proxy (가짜 응답 모드)', () => {
  it.each(['/me', '/me/deposit', '/notifications', '/tickets', '/cart', '/races/1/apply'])(
    '비로그인으로 %s를 열면 화면을 그리기 전에 로그인 화면으로 보낸다',
    (path) => {
      vi.stubEnv('API_MOCKING', 'on')
      const response = proxy(request(path))
      expect(redirectOf(response)).toBe(`http://localhost/login?next=${encodeURIComponent(path)}`)
    },
  )

  it('돌아올 경로에 쿼리도 넘긴다', () => {
    vi.stubEnv('API_MOCKING', 'on')
    const response = proxy(request('/checkout?orderId=3'))
    expect(redirectOf(response)).toBe(
      `http://localhost/login?next=${encodeURIComponent('/checkout?orderId=3')}`,
    )
  })

  it('로그인 표시 쿠키가 있으면 통과한다', () => {
    vi.stubEnv('API_MOCKING', 'on')
    const response = proxy(request('/me', `${MOCK_LOGIN_MARK_COOKIE_NAME}=1`))
    expect(redirectOf(response)).toBeNull()
  })

  it('로그인이 필요 없는 화면은 통과한다', () => {
    vi.stubEnv('API_MOCKING', 'on')
    expect(redirectOf(proxy(request('/races/1')))).toBeNull()
    expect(redirectOf(proxy(request('/seller')))).toBeNull()
  })

  it('비로그인으로 고객센터(/me/support)를 열면 로그인 화면으로 보내지 않는다', () => {
    vi.stubEnv('API_MOCKING', 'on')
    expect(redirectOf(proxy(request('/me/support')))).toBeNull()
  })

  it('고객센터 안의 문의 쓰기는 돌아올 경로를 붙여 로그인 화면으로 보낸다', () => {
    vi.stubEnv('API_MOCKING', 'on')
    expect(redirectOf(proxy(request('/me/inquiries/new')))).toBe(
      `http://localhost/login?next=${encodeURIComponent('/me/inquiries/new')}`,
    )
  })
})

describe('proxy (실제 API 모드)', () => {
  it('서버는 로그인 여부를 알 수 없어 보호 화면도 통과시킨다', () => {
    vi.stubEnv('API_MOCKING', 'off')
    vi.stubEnv('NEXT_PUBLIC_API_MOCKING', 'off')
    expect(redirectOf(proxy(request('/me')))).toBeNull()
  })
})
