import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { clearAccessToken, setAccessToken } from '@/shared/api'
import { boardHandlers, resetBoardMock, resetNavigationMock, server } from '@/shared/testing'
import { ToastProvider } from '@/shared/ui'

import { CommunityPage } from './CommunityPage'

vi.mock('next/navigation', async () =>
  (await import('@/shared/testing')).navigationMock('/community'),
)

const emptyList = http.get('*/api/v1/board/posts', () =>
  HttpResponse.json({ status: 200, message: '조회', data: [] }),
)

function renderPage(login: boolean) {
  if (login) setAccessToken('test-token')
  else clearAccessToken()
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <CommunityPage />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  resetNavigationMock()
  resetBoardMock()
  server.use(...boardHandlers)
})

describe('CommunityPage', () => {
  it('글 목록과 말머리 칩이 보이고, 칩을 누르면 그 말머리 글만 보인다', async () => {
    const user = userEvent.setup()
    renderPage(true)

    expect(await screen.findByText('JTBC 서울마라톤 첫 풀코스 완주했어요')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /토요일 아침 7시/ })).toHaveAttribute(
      'href',
      '/community/2',
    )
    expect(screen.getByText('판매자')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '질문' }))
    expect(
      await screen.findByText('하프 처음인데 에너지젤 몇 개 챙겨야 하나요?'),
    ).toBeInTheDocument()
    expect(screen.queryByText('JTBC 서울마라톤 첫 풀코스 완주했어요')).not.toBeInTheDocument()
  })

  it('정렬 시트에서 인기순을 고르면 좋아요가 많은 글이 먼저 온다', async () => {
    const user = userEvent.setup()
    renderPage(true)
    await screen.findByText('JTBC 서울마라톤 첫 풀코스 완주했어요')

    await user.click(screen.getByRole('button', { name: /최신순/ }))
    await user.click(await screen.findByRole('button', { name: '인기순' }))

    const links = await screen.findAllByRole('link', { name: /좋아요|^[^\s]/ })
    const titles = links.map((l) => l.textContent ?? '')
    const first = titles.findIndex((t) => t.includes('긍정 하프 마라톤 코스 정리'))
    const last = titles.findIndex((t) => t.includes('토요일 아침 7시'))
    expect(first).toBeGreaterThanOrEqual(0)
    expect(first).toBeLessThan(last)
  })

  it('글이 없으면 「아직 글이 없어요」만 보인다', async () => {
    server.use(emptyList)
    renderPage(true)
    expect(await screen.findByText('아직 글이 없어요')).toBeInTheDocument()
  })

  it('불러오지 못하면 오류 안내와 다시 시도 버튼이 보인다', async () => {
    server.use(http.get('*/api/v1/board/posts', () => HttpResponse.error()))
    renderPage(true)
    expect(await screen.findByText('정보를 불러오지 못했어요')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '다시 시도하기' })).toBeInTheDocument()
  })

  it('로그인 전에 글쓰기를 누르면 로그인 안내 시트가 뜨고, 로그인하면 글쓰기로 간다', async () => {
    const user = userEvent.setup()
    renderPage(false)
    await screen.findByText('JTBC 서울마라톤 첫 풀코스 완주했어요')

    await user.click(screen.getByRole('button', { name: '글쓰기' }))
    const sheet = await screen.findByRole('dialog')
    expect(within(sheet).getByText('로그인이 필요해요')).toBeInTheDocument()
    expect(within(sheet).getByRole('link', { name: '로그인' })).toHaveAttribute(
      'href',
      '/login?next=%2Fcommunity%2Fwrite',
    )
  })

  it('로그인했으면 글쓰기가 글쓰기 화면 링크다', async () => {
    renderPage(true)
    expect(await screen.findByRole('link', { name: '글쓰기' })).toHaveAttribute(
      'href',
      '/community/write',
    )
  })

  it('검색어를 보내면 찾은 글 수와 글이 보이고, 맞는 글이 없으면 그 검색어를 알려 준다', async () => {
    const user = userEvent.setup()
    renderPage(true)
    await screen.findByText('JTBC 서울마라톤 첫 풀코스 완주했어요')

    await user.click(screen.getByRole('button', { name: '글 검색' }))
    await user.type(screen.getByRole('searchbox', { name: '글 검색' }), '풀코스{Enter}')
    expect(await screen.findByText('글 1개')).toBeInTheDocument()

    await user.clear(screen.getByRole('searchbox', { name: '글 검색' }))
    await user.type(screen.getByRole('searchbox', { name: '글 검색' }), '울트라 트레일 후기{Enter}')
    expect(await screen.findByText('‘울트라 트레일 후기’에 맞는 글이 없어요')).toBeInTheDocument()
  })
})
