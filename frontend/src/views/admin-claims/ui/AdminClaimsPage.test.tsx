import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'

import type { payoutModel } from '@/shared/api'
import { server } from '@/shared/testing'
import { ToastProvider } from '@/shared/ui'

import { AdminClaimsPage } from './AdminClaimsPage'

const CLAIMING: payoutModel.PayoutClaimDto = {
  id: 2,
  claimCode: 'RC-20261215-0002',
  sellerId: 11,
  raceId: 7,
  amount: 105500,
  startDate: '2026-12-14',
  dueDate: '2027-01-13',
  status: '청구_중',
  confirmedBy: null,
  confirmedAt: null,
}

function claimsBody(content: payoutModel.PayoutClaimDto[], summary = [1, 105500, 0]) {
  return {
    status: 200,
    message: '청구 목록을 조회했습니다.',
    data: {
      summary: {
        claimingCount: summary[0],
        claimingAmount: summary[1],
        confirmedThisMonthCount: summary[2],
      },
      claims: { content, page: 0, size: 50, totalElements: content.length, totalPages: 1 },
    },
  }
}

/** 받은 목록 요청의 쿼리를 모은다 */
function mockClaims(content: payoutModel.PayoutClaimDto[], summary?: number[]) {
  const queries: URLSearchParams[] = []
  server.use(
    http.get('*/api/v1/payout/admin/claims', ({ request }) => {
      queries.push(new URL(request.url).searchParams)
      return HttpResponse.json(claimsBody(content, summary))
    }),
  )
  return queries
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <AdminClaimsPage />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

describe('AdminClaimsPage', () => {
  beforeEach(() => {
    server.use(
      http.get('*/api/v1/member/admin/sellers', () =>
        HttpResponse.json({
          status: 200,
          message: 'ok',
          data: [
            {
              id: 11,
              memberId: 8,
              sellerType: 'BUSINESS',
              businessName: '춘천러닝클럽',
              businessNumber: '123-45-67890',
              representativeName: '김대표',
              status: 'APPROVED',
              requestedAt: '2026-10-01T10:00:00',
            },
          ],
        }),
      ),
    )
  })

  it('카드 숫자와 청구 중 목록을 보여 주고, 판매자 이름을 판매자 ID로 맞춰 붙인다', async () => {
    const queries = mockClaims([CLAIMING], [1, 105500, 3])
    renderPage()

    const row = await screen.findByRole('row', { name: /RC-20261215-0002/ })
    expect(within(row).getByText('춘천러닝클럽')).toBeInTheDocument()
    expect(within(row).getByText('₩105,500')).toBeInTheDocument()
    expect(screen.getByText('3')).toBeInTheDocument()
    expect(queries[0].get('status')).toBe('청구_중')
  })

  it('입금 확인 탭을 누르면 입금 확인 목록을 부른다', async () => {
    const queries = mockClaims([CLAIMING])
    const user = userEvent.setup()
    renderPage()
    await screen.findByRole('row', { name: /RC-20261215-0002/ })

    await user.click(screen.getByRole('tab', { name: '입금 확인' }))

    await screen.findByRole('row', { name: /RC-20261215-0002/ })
    expect(queries.at(-1)?.get('status')).toBe('입금_확인')
  })

  it('청구 ID(RC-)로 한 건을 찾는다', async () => {
    const queries = mockClaims([CLAIMING])
    const user = userEvent.setup()
    renderPage()

    await user.type(screen.getByRole('textbox', { name: '청구 ID' }), 'RC-20261215-0002')
    await user.click(screen.getByRole('button', { name: '찾기' }))

    await screen.findByRole('row', { name: /RC-20261215-0002/ })
    expect(queries.at(-1)?.get('code')).toBe('RC-20261215-0002')
  })

  it('청구가 없으면 빈 안내를 보여 준다', async () => {
    mockClaims([], [0, 0, 0])
    renderPage()

    expect(await screen.findByText('청구 중인 건이 없어요')).toBeInTheDocument()
  })

  it('회수 완료를 누르면 고른 청구의 입금을 확인한다', async () => {
    mockClaims([CLAIMING])
    let confirmedPath = ''
    server.use(
      http.post('*/api/v1/payout/claims/:claimId/confirm', ({ request }) => {
        confirmedPath = new URL(request.url).pathname
        return HttpResponse.json({
          status: 200,
          message: '청구 입금을 확인했습니다.',
          data: {
            ...CLAIMING,
            status: '입금_확인',
            confirmedBy: 99,
            confirmedAt: '2026-12-20T10:00:00',
          },
        })
      }),
    )
    const user = userEvent.setup()
    renderPage()
    await screen.findByRole('row', { name: /RC-20261215-0002/ })

    await user.click(screen.getByRole('button', { name: '회수 완료' }))
    const dialog = await screen.findByRole('alertdialog')
    expect(dialog).toHaveTextContent('RC-20261215-0002 · 춘천러닝클럽, ₩105,500')
    await user.click(within(dialog).getByRole('button', { name: '입금 확인' }))

    expect(await screen.findByText('회수 완료로 바꿨어요.')).toBeInTheDocument()
    expect(confirmedPath).toBe('/api/v1/payout/claims/2/confirm')
  })

  it('확인 창에서 취소하면 입금 확인 요청을 보내지 않는다', async () => {
    mockClaims([CLAIMING])
    let called = false
    server.use(
      http.post('*/api/v1/payout/claims/:claimId/confirm', () => {
        called = true
        return HttpResponse.json({ status: 200, message: 'ok', data: CLAIMING })
      }),
    )
    const user = userEvent.setup()
    renderPage()
    await screen.findByRole('row', { name: /RC-20261215-0002/ })

    await user.click(screen.getByRole('button', { name: '회수 완료' }))
    await user.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', { name: '취소' }),
    )

    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument())
    expect(called).toBe(false)
  })
})
