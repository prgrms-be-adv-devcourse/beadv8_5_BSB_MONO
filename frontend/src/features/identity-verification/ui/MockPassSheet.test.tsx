import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { pickDate } from '@/shared/testing'

import { MockPassSheet } from './MockPassSheet'

const verify = vi.hoisted(() => vi.fn())
vi.mock('../api/identity-verification', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/identity-verification')>()),
  verifyWithMockPass: verify,
}))

describe('MockPassSheet', () => {
  it('이름·생년월일·휴대폰 번호가 맞아야 인증을 요청할 수 있고, 앞뒤 공백은 빼고 보낸다', async () => {
    verify.mockResolvedValue({
      status: 'verified',
      identity: { name: '김러너', birthDate: '1995-03-02', phone: '010-1234-5678' },
    })
    const onVerified = vi.fn()
    const user = userEvent.setup()
    render(<MockPassSheet open onOpenChange={vi.fn()} onVerified={onVerified} />)
    const request = screen.getByRole('button', { name: '인증 요청' })
    expect(request).toBeDisabled()

    await user.type(screen.getByRole('textbox', { name: '이름' }), '   ')
    await user.type(screen.getByRole('textbox', { name: '휴대폰 번호' }), '010-1234-5678')
    await pickDate(user, '생년월일', '1995-03-02')
    expect(request).toBeDisabled()

    await user.clear(screen.getByRole('textbox', { name: '이름' }))
    await user.type(screen.getByRole('textbox', { name: '이름' }), ' 김러너 ')
    await waitFor(() => expect(request).toBeEnabled())
    await user.click(request)
    await user.click(await screen.findByRole('button', { name: '인증 완료' }))

    await waitFor(() => expect(onVerified).toHaveBeenCalled())
    expect(verify).toHaveBeenCalledWith({
      carrier: 'SKT',
      name: '김러너',
      birthDate: '1995-03-02',
      phone: '010-1234-5678',
    })
  })
})
