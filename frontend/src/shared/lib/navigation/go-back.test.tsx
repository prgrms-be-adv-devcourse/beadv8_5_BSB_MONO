import { render, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { resetInAppHistory, useGoBack } from './go-back'
import { InAppHistoryTracker } from './InAppHistoryTracker'

const router = { back: vi.fn(), replace: vi.fn(), push: vi.fn() }
vi.mock('next/navigation', () => ({ useRouter: () => router }))

const goBack = (fallback: string) => renderHook(() => useGoBack()).result.current(fallback)

beforeEach(() => {
  vi.clearAllMocks()
  window.history.replaceState(null, '', '/a')
  resetInAppHistory()
  render(<InAppHistoryTracker />)
})

describe('useGoBack (Navigation API가 없는 브라우저)', () => {
  it('처음 연 화면이면 fallback으로 replace한다', () => {
    goBack('/home')
    expect(router.replace).toHaveBeenCalledWith('/home')
    expect(router.back).not.toHaveBeenCalled()
  })

  it('앱 안에서 push로 옮겨 왔으면 router.back()을 쓴다', () => {
    window.history.pushState(null, '', '/b')
    goBack('/home')
    expect(router.back).toHaveBeenCalled()
    expect(router.replace).not.toHaveBeenCalled()
  })

  it('처음 연 화면을 replace로 옮긴 것은 앱 안 기록으로 세지 않는다', () => {
    window.history.replaceState(null, '', '/b')
    goBack('/home')
    expect(router.replace).toHaveBeenCalledWith('/home')
  })

  it('push했다가 처음 화면으로 돌아왔으면 fallback으로 replace한다', () => {
    window.history.pushState(null, '', '/b')
    window.history.replaceState(null, '', '/a')
    goBack('/home')
    expect(router.replace).toHaveBeenCalledWith('/home')
  })
})

describe('useGoBack (Navigation API가 있는 브라우저)', () => {
  const setNavigation = (canGoBack: boolean) =>
    Object.defineProperty(window, 'navigation', { value: { canGoBack }, configurable: true })

  afterEach(() => {
    Reflect.deleteProperty(window, 'navigation')
  })

  it('canGoBack이 false면 push 기록이 있어도 fallback으로 replace한다', () => {
    setNavigation(false)
    window.history.pushState(null, '', '/b')
    goBack('/home')
    expect(router.replace).toHaveBeenCalledWith('/home')
    expect(router.back).not.toHaveBeenCalled()
  })

  it('canGoBack이 true면 router.back()을 쓴다', () => {
    setNavigation(true)
    goBack('/home')
    expect(router.back).toHaveBeenCalled()
  })
})
