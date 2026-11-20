import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { QueryClient, QueryClientProvider, useQuery } from '@tanstack/react-query'
import { renderHook, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import type { ReactNode } from 'react'
import { describe, expect, it } from 'vitest'

import { productApi } from '@/shared/api'
import { server } from '@/shared/testing'

import { raceQueryOptions } from './race-api'

function setup() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  )
  return { client, wrapper }
}

function sourceFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name)
    if (statSync(path).isDirectory()) return sourceFiles(path)
    return /\.tsx?$/.test(name) && !/\.test\./.test(name) ? [path] : []
  })
}

describe('대회 상세 쿼리 키', () => {
  it('키에는 응답 전체가 아니라 data만 저장된다', async () => {
    const { client, wrapper } = setup()
    const { result } = renderHook(() => useQuery(raceQueryOptions(1)), { wrapper })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    const cached = client.getQueryData<Record<string, unknown>>(productApi.getFindRaceQueryKey(1))
    expect(cached).toHaveProperty('raceId', 1)
    expect(cached).not.toHaveProperty('message')
  })

  it('먼저 저장된 값을 다른 화면에서 같은 방식으로 읽어도 대회 정보가 나온다', async () => {
    const { wrapper } = setup()
    const first = renderHook(() => useQuery(raceQueryOptions(1)), { wrapper })
    await waitFor(() => expect(first.result.current.isSuccess).toBe(true))

    const second = renderHook(() => useQuery({ ...raceQueryOptions(1), enabled: true }), {
      wrapper,
    })
    expect(second.result.current.data?.title).toBe('2026 용감한 쿠키RUN in 서울')
  })

  it('없는 대회(404)는 오류로 남는다', async () => {
    server.use(
      http.get('*/api/v1/product/races/:raceId', () =>
        HttpResponse.json({ status: 404, message: '없음', data: null }, { status: 404 }),
      ),
    )
    const { wrapper } = setup()
    const { result } = renderHook(() => useQuery(raceQueryOptions(999)), { wrapper })
    await waitFor(() => expect(result.current.isError).toBe(true))
  })

  it('화면은 생성된 useFindRace 대신 raceQueryOptions로 읽는다 (같은 키에 응답 전체가 들어가지 않게)', () => {
    const offenders = sourceFiles(join(process.cwd(), 'src/views')).filter((file) =>
      readFileSync(file, 'utf8').includes('useFindRace'),
    )
    expect(offenders).toEqual([])
  })
})
