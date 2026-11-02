import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { memberApi, setAccessToken } from '@/shared/api'
import { memberMock, server } from '@/shared/testing'

import { PROFILE_ERRORS } from '../model/profile-schema'
import { ProfileEditPage } from './ProfileEditPage'

const push = vi.fn()
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push, back: vi.fn(), replace: vi.fn() }),
  usePathname: () => '/me/edit',
}))

beforeEach(() => push.mockClear())

const ME = {
  status: 200,
  message: '내 정보를 조회했습니다.',
  data: {
    id: 1,
    nickname: '러너1',
    email: 'runner1@crewrun.com',
    phone: '010-1234-5678',
    name: '김러너',
    birthDate: '1995-04-12',
  },
}

function renderPage() {
  setAccessToken('access-token')
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <ProfileEditPage />
    </QueryClientProvider>,
  )
  return client
}

describe('ProfileEditPage', () => {
  beforeEach(() => server.use(memberMock.getFindMeMockHandler(ME)))

  it('닉네임 칸만 있고, 저장하면 닉네임만 보내 캐시를 바꾼 뒤 계정 설정으로 간다', async () => {
    let body: unknown
    const updated = { ...ME, data: { ...ME.data, nickname: '달리는곰' } }
    server.use(
      http.put('*/api/v1/member/members/me', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json(updated)
      }),
    )
    const user = userEvent.setup()
    const client = renderPage()

    const nickname = await screen.findByLabelText('닉네임')
    expect(nickname).toHaveValue('러너1')
    expect(screen.queryByLabelText('이름')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('휴대폰 번호')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /생년월일/ })).not.toBeInTheDocument()
    await user.clear(nickname)
    await user.type(nickname, '달리는곰')
    await user.click(screen.getByRole('button', { name: '저장하기' }))

    await vi.waitFor(() => expect(push).toHaveBeenCalledWith('/me/settings'))
    expect(body).toEqual({ nickname: '달리는곰' })
    expect(client.getQueryData(memberApi.getFindMeQueryKey())).toEqual(updated)
  })

  it('닉네임 칸 수가 백엔드 규칙(한글 2~8자, 영문 4~16자)에 안 맞으면 저장 버튼이 꺼진다', async () => {
    const user = userEvent.setup()
    renderPage()

    const nickname = await screen.findByLabelText('닉네임')
    await user.clear(nickname)
    await user.type(nickname, 'abc')
    await user.tab()

    expect(await screen.findByText(PROFILE_ERRORS.nicknameWidth)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '저장하기' })).toBeDisabled()
  })

  it('다른 회원이 쓰는 닉네임이면 닉네임 칸에 서버 문구를 띄우고 이동하지 않는다', async () => {
    server.use(
      http.put('*/api/v1/member/members/me', () =>
        HttpResponse.json(
          {
            status: 409,
            code: 'MEMBER_DUPLICATE_NICKNAME',
            message: '이미 사용 중인 닉네임입니다.',
            data: null,
          },
          { status: 409 },
        ),
      ),
    )
    const user = userEvent.setup()
    renderPage()

    await screen.findByLabelText('닉네임')
    await user.click(screen.getByRole('button', { name: '저장하기' }))

    expect(await screen.findByText('이미 사용 중인 닉네임입니다.')).toBeInTheDocument()
    expect(push).not.toHaveBeenCalled()
  })
})
