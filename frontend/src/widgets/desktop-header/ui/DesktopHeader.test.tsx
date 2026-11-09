import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it } from 'vitest'

import { clearAccessToken, setAccessToken } from '@/shared/api'
import { server } from '@/shared/testing'

import { DesktopHeader } from './DesktopHeader'

afterEach(() => clearAccessToken())

function renderHeader(unread: boolean) {
  server.use(
    http.get('*/api/v1/notification/notifications', () =>
      HttpResponse.json({
        status: 200,
        message: '알림을 조회했습니다.',
        data: [
          {
            id: '1',
            kind: 'REFUND_COMPLETED',
            icon: 'CircleCheck',
            title: '제목',
            body: '내용',
            at: '방금',
            group: 'today',
            href: null,
            sentAt: '2026-11-02T10:00:00+09:00',
            unread,
          },
        ],
      }),
    ),
  )
  setAccessToken('test-token')
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <DesktopHeader />
    </QueryClientProvider>,
  )
}

describe('DesktopHeader', () => {
  it('안 읽은 알림이 있으면 종 아이콘에 점이 붙는다', async () => {
    renderHeader(true)

    const bell = await screen.findByRole('link', { name: '알림, 읽지 않은 알림 있음' })
    expect(bell.querySelector('[data-unread-dot]')).not.toBeNull()
  })

  it('안 읽은 알림이 없으면 점이 없다', async () => {
    renderHeader(false)

    await new Promise((resolve) => setTimeout(resolve, 50))
    const bell = screen.getByRole('link', { name: '알림' })
    expect(bell.querySelector('[data-unread-dot]')).toBeNull()
  })
})
