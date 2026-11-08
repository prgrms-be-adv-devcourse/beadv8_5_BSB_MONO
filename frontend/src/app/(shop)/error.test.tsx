import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'

import ShopError from './error'

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), replace: vi.fn(), refresh: vi.fn() }),
  usePathname: () => '/',
}))

afterEach(() => vi.restoreAllMocks())

describe('회원 화면 오류 경계', () => {
  it('브라우저가 오프라인이면 연결 끊김 화면을 보여 주고, 다시 시도하기로 화면을 다시 그린다', async () => {
    vi.spyOn(window.navigator, 'onLine', 'get').mockReturnValue(false)
    const reset = vi.fn()
    render(<ShopError error={new Error('fetch failed')} reset={reset} />)

    expect(screen.getByText('인터넷에 연결되어 있지 않아요')).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: '주요 메뉴' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '다시 시도하기' }))
    expect(reset).toHaveBeenCalledTimes(1)
  })

  it('연결은 되어 있으면 불러오지 못함 화면을 보여 준다', async () => {
    vi.spyOn(window.navigator, 'onLine', 'get').mockReturnValue(true)
    const reset = vi.fn()
    render(<ShopError error={new Error('500')} reset={reset} />)

    expect(screen.getByText('정보를 불러오지 못했어요')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '다시 시도하기' }))
    expect(reset).toHaveBeenCalledTimes(1)
  })
})
