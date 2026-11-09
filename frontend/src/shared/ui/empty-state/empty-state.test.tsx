import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { EmptyState } from './EmptyState'

describe('EmptyState', () => {
  it('회색 아이콘과 제목·설명을 보여 주고, 아이콘은 화면 낭독기가 읽지 않는다', () => {
    const { container } = render(
      <EmptyState icon="Bell" title="아직 알림이 없어요" description="여기로 알려 드려요." />,
    )

    const icon = container.querySelector('svg')
    expect(icon).toHaveAttribute('aria-hidden', 'true')
    expect(container.querySelector('img')).not.toBeInTheDocument()
    expect(screen.getByText('아직 알림이 없어요')).toBeInTheDocument()
    expect(screen.getByText('여기로 알려 드려요.')).toBeInTheDocument()
  })

  it('아이콘을 넘기지 않아도 기본 아이콘을 그린다', () => {
    const { container } = render(<EmptyState title="아직 내역이 없어요" />)

    expect(container.querySelector('svg')).toBeInTheDocument()
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
