import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { NotificationSettingsPage } from './NotificationSettingsPage'

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn() }),
}))

describe('NotificationSettingsPage', () => {
  it('시안 값대로 스위치를 보여 주고, 결제 알림은 끌 수 없다', async () => {
    render(<NotificationSettingsPage />)

    expect(screen.getByRole('switch', { name: '대회 소식' })).toBeChecked()
    expect(screen.getByRole('switch', { name: '밤 9시~아침 8시 광고 받기' })).not.toBeChecked()
    expect(screen.getByRole('switch', { name: '결제 · 예치금' })).toHaveAttribute(
      'aria-disabled',
      'true',
    )

    await userEvent.click(screen.getByRole('switch', { name: '밤 9시~아침 8시 광고 받기' }))
    expect(screen.getByRole('switch', { name: '밤 9시~아침 8시 광고 받기' })).toBeChecked()
  })
})
