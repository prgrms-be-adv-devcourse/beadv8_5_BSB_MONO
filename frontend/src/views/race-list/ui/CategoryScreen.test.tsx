import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { productMock, server } from '@/shared/testing'

import { CategoryScreen } from './CategoryScreen'

const base = productMock.getSearchResponseMock().data!.races![0]
const cookie = { ...base, raceId: 11, title: '2026 용감한 쿠키RUN in 서울', raceDate: '2026-11-01' }
const jtbc = { ...base, raceId: 12, title: '2026 JTBC 서울마라톤', raceDate: '2026-11-02' }
const busan = { ...base, raceId: 21, title: '부산 바다마라톤', raceDate: '2026-11-08' }

const asked: (string | null)[] = []

beforeEach(() => {
  localStorage.clear()
  asked.length = 0
  server.use(
    http.get('*/api/v1/product/races', ({ request }) => {
      const sido = new URL(request.url).searchParams.get('regionSido')
      asked.push(sido)
      const races = sido === '서울특별시' ? [cookie, jtbc] : sido === '부산광역시' ? [busan] : []
      return HttpResponse.json({
        status: 200,
        message: '대회 목록을 조회했습니다.',
        data: { races, page: 0, size: 2, totalElements: races.length, totalPages: 1 },
      })
    }),
  )
})

function renderScreen() {
  const onPick = vi.fn()
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <CategoryScreen onPick={onPick} onBack={vi.fn()} />
    </QueryClientProvider>,
  )
  return { onPick, user: userEvent.setup() }
}

describe('CategoryScreen (V4 · 카테고리)', () => {
  it('종목 동그라미 네 개를 거리와 함께 보여 준다', () => {
    renderScreen()
    const items = within(screen.getByRole('list', { name: '거리' })).getAllByRole('listitem')
    expect(items.map((li) => li.textContent)).toEqual([
      '5K5km',
      '10K10km',
      '하프21.1km',
      '풀42.195km',
    ])
  })

  it('지역 목록은 전체 · 서울 · 경기 · 인천 · 강원 · 충청 · 전라 · 경상 · 제주 순서이고 서울이 골라져 있다', () => {
    renderScreen()
    const list = within(screen.getByRole('list', { name: '지역' }))
    expect(list.getAllByRole('button').map((b) => b.textContent)).toEqual([
      '전체',
      '서울',
      '경기 · 인천',
      '강원',
      '충청',
      '전라',
      '경상',
      '제주',
    ])
    expect(list.getByRole('button', { name: '서울' })).toHaveAttribute('aria-current', 'true')
    expect(list.getByRole('button', { name: '전체' })).not.toHaveAttribute('aria-current')
  })

  it('고른 지역의 대회 두 개를 사진 이름 · 날짜와 함께 보여 주고 대회 상세로 연결한다', async () => {
    renderScreen()
    const first = await screen.findByRole('link', { name: /2026 용감한 쿠키RUN in 서울/ })
    expect(first).toHaveAttribute('href', '/races/11')
    expect(first).toHaveTextContent('11/1 (일)')
    const second = screen.getByRole('link', { name: /2026 JTBC 서울마라톤/ })
    expect(second).toHaveAttribute('href', '/races/12')
    expect(second).toHaveTextContent('11/2 (월)')
  })

  it('지역을 고르면 오른쪽 내용이 그 지역으로 바뀐다', async () => {
    const { user } = renderScreen()
    await screen.findByRole('link', { name: /쿠키RUN/ })

    await user.click(
      within(screen.getByRole('list', { name: '지역' })).getByRole('button', { name: '경상' }),
    )

    expect(screen.getByRole('heading', { name: '경상' })).toBeInTheDocument()
    expect(await screen.findByRole('link', { name: /부산 바다마라톤/ })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /쿠키RUN/ })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '부산 전체 대회' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '서울 전체 대회' })).not.toBeInTheDocument()
    expect(asked).toContain('부산광역시')
  })

  it('오른쪽 줄을 누르면 그 조건을 목록으로 넘긴다', async () => {
    const { user, onPick } = renderScreen()
    const right = within(screen.getByRole('list', { name: '서울 대회' }))

    await user.click(right.getByRole('button', { name: '서울 전체 대회' }))
    expect(onPick).toHaveBeenLastCalledWith({ regionSido: '서울특별시' })

    await user.click(right.getByRole('button', { name: /^5K/ }))
    expect(onPick).toHaveBeenLastCalledWith({ regionSido: '서울특별시', courseType: ['KM_5'] })

    await user.click(right.getByRole('button', { name: /^하프/ }))
    expect(onPick).toHaveBeenLastCalledWith({ regionSido: '서울특별시', courseType: ['HALF'] })

    await user.click(right.getByRole('button', { name: /^풀코스/ }))
    expect(onPick).toHaveBeenLastCalledWith({ regionSido: '서울특별시', courseType: ['FULL'] })

    await user.click(right.getByRole('button', { name: /^트레일/ }))
    expect(onPick).toHaveBeenLastCalledWith({ regionSido: '서울특별시', surface: 'TRAIL' })
  })

  it('종목 동그라미와 전체를 누르면 그 조건만 넘긴다', async () => {
    const { user, onPick } = renderScreen()

    await user.click(
      within(screen.getByRole('list', { name: '거리' })).getByRole('button', { name: /10K/ }),
    )
    expect(onPick).toHaveBeenLastCalledWith({ courseType: ['KM_10'] })

    await user.click(
      within(screen.getByRole('list', { name: '지역' })).getByRole('button', { name: '전체' }),
    )
    expect(onPick).toHaveBeenLastCalledWith({})
  })

  it('최근 본 종목 줄에만 「최근 본」을 붙인다', () => {
    localStorage.setItem('crewrun:recent-categories', JSON.stringify(['10K', '하프']))
    renderScreen()
    const right = within(screen.getByRole('list', { name: '서울 대회' }))

    expect(right.getAllByText('최근 본')).toHaveLength(2)
    expect(right.getByRole('button', { name: /^10K/ })).toHaveTextContent('최근 본')
    expect(right.getByRole('button', { name: /^하프/ })).toHaveTextContent('최근 본')
    expect(right.getByRole('button', { name: /^5K/ })).not.toHaveTextContent('최근 본')
  })

  it('저장된 기록이 없거나 깨져 있으면 「최근 본」이 없다', () => {
    localStorage.setItem('crewrun:recent-categories', '{"깨진":')
    renderScreen()
    expect(screen.queryByText('최근 본')).not.toBeInTheDocument()
  })

  it('종목을 누르면 최근 본 종목으로 남기고, 저장소를 못 써도 화면은 동작한다', async () => {
    const { user, onPick } = renderScreen()
    await user.click(
      within(screen.getByRole('list', { name: '서울 대회' })).getByRole('button', { name: /^5K/ }),
    )
    expect(JSON.parse(localStorage.getItem('crewrun:recent-categories') ?? '[]')).toEqual(['5K'])

    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('저장소 사용 불가')
    })
    await user.click(
      within(screen.getByRole('list', { name: '서울 대회' })).getByRole('button', { name: /^10K/ }),
    )
    expect(onPick).toHaveBeenLastCalledWith({ regionSido: '서울특별시', courseType: ['KM_10'] })
    vi.restoreAllMocks()
  })
})
