import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { clearAccessToken, setAccessToken } from '@/shared/api'
import { boardHandlers, resetBoardMock, server } from '@/shared/testing'
import { ToastProvider } from '@/shared/ui'

import { CommunityPostPage } from './CommunityPostPage'

const router = vi.hoisted(() => ({
  push: vi.fn(),
  back: vi.fn(),
  replace: vi.fn(),
  refresh: vi.fn(),
}))
vi.mock('next/navigation', () => ({
  useRouter: () => router,
  usePathname: () => '/community/1',
}))

function renderPage(postId: number, login: boolean) {
  if (login) setAccessToken('test-token')
  else clearAccessToken()
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <CommunityPostPage postId={postId} />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  resetBoardMock()
  router.replace.mockClear()
  server.use(...boardHandlers)
})

describe('CommunityPostPage', () => {
  it('글 본문과 댓글이 보이고, 지운 댓글은 「삭제된 댓글이에요」로 남는다', async () => {
    renderPage(1, true)

    expect(
      await screen.findByRole('heading', { name: 'JTBC 서울마라톤 첫 풀코스 완주했어요' }),
    ).toBeInTheDocument()
    expect(screen.getAllByText(/수정됨/).length).toBeGreaterThan(0)
    expect(screen.getByRole('button', { name: /좋아요 24/ })).toBeInTheDocument()
    expect(
      await screen.findByText('완주 축하드려요! 25km 보급 위치는 내년에도 같다고 들었어요.'),
    ).toBeInTheDocument()
    expect(screen.getByText('삭제된 댓글이에요')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '댓글 5' })).toBeInTheDocument()
  })

  it('다른 사람 글은 더보기에 신고하기만 있고, 사유를 골라 신고하면 「신고했어요」가 뜬다', async () => {
    const user = userEvent.setup()
    let reported: unknown
    server.use(
      http.post('*/api/v1/board/posts/2/report', async ({ request }) => {
        reported = await request.json()
        return HttpResponse.json({ status: 201, message: '신고', data: null }, { status: 201 })
      }),
    )
    renderPage(2, true)
    await screen.findByRole('heading', { name: '토요일 아침 7시 여의도 10km 같이 뛰실 분' })

    await user.click(screen.getByRole('button', { name: '더보기' }))
    expect(screen.queryByRole('button', { name: '수정하기' })).not.toBeInTheDocument()
    await user.click(await screen.findByRole('button', { name: '신고하기' }))

    const sheet = await screen.findByRole('dialog')
    const submit = within(sheet).getByRole('button', { name: '신고하기' })
    expect(submit).toBeDisabled()
    await user.click(within(sheet).getByRole('radio', { name: '욕설·비방' }))
    await user.click(submit)

    expect(await screen.findByText('신고했어요')).toBeInTheDocument()
    expect(reported).toEqual({ reason: 'ABUSE' })
  })

  it('내 글은 더보기에 수정하기·삭제하기가 있고, 삭제를 확인하면 목록으로 돌아간다', async () => {
    const user = userEvent.setup()
    renderPage(1, true)
    await screen.findByRole('heading', { name: 'JTBC 서울마라톤 첫 풀코스 완주했어요' })

    await user.click(screen.getByRole('button', { name: '더보기' }))
    expect(await screen.findByRole('button', { name: '수정하기' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '삭제하기' }))

    const dialog = await screen.findByRole('alertdialog')
    expect(within(dialog).getByText('삭제한 글과 사진은 되돌릴 수 없어요.')).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: '삭제' }))

    await vi.waitFor(() => expect(router.replace).toHaveBeenCalledWith('/community'))
  })

  it('로그인 전에 좋아요를 누르면 로그인 안내 시트가 뜨고, 댓글 입력창 자리에 로그인 버튼이 보인다', async () => {
    const user = userEvent.setup()
    renderPage(1, false)
    await screen.findByRole('heading', { name: 'JTBC 서울마라톤 첫 풀코스 완주했어요' })
    expect(screen.getByRole('button', { name: '로그인하고 댓글 남기기' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /좋아요 24/ }))
    expect(await screen.findByText('로그인이 필요해요')).toBeInTheDocument()
  })

  it('신고로 숨겨진 내 글은 안내 줄이 보이고 댓글을 달 수 없으며, 더보기에는 삭제하기만 있다', async () => {
    const user = userEvent.setup()
    renderPage(6, true)

    expect(await screen.findByText('신고로 숨겨진 글이에요')).toBeInTheDocument()
    expect(screen.getByText('댓글을 달 수 없는 글이에요')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '더보기' }))
    expect(await screen.findByRole('button', { name: '삭제하기' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '수정하기' })).not.toBeInTheDocument()
  })

  it('남의 숨겨진 글(로그인 전)이나 없는 글은 「글을 찾을 수 없어요」가 보인다', async () => {
    renderPage(6, false)
    expect(await screen.findByText('글을 찾을 수 없어요')).toBeInTheDocument()
  })

  it('내 댓글 더보기 시트에는 수정하기·삭제하기만 있다', async () => {
    const user = userEvent.setup()
    renderPage(1, true)
    await screen.findByText('감사합니다. 내년에도 나갈게요!')

    await user.click(screen.getByRole('button', { name: '댓글 더보기' }))
    const sheet = await screen.findByRole('dialog', { name: '댓글' })
    expect(
      within(sheet)
        .getAllByRole('button')
        .map((b) => b.textContent),
    ).toEqual(['수정하기', '삭제하기'])
  })

  it('내 댓글을 지우면 확인창을 거쳐 지워진다', async () => {
    const user = userEvent.setup()
    renderPage(1, true)
    await screen.findByText('감사합니다. 내년에도 나갈게요!')

    await user.click(screen.getByRole('button', { name: '댓글 더보기' }))
    await user.click(await screen.findByRole('button', { name: '삭제하기' }))
    const dialog = await screen.findByRole('alertdialog')
    expect(within(dialog).getByText('삭제한 댓글은 되돌릴 수 없어요.')).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: '삭제' }))

    await vi.waitFor(() =>
      expect(screen.queryByText('감사합니다. 내년에도 나갈게요!')).not.toBeInTheDocument(),
    )
  })

  it('댓글을 쓰고 등록하면 목록에 보인다', async () => {
    const user = userEvent.setup()
    renderPage(1, true)
    await screen.findByText('삭제된 댓글이에요')

    await user.type(screen.getByRole('textbox', { name: '댓글 남기기' }), '다음 대회도 응원할게요')
    await user.click(screen.getByRole('button', { name: '등록' }))

    expect(await screen.findByText('다음 대회도 응원할게요')).toBeInTheDocument()
  })
})
