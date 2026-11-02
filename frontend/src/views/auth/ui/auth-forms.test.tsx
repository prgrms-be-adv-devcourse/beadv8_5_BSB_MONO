import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getAuthStatus } from '@/shared/api'
import { memberMock, pickDate, server } from '@/shared/testing'

import { AUTH_ERRORS } from '../model/schemas'
import { SignupStepProvider } from '../model/signup-store'
import { PasswordNewPage, PasswordResetPage } from './FindAccountPages'
import { LoginPage } from './LoginPage'
import { SignupAccountPage, SignupProfilePage } from './SignupPages'

const push = vi.fn()
const replace = vi.fn()
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push, back: vi.fn(), replace }),
  usePathname: () => '/',
}))

beforeEach(() => {
  push.mockClear()
  replace.mockClear()
})

// 약관 시트가 열려 있으면 시트가 포커스를 가져가 뒤쪽 입력칸 타이핑이 가끔 빠진다. 먼저 동의해 시트를 닫는다.
async function agreeAllTerms(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole('checkbox', { name: '모두 동의해요 (선택 포함)' }))
  await user.click(screen.getByRole('button', { name: '동의하고 계속하기' }))
}

function withQueryClient(ui: ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={client}>{ui}</QueryClientProvider>
}

async function fillLogin(user: ReturnType<typeof userEvent.setup>, password = 'password1') {
  await user.type(screen.getByLabelText('이메일'), 'runner@crewrun.kr')
  await user.type(screen.getByLabelText('비밀번호'), password)
}

describe('LoginPage', () => {
  it('이메일 형식이 틀리면 필드 아래에 오류가 뜨고 버튼이 꺼진다', async () => {
    const user = userEvent.setup()
    render(withQueryClient(<LoginPage />))

    await user.type(screen.getByLabelText('이메일'), 'runner')
    await user.type(screen.getByLabelText('비밀번호'), 'password1')

    expect(await screen.findByText(AUTH_ERRORS.emailFormat)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '로그인' })).toBeDisabled()
  })

  it('로그인 API에 이메일·비밀번호를 보내고, 성공하면 로그인 상태가 되어 홈으로 간다', async () => {
    let body: unknown
    server.use(
      http.post('*/api/v1/auth/login', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json(memberMock.getLoginResponseMock())
      }),
    )
    const user = userEvent.setup()
    render(withQueryClient(<LoginPage />))

    await fillLogin(user)
    const button = screen.getByRole('button', { name: '로그인' })
    expect(button).toBeEnabled()
    await user.click(button)

    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/'))
    expect(body).toEqual({ email: 'runner@crewrun.kr', password: 'password1' })
    expect(getAuthStatus()).toBe('authenticated')
  })

  it('이메일·비밀번호가 틀리면(401) 로그인 실패 문구를 띄우고 이동하지 않는다', async () => {
    server.use(
      http.post('*/api/v1/auth/login', () =>
        HttpResponse.json(
          { status: 401, message: '이메일 또는 비밀번호가 올바르지 않습니다.', data: null },
          { status: 401 },
        ),
      ),
    )
    const user = userEvent.setup()
    render(withQueryClient(<LoginPage />))

    await fillLogin(user, 'wrong-password1')
    await user.click(screen.getByRole('button', { name: '로그인' }))

    expect(await screen.findByText(AUTH_ERRORS.loginFailed)).toBeInTheDocument()
    expect(replace).not.toHaveBeenCalled()
    expect(getAuthStatus()).not.toBe('authenticated')
  })

  it('로그인 실패 문구는 비밀번호를 고치면 사라진다', async () => {
    server.use(
      http.post('*/api/v1/auth/login', () =>
        HttpResponse.json(
          { status: 401, message: '이메일 또는 비밀번호가 올바르지 않습니다.', data: null },
          { status: 401 },
        ),
      ),
    )
    const user = userEvent.setup()
    render(withQueryClient(<LoginPage />))

    await fillLogin(user, 'wrong-password1')
    await user.click(screen.getByRole('button', { name: '로그인' }))
    expect(await screen.findByText(AUTH_ERRORS.loginFailed)).toBeInTheDocument()

    await user.type(screen.getByLabelText('비밀번호'), '2')

    expect(screen.queryByText(AUTH_ERRORS.loginFailed)).not.toBeInTheDocument()
  })

  it('next가 있으면 로그인 뒤 그 경로로 간다', async () => {
    server.use(
      http.post('*/api/v1/auth/login', () => HttpResponse.json(memberMock.getLoginResponseMock())),
    )
    const user = userEvent.setup()
    render(withQueryClient(<LoginPage next="/races/1/apply?option=2" />))

    await fillLogin(user)
    await user.click(screen.getByRole('button', { name: '로그인' }))

    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/races/1/apply?option=2'))
  })

  it('next가 다른 사이트 주소면 로그인 뒤 홈으로 간다', async () => {
    server.use(
      http.post('*/api/v1/auth/login', () => HttpResponse.json(memberMock.getLoginResponseMock())),
    )
    const user = userEvent.setup()
    render(withQueryClient(<LoginPage next="//evil.com" />))

    await fillLogin(user)
    await user.click(screen.getByRole('button', { name: '로그인' }))

    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/'))
  })

  it('시안 미리보기(failed)는 로그인 실패 문구를 보여 준다', () => {
    render(withQueryClient(<LoginPage failed />))
    expect(screen.getByText(AUTH_ERRORS.loginFailed)).toBeInTheDocument()
  })
})

describe('SignupProfilePage', () => {
  it('닉네임이 짧으면 오류가 뜨고 가입 완료 버튼이 꺼진다', async () => {
    const user = userEvent.setup()
    render(
      withQueryClient(
        <SignupStepProvider
          initialState={{
            termsAgreed: true,
            account: {
              email: 'runner@crewrun.kr',
              password: 'password123',
              passwordConfirm: 'password123',
            },
          }}
        >
          <SignupProfilePage />
        </SignupStepProvider>,
      ),
    )

    await user.type(screen.getByLabelText('이름'), '김러너')
    await pickDate(user, '생년월일', '1995-03-02')
    await user.type(screen.getByLabelText('휴대폰 번호'), '010-1234-5678')
    await user.type(screen.getByLabelText('닉네임'), '곰')
    await user.tab()

    expect(await screen.findByText(AUTH_ERRORS.nicknameLength)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '가입 완료' })).toBeDisabled()
  })
})

describe('PasswordResetPage', () => {
  it('이메일이 맞으면 다음 화면으로 간다', async () => {
    const user = userEvent.setup()
    render(<PasswordResetPage />)

    const button = screen.getByRole('button', { name: '재설정 링크 보내기' })
    expect(button).toBeDisabled()

    await user.type(screen.getByLabelText('이메일'), 'runner@crewrun.kr')
    await user.click(button)
    expect(push).toHaveBeenCalledWith('/password-reset/new')
  })
})

describe('비밀번호 확인 칸', () => {
  it('확인 칸을 채운 뒤 비밀번호를 고치면 확인 칸 오류가 바로 바뀐다', async () => {
    const user = userEvent.setup()
    render(<PasswordNewPage />)

    await user.type(screen.getByLabelText('새 비밀번호'), 'password123')
    await user.type(screen.getByLabelText('새 비밀번호 확인'), 'password123')
    await user.tab()
    expect(screen.queryByText(AUTH_ERRORS.passwordMismatch)).not.toBeInTheDocument()

    await user.type(screen.getByLabelText('새 비밀번호'), '2')
    expect(await screen.findByText(AUTH_ERRORS.passwordMismatch)).toBeInTheDocument()

    await user.type(screen.getByLabelText('새 비밀번호 확인'), '2')
    await user.clear(screen.getByLabelText('새 비밀번호'))
    await user.type(screen.getByLabelText('새 비밀번호'), 'password1232')
    expect(screen.queryByText(AUTH_ERRORS.passwordMismatch)).not.toBeInTheDocument()
  })

  it('확인 칸이 비어 있으면 비밀번호를 입력해도 다르다는 오류를 먼저 띄우지 않는다', async () => {
    const user = userEvent.setup()
    render(
      <SignupStepProvider>
        <SignupAccountPage />
      </SignupStepProvider>,
    )
    await agreeAllTerms(user)

    await user.type(screen.getByLabelText('비밀번호'), 'password123')
    expect(screen.queryByText(AUTH_ERRORS.passwordMismatch)).not.toBeInTheDocument()
  })

  it('가입 계정 화면에서도 비밀번호를 고치면 확인 칸 오류가 바로 뜬다', async () => {
    const user = userEvent.setup()
    render(
      <SignupStepProvider>
        <SignupAccountPage />
      </SignupStepProvider>,
    )
    await agreeAllTerms(user)

    await user.type(screen.getByLabelText('비밀번호'), 'password123')
    await user.type(screen.getByLabelText('비밀번호 확인'), 'password123')
    await user.tab()
    await user.type(screen.getByLabelText('비밀번호'), '2')
    expect(await screen.findByText(AUTH_ERRORS.passwordMismatch)).toBeInTheDocument()
  })
})
