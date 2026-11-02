import { renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { useQueueTracking } from './use-queue-tracking'

const trackEvent = vi.hoisted(() => vi.fn())
vi.mock('@/shared/lib', () => ({ trackEvent }))

beforeEach(() => vi.useFakeTimers())
afterEach(() => {
  vi.useRealTimers()
  trackEvent.mockClear()
})

describe('useQueueTracking', () => {
  it('들어오면 queue_enter, 차례가 오면 기다린 초와 함께 queue_pass를 보내고 떠나도 queue_leave는 보내지 않는다', () => {
    const { rerender, unmount } = renderHook(
      ({ admitted }) => useQueueTracking('7', admitted, true),
      { initialProps: { admitted: false } },
    )
    expect(trackEvent).toHaveBeenLastCalledWith('queue_enter', { raceId: '7' })

    vi.advanceTimersByTime(42_000)
    rerender({ admitted: true })
    unmount()

    expect(trackEvent.mock.calls).toEqual([
      ['queue_enter', { raceId: '7' }],
      ['queue_pass', { raceId: '7', waitSeconds: 42 }],
    ])
  })

  it('차례가 오기 전에 떠나면 queue_leave를 한 번만 보낸다', () => {
    const { unmount } = renderHook(() => useQueueTracking('7', false, true))
    vi.advanceTimersByTime(10_000)
    window.dispatchEvent(new Event('pagehide'))
    unmount()

    expect(trackEvent.mock.calls).toEqual([
      ['queue_enter', { raceId: '7' }],
      ['queue_leave', { raceId: '7', waitSeconds: 10 }],
    ])
  })

  it('시안 미리보기는 보내지 않는다', () => {
    const { unmount } = renderHook(() => useQueueTracking('7', true, false))
    unmount()
    expect(trackEvent).not.toHaveBeenCalled()
  })
})
