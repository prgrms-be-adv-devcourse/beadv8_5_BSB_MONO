import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getAuthStatus, setAccessToken } from '@/shared/api'
import { server } from '@/shared/testing'

import { ConsoleSidebar } from './ConsoleSidebar'

const replace = vi.fn()
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), replace }),
  usePathname: () => '/seller',
}))

const reloadToLogin = vi.hoisted(() => vi.fn())
vi.mock('@/features/auth/lib/login-redirect', async (importOriginal) => ({
  ...(await importOriginal<object>()),
  reloadToLogin,
}))

beforeEach(() => {
  replace.mockClear()
  reloadToLogin.mockClear()
})

describe('ConsoleSidebar 로그아웃', () => {
  it('로그아웃을 누르면 로그아웃 API를 부르고, 응답이 온 뒤 문서를 새로 불러 로그인 화면으로 간다', async () => {
    let called = false
    let reloadedBeforeResponse = false
    server.use(
      http.post('*/api/v1/auth/logout', () => {
        called = true
        reloadedBeforeResponse = reloadToLogin.mock.calls.length > 0
        return HttpResponse.json({ status: 200, message: '로그아웃 성공', data: null })
      }),
    )
    setAccessToken('access-token')
    const user = userEvent.setup()
    render(
      <QueryClientProvider client={new QueryClient()}>
        <ConsoleSidebar role="seller" />
      </QueryClientProvider>,
    )

    await user.click(screen.getByRole('button', { name: '로그아웃' }))

    await vi.waitFor(() => expect(reloadToLogin).toHaveBeenCalledTimes(1))
    expect(called).toBe(true)
    expect(reloadedBeforeResponse).toBe(false)
    expect(replace).not.toHaveBeenCalled()
    // 새로 불러오기 전까지 토큰을 그대로 두어 「로그인이 필요해요」 안내가 잠깐 보이지 않는다
    expect(getAuthStatus()).toBe('authenticated')
  })
})

describe('ConsoleSidebar 크루런 홈으로', () => {
  it.each(['seller', 'admin'] as const)(
    '%s 콘솔의 설정과 로그아웃 사이에 크루런 홈(/)으로 가는 링크가 있다',
    (role) => {
      render(
        <QueryClientProvider client={new QueryClient()}>
          <ConsoleSidebar role={role} />
        </QueryClientProvider>,
      )

      const home = screen.getByRole('link', { name: '크루런 홈으로' })
      expect(home).toHaveAttribute('href', '/')
      expect(home).not.toHaveAttribute('aria-current')
      const settings = screen.getByRole('link', { name: '설정' })
      const logout = screen.getByRole('button', { name: '로그아웃' })
      expect(settings.compareDocumentPosition(home) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
      expect(home.compareDocumentPosition(logout) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    },
  )
})
