import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { TERMS_SLUGS } from '../model/terms'
import { TermsPage } from './TermsPage'

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), replace: vi.fn() }),
  usePathname: () => '/terms/privacy',
}))

describe('TermsPage (조회 전용)', () => {
  it.each(TERMS_SLUGS)('%s 약관에는 [동의하기] 버튼과 시안용 안내가 없다', (slug) => {
    render(<TermsPage slug={slug} />)

    expect(screen.queryByRole('button', { name: '동의하기' })).not.toBeInTheDocument()
    expect(screen.queryByText(/시안용 예시/)).not.toBeInTheDocument()
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
    expect(screen.getByText(/시행일 2026\.10\.30 · 버전 1\.1/)).toBeInTheDocument()
  })
})
