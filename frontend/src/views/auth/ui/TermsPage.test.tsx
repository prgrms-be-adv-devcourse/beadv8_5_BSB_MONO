import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { SignupStepProvider, useSignupStep } from '../model/signup-store'
import { TERMS_SLUGS } from '../model/terms'
import { TermsPage } from './TermsPage'

const back = vi.fn()
let query = ''
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back, replace: vi.fn() }),
  usePathname: () => '/terms/privacy',
  useSearchParams: () => new URLSearchParams(query),
}))

beforeEach(() => {
  back.mockClear()
  query = ''
})

// 가입 약관 체크 상태를 화면에 드러내 확인한다
function ChecksProbe() {
  const terms = useSignupStep((s) => s.terms)
  return (
    <>
      {Object.entries(terms).map(([id, checked]) => (
        <span key={id} data-testid={id}>
          {String(checked)}
        </span>
      ))}
    </>
  )
}

describe('TermsPage', () => {
  it.each(TERMS_SLUGS)(
    '%s 약관을 로그인 화면 등에서 열면 [동의하기] 버튼이 없다 (조회 전용)',
    (slug) => {
      render(<TermsPage slug={slug} />)

      expect(screen.queryByRole('button', { name: '동의하기' })).not.toBeInTheDocument()
      expect(screen.queryByText(/시안용 예시/)).not.toBeInTheDocument()
    },
  )

  it('가입 약관 시트에서 열면(?from=signup) [동의하기]가 그 약관을 체크하고 이전 화면으로 돌아간다', async () => {
    query = 'from=signup'
    const user = userEvent.setup()
    render(
      <SignupStepProvider>
        <TermsPage slug="privacy" />
        <ChecksProbe />
      </SignupStepProvider>,
    )

    expect(screen.getByTestId('privacy')).toHaveTextContent('false')
    await user.click(screen.getByRole('button', { name: '동의하기' }))

    expect(back).toHaveBeenCalled()
    expect(screen.getByTestId('privacy')).toHaveTextContent('true')
    expect(screen.getByTestId('service')).toHaveTextContent('false')
  })

  it('개인정보 수집 · 이용에 수집 항목, 이용 목적, 보유 기간, 제3자 제공, 쿠키와 이용 통계를 보여 준다', () => {
    render(<TermsPage slug="privacy" />)

    expect(screen.getByRole('heading', { name: '[필수] 개인정보 수집 · 이용' })).toBeInTheDocument()
    for (const title of ['수집 항목', '이용 목적', '보유 기간', '제3자 제공', '쿠키와 이용 통계']) {
      expect(screen.getByRole('heading', { name: title })).toBeInTheDocument()
    }
    expect(
      screen.getByText(/이메일, 비밀번호, 이름, 생년월일, 휴대폰 번호, 닉네임/),
    ).toBeInTheDocument()
    expect(screen.getByText(/Google 애널리틱스와 Google 태그 관리자로 보내요/)).toBeInTheDocument()
    expect(screen.getByText(/시행일 2026\.11\.03 · 버전 1\.2/)).toBeInTheDocument()
  })
})
