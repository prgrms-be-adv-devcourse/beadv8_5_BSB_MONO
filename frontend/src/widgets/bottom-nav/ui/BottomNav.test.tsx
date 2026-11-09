import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { BottomNav } from './BottomNav'

vi.mock('next/navigation', () => ({ usePathname: () => '/cart' }))

describe('BottomNav', () => {
  it('대회 · 내 참가권 · 홈 · 장바구니 · 마이 순서이고, 지금 화면 탭을 표시한다', () => {
    render(<BottomNav />)

    const links = screen.getAllByRole('link')
    expect(links.map((link) => link.getAttribute('aria-label') ?? link.textContent)).toEqual([
      '대회',
      '내 참가권',
      '홈',
      '장바구니',
      '마이',
    ])
    expect(screen.getByRole('link', { name: '장바구니' })).toHaveAttribute('aria-current', 'page')
    expect(screen.queryByRole('link', { name: '알림' })).not.toBeInTheDocument()
  })
})
