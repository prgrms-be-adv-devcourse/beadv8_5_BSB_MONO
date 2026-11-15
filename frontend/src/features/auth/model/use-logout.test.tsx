import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getAuthStatus, setAccessToken } from '@/shared/api'
import { addRecentSearch, loadRecentSearches } from '@/shared/lib'
import { server } from '@/shared/testing'

import { reloadToLogin } from '../lib/login-redirect'
import { useLogout } from './use-logout'

const replace = vi.fn()
vi.mock('next/navigation', () => ({ useRouter: () => ({ replace }) }))
vi.mock('../lib/login-redirect', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../lib/login-redirect')>()),
  reloadToLogin: vi.fn(),
}))

const BALANCE_KEY = ['/api/v1/cash/wallets/balance']

function renderLogout() {
  const client = new QueryClient()
  client.setQueryData(BALANCE_KEY, { data: { balance: 12000 } })
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  )
  return { client, ...renderHook(() => useLogout(), { wrapper }) }
}

beforeEach(() => {
  localStorage.clear()
  replace.mockClear()
  vi.mocked(reloadToLogin).mockClear()
  setAccessToken('token-a')
})

describe('useLogout', () => {
  it('로그아웃에 성공하면 화면을 로그아웃 상태로 다시 그리지 않고 문서를 새로 불러 로그인 화면으로 간다', async () => {
    const { result, client } = renderLogout()

    await act(async () => {
      result.current.mutate()
    })

    await waitFor(() => expect(reloadToLogin).toHaveBeenCalledTimes(1))
    // 토큰과 캐시를 그대로 두어야 지금 화면이 「로그인이 필요해요」나 빈 값으로 바뀌지 않는다.
    // 새로 불러오면 메모리의 토큰과 캐시는 사라진다.
    expect(getAuthStatus()).toBe('authenticated')
    expect(client.getQueryData(BALANCE_KEY)).toBeDefined()
    // 로그인 확인 장치가 붙이는 ?next= 없이 로그인 화면으로만 간다
    expect(replace).not.toHaveBeenCalled()
  })

  // 새로 불러와도 그 앞의 방문 기록은 이전 문서 것이다. 브라우저가 이전 문서를 메모리째 보관했다가
  // 뒤로 가기로 되살리면 앞사람의 토큰과 캐시가 보이므로, 페이지를 떠날 때 지운다.
  it('로그아웃에 성공한 뒤 페이지를 떠나면 토큰과 캐시를 지운다', async () => {
    const { result, client } = renderLogout()

    await act(async () => {
      result.current.mutate()
    })
    await waitFor(() => expect(reloadToLogin).toHaveBeenCalledTimes(1))
    window.dispatchEvent(new Event('pagehide'))

    expect(getAuthStatus()).toBe('anonymous')
    expect(client.getQueryData(BALANCE_KEY)).toBeUndefined()
  })

  it('로그아웃 요청이 실패하면 이 브라우저에서 로그아웃 상태로 바꾸고 캐시를 지운 뒤 로그인 화면으로 보낸다', async () => {
    server.use(http.post('*/api/v1/auth/logout', () => HttpResponse.error()))
    const { result, client } = renderLogout()

    await act(async () => {
      result.current.mutate()
    })

    await waitFor(() => expect(replace).toHaveBeenCalledWith('/login'))
    expect(getAuthStatus()).toBe('anonymous')
    expect(client.getQueryData(BALANCE_KEY)).toBeUndefined()
    // 서버 쿠키가 남아 있을 수 있어 새로 불러오지 않는다(새로 불러오면 재발급으로 다시 로그인된다)
    expect(reloadToLogin).not.toHaveBeenCalled()
  })

  // 최근 검색어는 이 브라우저 localStorage에 남는다. 지우지 않으면 같은 브라우저의 다음 사람에게 보인다.
  it('로그아웃에 성공한 뒤 페이지를 떠나면 최근 검색어도 지운다', async () => {
    addRecentSearch('서울 하프')
    const { result } = renderLogout()

    await act(async () => {
      result.current.mutate()
    })
    await waitFor(() => expect(reloadToLogin).toHaveBeenCalledTimes(1))
    expect(loadRecentSearches()).toEqual(['서울 하프'])
    window.dispatchEvent(new Event('pagehide'))

    expect(loadRecentSearches()).toEqual([])
  })

  it('로그아웃 요청이 실패해도 이 브라우저의 최근 검색어를 지운다', async () => {
    addRecentSearch('서울 하프')
    server.use(http.post('*/api/v1/auth/logout', () => HttpResponse.error()))
    const { result } = renderLogout()

    await act(async () => {
      result.current.mutate()
    })

    await waitFor(() => expect(replace).toHaveBeenCalledWith('/login'))
    expect(loadRecentSearches()).toEqual([])
  })
})
