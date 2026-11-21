import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import type { ReactNode } from 'react'
import { describe, expect, it } from 'vitest'

import { raceCoverQueryOptions, raceDetailQueryOptions, raceQueryOptions } from '@/entities/race'
import { productApi, type productModel } from '@/shared/api'
import { server } from '@/shared/testing'

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

// 이미지 연결은 메시지로 file 서비스에 넘어가 저장 응답보다 늦게 반영된다
describe('대회 이미지를 바꾼 저장', () => {
  const withThumbnail = (fileId: number) =>
    ({
      images: [{ fileId, fileType: 'THUMBNAIL', sortNo: 0 }],
      courses: [],
    }) as unknown as productModel.RaceSaveRequestDto

  function mockRaceFiles(activeIds: () => number[]) {
    let calls = 0
    server.use(
      http.get('*/api/v1/file/files', ({ request }) => {
        if (new URL(request.url).searchParams.get('refType') !== 'Race') {
          return HttpResponse.json({ status: 200, message: 'ok', data: [] })
        }
        calls += 1
        const data = activeIds().map((id) => ({ id, fileType: 'THUMBNAIL', status: 'ACTIVE' }))
        return HttpResponse.json({ status: 200, message: 'ok', data })
      }),
    )
    return () => calls
  }

  it('새 대표 이미지가 연결될 때까지 기다린 뒤 대회 정보를 새로 받는다', async () => {
    const { client, wrapper } = setup()
    let attempt = 0
    // 처음 한 번은 아직 옛 이미지(1)만 보이고, 다음부터 새 이미지(2)가 보인다
    const calls = mockRaceFiles(() => (++attempt < 2 ? [1] : [2]))
    const { result } = renderHook(() => useUpdateRace(1), { wrapper })
    await act(() => result.current.mutateAsync(withThumbnail(2)))
    expect(calls()).toBe(2)
    expect(client.getQueryState(raceDetailQueryOptions('1').queryKey)?.isInvalidated).toBe(true)
  })

  it('이미지가 그대로인 저장은 기다리지 않는다', async () => {
    const { wrapper } = setup()
    const calls = mockRaceFiles(() => [2])
    const { result } = renderHook(() => useUpdateRace(1), { wrapper })
    await act(() => result.current.mutateAsync(withThumbnail(2)))
    expect(calls()).toBe(1)
  })

  it('저장하면 대표 이미지 캐시도 새로 받는다', async () => {
    const { client, wrapper } = setup()
    mockRaceFiles(() => [2])
    const cover = raceCoverQueryOptions(1).queryKey
    client.setQueryData(cover, 'https://old.example/1.png')
    const { result } = renderHook(() => useUpdateRace(1), { wrapper })
    await act(() => result.current.mutateAsync(withThumbnail(2)))
    expect(client.getQueryState(cover)?.isInvalidated).toBe(true)
  })

  it('이미지를 빼기만 한 저장도 뺀 이미지가 떨어질 때까지 기다린다', async () => {
    const { wrapper } = setup()
    let attempt = 0
    // 처음 한 번은 지운 이미지(3)가 아직 붙어 있다
    const calls = mockRaceFiles(() => (++attempt < 2 ? [2, 3] : [2]))
    const { result } = renderHook(() => useUpdateRace(1), { wrapper })
    await act(() => result.current.mutateAsync(withThumbnail(2)))
    expect(calls()).toBe(2)
  })

  it('이번에 더한 종목의 이미지는 저장 응답의 종목 ID로 확인한다', async () => {
    const { wrapper } = setup()
    const asked: string[] = []
    server.use(
      http.get('*/api/v1/file/files', ({ request }) => {
        const url = new URL(request.url)
        asked.push(`${url.searchParams.get('refType')}:${url.searchParams.get('refId')}`)
        return HttpResponse.json({
          status: 200,
          message: 'ok',
          data: [{ id: 7, status: 'ACTIVE' }],
        })
      }),
      http.put('*/api/v1/product/races/:raceId', () =>
        HttpResponse.json({ status: 200, message: 'ok', data: { raceId: 1, courseIds: [55] } }),
      ),
    )
    const request = {
      courses: [{ images: [{ fileId: 7, fileType: 'DETAIL', sortNo: 0 }] }],
    } as unknown as productModel.RaceSaveRequestDto
    const { result } = renderHook(() => useUpdateRace(1), { wrapper })
    await act(() => result.current.mutateAsync(request))
    expect(asked).toEqual(['RaceCourse:55'])
  })
})
