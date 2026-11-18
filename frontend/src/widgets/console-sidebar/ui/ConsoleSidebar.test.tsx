import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getAuthStatus, setAccessToken } from '@/shared/api'
import { server } from '@/shared/testing'

import { ConsoleSidebar } from './ConsoleSidebar'

const replace = vi.fn()
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), replace }),
  usePathname: () => '/seller/ai',
}))

const reloadAfterLogout = vi.hoisted(() => vi.fn())
vi.mock('@/features/auth/lib/login-redirect', async (importOriginal) => ({
  ...(await importOriginal<object>()),
  reloadAfterLogout,
}))

beforeEach(() => {
  replace.mockClear()
  reloadAfterLogout.mockClear()
})

describe('ConsoleSidebar 로그아웃', () => {
  it('로그아웃을 누르면 로그아웃 API를 부르고, 응답이 온 뒤 문서를 새로 불러 로그인 화면으로 간다', async () => {
    let called = false
    // 응답을 붙잡아 두고, 응답 전에는 이동하지 않는지 본다
    let release = () => {}
    const held = new Promise<void>((resolve) => (release = resolve))
    server.use(
      http.post('*/api/v1/auth/logout', async () => {
        called = true
        await held
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

    await vi.waitFor(() => expect(called).toBe(true))
    expect(reloadAfterLogout).not.toHaveBeenCalled()
    release()
    await vi.waitFor(() => expect(reloadAfterLogout).toHaveBeenCalledTimes(1))
    expect(called).toBe(true)
    expect(replace).not.toHaveBeenCalled()
    // 새로 불러오기 전까지 토큰을 그대로 두어 「로그인이 필요해요」 안내가 잠깐 보이지 않는다
    expect(getAuthStatus()).toBe('authenticated')
  })
})

describe('ConsoleSidebar 크루런 홈으로', () => {
  it.each([
    ['seller', '판매자 정보'],
    ['admin', '설정'],
  ] as const)(
    '%s 콘솔의 %s와 로그아웃 사이에 크루런 홈(/)으로 가는 링크가 있다',
    (role, settingsLabel) => {
      render(
        <QueryClientProvider client={new QueryClient()}>
          <ConsoleSidebar role={role} />
        </QueryClientProvider>,
      )

      const home = screen.getByRole('link', { name: '크루런 홈으로' })
      expect(home).toHaveAttribute('href', '/')
      expect(home).not.toHaveAttribute('aria-current')
      const settings = screen.getByRole('link', { name: settingsLabel })
      const logout = screen.getByRole('button', { name: '로그아웃' })
      expect(settings.compareDocumentPosition(home) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
      expect(home.compareDocumentPosition(logout) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    },
  )
})

describe('ConsoleSidebar 크루런 AI', () => {
  it.each([
    ['seller', '/seller/ai'],
    ['admin', '/admin/ai'],
  ] as const)('%s 콘솔 메뉴에 크루런 AI 링크가 있다', (role, href) => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <ConsoleSidebar role={role} />
      </QueryClientProvider>,
    )

    expect(screen.getByRole('link', { name: '크루런 AI' })).toHaveAttribute('href', href)
  })

  it('/seller/ai에서는 크루런 AI만 선택 표시되고 대시보드는 선택되지 않는다', () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <ConsoleSidebar role="seller" />
      </QueryClientProvider>,
    )

    expect(screen.getByRole('link', { name: '크루런 AI' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('link', { name: '대시보드' })).not.toHaveAttribute('aria-current')
  })
})

describe('ConsoleSidebar 메뉴 순서', () => {
  // 피그마 판매자 센터(1388:60976) · 관리자 센터(1388:66074) 사이드바 순서
  it.each([
    ['seller', ['대시보드', '상품 관리', '주문 관리', '정산 관리', '문의', '크루런 AI']],
    [
      'admin',
      [
        '대시보드',
        '회원 관리',
        '판매자 관리',
        '대회 관리',
        '정산 관리',
        '회수 관리',
        '문의',
        '커뮤니티 신고',
        '크루런 AI',
        '이용 통계',
      ],
    ],
  ] as const)('%s 콘솔 메뉴가 시안 순서대로 나온다', (role, labels) => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <ConsoleSidebar role={role} />
      </QueryClientProvider>,
    )

    const nav = screen.getByRole('navigation')
    expect(
      within(nav)
        .getAllByRole('link')
        .map((link) => link.textContent),
    ).toEqual(labels)
  })
})
