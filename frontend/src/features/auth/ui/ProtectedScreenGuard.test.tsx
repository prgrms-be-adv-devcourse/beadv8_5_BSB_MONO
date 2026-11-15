import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { clearAccessToken, setAccessToken } from '@/shared/api'

import { ProtectedScreenGuard } from './ProtectedScreenGuard'

const replace = vi.fn()
let pathname = '/me'
vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace }),
  usePathname: () => pathname,
}))

beforeEach(() => {
  replace.mockClear()
  pathname = '/me'
})
const renderGuard = () =>
  render(
    <ProtectedScreenGuard>
      <p>회원 정보</p>
    </ProtectedScreenGuard>,
  )

describe('ProtectedScreenGuard', () => {
  it('로그인 확인 전에는 내용 없이 로딩만 그린다', () => {
    renderGuard()
    expect(screen.queryByText('회원 정보')).not.toBeInTheDocument()
    expect(screen.getByRole('progressbar', { name: '로그인 확인 중' })).toBeInTheDocument()
    expect(replace).not.toHaveBeenCalled()
  })

  it('로그인 확인이 실패하면(비로그인) 내용 없이 로그인 화면으로 보낸다', () => {
    pathname = '/notifications'
    window.history.replaceState(null, '', '/notifications?tab=1')
    clearAccessToken()
    renderGuard()
    expect(screen.queryByText('회원 정보')).not.toBeInTheDocument()
    expect(replace).toHaveBeenCalledWith(
      `/login?next=${encodeURIComponent('/notifications?tab=1')}`,
    )
  })

  it.each(['/tickets', '/cart', '/me', '/me/deposit', '/crews'])(
    '하단 탭·홈 바로가기 화면(%s)을 비로그인으로 열면 돌아올 경로 없이 로그인 화면으로 보낸다 (로그인 뒤 홈)',
    (path) => {
      pathname = path
      window.history.replaceState(null, '', path)
      clearAccessToken()
      renderGuard()
      expect(replace).toHaveBeenCalledWith('/login')
    },
  )

  it('로그인했으면 내용을 그린다', () => {
    setAccessToken('token')
    renderGuard()
    expect(screen.getByText('회원 정보')).toBeInTheDocument()
  })

  it('보호되지 않은 화면은 확인 없이 그린다', () => {
    pathname = '/races/1'
    renderGuard()
    expect(screen.getByText('회원 정보')).toBeInTheDocument()
  })
})
