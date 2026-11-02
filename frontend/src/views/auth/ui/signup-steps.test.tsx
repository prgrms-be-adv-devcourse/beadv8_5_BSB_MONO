import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getAuthStatus } from '@/shared/api'
import { server } from '@/shared/testing'
import { ToastProvider } from '@/shared/ui'

import { AUTH_ERRORS } from '../model/schemas'
import { SignupStepProvider, type SignupStepInitial } from '../model/signup-store'
import { termChecks } from '../model/signup-terms'
import { SignupAccountPage, SignupIdentityPage, SignupProfilePage } from './SignupPages'

const push = vi.fn()
const replace = vi.fn()
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push, back: vi.fn(), replace }),
  usePathname: () => '/signup',
}))

beforeEach(() => {
  push.mockClear()
  replace.mockClear()
})

// 본인인증을 마친 결과. 본인인증 화면 자체는 signup-verification.test.tsx에서 확인한다
const VERIFIED = {
  verificationToken: 'mock-verification.token',
  name: '김러너',
  birthDate: '1995-03-02',
  phone: '010-1234-5678',
}

// 약관 동의와 본인인증을 마친 채로 계정 단계를 연다.
const IDENTITY_DONE: SignupStepInitial = {
  terms: termChecks(true),
  termsAgreed: true,
  verification: VERIFIED,
}

// 계정 입력까지 마친 채로 회원 정보 단계를 연다.
const ACCOUNT_DONE: SignupStepInitial = {
  ...IDENTITY_DONE,
  account: {
    email: 'runner@crewrun.kr',
    password: 'password1234',
    passwordConfirm: 'password1234',
  },
}

type Step = 'identity' | 'account' | 'profile'
const PAGES: Record<Step, ReactNode> = {
  identity: <SignupIdentityPage />,
  account: <SignupAccountPage />,
  profile: <SignupProfilePage />,
}

// (auth) 레이아웃처럼 Provider는 그대로 두고 안쪽 페이지만 바꿔 단계 이동을 흉내 낸다.
function renderSignup(first: Step = 'account', initialState: SignupStepInitial = IDENTITY_DONE) {
  const client = new QueryClient()
  const wrap = (page: ReactNode) => (
    <QueryClientProvider client={client}>
      <ToastProvider>
        <SignupStepProvider initialState={initialState}>{page}</SignupStepProvider>
      </ToastProvider>
    </QueryClientProvider>
  )
  const view = render(wrap(PAGES[first]))
  const show = (page: Step) => view.rerender(wrap(PAGES[page]))
  return { show }
}

function renderProfileStep(initialState: SignupStepInitial = ACCOUNT_DONE) {
  renderSignup('profile', initialState)
}

// 이름 · 생년월일 · 휴대폰 번호는 본인인증 결과로 잠겨 있어 닉네임만 적는다
async function fillProfile(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('닉네임'), '달리는곰')
}

describe('회원가입 단계', () => {
  it('필수 약관을 체크하지 않으면 본인 확인으로 넘어갈 수 없다', async () => {
    const user = userEvent.setup()
    renderSignup('identity', {})

    const agree = await screen.findByRole('button', { name: '동의하고 계속하기' })
    expect(agree).toBeDisabled()

    await user.click(screen.getByRole('checkbox', { name: '[필수] 서비스 이용약관' }))
    await user.click(screen.getByRole('checkbox', { name: '[선택] 마케팅 정보 받기' }))
    expect(agree).toBeDisabled()
  })

  it('약관 시트를 동의 없이 닫아도 본인인증 버튼을 누르면 시트가 다시 열린다', async () => {
    const user = userEvent.setup()
    renderSignup('identity', {})

    await user.click(await screen.findByRole('button', { name: '닫기' }))
    expect(screen.queryByRole('button', { name: '동의하고 계속하기' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '휴대폰 본인인증' }))
    expect(await screen.findByRole('button', { name: '동의하고 계속하기' })).toBeInTheDocument()
    expect(push).not.toHaveBeenCalled()
  })

  it('비밀번호가 9자면(백엔드 10자 이상) 오류가 뜨고 다음 버튼이 꺼진다', async () => {
    const user = userEvent.setup()
    renderSignup()

    await user.type(screen.getByLabelText('이메일'), 'runner@crewrun.kr')
    await user.type(screen.getByLabelText('비밀번호'), 'password1')
    await user.tab()

    expect(await screen.findByText(AUTH_ERRORS.passwordRule)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '다음' })).toBeDisabled()
  })

  it('다음 단계로 갔다가 돌아와도 입력값이 남아 있다', async () => {
    const user = userEvent.setup()
    const { show } = renderSignup()

    await user.type(screen.getByLabelText('이메일'), 'runner@crewrun.kr')
    await user.type(screen.getByLabelText('비밀번호'), 'password123')
    await user.type(screen.getByLabelText('비밀번호 확인'), 'password123')
    await user.click(screen.getByRole('button', { name: '다음' }))
    await vi.waitFor(() => expect(push).toHaveBeenCalledWith('/signup/profile'))

    show('profile')
    await user.type(screen.getByLabelText('닉네임'), '달리는곰')

    show('account')
    expect(screen.getByLabelText('이메일')).toHaveValue('runner@crewrun.kr')
    expect(screen.getByLabelText('비밀번호 확인')).toHaveValue('password123')
    expect(await screen.findByRole('button', { name: '다음' })).toBeEnabled()

    show('profile')
    expect(screen.getByLabelText('닉네임')).toHaveValue('달리는곰')
  })

  it('본인인증을 마쳤으면 본인 확인 화면이 인증된 정보와 다음 버튼을 보여 준다', async () => {
    const user = userEvent.setup()
    renderSignup('identity')

    expect(screen.getByLabelText('이름')).toHaveValue('김러너')
    await user.click(screen.getByRole('button', { name: '다음' }))
    expect(push).toHaveBeenCalledWith('/signup/account')
  })

  it('가입 완료를 누르면 이름 · 생년월일 · 휴대폰 번호 대신 인증 토큰을 보내고, 바로 로그인해 홈으로 간다', async () => {
    let body: unknown
    server.use(
      http.post('*/api/v1/auth/join', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json(
          { status: 201, message: 'Member Joined Successfully', data: null },
          { status: 201 },
        )
      }),
    )
    const user = userEvent.setup()
    renderProfileStep()

    for (const [label, value] of [
      ['이름', '김러너'],
      ['생년월일', '1995.03.02'],
      ['휴대폰 번호', '010-1234-5678'],
    ]) {
      expect(screen.getByLabelText(label)).toHaveValue(value)
      expect(screen.getByLabelText(label)).toBeDisabled()
    }
    await fillProfile(user)
    await user.click(screen.getByRole('radio', { name: '여성' }))
    await user.click(screen.getByRole('button', { name: '가입 완료' }))

    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/'))
    expect(body).toEqual({
      email: 'runner@crewrun.kr',
      password: 'password1234',
      nickname: '달리는곰',
      gender: 'FEMALE',
      agreedTerms: ['SERVICE', 'PRIVACY', 'MARKETING'],
      verificationToken: 'mock-verification.token',
    })
    expect(getAuthStatus()).toBe('authenticated')
  })

  it('닉네임이 이미 쓰이면 닉네임 칸에 서버 문구를 띄우고 이동하지 않는다', async () => {
    server.use(
      http.post('*/api/v1/auth/join', () =>
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
    renderProfileStep()

    await fillProfile(user)
    await user.click(screen.getByRole('button', { name: '가입 완료' }))

    expect(await screen.findByText('이미 사용 중인 닉네임입니다.')).toBeInTheDocument()
    expect(replace).not.toHaveBeenCalled()
  })

  it.each(['MEMBER_DUPLICATE_IDENTITY', 'MEMBER_DUPLICATE_PHONE'])(
    '이미 가입한 사람(%s)이면 로그인을 안내하는 모달을 띄운다',
    async (code) => {
      server.use(
        http.post('*/api/v1/auth/join', () =>
          HttpResponse.json(
            { status: 409, code, message: '이미 가입', data: null },
            { status: 409 },
          ),
        ),
      )
      const user = userEvent.setup()
      renderProfileStep()

      await fillProfile(user)
      await user.click(screen.getByRole('button', { name: '가입 완료' }))

      expect(await screen.findByText('이미 가입한 사람이에요')).toBeInTheDocument()
      await user.click(screen.getByRole('button', { name: '로그인하기' }))
      expect(push).toHaveBeenCalledWith('/login')
    },
  )

  it('본인인증 결과가 지났으면(AUTH_VERIFICATION_REQUIRED) 안내를 띄우고 본인 확인 단계로 돌아간다', async () => {
    server.use(
      http.post('*/api/v1/auth/join', () =>
        HttpResponse.json(
          { status: 400, code: 'AUTH_VERIFICATION_REQUIRED', message: '다시', data: null },
          { status: 400 },
        ),
      ),
    )
    const user = userEvent.setup()
    renderProfileStep()

    await fillProfile(user)
    await user.click(screen.getByRole('button', { name: '가입 완료' }))

    // Toast는 화면 낭독기용 영역에도 같은 글을 그려 두 번 찾힌다
    expect((await screen.findAllByText(AUTH_ERRORS.verificationExpired)).length).toBeGreaterThan(0)
    expect(replace).toHaveBeenCalledWith('/signup')
  })

  it.each([
    ['계정', <SignupAccountPage key="account" />],
    ['회원 정보', <SignupProfilePage key="profile" />],
  ])('본인인증 없이 %s 단계에 오면 첫 단계로 돌려보낸다', (_, page) => {
    render(
      <SignupStepProvider initialState={{ terms: termChecks(true), termsAgreed: true }}>
        {page}
      </SignupStepProvider>,
    )

    expect(replace).toHaveBeenCalledWith('/signup')
  })

  it('시안 미리보기(invalid)는 앞 단계 없이 M04-1E 오류를 보여 준다', () => {
    render(
      <SignupStepProvider>
        <SignupAccountPage invalid />
      </SignupStepProvider>,
    )
    expect(screen.getByText(AUTH_ERRORS.emailTaken)).toBeInTheDocument()
    expect(screen.getByText(AUTH_ERRORS.passwordMismatch)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '다음' })).toBeDisabled()
  })
})

describe('이메일 중복 확인 (PRO-201)', () => {
  async function fillAccount(user: ReturnType<typeof userEvent.setup>, email: string) {
    await user.type(screen.getByLabelText('이메일'), email)
    await user.type(screen.getByLabelText('비밀번호'), 'password123')
    await user.type(screen.getByLabelText('비밀번호 확인'), 'password123')
  }

  it('이미 가입된 이메일이면 칸에 오류가 뜨고 다음 단계로 못 넘어간다', async () => {
    const user = userEvent.setup()
    renderSignup()

    await fillAccount(user, 'taken@crewrun.kr')
    await user.click(screen.getByRole('button', { name: '다음' }))

    expect(await screen.findByText(AUTH_ERRORS.emailTaken)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '다음' })).toBeDisabled()
    expect(push).not.toHaveBeenCalled()
  })

  it('이메일 칸을 벗어나면 다음을 누르기 전에 중복을 알려 준다', async () => {
    const user = userEvent.setup()
    renderSignup()

    await user.type(screen.getByLabelText('이메일'), 'taken@crewrun.kr')
    await user.tab()

    expect(await screen.findByText(AUTH_ERRORS.emailTaken)).toBeInTheDocument()
  })

  it('처음 쓰는 이메일이면 다음 단계로 넘어간다', async () => {
    const user = userEvent.setup()
    renderSignup()

    await fillAccount(user, 'runner@crewrun.kr')
    await user.click(screen.getByRole('button', { name: '다음' }))

    await vi.waitFor(() => expect(push).toHaveBeenCalledWith('/signup/profile'))
    expect(screen.queryByText(AUTH_ERRORS.emailTaken)).not.toBeInTheDocument()
  })

  it('확인 요청이 실패하면 안내를 띄우고 넘어가지 않는다', async () => {
    server.use(
      http.get('*/api/v1/member/members/email-availability', () =>
        HttpResponse.json(
          { status: 500, code: 'INTERNAL_ERROR', message: '서버 오류', data: null },
          { status: 500 },
        ),
      ),
    )
    const user = userEvent.setup()
    renderSignup()

    await fillAccount(user, 'runner@crewrun.kr')
    await user.click(screen.getByRole('button', { name: '다음' }))

    expect(await screen.findByText(AUTH_ERRORS.emailCheckFailed)).toBeInTheDocument()
    expect(push).not.toHaveBeenCalled()

    // 확인 실패는 버튼을 막지 않는다. 서버가 돌아오면 같은 이메일로 다시 시도할 수 있다.
    server.resetHandlers()
    await user.click(screen.getByRole('button', { name: '다음' }))
    await vi.waitFor(() => expect(push).toHaveBeenCalledWith('/signup/profile'))
  })
})

describe('Enter로 제출 (QA 10/20)', () => {
  it('계정 단계에서 Enter를 누르면 다음과 같이 회원 정보 단계로 간다', async () => {
    const user = userEvent.setup()
    renderSignup()

    await user.type(screen.getByLabelText('이메일'), 'runner@crewrun.kr')
    await user.type(screen.getByLabelText('비밀번호'), 'password123')
    await user.type(screen.getByLabelText('비밀번호 확인'), 'password123{Enter}')

    await vi.waitFor(() => expect(push).toHaveBeenCalledWith('/signup/profile'))
  })

  it('계정 입력이 틀리면 Enter를 눌러도 넘어가지 않는다', async () => {
    const user = userEvent.setup()
    renderSignup()

    await user.type(screen.getByLabelText('이메일'), 'runner@crewrun.kr')
    await user.type(screen.getByLabelText('비밀번호'), 'short{Enter}')

    // 검사에 실패하면 다음 버튼이 꺼져 있어 Enter로도 제출되지 않는다
    expect(screen.getByRole('button', { name: '다음' })).toBeDisabled()
    expect(push).not.toHaveBeenCalled()
  })

  it('회원 정보 단계의 닉네임 칸에서 Enter를 누르면 가입한다', async () => {
    let joined = 0
    server.use(
      http.post('*/api/v1/auth/join', () => {
        joined += 1
        return HttpResponse.json(
          { status: 201, message: 'Member Joined Successfully', data: null },
          { status: 201 },
        )
      }),
    )
    const user = userEvent.setup()
    renderProfileStep()

    await user.type(screen.getByLabelText('닉네임'), '달리는곰{Enter}')
    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/'))
    expect(joined).toBe(1)
  })

  it('가입 요청 중에는 버튼에 로딩을 띄우고 다시 눌러도 요청을 또 보내지 않는다', async () => {
    let joined = 0
    let finish = () => {}
    server.use(
      http.post('*/api/v1/auth/join', async () => {
        joined += 1
        await new Promise<void>((resolve) => (finish = resolve))
        return HttpResponse.json({ status: 201, message: 'ok', data: null }, { status: 201 })
      }),
    )
    const user = userEvent.setup()
    renderProfileStep()

    await fillProfile(user)
    const button = screen.getByRole('button', { name: '가입 완료' })
    await user.click(button)
    await vi.waitFor(() => expect(button).toHaveAttribute('aria-busy', 'true'))
    await user.click(button)
    await user.type(screen.getByLabelText('닉네임'), '{Enter}')
    expect(joined).toBe(1)

    finish()
    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/'))
  })

  it('회원 정보가 덜 채워졌으면 Enter를 눌러도 가입 요청을 보내지 않는다', async () => {
    let joined = 0
    server.use(
      http.post('*/api/v1/auth/join', () => {
        joined += 1
        return HttpResponse.json({ status: 201, message: 'ok', data: null }, { status: 201 })
      }),
    )
    const user = userEvent.setup()
    renderProfileStep()

    await user.type(screen.getByLabelText('닉네임'), '곰{Enter}')

    expect(screen.getByRole('button', { name: '가입 완료' })).toBeDisabled()
    expect(joined).toBe(0)
    expect(replace).not.toHaveBeenCalled()
  })
})

describe('약관 동의 전송 (PRO-208)', () => {
  async function submitWith(terms: SignupStepInitial['terms']) {
    const user = userEvent.setup()
    renderProfileStep({ ...ACCOUNT_DONE, terms })
    await fillProfile(user)
    await user.click(screen.getByRole('button', { name: '가입 완료' }))
  }

  it('체크하지 않은 선택 약관은 빼고 보낸다', async () => {
    let sent: { agreedTerms?: string[] } = {}
    server.use(
      http.post('*/api/v1/auth/join', async ({ request }) => {
        sent = (await request.json()) as typeof sent
        return HttpResponse.json({ status: 201, message: 'ok', data: null }, { status: 201 })
      }),
    )
    await submitWith({ service: true, privacy: true, age: true, marketing: false })

    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/'))
    expect(sent.agreedTerms).toEqual(['SERVICE', 'PRIVACY'])
  })

  it('필수 약관 미동의(400 MEMBER_REQUIRED_TERMS_NOT_AGREED)는 약관 안내를 보여 준다', async () => {
    await submitWith({ service: true, privacy: false, age: true, marketing: false })

    expect(await screen.findByText(AUTH_ERRORS.joinTermsRequired)).toBeInTheDocument()
    expect(replace).not.toHaveBeenCalled()
  })

  it.each([
    ['INVALID_REQUEST', 400],
    ['INTERNAL_ERROR', 500],
  ])('%s 응답은 일반 가입 실패 안내를 보여 준다', async (code, status) => {
    server.use(
      http.post('*/api/v1/auth/join', () =>
        HttpResponse.json({ status, code, message: '서버 내부 문구', data: null }, { status }),
      ),
    )
    await submitWith(termChecks(true))

    expect(await screen.findByText(AUTH_ERRORS.joinUnavailable)).toBeInTheDocument()
    expect(screen.queryByText('서버 내부 문구')).not.toBeInTheDocument()
  })
})
