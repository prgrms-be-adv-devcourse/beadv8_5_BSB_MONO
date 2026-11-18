import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { RACE_BANNERS, type RaceSummary } from '@/entities/race'
import { clearAccessToken, setAccessToken } from '@/shared/api'
import { server } from '@/shared/testing'

import { HomePage } from './HomePage'

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), replace: vi.fn(), refresh: vi.fn() }),
  usePathname: () => '/',
}))

// 러너 이야기(커뮤니티 인기 글)는 가짜 응답 모드에서만 보인다(백엔드에 글 목록 API가 아직 없다)
const env = vi.hoisted(() => ({ NEXT_PUBLIC_API_MOCKING: 'on' as 'on' | 'off' }))
vi.mock('@/shared/config', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/shared/config')>()
  return { ...original, clientEnv: env }
})

const RACE: RaceSummary = {
  id: '1',
  title: '2026 용감한 쿠키RUN in 서울',
  posterLabel: '10K',
  status: 'D-1',
  meta: '11/1 (일) · 서울숲 일대',
  price: '35,000원부터',
}

function renderHome(closingSoon: RaceSummary[] = []) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <HomePage closingSoon={closingSoon} />
    </QueryClientProvider>,
  )
}

describe('HomePage', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-10-15T12:00:00+09:00'))
    env.NEXT_PUBLIC_API_MOCKING = 'on'
    clearAccessToken()
  })
  afterEach(() => {
    vi.useRealTimers()
    clearAccessToken()
  })

  it('배너 첫 장만 먼저 불러오고 나머지는 늦게 불러온다', () => {
    renderHome()
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

  it('위쪽 탭과 바로가기 줄은 없다 (v3 홈)', () => {
    renderHome()
    expect(screen.queryByRole('navigation', { name: '홈 메뉴' })).not.toBeInTheDocument()
    expect(screen.queryByRole('navigation', { name: '바로가기' })).not.toBeInTheDocument()
  })

  it('로그아웃 상태면 헤더 오른쪽에 알림 대신 로그인 버튼이 있다 (홈 로그아웃)', () => {
    clearAccessToken()
    renderHome()
    expect(screen.getByRole('link', { name: '로그인' })).toHaveAttribute('href', '/login?next=%2F')
    expect(screen.queryByRole('link', { name: '알림' })).not.toBeInTheDocument()
  })

  it('로그인하면 헤더 오른쪽에는 알림만 있다', () => {
    setAccessToken('test-token')
    renderHome()
    expect(screen.getByRole('link', { name: '알림' })).toHaveAttribute('href', '/notifications')
    expect(screen.queryByRole('link', { name: '대회 찾기' })).not.toBeInTheDocument()
  })

  it('곧 마감돼요 옆에 카드 수를 보여 주고, 카드에 상태 · 대회일 · 가격이 보인다', () => {
    renderHome([RACE, { ...RACE, id: '2' }])
    const section = within(screen.getByRole('region', { name: /곧 마감돼요/ }))
    expect(section.getByText('2')).toBeInTheDocument()
    expect(section.getAllByText('D-1')).toHaveLength(2)
    expect(section.getAllByText('11/1 (일) · 서울숲 일대')).toHaveLength(2)
    expect(section.getAllByText('35,000원부터')).toHaveLength(2)
    expect(screen.queryByText(/지금 접수 중/)).not.toBeInTheDocument()
  })

  it('곧 마감되는 대회가 없으면 빈 안내를 보여 준다', () => {
    renderHome()
    expect(screen.getByText('곧 마감되는 대회가 없어요')).toBeInTheDocument()
  })

  it('곧 마감돼요 제목 줄 전체가 대회 목록으로 가는 링크이고 따로 전체 보기는 없다', () => {
    renderHome([RACE])
    const link = screen.getByRole('link', { name: /곧 마감돼요/ })
    expect(link).toHaveAttribute('href', '/races')
    expect(screen.queryByRole('link', { name: '전체 보기' })).not.toBeInTheDocument()
  })

  it('마감 임박 칩은 경고 톤 배지로 그린다', () => {
    renderHome([RACE])
    expect(screen.getByText('D-1')).toHaveClass('bg-bg-badge-warning')
  })

  it('로그인했고 다가오는 대회가 있으면 다음 대회 카드를 보여 주고 참가권으로 이동한다', async () => {
    setAccessToken('test-token')
    renderHome()
    const card = await screen.findByRole('link', { name: /다음 대회/ })
    expect(card).toHaveAttribute('href', '/tickets/1-1')
    expect(within(card).getByText('다음 대회 · D-17')).toBeInTheDocument()
    expect(within(card).getByText('2026 용감한 쿠키RUN in 서울')).toBeInTheDocument()
  })

  it('로그아웃 상태면 다음 대회 카드가 없다', () => {
    renderHome()
    expect(screen.queryByRole('link', { name: /다음 대회/ })).not.toBeInTheDocument()
  })

  it('로그인했어도 참가권이 없으면 다음 대회 카드가 없다', async () => {
    server.use(
      http.get('*/api/v1/order/orders', () =>
        HttpResponse.json({
          status: 200,
          message: '주문 내역을 조회했습니다.',
          data: { orders: [], page: 0, size: 100, totalElements: 0 },
        }),
      ),
    )
    setAccessToken('test-token')
    renderHome()
    // 러너 이야기가 그려진 뒤에도 카드가 없는지 본다
    expect(await screen.findByRole('link', { name: /러너 이야기/ })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /다음 대회/ })).not.toBeInTheDocument()
  })

  it('러너 이야기에 인기 글 3개를 보여 주고 제목 줄이 커뮤니티로 가는 링크다', async () => {
    renderHome()
    expect(await screen.findByRole('link', { name: /러너 이야기/ })).toHaveAttribute(
      'href',
      '/community',
    )
    const list = within(screen.getByRole('region', { name: /러너 이야기/ }))
    expect(list.getAllByRole('listitem')).toHaveLength(3)
  })

  it('러너 이야기를 불러오지 못하면 영역을 숨긴다', async () => {
    server.use(
      http.get('*/api/v1/board/posts', () => HttpResponse.json({ message: 'x' }, { status: 500 })),
    )
    renderHome([RACE])
    expect(await screen.findByText('곧 마감돼요')).toBeInTheDocument()
    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(screen.queryByRole('region', { name: /러너 이야기/ })).not.toBeInTheDocument()
  })

  it('실제 API 모드에서는 러너 이야기를 숨긴다', () => {
    env.NEXT_PUBLIC_API_MOCKING = 'off'
    renderHome()
    expect(screen.queryByRole('region', { name: /러너 이야기/ })).not.toBeInTheDocument()
  })
})
