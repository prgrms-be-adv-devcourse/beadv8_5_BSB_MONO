import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { currentSearch, productMock, resetNavigationMock, server } from '@/shared/testing'
import { ToastProvider } from '@/shared/ui'

import { RaceListPage } from './RaceListPage'

vi.mock('next/navigation', async () => (await import('@/shared/testing')).navigationMock('/races'))

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
  beforeEach(() => {
    resetNavigationMock()
  })

  it('정렬을 고르고 화면을 나갔다 돌아와도 정렬이 유지된다', async () => {
    const sorts: (string | null)[] = []
    server.use(
      http.get('*/api/v1/product/races', ({ request }) => {
        sorts.push(new URL(request.url).searchParams.get('sort'))
        return HttpResponse.json({
          status: 200,
          message: '대회 목록을 조회했습니다.',
          data: { races: [soon], page: 0, size: 20, totalElements: 1, totalPages: 1 },
        })
      }),
    )
    const user = userEvent.setup()
    const first = renderPage()
    await user.click(await screen.findByRole('combobox', { name: '대회 정렬' }))
    await user.click(await screen.findByRole('option', { name: '가격 낮은순' }))
    await vi.waitFor(() => expect(currentSearch()).toBe('sort=PRICE'))

    first.unmount()
    sorts.length = 0
    renderPage()
    expect(await screen.findByRole('combobox', { name: '대회 정렬' })).toHaveTextContent(
      '가격 낮은순',
    )
    await vi.waitFor(() => expect(sorts.at(-1)).toBe('PRICE'))
  })

  it('허용되지 않은 정렬 값이 주소에 있으면 기본 정렬을 쓴다', async () => {
    resetNavigationMock('sort=HACKED')
    renderPage()
    expect(await screen.findByRole('combobox', { name: '대회 정렬' })).toHaveTextContent(
      '마감 임박순',
    )
  })
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
    expect(screen.getByText('대회 2')).toBeInTheDocument()
  })

  it('개수 줄은 「대회 N」이고(목록에 오픈 예정 대회도 섞여 오므로 접수 중이라 쓰지 않는다), 목록 줄에 더보기 버튼은 없다 (v3)', async () => {
    renderPage()
    expect(await screen.findByText(/^대회 \d+$/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /더보기/ })).not.toBeInTheDocument()
  })

  it('큰 「대회 찾기」 제목은 화면에 보이지 않고 읽는 프로그램에만 남는다 (v3)', async () => {
    renderPage()
    expect(await screen.findByRole('heading', { level: 1, name: '대회 찾기' })).toHaveClass(
      'sr-only',
    )
  })

  it('접수 중 대회는 배지가 없고, 오픈 예정 대회만 사진 위 배지가 붙는다', async () => {
    server.use(
      http.get('*/api/v1/product/races', () =>
        HttpResponse.json({
          status: 200,
          message: '대회 목록을 조회했습니다.',
          data: {
            races: [
              {
                ...soon,
                individualOpenAt: '2000-01-01T09:00:00',
                applicationCloseAt: '2099-12-31T18:00:00',
              },
              {
                ...cheap,
                individualOpenAt: '2099-01-01T09:00:00',
                applicationCloseAt: '2099-12-31T18:00:00',
              },
            ],
            page: 0,
            size: 20,
            totalElements: 2,
            totalPages: 1,
          },
        }),
      ),
    )
    renderPage()
    const items = await screen.findAllByRole('listitem')
    expect(within(items[0]).queryByText('접수 중')).not.toBeInTheDocument()
    expect(within(items[1]).getByText('오픈 예정')).toHaveClass('bg-bg-brand-subtle')
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
    }))
    server.use(
      // 목록 응답엔 이미지가 없고, 대회 파일(file 서비스)에서 받는다. 1번 대회만 파일이 없다.
      http.get('*/api/v1/file/files', ({ request }) => {
        const id = new URL(request.url).searchParams.get('refId')
        const data =
          id === '1' ? [] : [{ id: 1, fileType: 'THUMBNAIL', url: `/mock/races/${id}.webp` }]
        return HttpResponse.json({ status: 200, message: 'ok', data })
      }),
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
    await user.click(
      within(screen.getByRole('list', { name: '지역' })).getByRole('button', { name: group }),
    )
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

  it('검색어 앞뒤 공백은 빼고 최근 검색어에 남긴다', async () => {
    localStorage.clear()
    const user = userEvent.setup()
    renderPage()
    const input = screen.getByRole('textbox', { name: '대회 찾기' })

    await user.type(input, '  제주 트레일  {Enter}')
    await user.click(input)
    await user.clear(input)

    expect(await screen.findByRole('button', { name: '제주 트레일' })).toBeInTheDocument()
  })

  describe('AI 추천 (검색어)', () => {
    const recommended = { ...base, raceId: 7, title: '한강 야간 달리기' }
    const useRecommendations = (respond: () => Response) => {
      const keywords: (string | null)[] = []
      server.use(
        // 검색 결과는 어떤 검색어에도 한 건을 준다 (추천과 상관없이 목록이 그대로인지 보려고)
        http.get('*/api/v1/product/races', () =>
          HttpResponse.json({
            status: 200,
            message: '대회 목록을 조회했습니다.',
            data: { races: [soon], page: 0, size: 20, totalElements: 1, totalPages: 1 },
          }),
        ),
        http.get('*/api/v1/product/races/recommendations', ({ request }) => {
          keywords.push(new URL(request.url).searchParams.get('keyword'))
          return respond()
        }),
      )
      return keywords
    }
    const ok = (races: unknown[]) => () =>
      HttpResponse.json({ status: 200, message: 'AI 추천 대회를 조회했습니다.', data: races })

    it('검색어를 보내면 검색 결과 위에 AI 추천 줄을 보여 준다', async () => {
      const keywords = useRecommendations(ok([recommended]))
      const user = userEvent.setup()
      renderPage()
      await user.type(screen.getByRole('textbox', { name: '대회 찾기' }), '밤에 강변 달리기{Enter}')

      const section = await screen.findByRole('region', { name: 'AI 추천' })
      expect(within(section).getByRole('link', { name: /한강 야간 달리기/ })).toHaveAttribute(
        'href',
        '/races/7',
      )
      expect(keywords.at(-1)).toBe('밤에 강변 달리기')
      expect(await screen.findByText(/^대회 \d+$/)).toBeInTheDocument()
    })

    it('추천이 비어 있으면 영역을 숨기고 검색 결과는 그대로 둔다', async () => {
      const keywords = useRecommendations(ok([]))
      const user = userEvent.setup()
      renderPage()
      await user.type(screen.getByRole('textbox', { name: '대회 찾기' }), '밤에 강변 달리기{Enter}')

      await vi.waitFor(() => expect(keywords).toHaveLength(1))
      expect(await screen.findByText(/^대회 \d+$/)).toBeInTheDocument()
      expect(screen.queryByRole('region', { name: 'AI 추천' })).not.toBeInTheDocument()
    })

    it('추천 API가 실패하면 영역을 숨기고 검색 결과는 그대로 둔다', async () => {
      const keywords = useRecommendations(() => HttpResponse.error())
      const user = userEvent.setup()
      renderPage()
      await user.type(screen.getByRole('textbox', { name: '대회 찾기' }), '밤에 강변 달리기{Enter}')

      await vi.waitFor(() => expect(keywords).toHaveLength(1))
      expect(await screen.findByText(/^대회 \d+$/)).toBeInTheDocument()
      expect(screen.queryByRole('region', { name: 'AI 추천' })).not.toBeInTheDocument()
    })

    it('검색어가 없으면 추천 API를 부르지 않는다', async () => {
      const keywords = useRecommendations(ok([recommended]))
      renderPage()

      expect(await screen.findByText(/^대회 \d+$/)).toBeInTheDocument()
      expect(keywords).toHaveLength(0)
      expect(screen.queryByRole('region', { name: 'AI 추천' })).not.toBeInTheDocument()
    })

    it('검색어가 2글자보다 짧으면 부르지 않는다', async () => {
      const keywords = useRecommendations(ok([recommended]))
      const user = userEvent.setup()
      renderPage()
      await user.type(screen.getByRole('textbox', { name: '대회 찾기' }), '한{Enter}')

      expect(await screen.findByText(/^대회 \d+$/)).toBeInTheDocument()
      expect(keywords).toHaveLength(0)
    })
  })
})
