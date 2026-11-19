import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { ToastProvider } from '@/shared/ui'

import Loading from './loading'

vi.mock('next/navigation', async () => (await import('@/shared/testing')).navigationMock('/races'))

describe('대회 찾기 로딩 화면', () => {
  // 로딩 화면이 목록 요청을 먼저 만들면, 서버가 미리 받은 첫 페이지가 서버 렌더에 쓰이지 않아
  // 서버는 회색 틀, 브라우저는 목록을 그리는 불일치(hydration mismatch)가 난다
  it('대회 목록 요청을 만들지 않는다', () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={client}>
        <ToastProvider>
          <Loading />
        </ToastProvider>
      </QueryClientProvider>,
    )
    expect(client.getQueryCache().findAll({ queryKey: ['/api/v1/product/races'] })).toHaveLength(0)
  })
})
