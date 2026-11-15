import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import type { ReactNode } from 'react'
import { describe, expect, it } from 'vitest'

import { server } from '@/shared/testing'

import { useApplyParty, useLeaveParty, usePartyDetail } from './party-api'

function setup() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: 60_000 } },
  })
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  )
  return wrapper
}

// 파티 신청 · 나가기가 성공하면 이미 받아 둔 파티 캐시를 다시 불러와야 화면이 새 상태로 바뀐다.
describe('파티 mutation 성공 뒤 캐시', () => {
  it.each([
    ['신청', useApplyParty],
    ['나가기', useLeaveParty],
  ])('%s에 성공하면 파티 상세를 다시 불러온다', async (_name, useMutationHook) => {
    let detailCalls = 0
    server.use(
      http.get('*/api/v1/order/parties/5', () => {
        detailCalls += 1
        return HttpResponse.json({ status: 200, message: '성공', data: { id: 5 } })
      }),
    )
    const wrapper = setup()
    const detail = renderHook(() => usePartyDetail(5), { wrapper })
    await waitFor(() => expect(detail.result.current.data).toBeTruthy())
    expect(detailCalls).toBe(1)

    const mutation = renderHook(() => useMutationHook(), { wrapper })
    await act(async () => {
      await mutation.result.current.mutateAsync(5)
    })
    await waitFor(() => expect(detailCalls).toBe(2))
  })
})
