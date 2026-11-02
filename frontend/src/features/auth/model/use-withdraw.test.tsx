import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getAuthStatus, setAccessToken } from '@/shared/api'

import { reloadTo } from '../lib/login-redirect'
import { useWithdraw } from './use-withdraw'

vi.mock('../lib/login-redirect', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../lib/login-redirect')>()),
  reloadTo: vi.fn(),
}))

const BALANCE_KEY = ['/api/v1/cash/wallets/balance']

function renderWithdraw() {
  const client = new QueryClient()
  client.setQueryData(BALANCE_KEY, { data: { balance: 12000 } })
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  )
  return { client, ...renderHook(() => useWithdraw(), { wrapper }) }
}

beforeEach(() => {
  vi.mocked(reloadTo).mockClear()
  setAccessToken('token-a')
})

describe('useWithdraw', () => {
  it('탈퇴에 성공하면 토큰을 그대로 둔 채 문서를 새로 불러 탈퇴 완료 화면으로 간다', async () => {
    const { result, client } = renderWithdraw()

    await act(async () => {
      result.current.mutate()
    })

    await waitFor(() => expect(reloadTo).toHaveBeenCalledWith('/me/withdraw/done'))
    // 여기서 토큰을 비우면 로그인 확인 장치가 탈퇴 화면을 내리고 로그인 화면으로 보낸다
    expect(getAuthStatus()).toBe('authenticated')
    expect(client.getQueryData(BALANCE_KEY)).toBeDefined()
  })

  it('탈퇴에 성공한 뒤 페이지를 떠나면 토큰과 캐시를 지운다', async () => {
    const { result, client } = renderWithdraw()

    await act(async () => {
      result.current.mutate()
    })
    await waitFor(() => expect(reloadTo).toHaveBeenCalledTimes(1))
    window.dispatchEvent(new Event('pagehide'))

    expect(getAuthStatus()).toBe('anonymous')
    expect(client.getQueryData(BALANCE_KEY)).toBeUndefined()
  })
})
