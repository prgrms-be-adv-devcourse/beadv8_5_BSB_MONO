import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'

import { setAccessToken } from '@/shared/api'
import { server } from '@/shared/testing'

import { AdminDashboardPage } from './AdminDashboardPage'

const ok = (data: unknown) => HttpResponse.json({ status: 200, message: '조회', data })

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <AdminDashboardPage />
    </QueryClientProvider>,
  )
}

function mockLists({ sellersFail = false } = {}) {
  server.use(
    http.get('*/api/v1/member/admin/sellers', () =>
      sellersFail
        ? HttpResponse.json({ status: 500, message: '오류', data: null }, { status: 500 })
        : ok([]),
    ),
    http.get('*/api/v1/member/admin/members/identity-review', () =>
      ok({ groups: [], page: 0, size: 1, totalElements: 3, totalPages: 3 }),
    ),
    http.get('*/api/v1/board/admin/reports', () => ok({ items: [], hiddenCount: 2 })),
    http.get('*/api/v1/inquiry/admin/inquiries', () =>
      ok({
        inquiries: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
        waitingCount: 5,
        answeredCount: 0,
      }),
    ),
  )
}

beforeEach(() => setAccessToken('access-token'))

describe('AdminDashboardPage 할 일', () => {
  it('묶음 수와 글·댓글 수를 실제 뜻에 맞는 이름으로 보인다', async () => {
    mockLists()
    renderPage()

    const row = (name: string) => screen.getByRole('link', { name: new RegExp(name) })
    expect(await screen.findByRole('link', { name: /확인이 필요한 계정.*3건/ })).toBeVisible()
    expect(row('숨겨진 신고 글')).toHaveTextContent('2건')
    expect(row('답변 대기 문의')).toHaveTextContent('5건')
  })

  it('요청이 실패한 줄은 숫자 대신 불러오지 못했다고 알린다', async () => {
    mockLists({ sellersFail: true })
    renderPage()

    const sellers = await screen.findByRole('link', { name: /판매자 승인 대기/ })
    expect(await within(sellers).findByText('불러오지 못했어요')).toBeInTheDocument()
  })

  it('네 줄이 모두 실패하면 줄 대신 카드 하나로 알리고 다시 시도할 수 있다', async () => {
    const fail = () =>
      HttpResponse.json({ status: 500, message: '오류', data: null }, { status: 500 })
    server.use(
      http.get('*/api/v1/member/admin/sellers', fail),
      http.get('*/api/v1/member/admin/members/identity-review', fail),
      http.get('*/api/v1/board/admin/reports', fail),
      http.get('*/api/v1/inquiry/admin/inquiries', fail),
    )
    renderPage()

    expect(await screen.findByText('할 일을 불러오지 못했어요')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '다시 시도하기' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /판매자 승인 대기/ })).not.toBeInTheDocument()
  })

  it('지급 확인 필요 기록 표는 없다', async () => {
    mockLists()
    renderPage()

    await screen.findByRole('link', { name: /판매자 승인 대기/ })
    expect(screen.queryByText('지급 확인 필요 · 최근 1개월')).not.toBeInTheDocument()
  })
})
