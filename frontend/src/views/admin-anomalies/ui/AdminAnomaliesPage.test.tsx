import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { payoutMock, server } from '@/shared/testing'

import { AdminAnomaliesPage } from './AdminAnomaliesPage'

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <AdminAnomaliesPage />
    </QueryClientProvider>,
  )
}

const EMPTY = {
  status: 200,
  message: '이상 감지 기록을 조회했습니다.',
  data: {
    mismatchCount: 0,
    autoFilledCount: 0,
    orderLookupFailureCount: 0,
    records: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 },
  },
}

describe('AdminAnomaliesPage', () => {
  it('카드 숫자와 기록을 보여 주고, 지급보류 기록은 보류 화면으로 이어진다', async () => {
    renderPage()

    const table = await screen.findByRole('table', { name: '이상 감지' })
    expect(within(table).getByText('더 들어감 2장 (코스 4: 2장)')).toBeInTheDocument()
    expect(within(table).getByText('판매자 #1')).toBeInTheDocument()
    expect(within(table).getByText('대회 #10 · 최종')).toBeInTheDocument()
    expect(within(table).getByText('지급보류')).toBeInTheDocument()
    expect(screen.getByText('자동으로 채운 참가권')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '보류 보기' })).toHaveAttribute('href', '/admin/holds')
  })

  it('기록이 없으면 어긋난 곳이 없다고 보여 준다 (N8)', async () => {
    server.use(payoutMock.getCreateFailuresMockHandler(EMPTY))
    renderPage()

    expect(await screen.findByText(/점검에서 어긋난 곳이 없어요/)).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('종류를 고르면 그 종류로 다시 불러온다', async () => {
    const types: (string | null)[] = []
    server.use(
      http.get('*/api/v1/payout/admin/create-failures', ({ request }) => {
        types.push(new URL(request.url).searchParams.get('type'))
        return HttpResponse.json(EMPTY)
      }),
    )
    const user = userEvent.setup()
    renderPage()
    await screen.findByText(/점검에서 어긋난 곳이 없어요/)

    await user.click(screen.getByRole('combobox', { name: '종류' }))
    await user.click(await screen.findByRole('option', { name: '주문 조회 실패' }))

    await waitFor(() => expect(types).toEqual([null, '주문_조회_실패']))
  }, 15_000)

  it('불러오지 못하면 서버 문구를 보여 준다', async () => {
    server.use(
      http.get('*/api/v1/payout/admin/create-failures', () =>
        HttpResponse.json(
          {
            status: 403,
            code: 'PAYOUT_NOT_ADMIN',
            message: '관리자만 이용할 수 있습니다.',
            data: null,
          },
          { status: 403 },
        ),
      ),
    )
    renderPage()

    expect(await screen.findByText('관리자만 이용할 수 있습니다.')).toBeInTheDocument()
  })
})
