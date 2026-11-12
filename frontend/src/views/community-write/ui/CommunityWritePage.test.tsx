import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { clearAccessToken, setAccessToken } from '@/shared/api'
import { boardHandlers, resetBoardMock, server } from '@/shared/testing'
import { ToastProvider } from '@/shared/ui'

import { CommunityWritePage } from './CommunityWritePage'

const router = vi.hoisted(() => ({
  push: vi.fn(),
  back: vi.fn(),
  replace: vi.fn(),
  refresh: vi.fn(),
}))
vi.mock('next/navigation', () => ({
  useRouter: () => router,
  usePathname: () => '/community/write',
}))

function renderPage(postId?: number, login = true) {
  if (login) setAccessToken('test-token')
  else clearAccessToken()
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <CommunityWritePage postId={postId} />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  resetBoardMock()
  router.replace.mockClear()
  server.use(...boardHandlers)
  URL.createObjectURL = vi.fn(() => 'blob:photo')
})

describe('CommunityWritePage', () => {
  it('말머리는 자유가 미리 골라져 있고, 제목·본문이 비면 등록할 수 없다', async () => {
    renderPage()
    expect(await screen.findByRole('button', { name: '자유' })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
    expect(screen.getByRole('button', { name: '등록' })).toBeDisabled()
  })

  it('말머리·제목·본문·사진을 넣고 등록하면 서버로 보내고 글 상세로 옮겨 간다', async () => {
    const user = userEvent.setup()
    let sent: unknown
    server.use(
      http.post('*/api/v1/board/posts', async ({ request }) => {
        sent = await request.json()
        return HttpResponse.json(
          { status: 201, message: '등록', data: { postId: 101 } },
          { status: 201 },
        )
      }),
    )
    renderPage()

    await user.click(await screen.findByRole('button', { name: '같이 뛰어요' }))
    await user.type(screen.getByRole('textbox', { name: '제목' }), '일요일 한강 5km')
    await user.type(screen.getByRole('textbox', { name: '본문' }), '7시에 만나요')
    await user.upload(
      screen.getByTestId('photo-input'),
      new File(['x'], 'run.png', { type: 'image/png' }),
    )
    expect(await screen.findByLabelText('사진 추가, 1장 / 최대 5장')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByRole('button', { name: '등록' })).toBeEnabled())
    await user.click(screen.getByRole('button', { name: '등록' }))

    await waitFor(() => expect(router.replace).toHaveBeenCalledWith('/community/101'))
    expect(sent).toMatchObject({
      category: 'RUN_TOGETHER',
      title: '일요일 한강 5km',
      content: '7시에 만나요',
      fileIds: [expect.any(Number)],
    })
  })

  it('서버가 거절하면(너무 빨리 씀 등) 그 문구를 알리고 화면에 남는다', async () => {
    const user = userEvent.setup()
    server.use(
      http.post('*/api/v1/board/posts', () =>
        HttpResponse.json(
          { status: 429, code: 'BOARD_TOO_FAST', message: '잠시 뒤에 다시 써주세요.', data: null },
          { status: 429 },
        ),
      ),
    )
    renderPage()
    await user.type(await screen.findByRole('textbox', { name: '제목' }), '제목')
    await user.type(screen.getByRole('textbox', { name: '본문' }), '본문')
    await user.click(screen.getByRole('button', { name: '등록' }))

    expect((await screen.findAllByText('잠시 뒤에 다시 써주세요.')).length).toBeGreaterThan(0)
    expect(router.replace).not.toHaveBeenCalled()
  })

  it('글 수정은 원래 제목·본문이 채워져 있고, 저장하면 글 상세로 돌아간다', async () => {
    const user = userEvent.setup()
    renderPage(1)

    expect(await screen.findByRole('textbox', { name: '제목' })).toHaveValue(
      'JTBC 서울마라톤 첫 풀코스 완주했어요',
    )
    await user.type(screen.getByRole('textbox', { name: '제목' }), '!')
    await user.click(screen.getByRole('button', { name: '저장하기' }))

    await waitFor(() => expect(router.replace).toHaveBeenCalledWith('/community/1'))
  })

  it('신고로 숨겨진 글은 고칠 수 없다', async () => {
    renderPage(6)
    expect(await screen.findByText('고칠 수 없는 글이에요')).toBeInTheDocument()
  })

  it('로그인하지 않았으면 로그인 화면으로 보낸다', async () => {
    renderPage(undefined, false)
    await waitFor(() =>
      expect(router.replace).toHaveBeenCalledWith('/login?next=%2Fcommunity%2Fwrite'),
    )
  })
})
