import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'

import GroupError from './error'

vi.mock('next/navigation', () => ({ useRouter: () => ({ refresh: vi.fn() }) }))

afterEach(() => vi.restoreAllMocks())

describe('(admin) 오류 경계', () => {
  it('오류 문구와 다시 시도하기를 보여 주고, 누르면 retry를 부른다', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    const retry = vi.fn()
    render(<GroupError error={new Error('boom')} retry={retry} />)

    expect(screen.getByText('정보를 불러오지 못했어요')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '다시 시도하기' }))
    expect(retry).toHaveBeenCalledTimes(1)
  })
})
