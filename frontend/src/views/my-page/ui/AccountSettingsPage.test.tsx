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
  it('닉네임만 수정 버튼이 있고, 본인인증 값은 입력 칸 없이 보기만 한다', async () => {
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
    expect(screen.getByText('010-****-5432')).toBeInTheDocument()
    expect(screen.getByText('김곰')).toBeInTheDocument()
    expect(screen.getByText('1990.01.31')).toBeInTheDocument()
    expect(screen.getByText('본인인증으로 확인한 정보라 바꿀 수 없어요')).toBeInTheDocument()
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: /수정/ })).toHaveLength(1)
    expect(screen.getByRole('link', { name: '닉네임 수정' })).toHaveAttribute('href', '/me/edit')
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
