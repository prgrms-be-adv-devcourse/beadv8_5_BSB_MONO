import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render as rtlRender, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'

import ShopError from './error'

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), replace: vi.fn(), refresh: vi.fn() }),
  usePathname: () => '/',
}))

afterEach(() => vi.restoreAllMocks())

// 머리말 알림 아이콘이 알림 쿼리를 쓴다. 앱에서는 루트 Provider가 감싼다
const render = (ui: React.ReactElement) =>
  rtlRender(<QueryClientProvider client={new QueryClient()}>{ui}</QueryClientProvider>)

describe('회원 화면 오류 경계', () => {
  it('브라우저가 오프라인이면 연결 끊김 화면을 보여 주고, 다시 시도하기로 화면을 다시 그린다', async () => {
    vi.spyOn(window.navigator, 'onLine', 'get').mockReturnValue(false)
    const retry = vi.fn()
    render(<ShopError error={new Error('fetch failed')} retry={retry} />)

    expect(screen.getByText('인터넷에 연결되어 있지 않아요')).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: '주요 메뉴' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '다시 시도하기' }))
    expect(retry).toHaveBeenCalledTimes(1)
  })

  it('연결은 되어 있으면 불러오지 못함 화면을 보여 준다', async () => {
    vi.spyOn(window.navigator, 'onLine', 'get').mockReturnValue(true)
    const retry = vi.fn()
    render(<ShopError error={new Error('500')} retry={retry} />)

    expect(screen.getByText('정보를 불러오지 못했어요')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '다시 시도하기' }))
    expect(retry).toHaveBeenCalledTimes(1)
  })
})
