import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { describe, expect, it } from 'vitest'

import { raceDetailQueryOptions, raceQueryOptions } from '@/entities/race'
import { productApi, type productModel } from '@/shared/api'

import { useCancelRace } from './use-cancel-race'
import { useUpdateRace } from './use-update-race'

function setup() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  // 장바구니 · 결제 화면이 들고 있는 대회 상세와 내 참가권이 쓰는 원본 상세
  client.setQueryData(raceDetailQueryOptions('1').queryKey, null)
  client.setQueryData(raceQueryOptions(1).queryKey, {} as productModel.RaceDetailDto)
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  )
  return { client, wrapper }
}

describe('대회 상세 캐시', () => {
  it('화면용 상세는 상품 API 대회 키 아래에 있고, 원본 상세와는 다른 키다', () => {
    const detailKey = raceDetailQueryOptions('1').queryKey
    expect(detailKey.slice(0, 1)).toEqual(productApi.getFindRaceQueryKey(1))
    // 원본(API 응답)과 화면용(이미지를 합친 값)은 모양이 달라 같은 키를 쓰면 안 된다
    expect(detailKey).not.toEqual(raceQueryOptions(1).queryKey)
  })

  it('대회를 고치면 장바구니 · 결제 화면의 대회 상세도 새로 받는다', async () => {
    const { client, wrapper } = setup()
    const { result } = renderHook(() => useUpdateRace(1), { wrapper })
    await act(() => result.current.mutateAsync({} as productModel.RaceSaveRequestDto))
    await waitFor(() =>
      expect(client.getQueryState(raceDetailQueryOptions('1').queryKey)?.isInvalidated).toBe(true),
    )
    expect(client.getQueryState(raceQueryOptions(1).queryKey)?.isInvalidated).toBe(true)
  })

  it('대회를 취소하면 장바구니 · 결제 화면의 대회 상세도 새로 받는다', async () => {
    const { client, wrapper } = setup()
    const { result } = renderHook(() => useCancelRace(1), { wrapper })
    await act(() => result.current.mutateAsync())
    await waitFor(() =>
      expect(client.getQueryState(raceDetailQueryOptions('1').queryKey)?.isInvalidated).toBe(true),
    )
    expect(client.getQueryState(raceQueryOptions(1).queryKey)?.isInvalidated).toBe(true)
  })

  it('다른 대회를 고치면 이 대회 상세는 그대로 둔다', async () => {
    const { client, wrapper } = setup()
    const { result } = renderHook(() => useUpdateRace(2), { wrapper })
    await act(() => result.current.mutateAsync({} as productModel.RaceSaveRequestDto))
    expect(client.getQueryState(raceDetailQueryOptions('1').queryKey)?.isInvalidated).toBe(false)
  })
})
