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

function renderPage(state?: 'empty') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <RaceListPage state={state} />
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

  it('첫 카드에 사진이 없으면 사진이 있는 첫 카드 썸네일을 먼저 불러온다', async () => {
    const races = [1, 2, 3].map((raceId) => ({
      ...base,
      raceId,
      title: `${raceId}번 대회`,
      imageUrl: raceId === 1 ? undefined : `/mock/races/${raceId}.webp`,
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
    expect(items[0].querySelector('img')).toBeNull()
    expect(items[1].querySelector('img')).toHaveAttribute('fetchpriority', 'high')
    expect(items[2].querySelector('img')).toHaveAttribute('loading', 'lazy')
  })

  it('검색어를 제출하면 최근 검색어에 남고, 하나씩 또는 전체 삭제할 수 있다', async () => {
    localStorage.clear()
    const user = userEvent.setup()
    renderPage()
    const input = screen.getByRole('textbox', { name: '대회 찾기' })

    await user.type(input, '서울 하프{Enter}')
    await user.clear(input)
    await user.type(input, 'JTBC{Enter}')
    await user.click(input)
    await user.clear(input)

    expect(await screen.findByRole('heading', { name: '최근 검색어' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '서울 하프' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '서울 하프 삭제' }))
    expect(screen.queryByRole('button', { name: '서울 하프' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '전체 삭제' }))
    expect(screen.queryByRole('heading', { name: '최근 검색어' })).not.toBeInTheDocument()
  })

  it('카테고리에서 거리를 고르면 그 종목으로 거른 목록을 불러온다', async () => {
    const types: (string | null)[] = []
    server.use(
      http.get('*/api/v1/product/races', ({ request }) => {
        types.push(new URL(request.url).searchParams.get('courseType'))
        return HttpResponse.json({
          status: 200,
          message: '대회 목록을 조회했습니다.',
          data: { races: [soon], page: 0, size: 20, totalElements: 1, totalPages: 1 },
        })
      }),
    )
    const user = userEvent.setup()
    renderPage()

    await user.click(screen.getByRole('button', { name: '종목' }))
    await user.click(
      within(screen.getByRole('list', { name: '거리' })).getByRole('button', { name: /5K/ }),
    )

    await vi.waitFor(() => expect(types.at(-1)).toBe('KM_5'))
    expect(await screen.findByRole('button', { name: '종목', pressed: true })).toBeInTheDocument()
  })

  // 검색은 regionSido.keyword로 글자가 똑같아야 걸린다. 화면 글자는 짧아도 보내는 값은 주소 검색이 저장하는 시·도 이름이다.
  it.each([
    ['서울', '서울', '서울특별시'],
    ['경기 · 인천', '경기', '경기도'],
    ['경기 · 인천', '인천', '인천광역시'],
    ['충청', '충북', '충청북도'],
    ['충청', '대전', '대전광역시'],
    ['충청', '세종', '세종특별자치시'],
    ['전라', '광주', '광주광역시'],
    ['경상', '부산', '부산광역시'],
    ['경상', '대구', '대구광역시'],
    ['경상', '울산', '울산광역시'],
  ])('지역 %s의 %s 전체 대회는 저장된 시·도 이름(%s)으로 요청한다', async (group, label, sido) => {
    const regions: (string | null)[] = []
    server.use(
      http.get('*/api/v1/product/races', ({ request }) => {
        regions.push(new URL(request.url).searchParams.get('regionSido'))
        return HttpResponse.json({
          status: 200,
          message: '대회 목록을 조회했습니다.',
          data: { races: [soon], page: 0, size: 20, totalElements: 1, totalPages: 1 },
        })
      }),
    )
    const user = userEvent.setup()
    renderPage()

    await user.click(screen.getByRole('button', { name: '지역' }))
    await user.click(screen.getByRole('button', { name: group }))
    await user.click(screen.getByRole('button', { name: `${label} 전체 대회` }))

    await vi.waitFor(() => expect(regions.at(-1)).toBe(sido))
  })

  it('결과가 없으면 필터 초기화로 검색어를 지운다', async () => {
    const user = userEvent.setup()
    renderPage('empty')

    expect(screen.getByRole('textbox', { name: '대회 찾기' })).toHaveValue('제주 트레일')
    await user.click(await screen.findByRole('button', { name: '필터 초기화' }))
    expect(screen.getByRole('textbox', { name: '대회 찾기' })).toHaveValue('')
  })
})
