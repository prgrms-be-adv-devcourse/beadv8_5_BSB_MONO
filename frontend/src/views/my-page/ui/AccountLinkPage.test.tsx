import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { setAccessToken } from '@/shared/api'
import { server } from '@/shared/testing'
import { ToastProvider } from '@/shared/ui'

import { AccountLinkPage } from './AccountLinkPage'

const goToSocialLogin = vi.hoisted(() => vi.fn())
vi.mock('@/features/auth/lib/social-login', async (importOriginal) => ({
  ...(await importOriginal<object>()),
  goToSocialLogin,
}))
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
  return render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <ToastProvider>
        <AccountLinkPage {...props} />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  setAccessToken('access-token')
  goToSocialLogin.mockClear()
  sessionStorage.clear()
  server.use(
    http.post('*/api/v1/member/members/me/social-accounts/:provider/link-ticket', () =>
      HttpResponse.json({ status: 200, message: '연결 표', data: null }),
    ),
  )
})

describe('AccountLinkPage', () => {
  it('연결 상태를 보여 주고, 비밀번호 없이 하나만 연결돼 있으면 연결 해제 버튼을 막는다', async () => {
    me(['KAKAO'], false)
    renderPage()

    expect(await screen.findByText('연결됨')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '연결 해제' })).toBeDisabled()
    expect(screen.getByRole('button', { name: '연결하기' })).toBeEnabled()
    expect(screen.getByText('카카오로 가입했어요')).toBeInTheDocument()
  })

  it('비밀번호가 있으면 해제 확인 창을 거쳐 해제하고, 해제했다고 알린다', async () => {
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

    await user.click(await screen.findByRole('button', { name: '연결 해제' }))
    expect(screen.getByText('카카오 연결을 해제할까요?')).toBeInTheDocument()
    await user.click(screen.getAllByRole('button', { name: '연결 해제' }).at(-1)!)

    expect(await screen.findAllByRole('button', { name: '연결하기' })).toHaveLength(2)
    expect(await screen.findByText('카카오 연결을 해제했어요')).toBeInTheDocument()
  })

  it('해제 확인 창에서 돌아가기를 누르면 해제하지 않는다', async () => {
    me(['KAKAO'], true)
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: '연결 해제' }))
    await user.click(screen.getByRole('button', { name: '돌아가기' }))

    expect(screen.getByText('연결됨')).toBeInTheDocument()
  })

  it('다른 회원에 연결된 계정이면 안내 창을 띄운다', async () => {
    me(['KAKAO'], true)
    renderPage({ error: 'AUTH_SOCIAL_ALREADY_LINKED' })

    expect(
      await screen.findByText('이 Google 계정은 다른 크루런 회원에 연결돼 있어요'),
    ).toBeInTheDocument()
  })

  it('연결을 마치지 못하면 안내 창에서 다시 시도할 수 있다', async () => {
    me(['KAKAO'], true)
    renderPage({ error: 'AUTH_SOCIAL_FAILED' })

    expect(await screen.findByText('Google 연결을 마치지 못했어요')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '다시 시도하기' })).toBeInTheDocument()
  })

  it('연결을 마치고 돌아오면 연결했다고 알린다', async () => {
    me(['KAKAO', 'GOOGLE'], true)
    renderPage({ linked: 'google' })

    expect(await screen.findByText('Google 계정을 연결했어요')).toBeInTheDocument()
  })

  // 돌아온 주소에는 어느 제공자인지 없다. 연결을 시작할 때 남겨 둔 제공자로 안내하고 다시 시도한다.
  it('연결하려던 제공자를 기억해 실패하면 그 제공자로 안내하고 다시 시도한다', async () => {
    me([], true)
    const user = userEvent.setup()
    const first = renderPage()

    // 둘 다 연결 안 된 상태에서 Google을 시작한다 (연결 안 된 제공자가 둘이라 짐작으로는 알 수 없다)
    await user.click((await screen.findAllByRole('button', { name: '연결하기' }))[1])
    await vi.waitFor(() => expect(goToSocialLogin).toHaveBeenCalledTimes(1))
    expect(goToSocialLogin.mock.calls[0][0]).toContain('/google')
    first.unmount()
    goToSocialLogin.mockClear()

    renderPage({ error: 'AUTH_SOCIAL_FAILED' })
    expect(await screen.findByText('Google 연결을 마치지 못했어요')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '다시 시도하기' }))

    await vi.waitFor(() => expect(goToSocialLogin).toHaveBeenCalledTimes(1))
    expect(goToSocialLogin.mock.calls[0][0]).toContain('/google')
  })

  it('다른 회원에 연결된 계정 안내도 시작했던 제공자 이름을 쓴다', async () => {
    me([], true)
    sessionStorage.setItem('crewrun:social-link-provider', 'kakao')
    renderPage({ error: 'AUTH_SOCIAL_ALREADY_LINKED' })

    expect(
      await screen.findByText('이 카카오 계정은 다른 크루런 회원에 연결돼 있어요'),
    ).toBeInTheDocument()
  })

  it('이미 연결한 제공자를 또 연결하려 했다는 코드는 그 뜻으로 안내하고 다시 시도 버튼은 없다', async () => {
    me(['KAKAO'], true)
    renderPage({ error: 'MEMBER_SOCIAL_PROVIDER_ALREADY_LINKED' })

    expect(await screen.findByText('이미 연결된 소셜 로그인이에요.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '다시 시도하기' })).not.toBeInTheDocument()
  })

  it('연결에 성공하면 기억해 둔 제공자를 지운다', async () => {
    me(['KAKAO', 'GOOGLE'], true)
    sessionStorage.setItem('crewrun:social-link-provider', 'google')
    renderPage({ linked: 'google' })

    expect(await screen.findByText('Google 계정을 연결했어요')).toBeInTheDocument()
    expect(sessionStorage.getItem('crewrun:social-link-provider')).toBeNull()
  })
})
