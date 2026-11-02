import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { PHONE_VERIFICATION_MESSAGES } from '@/features/identity-verification'
import { server } from '@/shared/testing'

import { AUTH_ERRORS } from '../model/schemas'
import { SignupStepProvider, type SignupStepInitial } from '../model/signup-store'
import { termChecks } from '../model/signup-terms'
import { SignupProfilePage } from './SignupPages'

const replace = vi.fn()
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), replace }),
  usePathname: () => '/signup/profile',
}))

beforeEach(() => replace.mockClear())

// 약관 동의와 계정 입력을 마친 채로 회원 정보 단계를 연다. 테스트는 실제 백엔드 모드라 인증은 브라우저 안 가짜 응답이 답한다.
const ACCOUNT_DONE: SignupStepInitial = {
  terms: termChecks(true),
  termsAgreed: true,
  account: {
    email: 'runner@crewrun.kr',
    password: 'password1234',
    passwordConfirm: 'password1234',
  },
}

function renderProfileStep() {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <SignupStepProvider initialState={ACCOUNT_DONE}>
        <SignupProfilePage />
      </SignupStepProvider>
    </QueryClientProvider>,
  )
}

/** 가입 목이 받은 본문을 남기고 성공으로 답한다 (로그인은 기본 목이 답한다) */
function captureJoin() {
  const sent: { body?: Record<string, unknown> } = {}
  server.use(
    http.post('*/api/v1/auth/join', async ({ request }) => {
      sent.body = (await request.json()) as Record<string, unknown>
      return HttpResponse.json({ status: 201, message: '가입', data: null }, { status: 201 })
    }),
  )
  return sent
}

describe('휴대폰 번호 인증', () => {
  it('번호를 넣기 전에는 인증번호 받기 버튼이 꺼져 있고, 인증 전에는 가입 버튼도 꺼져 있다', async () => {
    const user = userEvent.setup()
    renderProfileStep()

    expect(screen.getByRole('button', { name: '인증번호 받기' })).toBeDisabled()
    expect(screen.getByRole('button', { name: '가입 완료' })).toBeDisabled()
    expect(screen.getByText(AUTH_ERRORS.verificationNeeded)).toBeInTheDocument()

    await user.type(screen.getByLabelText('휴대폰 번호'), '01012345678')
    expect(screen.getByRole('button', { name: '인증번호 받기' })).toBeEnabled()
  })

  it('인증번호를 받으면 남은 시간이 보이고, 틀리게 넣으면 안내를 띄운다', async () => {
    const user = userEvent.setup()
    renderProfileStep()

    await user.type(screen.getByLabelText('휴대폰 번호'), '01012345678')
    await user.click(screen.getByRole('button', { name: '인증번호 받기' }))
    await user.type(await screen.findByLabelText('인증번호'), '000000')
    expect(screen.getByText('남은 시간 3분 00초')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '인증번호 확인하기' }))
    expect(await screen.findByText(PHONE_VERIFICATION_MESSAGES.codeMismatch)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '가입 완료' })).toBeDisabled()
  })

  it('인증을 마치면 번호를 잠그고, 가입 요청에 인증 토큰을 넣는다', async () => {
    const sent = captureJoin()
    const user = userEvent.setup()
    renderProfileStep()

    await user.type(screen.getByLabelText('이름'), '김러너')
    await user.type(screen.getByLabelText('생년월일'), '1995-03-02')
    await user.type(screen.getByLabelText('휴대폰 번호'), '01012345678')
    await user.type(screen.getByLabelText('닉네임'), '달리는곰')
    await user.click(screen.getByRole('button', { name: '인증번호 받기' }))
    await user.type(await screen.findByLabelText('인증번호'), '123456{Enter}')

    expect(await screen.findByText('휴대폰 인증을 마쳤어요')).toBeInTheDocument()
    expect(screen.getByLabelText('휴대폰 번호')).toHaveAttribute('readonly')
    // 인증번호 칸의 Enter는 가입이 아니라 인증번호 확인이다
    expect(sent.body).toBeUndefined()

    await user.click(screen.getByRole('button', { name: '가입 완료' }))
    await vi.waitFor(() => expect(sent.body).toBeDefined())
    expect(sent.body?.verificationToken).toMatch(/^mock-verification\./)
  })

  it('번호 바꾸기를 누르면 잠금이 풀리고 다시 인증해야 가입할 수 있다', async () => {
    const user = userEvent.setup()
    renderProfileStep()

    await user.type(screen.getByLabelText('휴대폰 번호'), '01012345678')
    await user.click(screen.getByRole('button', { name: '인증번호 받기' }))
    await user.type(await screen.findByLabelText('인증번호'), '123456')
    await user.click(screen.getByRole('button', { name: '인증번호 확인하기' }))
    await user.click(await screen.findByRole('button', { name: '번호 바꾸기' }))

    expect(screen.getByLabelText('휴대폰 번호')).not.toHaveAttribute('readonly')
    expect(screen.getByRole('button', { name: '인증번호 받기' })).toBeInTheDocument()
    expect(screen.getByText(AUTH_ERRORS.verificationNeeded)).toBeInTheDocument()
  })
})

describe('인증 시간이 지났을 때', () => {
  beforeEach(() => vi.useFakeTimers({ shouldAdvanceTime: true }))
  afterEach(() => vi.useRealTimers())

  it('3분이 지나면 안내를 띄우고, 다시 받으면 남은 시간이 처음부터 시작한다', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderProfileStep()

    await user.type(screen.getByLabelText('휴대폰 번호'), '01012345678')
    await user.click(screen.getByRole('button', { name: '인증번호 받기' }))
    await screen.findByLabelText('인증번호')

    await act(() => vi.advanceTimersByTimeAsync(181_000))
    expect(screen.getByText(PHONE_VERIFICATION_MESSAGES.expired)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '인증번호 확인하기' })).toBeDisabled()

    await user.click(screen.getByRole('button', { name: '인증번호 다시 받기' }))
    expect(await screen.findByText('남은 시간 3분 00초')).toBeInTheDocument()
    expect(screen.queryByText(PHONE_VERIFICATION_MESSAGES.expired)).not.toBeInTheDocument()
  })
})

describe('PASS 본인인증', () => {
  async function verifyWithPass(user: ReturnType<typeof userEvent.setup>) {
    await user.click(screen.getByRole('button', { name: 'PASS로 본인인증하기' }))
    await user.click(await screen.findByRole('button', { name: 'LG U+' }))
    const sheet = await screen.findByRole('dialog')
    await user.type(within(sheet).getByLabelText('이름'), '김러너')
    await user.type(within(sheet).getByLabelText('생년월일'), '1995-04-12')
    await user.type(within(sheet).getByLabelText('휴대폰 번호'), '01098765432')
    await user.click(within(sheet).getByRole('button', { name: '본인인증 마치기' }))
    await screen.findByText(/PASS로 본인인증을 마쳤어요/)
  }

  it('인증을 마치면 이름 · 생년월일 · 휴대폰 번호를 채우고 고칠 수 없게 잠근다', async () => {
    const sent = captureJoin()
    const user = userEvent.setup()
    renderProfileStep()

    await verifyWithPass(user)

    for (const [label, value] of [
      ['이름', '김러너'],
      ['생년월일', '1995-04-12'],
      ['휴대폰 번호', '010-9876-5432'],
    ]) {
      expect(screen.getByLabelText(label)).toHaveValue(value)
      expect(screen.getByLabelText(label)).toHaveAttribute('readonly')
    }
    // PASS로 번호까지 확인했으므로 휴대폰 인증 버튼은 숨긴다
    expect(screen.queryByRole('button', { name: '인증번호 받기' })).not.toBeInTheDocument()

    await user.type(screen.getByLabelText('닉네임'), '달리는곰')
    await user.click(screen.getByRole('button', { name: '가입 완료' }))
    await vi.waitFor(() => expect(sent.body).toBeDefined())
    expect(sent.body).toMatchObject({
      name: '김러너',
      birthDate: '1995-04-12',
      phone: '010-9876-5432',
      verificationToken: expect.stringMatching(/^mock-verification\./),
    })
  })

  it('가입 요청이 인증 만료(AUTH_VERIFICATION_REQUIRED)로 거절되면 안내를 띄우고 잠금을 푼다', async () => {
    server.use(
      http.post('*/api/v1/auth/join', () =>
        HttpResponse.json(
          { status: 400, code: 'AUTH_VERIFICATION_REQUIRED', message: '본인 확인', data: null },
          { status: 400 },
        ),
      ),
    )
    const user = userEvent.setup()
    renderProfileStep()

    await verifyWithPass(user)
    await user.type(screen.getByLabelText('닉네임'), '달리는곰')
    await user.click(screen.getByRole('button', { name: '가입 완료' }))

    expect(await screen.findByText(AUTH_ERRORS.verificationExpired)).toBeInTheDocument()
    expect(screen.getByLabelText('이름')).not.toHaveAttribute('readonly')
    expect(screen.getByRole('button', { name: 'PASS로 본인인증하기' })).toBeInTheDocument()
  })
})
