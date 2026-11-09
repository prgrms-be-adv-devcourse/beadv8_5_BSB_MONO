import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'

import { payoutMock, server } from '@/shared/testing'

import { AdminAnomaliesPage } from './AdminAnomaliesPage'

const push = vi.fn()
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push, back: vi.fn(), replace: vi.fn() }),
  usePathname: () => '/admin/anomalies',
}))

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
  it('시안대로 탭 · 카드 · 목록 · 상세를 보여 주고, 이름은 회원 · 대회 API로 붙인다 (N4)', async () => {
    server.use(
      http.get('*/api/v1/product/races/:raceId', () =>
        HttpResponse.json({
          status: 200,
          message: '대회를 조회했습니다.',
          data: {
            raceId: 10,
            title: '춘천 나이트런',
            courses: [
              { raceCourseId: 4, name: '하프' },
              { raceCourseId: 5, name: '10K' },
            ],
          },
        }),
      ),
    )
    renderPage()

    const table = await screen.findByRole('table', { name: '이상 감지' })
    expect(await within(table).findByText('크루런 스포츠')).toBeInTheDocument()
    expect(await within(table).findByText('춘천 나이트런 · 최종')).toBeInTheDocument()
    expect(within(table).getByText('+2장')).toBeInTheDocument()
    expect(within(table).getByText('정산이 판매보다 많음')).toBeInTheDocument()
    expect(within(table).getByText('지급보류')).toBeInTheDocument()

    expect(screen.getByRole('tab', { name: '이상 감지' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('button', { name: '엑셀로 내려받기' })).toBeDisabled()
    expect(screen.getByText('자동으로 채운 참가권')).toBeInTheDocument()
    expect(screen.getByText('판매 310장 · 정산 312장')).toBeInTheDocument()
    expect(screen.getByText('하프 +2장 · 10K 0장')).toBeInTheDocument()
    expect(
      screen.getByText(
        '정산이 판매보다 많아 지급보류(HD-20261106-0D44)를 걸었어요. 참가권 ID는 서버 로그에 있어요.',
      ),
    ).toBeInTheDocument()
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

  it('탭을 누르면 그 화면으로 가고, 화면이 없는 회수 탭은 옮기지 않는다', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(screen.getByRole('tab', { name: '지급보류' }))
    await user.click(screen.getByRole('tab', { name: '회수' }))

    expect(push.mock.calls).toEqual([['/admin/holds']])
  })

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
