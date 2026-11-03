import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'

import { productMock, server } from '@/shared/testing'
import { ToastProvider } from '@/shared/ui'

import { RaceListPage } from './RaceListPage'

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), replace: vi.fn() }),
  usePathname: () => '/races',
}))

const base = productMock.getSearchResponseMock().data!.races![0]
const cheap = { ...base, raceId: 2, title: '싼 대회', minPrice: 10000 }
const soon = { ...base, raceId: 1, title: '곧 마감 대회', minPrice: 50000 }

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <RaceListPage />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

describe('RaceListPage', () => {
  it('정렬을 바꾸면 요청의 sort가 바뀌고 목록 순서가 바뀐다', async () => {
    const sorts: (string | null)[] = []
    server.use(
      http.get('*/api/v1/product/races', ({ request }) => {
        const sort = new URL(request.url).searchParams.get('sort')
        sorts.push(sort)
        const races = sort === 'PRICE' ? [cheap, soon] : [soon, cheap]
        return HttpResponse.json({
          status: 200,
          message: '대회 목록을 조회했습니다.',
          data: { races, page: 0, size: 20, totalElements: 2, totalPages: 1 },
        })
      }),
    )
    const user = userEvent.setup()
    renderPage()

    const titles = async () =>
      (await screen.findAllByRole('listitem')).map(
        (li) => within(li).queryByText(/대회$/)?.textContent,
      )
    expect(await titles()).toEqual(['곧 마감 대회', '싼 대회'])
    expect(sorts.at(-1)).toBe('DEADLINE')

    await user.click(screen.getByRole('combobox', { name: '대회 정렬' }))
    await user.click(await screen.findByRole('option', { name: '가격 낮은순' }))

    await vi.waitFor(() => expect(sorts.at(-1)).toBe('PRICE'))
    await vi.waitFor(async () => expect(await titles()).toEqual(['싼 대회', '곧 마감 대회']))
    expect(screen.getByText('대회 2개')).toBeInTheDocument()
  })

  it('검색어를 입력하면 연관검색어를 보여 준다', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.type(screen.getByRole('textbox', { name: '대회 찾기' }), '서울')

    const list = await screen.findByRole('list', { name: '연관검색어' })
    expect(
      within(list).getByRole('link', { name: /2026 용감한 쿠키RUN in 서울 10K/ }),
    ).toHaveAttribute('href', '/races/1?option=1')
  })

  it('첫 카드 썸네일만 먼저 불러오고 나머지는 늦게 불러온다', async () => {
    const races = [1, 2, 3].map((raceId) => ({
      ...base,
      raceId,
      title: `${raceId}번 대회`,
      imageUrl: `/mock/races/${raceId}.webp`,
    }))
    server.use(
      http.get('*/api/v1/product/races', () =>
        HttpResponse.json({
          status: 200,
          message: '대회 목록을 조회했습니다.',
          data: { races, page: 0, size: 20, totalElements: 3, totalPages: 1 },
        }),
      ),
    )
    renderPage()

    const items = await screen.findAllByRole('listitem')
    const thumbs = items.map((li) => li.querySelector('img'))
    expect(thumbs).toHaveLength(3)
    // 목록 화면에서 가장 큰 요소(LCP)가 첫 카드 썸네일이다
    expect(thumbs[0]).toHaveAttribute('loading', 'eager')
    expect(thumbs[0]).toHaveAttribute('fetchpriority', 'high')
    for (const thumb of thumbs.slice(1)) {
      expect(thumb).toHaveAttribute('loading', 'lazy')
      expect(thumb).not.toHaveAttribute('fetchpriority')
    }
  })
})
