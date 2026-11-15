import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { RACE_BANNERS, type RaceSummary } from '@/entities/race'

import { HomePage } from './HomePage'

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), replace: vi.fn(), refresh: vi.fn() }),
  usePathname: () => '/',
}))

// 커뮤니티 탭은 가짜 응답 모드에서만 보인다(백엔드에 글 목록 API가 아직 없다)
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
    env.NEXT_PUBLIC_API_MOCKING = 'on'
  })

  it('실제 API 모드에서는 커뮤니티 탭을 숨긴다', () => {
    env.NEXT_PUBLIC_API_MOCKING = 'off'
    renderHome()
    const menu = screen.getByRole('navigation', { name: '홈 메뉴' })
    expect(within(menu).queryByRole('link', { name: '커뮤니티' })).not.toBeInTheDocument()
    expect(within(menu).getByRole('link', { name: '크루' })).toBeInTheDocument()
  })

  it('홈 메뉴에 커뮤니티 탭이 있다', () => {
    renderHome()
    const menu = screen.getByRole('navigation', { name: '홈 메뉴' })
    expect(within(menu).getByRole('link', { name: '커뮤니티' })).toHaveAttribute(
      'href',
      '/community',
    )
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

  it('위쪽 탭은 홈 · 대회 일정 · 크루이고 지금 화면은 홈이다', () => {
    renderHome()
    const tabs = within(screen.getByRole('navigation', { name: '홈 메뉴' }))
    expect(tabs.getByRole('link', { name: '홈' })).toHaveAttribute('aria-current', 'page')
    expect(tabs.getByRole('link', { name: '대회 일정' })).toHaveAttribute('href', '/races')
    expect(tabs.getByRole('link', { name: '크루' })).toHaveAttribute('href', '/crews')
  })

  it('바로가기는 신청 내역 · 예치금 · 크루다', () => {
    renderHome()
    const shortcuts = within(screen.getByRole('navigation', { name: '바로가기' }))
    expect(shortcuts.getByRole('link', { name: '신청 내역' })).toHaveAttribute('href', '/tickets')
    expect(shortcuts.getByRole('link', { name: '예치금' })).toHaveAttribute('href', '/me/deposit')
    expect(shortcuts.getByRole('link', { name: '크루' })).toHaveAttribute('href', '/crews')
  })

  it('헤더 오른쪽에는 알림만 있다', () => {
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
})
