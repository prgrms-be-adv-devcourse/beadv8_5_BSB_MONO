import { act, render, renderHook, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useLeaveGuard } from './use-leave-guard'

const push = vi.hoisted(() => vi.fn())
vi.mock('next/navigation', () => ({ useRouter: () => ({ push, replace: vi.fn(), back: vi.fn() }) }))

beforeEach(() => push.mockClear())

function Link({ href = '/seller/races' }: { href?: string }) {
  return <a href={href}>이동</a>
}

describe('useLeaveGuard 나가기 막기', () => {
  it('꺼져 있으면 화면 안 이동 · 새로 고침을 막지 않는다', async () => {
    renderHook(() => useLeaveGuard(false))
    render(<Link />)
    const event = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(event)
    expect(event.defaultPrevented).toBe(false)

    const click = vi.fn((e: Event) => e.preventDefault()) // jsdom이 실제로 이동하지 않게
    document.addEventListener('click', click)
    await userEvent.click(screen.getByRole('link'))
    expect(push).not.toHaveBeenCalled()
    document.removeEventListener('click', click)
  })

  it('켜져 있으면 안쪽 링크 이동을 막고 창을 띄운다. 계속 쓰기는 창만 닫는다', async () => {
    const { result } = renderHook(() => useLeaveGuard(true))
    render(<Link />)
    expect(result.current.leaving).toBe(false)

    await userEvent.click(screen.getByRole('link'))
    expect(result.current.leaving).toBe(true)
    expect(push).not.toHaveBeenCalled()

    act(() => result.current.stay())
    expect(result.current.leaving).toBe(false)
    expect(push).not.toHaveBeenCalled()
  })

  it('나가기는 막았던 주소로 이동한다', async () => {
    const { result } = renderHook(() => useLeaveGuard(true))
    render(<Link href="/seller/sales?tab=1" />)
    await userEvent.click(screen.getByRole('link'))
    act(() => result.current.leave())
    expect(push).toHaveBeenCalledWith('/seller/sales?tab=1')
  })

  it('다른 사이트 링크 · 새 탭 링크 · 같은 주소 링크는 막지 않는다', async () => {
    const { result } = renderHook(() => useLeaveGuard(true))
    const stop = (e: Event) => e.preventDefault()
    document.addEventListener('click', stop)
    render(
      <>
        <a href="https://example.com/">밖</a>
        <a href="/seller/races" target="_blank">
          새 탭
        </a>
        <a href={window.location.pathname}>같은 주소</a>
      </>,
    )
    for (const link of screen.getAllByRole('link')) await userEvent.click(link)
    expect(result.current.leaving).toBe(false)
    document.removeEventListener('click', stop)
  })

  it('새로 고침 · 탭 닫기는 브라우저 기본 경고를 띄운다', () => {
    renderHook(() => useLeaveGuard(true))
    const event = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(event)
    expect(event.defaultPrevented).toBe(true)
  })

  it('뒤로 가기를 누르면 창을 띄운다', () => {
    const { result } = renderHook(() => useLeaveGuard(true))
    act(() => {
      window.dispatchEvent(new PopStateEvent('popstate'))
    })
    expect(result.current.leaving).toBe(true)
  })

  it('끄면 더 이상 막지 않는다 (등록이 끝나 화면을 옮길 때)', () => {
    const { rerender } = renderHook(({ on }) => useLeaveGuard(on), { initialProps: { on: true } })
    rerender({ on: false })
    const event = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(event)
    expect(event.defaultPrevented).toBe(false)
  })
})
