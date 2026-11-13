import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { boardHandlers, resetBoardMock, server } from '@/shared/testing'
import { ToastProvider } from '@/shared/ui'

import { AdminCommunityReportsPage } from './AdminCommunityReportsPage'

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), replace: vi.fn(), refresh: vi.fn() }),
  usePathname: () => '/admin/community-reports',
}))

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <AdminCommunityReportsPage />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  resetBoardMock()
  server.use(...boardHandlers)
})

describe('AdminCommunityReportsPage', () => {
  it('숫자 타일과 숨겨진 글·댓글 표, 처리할 항목의 신고 사유가 보인다', async () => {
    renderPage()

    expect(await screen.findByText('숨겨진 글 · 댓글 3건')).toBeInTheDocument()
    expect(screen.getByText('확인을 기다리는 신고')).toBeInTheDocument()
    const table = screen.getByRole('table', { name: '숨겨진 글 · 댓글' })
    expect(within(table).getAllByText('숨김')).toHaveLength(2)
    expect(within(table).getByText('되살림')).toBeInTheDocument()
    expect(screen.getByText('광고 7')).toBeInTheDocument()
  })

  it('줄을 고르고 되살리면 상태가 「되살림」으로 바뀌고 처리 버튼 대신 처리 결과가 보인다', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('숨겨진 글 · 댓글 3건')

    await user.click(screen.getByText('달리자123'))
    expect(screen.getByText('욕설 · 비방 4 · 기타 1')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '되살리기' }))

    const table = screen.getByRole('table', { name: '숨겨진 글 · 댓글' })
    await vi.waitFor(() => expect(within(table).getAllByText('되살림')).toHaveLength(2))
    expect(screen.queryByRole('button', { name: '되살리기' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '지우기' })).not.toBeInTheDocument()
    expect(screen.getByText(/^되살림 · \d+\.\d+ \d{2}:\d{2}$/)).toBeInTheDocument()
  })

  it('지우기를 누르면 상태가 「지움」으로 바뀐다', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('숨겨진 글 · 댓글 3건')

    await user.click(screen.getByRole('button', { name: '지우기' }))
    await vi.waitFor(() => expect(screen.getByText('지움')).toBeInTheDocument())
  })

  it('불러오지 못하면 오류 안내가 보인다', async () => {
    server.use(http.get('*/api/v1/board/admin/reports', () => HttpResponse.error()))
    renderPage()
    expect(await screen.findByText('신고 목록을 불러오지 못했어요')).toBeInTheDocument()
  })
})
