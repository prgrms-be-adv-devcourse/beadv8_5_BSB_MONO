import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getAuthStatus } from '@/shared/api'
import { server } from '@/shared/testing'
import { ToastProvider } from '@/shared/ui'

import { SignupStepProvider } from '../model/signup-store'
import { termChecks } from '../model/signup-terms'
import { LoginPage } from './LoginPage'
import { SocialSignupPage } from './SocialPages'

const replace = vi.fn()
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), replace }),
  usePathname: () => '/',
}))

beforeEach(() => replace.mockClear())

// 본인인증을 마친 결과. 본인인증 버튼과 창은 signup-verification.test.tsx에서 확인한다
const VERIFIED = {
  verificationToken: 'mock-verification.token',
  name: '김인증',
  birthDate: '1995-04-12',
  phone: '010-9876-5432',
}

function withProviders(ui: ReactNode, { termsAgreed = true, verified = false } = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return (
    <QueryClientProvider client={client}>
      <ToastProvider>
        <SignupStepProvider
          initialState={{
            terms: termChecks(termsAgreed),
            termsAgreed,
            verification: verified ? VERIFIED : null,
          }}
        >
          {ui}
        </SignupStepProvider>
      </ToastProvider>
    </QueryClientProvider>
  )
}

function signupInfo(provider: 'KAKAO' | 'GOOGLE', email: string) {
  return http.get('*/api/v1/auth/oauth2/signup', () =>
    HttpResponse.json({
      status: 200,
      message: '소셜 가입 정보 조회 성공',
      data: { provider, email, emailLocked: true, name: '김러너' },
    }),
  )
}

describe('LoginPage 소셜 로그인', () => {
  it('카카오 · 구글로 시작하기 버튼이 있다', () => {
    render(withProviders(<LoginPage />))
    expect(screen.getByRole('button', { name: '카카오로 시작하기' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '구글로 시작하기' })).toBeInTheDocument()
  })

  it('같은 이메일로 거절되면 로그인한 뒤 계정 연결하라는 안내를 보여 준다', () => {
    render(
      withProviders(
        <LoginPage
          social={{ error: 'AUTH_SOCIAL_EMAIL_TAKEN', provider: 'google', joinedWith: 'email' }}
        />,
      ),
    )
    expect(
      screen.getByText(
        '이메일로 가입한 계정이에요. 이메일로 로그인해 주세요. 마이 > 계정 연결에서 구글을 연결할 수 있어요.',
      ),
    ).toBeInTheDocument()
  })
})

describe('SocialSignupPage', () => {
  it('본인인증 전에는 제공자 이름만 보여 주고 나머지 칸은 비운 채 잠그며, 가입 버튼이 꺼져 있다', async () => {
    server.use(signupInfo('KAKAO', 'runner@kakao.com'))
    render(withProviders(<SocialSignupPage />))

    const email = await screen.findByLabelText('이메일')
    expect(email).toHaveValue('runner@kakao.com')
    expect(email).toHaveAttribute('readonly')
    expect(screen.getByLabelText('이름')).toHaveValue('김러너')
    expect(screen.getByLabelText('이름')).toBeDisabled()
    expect(
      screen.getByText('카카오에서 받은 이름이에요. 본인인증을 하면 인증된 이름으로 바뀌어요'),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('휴대폰 번호')).toHaveValue('')
    expect(screen.getAllByText('본인인증을 하면 채워져요')).toHaveLength(2)
    expect(screen.getByRole('button', { name: '휴대폰 본인인증' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '가입 완료' })).toBeDisabled()
    expect(screen.getByText('카카오 계정으로 가입하고 있어요')).toBeInTheDocument()
  })

  it('본인인증을 마쳤으면 인증된 이름으로 바꿔 잠그고, 가입하면 인증 토큰을 보내 로그인 상태로 next에 간다', async () => {
    let body: Record<string, unknown> | undefined
    server.use(
      signupInfo('KAKAO', 'runner@kakao.com'),
      http.post('*/api/v1/auth/oauth2/join', async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>
        return HttpResponse.json(
          {
            status: 201,
            message: '소셜 가입 성공',
            data: { accessToken: 'access-token', member: { id: 1, nickname: '소셜러너' } },
          },
          { status: 201 },
        )
      }),
    )
    const user = userEvent.setup()
    render(withProviders(<SocialSignupPage next="/races/12" />, { verified: true }))

    await screen.findByLabelText('이메일')
    expect(screen.getByText('본인인증 완료')).toBeInTheDocument()
    expect(screen.getByLabelText('이름')).toHaveValue('김인증')
    expect(screen.getByLabelText('휴대폰 번호')).toHaveValue('010-9876-5432')
    await user.type(screen.getByLabelText('닉네임'), '소셜러너')
    await user.click(screen.getByRole('button', { name: '가입 완료' }))

    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/races/12'))
    expect(body).toEqual({
      nickname: '소셜러너',
      agreedTerms: ['SERVICE', 'PRIVACY', 'MARKETING'],
      verificationToken: 'mock-verification.token',
    })
    expect(getAuthStatus()).toBe('authenticated')
  })

  it('휴대폰 번호로 이미 가입한 사람이면 새로 가입하지 않고 계정 연결을 안내한다', async () => {
    server.use(
      signupInfo('GOOGLE', 'runner@gmail.com'),
      http.post('*/api/v1/auth/oauth2/join', () =>
        HttpResponse.json(
          { status: 409, code: 'AUTH_SOCIAL_PHONE_TAKEN', message: '이미 가입', data: null },
          { status: 409 },
        ),
      ),
    )
    const user = userEvent.setup()
    render(withProviders(<SocialSignupPage />, { verified: true }))

    await screen.findByLabelText('이메일')
    await user.type(screen.getByLabelText('닉네임'), '소셜러너')
    await user.click(screen.getByRole('button', { name: '가입 완료' }))

    expect(
      await screen.findByText(
        '이 번호로 가입한 계정이 있어요. 원래 쓰던 방법으로 로그인한 뒤 마이 → 계정 연결에서 구글 계정을 연결해 주세요.',
      ),
    ).toBeInTheDocument()
    expect(replace).not.toHaveBeenCalled()
  })

  it('가입 대기가 없거나 지났으면 로그인 화면으로 보낸다', async () => {
    server.use(
      http.get('*/api/v1/auth/oauth2/signup', () =>
        HttpResponse.json(
          { status: 401, code: 'AUTH_SOCIAL_SIGNUP_EXPIRED', message: '만료', data: null },
          { status: 401 },
        ),
      ),
    )
    render(withProviders(<SocialSignupPage />))

    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/login?error=AUTH_SOCIAL_FAILED'))
  })
})
