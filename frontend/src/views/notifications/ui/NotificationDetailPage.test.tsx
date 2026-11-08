import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { clearAccessToken, setAccessToken } from '@/shared/api'
import { server } from '@/shared/testing'

import { NotificationDetailPage } from './NotificationDetailPage'

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), replace: vi.fn(), refresh: vi.fn() }),
  usePathname: () => '/notifications/race',
}))

const URL = '*/api/v1/notification/notifications'

afterEach(() => clearAccessToken())

function renderPage(category: 'race' | 'payment' | 'crew') {
  setAccessToken('test-token')
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <NotificationDetailPage category={category} />
    </QueryClientProvider>,
  )
}

describe('NotificationDetailPage', () => {
  it('종류 이름을 머리말에 두고, 날짜 칩 아래에 알림과 서울 시각을 오래된 것부터 보여 준다', async () => {
    renderPage('race')

    expect(screen.getByRole('heading', { name: '대회 소식' })).toBeInTheDocument()
    const first = await screen.findByText('2026.10.30 (금)')
    const second = screen.getByText('2026.11.2 (월)')
    expect(first.compareDocumentPosition(second) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(screen.getByText('오후 6:00')).toBeInTheDocument()
    expect(screen.getByText('오전 10:00')).toBeInTheDocument()
  })

  it('알림에 이동 주소가 있으면 버튼으로 그 화면에 간다', async () => {
    renderPage('race')

    const link = await screen.findByRole('link', { name: '대회 보러 가기' })
    expect(link.getAttribute('href')).toMatch(/^\/races\//)
  })

  it('앱 밖 주소나 스크립트 주소는 버튼으로 그리지 않는다', async () => {
    server.use(
      http.get(URL, () =>
        HttpResponse.json({
          status: 200,
          message: '알림을 조회했습니다.',
          data: [
            {
              id: 'x0',
              category: 'race',
              body: '탭 끼운 주소',
              sentAt: '2026-11-02T09:00:00+09:00',
              link: { label: '탭 끼움', href: '/\t/evil.example' },
              unread: true,
            },
            {
              id: 'x4',
              category: 'race',
              body: '줄바꿈 끼운 주소',
              sentAt: '2026-11-02T09:10:00+09:00',
              link: { label: '줄바꿈 끼움', href: '/\n/evil.example' },
              unread: true,
            },
            {
              id: 'x5',
              category: 'race',
              body: 'API 주소',
              sentAt: '2026-11-02T09:20:00+09:00',
              link: { label: 'API', href: '/api/v1/member/members/me' },
              unread: true,
            },
            {
              id: 'x1',
              category: 'race',
              body: '바깥 주소',
              sentAt: '2026-11-02T10:00:00+09:00',
              link: { label: '바깥으로', href: 'https://evil.example' },
              unread: true,
            },
            {
              id: 'x2',
              category: 'race',
              body: '두 빗금 주소',
              sentAt: '2026-11-02T11:00:00+09:00',
              link: { label: '두 빗금', href: '//evil.example' },
              unread: true,
            },
            {
              id: 'x3',
              category: 'race',
              body: '스크립트 주소',
              sentAt: '2026-11-02T12:00:00+09:00',
              link: { label: '스크립트', href: 'javascript:alert(1)' },
              unread: true,
            },
          ],
        }),
      ),
    )
    renderPage('race')

    expect(await screen.findByText('바깥 주소')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '바깥으로' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '두 빗금' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '스크립트' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '탭 끼움' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '줄바꿈 끼움' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'API' })).not.toBeInTheDocument()
  })

  it('그 종류의 알림이 없으면 빈 화면을 보여 준다', async () => {
    server.use(
      http.get(URL, () =>
        HttpResponse.json({ status: 200, message: '알림을 조회했습니다.', data: [] }),
      ),
    )
    renderPage('crew')

    expect(await screen.findByText('아직 알림이 없어요')).toBeInTheDocument()
  })
})
