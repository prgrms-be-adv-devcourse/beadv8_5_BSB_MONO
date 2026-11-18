import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { RaceListSkeleton } from './RaceListSkeleton'

vi.mock('next/navigation', async () => (await import('@/shared/testing')).navigationMock('/races'))

describe('RaceListSkeleton', () => {
  it('대회 목록 쿼리를 캐시에 만들지 않는다 (진짜 페이지의 데이터 주입을 미루지 않게)', () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={client}>
        <RaceListSkeleton />
      </QueryClientProvider>,
    )

    expect(screen.getByRole('heading', { name: '대회 찾기' })).toBeInTheDocument()
    expect(screen.getByRole('status', { name: '불러오는 중' })).toBeInTheDocument()
    const raceList = client
      .getQueryCache()
      .getAll()
      .filter((q) => q.queryKey[0] === '/api/v1/product/races' && q.queryKey.at(-1) === 'infinite')
    expect(raceList).toHaveLength(0)
  })
})
