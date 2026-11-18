import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { RaceListSkeleton } from './RaceListSkeleton'

vi.mock('next/navigation', async () => (await import('@/shared/testing')).navigationMock('/races'))

describe('RaceListSkeleton', () => {
  it('QueryClientProvider 없이도 그려진다 (대회 목록 쿼리를 부르지 않는다)', () => {
    render(<RaceListSkeleton />)

    expect(screen.getByRole('heading', { name: '대회 찾기' })).toBeInTheDocument()
    expect(screen.getByRole('status', { name: '불러오는 중' })).toBeInTheDocument()
  })
})
