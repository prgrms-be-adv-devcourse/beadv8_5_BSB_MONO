import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'

import GlobalError from './global-error'

const track = vi.hoisted(() => vi.fn())
vi.mock('@/shared/lib', () => ({ trackEvent: track, trackingGtmId: () => undefined }))

afterEach(() => vi.restoreAllMocks())

describe('전역 오류 경계', () => {
  it('자체 html·body 안에 문구와 다시 시도하기를 그리고, 누르면 retry를 부른다', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    const retry = vi.fn()
    render(<GlobalError error={Object.assign(new Error('x'), { digest: 'g1' })} retry={retry} />, {
      container: document,
    })

    expect(screen.getByText('문제가 생겼어요')).toBeInTheDocument()
    expect(track).toHaveBeenCalledWith('app_error', { digest: 'g1' })
    await userEvent.click(screen.getByRole('button', { name: '다시 시도하기' }))
    expect(retry).toHaveBeenCalledTimes(1)
  })
})
