import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getAuthStatus } from '@/shared/api'
import { pickDate, server } from '@/shared/testing'

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

function withProviders(ui: ReactNode, termsAgreed = true) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return (
    <QueryClientProvider client={client}>
      <SignupStepProvider initialState={{ terms: termChecks(termsAgreed), termsAgreed }}>
        {ui}
      </SignupStepProvider>
    </QueryClientProvider>
  )
}

// 휴대폰 번호 칸에 넣은 번호로 인증까지 마친다 (목업 인증번호는 언제나 123456). 인증해야 가입 버튼이 켜진다.
async function verifyPhone(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole('button', { name: '인증번호 받기' }))
  await user.type(await screen.findByLabelText('인증번호'), '123456')
  await user.click(screen.getByRole('button', { name: '인증번호 확인하기' }))
  await screen.findByText('휴대폰 인증을 마쳤어요')
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
  it('제공자가 확인한 이메일은 고칠 수 없게 보여 주고, 가입하면 로그인 상태로 next에 간다', async () => {
    let body: Record<string, unknown> | undefined
    server.use(
      http.get('*/api/v1/auth/oauth2/signup', () =>
        HttpResponse.json({
          status: 200,
          message: '소셜 가입 정보 조회 성공',
          data: { provider: 'KAKAO', email: 'runner@kakao.com', emailLocked: true, name: '김러너' },
        }),
      ),
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
    render(withProviders(<SocialSignupPage next="/races/12" />))

    const email = await screen.findByLabelText('이메일')
    expect(email).toHaveValue('runner@kakao.com')
    expect(email).toHaveAttribute('readonly')
    expect(screen.getByLabelText('이름')).toHaveValue('김러너')

    await pickDate(user, '생년월일', '1995-04-12')
    await user.type(screen.getByLabelText('휴대폰 번호'), '01098765432')
    await user.type(screen.getByLabelText('닉네임'), '소셜러너')
    // 본인 확인 전에는 가입 버튼이 꺼져 있고, 왜 꺼졌는지 버튼 위에 알려 준다
    expect(screen.getByRole('button', { name: '가입 완료' })).toBeDisabled()
    expect(
      screen.getByText('휴대폰 인증이나 PASS 본인인증을 마치면 가입할 수 있어요.'),
    ).toBeInTheDocument()
    await verifyPhone(user)
    await user.click(screen.getByRole('button', { name: '가입 완료' }))

    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/races/12'))
    expect(body).toMatchObject({
      name: '김러너',
      nickname: '소셜러너',
      agreedTerms: ['SERVICE', 'PRIVACY', 'MARKETING'],
      verificationToken: expect.stringMatching(/^mock-verification\./),
    })
    expect(body?.email).toBeUndefined()
    expect(getAuthStatus()).toBe('authenticated')
  })

  it('휴대폰 번호로 이미 가입한 사람이면 새로 가입하지 않고 계정 연결을 안내한다', async () => {
    server.use(
      http.get('*/api/v1/auth/oauth2/signup', () =>
        HttpResponse.json({
          status: 200,
          message: '소셜 가입 정보 조회 성공',
          data: {
            provider: 'GOOGLE',
            email: 'runner@gmail.com',
            emailLocked: true,
            name: '김러너',
          },
        }),
      ),
      http.post('*/api/v1/auth/oauth2/join', () =>
        HttpResponse.json(
          { status: 409, code: 'AUTH_SOCIAL_PHONE_TAKEN', message: '이미 가입', data: null },
          { status: 409 },
        ),
      ),
    )
    const user = userEvent.setup()
    render(withProviders(<SocialSignupPage />))

    await screen.findByLabelText('이메일')
    await pickDate(user, '생년월일', '1995-04-12')
    await user.type(screen.getByLabelText('휴대폰 번호'), '01098765432')
    await user.type(screen.getByLabelText('닉네임'), '소셜러너')
    await verifyPhone(user)
    await user.click(screen.getByRole('button', { name: '가입 완료' }))

    expect(await screen.findByText('이 번호로 가입한 계정이 있어요.')).toBeInTheDocument()
    expect(
      screen.getByText(
        '원래 쓰던 방법으로 로그인한 뒤 마이 → 계정 연결에서 구글 계정을 연결해 주세요.',
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
