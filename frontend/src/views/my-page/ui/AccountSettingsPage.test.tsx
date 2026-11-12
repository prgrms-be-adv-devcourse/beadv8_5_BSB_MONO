import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { setAccessToken } from '@/shared/api'
import { memberMock, server } from '@/shared/testing'

import { AccountSettingsPage } from './AccountSettingsPage'

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), replace: vi.fn() }),
  usePathname: () => '/me/settings',
}))

const renderPage = () => {
  setAccessToken('access-token')
  render(
    <QueryClientProvider client={new QueryClient()}>
      <AccountSettingsPage />
    </QueryClientProvider>,
  )
}

describe('AccountSettingsPage', () => {
  it('본인인증 이름과 정보 보기를 보여 주고, 닉네임 변경은 회원 정보 수정으로 간다', async () => {
    server.use(
      memberMock.getFindMeMockHandler({
        status: 200,
        message: '내 정보를 조회했습니다.',
        data: {
          id: 1,
          nickname: '달리는곰',
          email: 'bear@crewrun.kr',
          phone: '01098765432',
          name: '김곰',
          birthDate: '1990-01-31',
        },
      }),
    )
    renderPage()

    expect(await screen.findByText('달리는곰')).toBeInTheDocument()
    expect(screen.getByText('김곰')).toBeInTheDocument()
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: '정보 보기' })).toHaveAttribute('href', '/me/edit')
    expect(screen.getByRole('link', { name: /닉네임 변경/ })).toHaveAttribute('href', '/me/edit')
  })

  it('이메일 정보 · 약관 및 동의 · 고객센터로 들어갈 수 있다', () => {
    renderPage()

    expect(screen.getByRole('link', { name: /이메일 정보/ })).toHaveAttribute(
      'href',
      '/me/settings/email',
    )
    expect(screen.getByRole('link', { name: /약관 및 동의/ })).toHaveAttribute(
      'href',
      '/me/settings/terms',
    )
    expect(screen.getByRole('link', { name: /고객센터/ })).toHaveAttribute('href', '/me/support')
  })

  it('계정 연결로 들어갈 수 있다', () => {
    renderPage()

    expect(screen.getByRole('link', { name: /계정 연결/ })).toHaveAttribute('href', '/me/accounts')
  })

  it('탈퇴하기는 맨 아래에 있고 탈퇴 화면으로 간다', () => {
    renderPage()

    const links = screen.getAllByRole('link')
    const withdraw = screen.getByRole('link', { name: '탈퇴하기' })
    expect(withdraw).toHaveAttribute('href', '/me/withdraw')
    expect(links.at(-1)).toBe(withdraw)
  })
})
