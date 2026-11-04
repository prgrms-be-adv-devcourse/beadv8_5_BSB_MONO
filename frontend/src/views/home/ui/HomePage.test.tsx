import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { RACE_BANNERS } from '@/entities/race'

import { HomePage } from './HomePage'

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), replace: vi.fn() }),
  usePathname: () => '/',
}))

describe('HomePage', () => {
  it('배너 첫 장만 먼저 불러오고 나머지는 늦게 불러온다', () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={client}>
        <HomePage closingSoon={[]} openRaces={[]} />
      </QueryClientProvider>,
    )
    const banner = screen.getByRole('region', { name: '접수 중인 대회' })
    // 제목이 없는 배너는 alt가 비어 그림 역할로 잡히지 않아 태그로 찾는다
    const images = [...banner.querySelectorAll('img')]
    expect(images).toHaveLength(RACE_BANNERS.length)
    expect(images.length).toBeGreaterThan(1)

    const [first, ...rest] = images
    // 첫 화면에서 가장 큰 요소(LCP)라 지연 로딩이면 늦게 그려진다
    expect(first).toHaveAttribute('loading', 'eager')
    expect(first).toHaveAttribute('fetchpriority', 'high')
    for (const image of rest) {
      expect(image).toHaveAttribute('loading', 'lazy')
      expect(image).not.toHaveAttribute('fetchpriority')
    }
  })
})
