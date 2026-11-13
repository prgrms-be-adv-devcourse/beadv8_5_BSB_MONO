import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { RouteErrorScreen } from './RouteErrorScreen'

const track = vi.hoisted(() => vi.fn())
vi.mock('@/shared/lib', () => ({ trackEvent: track }))
vi.mock('next/navigation', () => ({ useRouter: () => ({ refresh: vi.fn() }) }))

afterEach(() => {
  vi.restoreAllMocks()
  track.mockClear()
})

describe('RouteErrorScreen', () => {
  it('불러오지 못함 문구와 다시 시도하기를 보여 주고, 누르면 retry를 부른다', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    const retry = vi.fn()
    render(<RouteErrorScreen error={new Error('500')} retry={retry} />)

    expect(screen.getByText('정보를 불러오지 못했어요')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '다시 시도하기' }))
    expect(retry).toHaveBeenCalledTimes(1)
  })

  it('오프라인이면 연결 끊김 문구를 보여 준다', () => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    vi.spyOn(window.navigator, 'onLine', 'get').mockReturnValue(false)
    render(<RouteErrorScreen error={new Error('x')} retry={vi.fn()} />)
    expect(screen.getByText('인터넷에 연결되어 있지 않아요')).toBeInTheDocument()
  })

  it('app_error를 digest만 담아 한 번 보낸다 (오류 문구·개인정보 없음)', () => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    const error = Object.assign(new Error('email a@b.c failed'), { digest: 'd1' })
    render(<RouteErrorScreen error={error} retry={vi.fn()} />)
    expect(track).toHaveBeenCalledWith('app_error', { digest: 'd1' })
    expect(track).toHaveBeenCalledTimes(1)
  })
})
