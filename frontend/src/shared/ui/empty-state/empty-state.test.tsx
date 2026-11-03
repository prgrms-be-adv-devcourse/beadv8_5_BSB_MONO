import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { EmptyState } from './EmptyState'

describe('EmptyState', () => {
  it('회색 로고 그림과 제목·설명을 보여 주고, 그림은 화면 낭독기가 읽지 않는다', () => {
    const { container } = render(
      <EmptyState title="장바구니가 비어 있어요" description="관심 있는 대회를 담아 보세요." />,
    )

    const logo = container.querySelector('img')
    expect(logo).toHaveAttribute('src', expect.stringContaining('/brand/symbol-muted.svg'))
    expect(logo).toHaveAttribute('alt', '')
    expect(logo).toHaveAttribute('aria-hidden', 'true')
    expect(screen.getByText('장바구니가 비어 있어요')).toBeInTheDocument()
    expect(screen.getByText('관심 있는 대회를 담아 보세요.')).toBeInTheDocument()
  })

  it('role을 넘기지 않으면 알림으로 읽지 않는다', () => {
    render(<EmptyState title="아직 내역이 없어요" />)

    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('role을 넘기면 제목과 설명을 그 역할로 읽는다', () => {
    render(
      <EmptyState
        role="alert"
        title="불러오지 못했어요"
        description="잠시 뒤 다시 시도해 주세요."
      />,
    )

    expect(screen.getByRole('alert')).toHaveTextContent(
      '불러오지 못했어요잠시 뒤 다시 시도해 주세요.',
    )
  })
})
