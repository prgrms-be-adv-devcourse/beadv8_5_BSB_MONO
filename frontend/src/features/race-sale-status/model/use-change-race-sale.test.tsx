import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import type { ReactNode } from 'react'
import { describe, expect, it } from 'vitest'

import { productApi } from '@/shared/api'
import { server } from '@/shared/testing'

import { useChangeRaceSale } from './use-change-race-sale'

function setup() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  client.setQueryData(productApi.getFindMineQueryKey(), [])
  client.setQueryData(productApi.getFindAdminRacesQueryKey(), [])
  client.setQueryData(productApi.getFindRaceQueryKey(1), null)
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  )
  return { client, wrapper }
}

const invalidated = (client: QueryClient, key: readonly unknown[]) =>
  client.getQueryState(key)?.isInvalidated

describe('판매 상태 바꾸기', () => {
  it('판매자가 바꾸면 판매자 주소로 보내고 내 대회 목록과 이 대회 상세를 새로 받는다', async () => {
    const { client, wrapper } = setup()
    let called = ''
    server.use(
      http.post('*/api/v1/product/races/:raceId/open', ({ request }) => {
        called = new URL(request.url).pathname
        return HttpResponse.json({ status: 200, message: 'ok', data: { raceId: 1 } })
      }),
    )
    const { result } = renderHook(() => useChangeRaceSale(1, 'seller'), { wrapper })
    await act(() => result.current.mutateAsync('open'))

    expect(called).toBe('/api/v1/product/races/1/open')
    await waitFor(() => expect(invalidated(client, productApi.getFindMineQueryKey())).toBe(true))
    expect(invalidated(client, productApi.getFindRaceQueryKey(1))).toBe(true)
    expect(invalidated(client, productApi.getFindAdminRacesQueryKey())).toBe(false)
  })

  it('관리자가 바꾸면 관리자 주소로 보내고 관리자 대회 목록을 새로 받는다', async () => {
    const { client, wrapper } = setup()
    const called: string[] = []
    server.use(
      http.post('*/api/v1/product/admin/races/:raceId/:action', ({ request }) => {
        called.push(new URL(request.url).pathname)
        return HttpResponse.json({ status: 200, message: 'ok', data: { raceId: 1 } })
      }),
    )
    const { result } = renderHook(() => useChangeRaceSale(1, 'admin'), { wrapper })
    await act(() => result.current.mutateAsync('stop'))
    await act(() => result.current.mutateAsync('resume'))

    expect(called).toEqual([
      '/api/v1/product/admin/races/1/stop',
      '/api/v1/product/admin/races/1/resume',
    ])
    await waitFor(() =>
      expect(invalidated(client, productApi.getFindAdminRacesQueryKey())).toBe(true),
    )
    expect(invalidated(client, productApi.getFindRaceQueryKey(1))).toBe(true)
  })

  it('서버가 거절하면 서버 문구가 담긴 오류로 끝나고 목록은 그대로 둔다', async () => {
    const { client, wrapper } = setup()
    server.use(
      http.post('*/api/v1/product/admin/races/:raceId/open', () =>
        HttpResponse.json(
          { status: 409, message: '이미 판매 중인 대회입니다.', data: null },
          { status: 409 },
        ),
      ),
    )
    const { result } = renderHook(() => useChangeRaceSale(1, 'admin'), { wrapper })
    await act(() => result.current.mutateAsync('open').catch(() => undefined))

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(result.current.error).toMatchObject({ serverMessage: '이미 판매 중인 대회입니다.' })
    expect(invalidated(client, productApi.getFindAdminRacesQueryKey())).toBe(false)
  })
})
