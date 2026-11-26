import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { customFetch, getAuthStatus, memberApi, setAccessToken } from '@/shared/api'
import { server } from '@/shared/testing'

import { AuthSessionRestorer } from './AuthSessionRestorer'

const replace = vi.fn()
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), replace }),
}))

beforeEach(() => replace.mockClear())

function renderRestorer() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <AuthSessionRestorer />
    </QueryClientProvider>,
  )
  return client
}

describe('AuthSessionRestorer', () => {
  it('새로고침 뒤 재발급으로 로그인 상태를 되살리고, 새 토큰으로 내 정보를 불러 둔다', async () => {
    const authorizations: (string | null)[] = []
    server.use(
      http.post('*/api/v1/auth/reissue', () =>
        HttpResponse.json({
          status: 200,
          message: '토큰 재발급 성공',
          data: { accessToken: 'restored-token' },
        }),
      ),
      http.get('*/api/v1/member/members/me', ({ request }) => {
        authorizations.push(request.headers.get('Authorization'))
        return HttpResponse.json({
          status: 200,
          message: '내 정보를 조회했습니다.',
          data: { id: 1, nickname: '러너1', email: 'runner1@crewrun.com' },
        })
      }),
    )
    expect(getAuthStatus()).toBe('pending')

    const client = renderRestorer()

    await waitFor(() =>
      expect(client.getQueryData(memberApi.getFindMeQueryKey())).toMatchObject({
        data: { nickname: '러너1' },
      }),
    )
    expect(getAuthStatus()).toBe('authenticated')
    expect(authorizations).toEqual(['Bearer restored-token'])
  })

  it('Refresh Token이 없으면(재발급 401) 로그아웃 상태로 두고 로그인 화면으로 보내지 않는다', async () => {
    server.use(
      http.post('*/api/v1/auth/reissue', () =>
        HttpResponse.json(
          { status: 401, message: 'Refresh Token이 없습니다. 다시 로그인해주세요.', data: null },
          { status: 401 },
        ),
      ),
    )

    renderRestorer()

    await waitFor(() => expect(getAuthStatus()).toBe('anonymous'))
    expect(replace).not.toHaveBeenCalled()
  })

  it('로그인 중에 재발급이 실패하면 보던 화면 주소를 next로 넘겨 로그인 화면으로 보낸다', async () => {
    window.history.replaceState(null, '', '/tickets?tab=done')
    server.use(
      http.post('*/api/v1/auth/reissue', () =>
        HttpResponse.json({ status: 401, message: '만료', data: null }, { status: 401 }),
      ),
      http.get('*/api/v1/order/orders', () =>
        HttpResponse.json(
          { status: 401, message: '만료된 토큰입니다.', data: null },
          { status: 401 },
        ),
      ),
    )
    setAccessToken('expired-token')
    renderRestorer()

    await customFetch('http://localhost/api/v1/order/orders').catch(() => undefined)

    expect(getAuthStatus()).toBe('anonymous')
    expect(replace).toHaveBeenCalledWith('/login?next=%2Ftickets%3Ftab%3Ddone')
    window.history.replaceState(null, '', '/')
  })

  it('앱 충전 페이지(/app-charge)에서는 로그인이 필요 없어 재발급을 부르지 않는다', async () => {
    window.history.replaceState(null, '', '/app-charge/done?orderId=o1')
    const reissue = vi.fn(() =>
      HttpResponse.json({ status: 401, message: '없음', data: null }, { status: 401 }),
    )
    server.use(http.post('*/api/v1/auth/reissue', reissue))

    renderRestorer()
    await new Promise((resolve) => setTimeout(resolve, 50))

    expect(reissue).not.toHaveBeenCalled()
    window.history.replaceState(null, '', '/')
  })
})
