import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { setAccessToken } from '@/shared/api'
import { server } from '@/shared/testing'

import { AccountLinkPage } from './AccountLinkPage'

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), replace: vi.fn() }),
  usePathname: () => '/me/accounts',
}))

function me(socialProviders: string[], passwordSet: boolean) {
  server.use(
    http.get('*/api/v1/member/members/me', () =>
      HttpResponse.json({
        status: 200,
        message: '내 정보를 조회했습니다.',
        data: { id: 1, nickname: '러너1', socialProviders, passwordSet },
      }),
    ),
  )
}

function renderPage(props: { linked?: string; error?: string } = {}) {
  render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <AccountLinkPage {...props} />
    </QueryClientProvider>,
  )
}

beforeEach(() => setAccessToken('access-token'))

describe('AccountLinkPage', () => {
  it('연결 상태를 보여 주고, 비밀번호 없이 하나만 연결돼 있으면 해제 버튼을 막는다', async () => {
    me(['KAKAO'], false)
    renderPage()

    expect(await screen.findByText('연결됨')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '해제' })).toBeDisabled()
    expect(screen.getByRole('button', { name: '연결' })).toBeEnabled()
    expect(screen.getByText(/로그인할 방법이 하나뿐이라 해제할 수 없어요/)).toBeInTheDocument()
  })

  it('비밀번호가 있으면 해제할 수 있고, 해제하면 연결 안 됨으로 바뀐다', async () => {
    me(['KAKAO'], true)
    server.use(
      http.delete('*/api/v1/member/members/me/social-accounts/kakao', () =>
        HttpResponse.json({
          status: 200,
          message: '연결을 해제했습니다.',
          data: { id: 1, nickname: '러너1', socialProviders: [], passwordSet: true },
        }),
      ),
    )
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: '해제' }))

    expect(await screen.findAllByText('연결 안 됨')).toHaveLength(2)
  })

  it('제공자 화면을 다녀온 결과를 알려 준다', async () => {
    me([], true)
    renderPage({ error: 'AUTH_SOCIAL_ALREADY_LINKED' })

    expect(await screen.findByText('이미 다른 회원에 연결된 소셜 계정이에요.')).toBeInTheDocument()
  })
})
