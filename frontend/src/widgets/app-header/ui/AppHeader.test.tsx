import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { AppHeader } from './AppHeader'

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), replace: vi.fn() }),
}))

describe('AppHeader', () => {
  it('기본 머리말 오른쪽에 대회 찾기와 알림 아이콘이 있고, 알림은 알림 목록으로 간다', () => {
    render(<AppHeader />)

    expect(screen.getByRole('link', { name: '대회 찾기' })).toHaveAttribute('href', '/races')
    expect(screen.getByRole('link', { name: '알림' })).toHaveAttribute('href', '/notifications')
    expect(screen.queryByRole('link', { name: '장바구니' })).not.toBeInTheDocument()
  })
})
