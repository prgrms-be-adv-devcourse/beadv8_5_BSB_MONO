import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'

import { setAccessToken } from '@/shared/api'
import { server } from '@/shared/testing'
import { ToastProvider } from '@/shared/ui'

import { AdminSettingsPage } from './AdminSettingsPage'

const ME = {
  id: 1,
  name: '김운영',
  email: 'admin@crewrun.kr',
  nickname: '운영왕',
  createDate: '2026-10-01T09:00:00',
  passwordSet: true,
}

function mockMe(me: Partial<typeof ME> = {}) {
  server.use(
    http.get('*/api/v1/member/members/me', () =>
      HttpResponse.json({ status: 200, message: '조회', data: { ...ME, ...me } }),
    ),
  )
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <AdminSettingsPage />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

beforeEach(() => setAccessToken('access-token'))

describe('AdminSettingsPage', () => {
  it('이름 · 이메일 · 가입일을 보여 준다', async () => {
    mockMe()
    renderPage()

    expect(await screen.findByText('김운영')).toBeInTheDocument()
    expect(screen.getByText('admin@crewrun.kr')).toBeInTheDocument()
    expect(screen.getByText('2026. 10. 1.')).toBeInTheDocument()
  })

  it('내 정보를 못 불러오면 오류 안내를 보인다', async () => {
    server.use(
      http.get('*/api/v1/member/members/me', () =>
        HttpResponse.json({ status: 500, message: '오류', data: null }, { status: 500 }),
      ),
    )
    renderPage()

    expect(await screen.findByText('계정 정보를 불러오지 못했어요')).toBeInTheDocument()
  })

  it('닉네임을 저장하면 내 정보 수정 API에 새 닉네임을 보낸다', async () => {
    mockMe()
    let sent: unknown
    server.use(
      http.put('*/api/v1/member/members/me', async ({ request }) => {
        sent = await request.json()
        return HttpResponse.json({ status: 200, message: '수정', data: { ...ME, nickname: '새운영' } })
      }),
    )
    const user = userEvent.setup()
    renderPage()

    const input = await screen.findByRole('textbox', { name: '닉네임' })
    await user.clear(input)
    await user.type(input, '새운영')
    await user.click(screen.getByRole('button', { name: '저장하기' }))

    expect(await screen.findByText('닉네임을 바꿨어요')).toBeInTheDocument()
    expect(sent).toEqual({ nickname: '새운영' })
  })

  it('닉네임이 그대로면 저장하기를 누를 수 없다', async () => {
    mockMe()
    renderPage()

    await screen.findByRole('textbox', { name: '닉네임' })
    expect(screen.getByRole('button', { name: '저장하기' })).toBeDisabled()
  })

  it('규칙에 맞지 않는 닉네임은 보내지 않고 칸 아래에 알린다', async () => {
    mockMe()
    let called = false
    server.use(
      http.put('*/api/v1/member/members/me', () => {
        called = true
        return HttpResponse.json({ status: 200, message: '수정', data: ME })
      }),
    )
    const user = userEvent.setup()
    renderPage()

    const input = await screen.findByRole('textbox', { name: '닉네임' })
    await user.clear(input)
    await user.type(input, '가')
    await user.click(screen.getByRole('button', { name: '저장하기' }))

    expect(
      await screen.findByText('닉네임은 한글 2~8자, 영문 4~16자로 정해 주세요.'),
    ).toBeInTheDocument()
    expect(called).toBe(false)
  })

  it('이미 쓰는 닉네임이면 서버 안내를 칸 아래에 보인다', async () => {
    mockMe()
    server.use(
      http.put('*/api/v1/member/members/me', () =>
        HttpResponse.json(
          {
            status: 409,
            code: 'MEMBER_DUPLICATE_NICKNAME',
            message: '이미 쓰고 있는 닉네임이에요.',
            data: null,
          },
          { status: 409 },
        ),
      ),
    )
    const user = userEvent.setup()
    renderPage()

    const input = await screen.findByRole('textbox', { name: '닉네임' })
    await user.clear(input)
    await user.type(input, '겹친닉')
    await user.click(screen.getByRole('button', { name: '저장하기' }))

    expect(await screen.findByText('이미 쓰고 있는 닉네임이에요.')).toBeInTheDocument()
  })

  it('재설정 메일 받기를 누르면 내 이메일로 재설정 메일을 요청한다', async () => {
    mockMe()
    let sent: unknown
    server.use(
      http.post('*/api/v1/auth/password-reset/request', async ({ request }) => {
        sent = await request.json()
        return HttpResponse.json({ status: 200, message: '발송', data: null })
      }),
    )
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: '재설정 메일 받기' }))

    expect(await screen.findByText('admin@crewrun.kr로 메일을 보냈어요')).toBeInTheDocument()
    expect(sent).toEqual({ email: 'admin@crewrun.kr' })
  })

  it('재설정 메일 요청이 실패하면 오류 알림을 보인다', async () => {
    mockMe()
    server.use(
      http.post('*/api/v1/auth/password-reset/request', () =>
        HttpResponse.json(
          { status: 429, message: '1분 뒤에 다시 시도해 주세요.', data: null },
          { status: 429 },
        ),
      ),
    )
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: '재설정 메일 받기' }))

    // 오류 알림은 화면 낭독기용 사본이 함께 그려진다
    expect((await screen.findAllByText('1분 뒤에 다시 시도해 주세요.'))[0]).toBeInTheDocument()
  })

  it('비밀번호가 없는 계정은 재설정 메일 버튼을 보이지 않는다', async () => {
    mockMe({ passwordSet: false })
    renderPage()

    await screen.findByText('김운영')
    expect(screen.queryByRole('button', { name: '재설정 메일 받기' })).not.toBeInTheDocument()
  })
})
