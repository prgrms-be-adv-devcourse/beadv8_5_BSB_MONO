import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderHook, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { afterEach, describe, expect, it } from 'vitest'

import { useMyBalance } from '@/entities/wallet'
import { clearAccessToken, setAccessToken } from '@/shared/api'

import { useDepositBalance } from './use-deposit-balance'

// 예치금 화면(useMyBalance)과 결제 화면(useDepositBalance)은 같은 잔액 캐시를 쓴다.
// 한쪽이 캐시에 다른 모양을 넣으면 반대쪽이 `₩[object Object]`나 오류가 되므로, 두 순서를 모두 확인한다.
function setup() {
  setAccessToken('test-token')
  // 앱의 기본값처럼 받아 둔 잔액을 한동안 새로 받지 않는다. 그래서 캐시의 모양이 맞아야 한다.
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: 60_000 } },
  })
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  )
  return wrapper
}

afterEach(() => clearAccessToken())

describe('잔액 캐시 공유', () => {
  it('결제 화면을 거쳐 예치금 화면으로 가도 잔액이 숫자로 나온다', async () => {
    const wrapper = setup()
    const checkout = renderHook(() => useDepositBalance(), { wrapper })
    await waitFor(() => expect(checkout.result.current.balance).toEqual(expect.any(Number)))
    checkout.unmount()

    const deposit = renderHook(() => useMyBalance(), { wrapper })
    await waitFor(() => expect(deposit.result.current.balance).toEqual(expect.any(Number)))
    expect(deposit.result.current.isError).toBe(false)
  })

  it('예치금 화면을 거쳐 결제 화면으로 가도 잔액이 오류 없이 나온다', async () => {
    const wrapper = setup()
    const deposit = renderHook(() => useMyBalance(), { wrapper })
    await waitFor(() => expect(deposit.result.current.balance).toEqual(expect.any(Number)))
    deposit.unmount()

    const checkout = renderHook(() => useDepositBalance(), { wrapper })
    await waitFor(() => expect(checkout.result.current.balance).toEqual(expect.any(Number)))
    expect(checkout.result.current.isError).toBe(false)
  })
})
