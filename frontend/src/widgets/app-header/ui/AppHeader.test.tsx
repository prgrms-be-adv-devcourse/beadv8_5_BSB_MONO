import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import type { ReactNode } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { clearAccessToken, setAccessToken } from '@/shared/api'
import { server } from '@/shared/testing'

import { AppHeader } from './AppHeader'

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), replace: vi.fn() }),
}))

afterEach(() => clearAccessToken())

function withQuery(ui: ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={client}>{ui}</QueryClientProvider>
}

const notices = (unread: boolean[], kind = 'CREW_JOINED') =>
  http.get('*/api/v1/notification/notifications', () =>
    HttpResponse.json({
      status: 200,
      message: '알림을 조회했습니다.',
      data: unread.map((u, i) => ({
        id: String(i),
        kind,
        icon: 'Users',
        title: '제목',
        body: '내용',
        at: '방금',
        group: 'today',
        href: null,
        sentAt: '2026-11-02T10:00:00+09:00',
        unread: u,
      })),
    }),
  )

describe('AppHeader', () => {
  it('기본 머리말 오른쪽에 대회 찾기와 알림 아이콘이 있고, 알림은 알림 목록으로 간다', () => {
    render(withQuery(<AppHeader />))

    expect(screen.getByRole('link', { name: '대회 찾기' })).toHaveAttribute('href', '/races')
    expect(screen.getByRole('link', { name: '알림' })).toHaveAttribute('href', '/notifications')
    expect(screen.queryByRole('link', { name: '장바구니' })).not.toBeInTheDocument()
  })

  it('안 읽은 알림이 있으면 알림 아이콘에 점이 붙고 이름에 알린다', async () => {
    server.use(notices([false, true]))
    setAccessToken('test-token')
    render(withQuery(<AppHeader />))

    const bell = await screen.findByRole('link', { name: '알림, 읽지 않은 알림 있음' })
    expect(bell.querySelector('[data-unread-dot]')).not.toBeNull()
  })

  it('목록에 안 나오는 종류(판매자 알림)라도 안 읽었으면 점이 붙는다', async () => {
    server.use(notices([true], 'KIT_SHIPMENT_REQUIRED'))
    setAccessToken('test-token')
    render(withQuery(<AppHeader />))

    expect(
      await screen.findByRole('link', { name: '알림, 읽지 않은 알림 있음' }),
    ).toBeInTheDocument()
  })

  it('모두 읽었으면 점이 없다', async () => {
    let called = false
    server.use(
      http.get('*/api/v1/notification/notifications', () => {
        called = true
        return HttpResponse.json({ status: 200, message: '알림을 조회했습니다.', data: [] })
      }),
    )
    setAccessToken('test-token')
    render(withQuery(<AppHeader />))

    await vi.waitFor(() => expect(called).toBe(true))
    const bell = screen.getByRole('link', { name: '알림' })
    expect(bell.querySelector('[data-unread-dot]')).toBeNull()
  })
})
