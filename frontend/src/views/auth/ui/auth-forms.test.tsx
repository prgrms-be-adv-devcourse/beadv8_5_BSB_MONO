import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getAuthStatus } from '@/shared/api'
import { memberMock, server } from '@/shared/testing'
import { ToastProvider } from '@/shared/ui'

import { AUTH_ERRORS } from '../model/schemas'
import { SignupStepProvider } from '../model/signup-store'
import { termChecks } from '../model/signup-terms'
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

// 본인인증을 마친 결과 (계정 · 회원 정보 단계는 약관 동의와 본인인증을 마쳐야 열린다)
const VERIFIED = {
  verificationToken: 'mock-verification.token',
  name: '김러너',
  birthDate: '1995-03-02',
  phone: '010-1234-5678',
}
const IDENTITY_DONE = { termsAgreed: true, terms: termChecks(true), verification: VERIFIED }

function withQueryClient(ui: ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return (
    <QueryClientProvider client={client}>
      <ToastProvider>{ui}</ToastProvider>
    </QueryClientProvider>
  )
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
            terms: termChecks(true),
            verification: VERIFIED,
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

    // 이름 · 생년월일 · 휴대폰 번호는 본인인증 결과로 잠겨 있다
    expect(screen.getByLabelText('이름')).toHaveValue('김러너')
    expect(screen.getByLabelText('이름')).toBeDisabled()
    await user.type(screen.getByLabelText('닉네임'), '곰')
    await user.tab()

    expect(await screen.findByText(AUTH_ERRORS.nicknameLength)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '가입 완료' })).toBeDisabled()
  })
})

describe('PasswordResetPage', () => {
  it('재설정 API에 이메일을 보내고, 보냈다는 안내를 보여 준다', async () => {
    const user = userEvent.setup()
    let sent: unknown
    server.use(
      http.post('*/api/v1/auth/password-reset/request', async ({ request }) => {
        sent = await request.json()
        return HttpResponse.json({ status: 200, message: 'ok', data: null })
      }),
    )
    render(withQueryClient(<PasswordResetPage />))

    const button = screen.getByRole('button', { name: '재설정 링크 보내기' })
    expect(button).toBeDisabled()

    await user.type(screen.getByLabelText('이메일'), 'runner@crewrun.kr')
    await user.click(button)

    expect(await screen.findByText('메일을 보냈어요')).toBeInTheDocument()
    expect(sent).toEqual({ email: 'runner@crewrun.kr' })
    expect(push).not.toHaveBeenCalled()
  })

  it('1분 안에 다시 요청하면(429) 잠시 뒤 다시 요청하라고 알린다', async () => {
    const user = userEvent.setup()
    server.use(
      http.post('*/api/v1/auth/password-reset/request', () =>
        HttpResponse.json(
          { status: 429, code: 'AUTH_RESET_TOO_MANY', message: '너무 자주 요청', data: null },
          { status: 429 },
        ),
      ),
    )
    render(withQueryClient(<PasswordResetPage />))

    await user.type(screen.getByLabelText('이메일'), 'runner@crewrun.kr')
    await user.click(screen.getByRole('button', { name: '재설정 링크 보내기' }))

    expect(await screen.findByText(/잠시 뒤 다시 요청해 주세요/)).toBeInTheDocument()
    expect(screen.queryByText('메일을 보냈어요')).not.toBeInTheDocument()
  })
})

// 메일 링크로 들어온 것처럼 주소에 토큰을 넣고 연다
function openResetLink(token: string) {
  window.history.replaceState(null, '', `/password-reset/new#token=${token}`)
  return render(withQueryClient(<PasswordNewPage />))
}

describe('PasswordNewPage', () => {
  it('링크의 토큰을 읽어 주소에서 지우고, 새 비밀번호와 함께 보낸 뒤 로그인 화면으로 간다', async () => {
    const user = userEvent.setup()
    let sent: unknown
    server.use(
      http.post('*/api/v1/auth/password-reset/confirm', async ({ request }) => {
        sent = await request.json()
        return HttpResponse.json({ status: 200, message: 'ok', data: null })
      }),
    )
    openResetLink('link-token')

    await user.type(await screen.findByLabelText('새 비밀번호'), 'newpassword1')
    await user.type(screen.getByLabelText('새 비밀번호 확인'), 'newpassword1')
    expect(window.location.hash).toBe('')
    await user.click(screen.getByRole('button', { name: '비밀번호 바꾸기' }))

    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/login'))
    expect(sent).toEqual({ token: 'link-token', password: 'newpassword1' })
    expect(getAuthStatus()).toBe('anonymous')
  })

  it('만료됐거나 이미 쓴 링크면 다시 받기 안내를 보여 준다', async () => {
    openResetLink('expired')

    expect(await screen.findByText('링크를 쓸 수 없어요')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '재설정 링크 다시 받기' })).toHaveAttribute(
      'href',
      '/password-reset',
    )
    expect(screen.queryByLabelText('새 비밀번호')).not.toBeInTheDocument()
  })

  it('주소에 토큰이 없으면 다시 받기 안내를 보여 준다', async () => {
    window.history.replaceState(null, '', '/password-reset/new')
    render(withQueryClient(<PasswordNewPage />))

    expect(await screen.findByText('링크를 쓸 수 없어요')).toBeInTheDocument()
  })

  it('입력하는 사이 링크가 무효가 되면 바꾸기를 눌렀을 때 다시 받기 안내로 바뀐다', async () => {
    const user = userEvent.setup()
    server.use(
      http.post('*/api/v1/auth/password-reset/confirm', () =>
        HttpResponse.json(
          { status: 400, code: 'AUTH_RESET_TOKEN_INVALID', message: '만료', data: null },
          { status: 400 },
        ),
      ),
    )
    openResetLink('link-token')

    await user.type(await screen.findByLabelText('새 비밀번호'), 'newpassword1')
    await user.type(screen.getByLabelText('새 비밀번호 확인'), 'newpassword1')
    await user.click(screen.getByRole('button', { name: '비밀번호 바꾸기' }))

    expect(await screen.findByText('링크를 쓸 수 없어요')).toBeInTheDocument()
    expect(replace).not.toHaveBeenCalled()
  })
})

describe('비밀번호 확인 칸', () => {
  it('확인 칸을 채운 뒤 비밀번호를 고치면 확인 칸 오류가 바로 바뀐다', async () => {
    const user = userEvent.setup()
    openResetLink('link-token')

    await user.type(await screen.findByLabelText('새 비밀번호'), 'password123')
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
      <SignupStepProvider initialState={IDENTITY_DONE}>
        <SignupAccountPage />
      </SignupStepProvider>,
    )

    await user.type(screen.getByLabelText('비밀번호'), 'password123')
    expect(screen.queryByText(AUTH_ERRORS.passwordMismatch)).not.toBeInTheDocument()
  })

  it('가입 계정 화면에서도 비밀번호를 고치면 확인 칸 오류가 바로 뜬다', async () => {
    const user = userEvent.setup()
    render(
      <SignupStepProvider initialState={IDENTITY_DONE}>
        <SignupAccountPage />
      </SignupStepProvider>,
    )

    await user.type(screen.getByLabelText('비밀번호'), 'password123')
    await user.type(screen.getByLabelText('비밀번호 확인'), 'password123')
    await user.tab()
    await user.type(screen.getByLabelText('비밀번호'), '2')
    expect(await screen.findByText(AUTH_ERRORS.passwordMismatch)).toBeInTheDocument()
  })
})
