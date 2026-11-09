import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderHook } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import type { ReactNode } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { clearAccessToken, setAccessToken } from '@/shared/api'
import { server } from '@/shared/testing'

import { useNotifications } from './use-notifications'

afterEach(() => {
  clearAccessToken()
  vi.useRealTimers()
})

describe('useNotifications', () => {
  it('30초마다 알림을 다시 불러온다', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    let gets = 0
    server.use(
      http.get('*/api/v1/notification/notifications', () => {
        gets += 1
        return HttpResponse.json({ status: 200, message: '알림을 조회했습니다.', data: [] })
      }),
    )
    setAccessToken('test-token')
    const client = new QueryClient()
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    )
    renderHook(() => useNotifications(), { wrapper })

    await vi.waitFor(() => expect(gets).toBe(1))
    await vi.advanceTimersByTimeAsync(29_000)
    expect(gets).toBe(1)
    await vi.advanceTimersByTimeAsync(1_500)
    await vi.waitFor(() => expect(gets).toBe(2))
  })
})
