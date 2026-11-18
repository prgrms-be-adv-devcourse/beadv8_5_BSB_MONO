import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'

import { payoutMock, server } from '@/shared/testing'
import { ToastProvider } from '@/shared/ui'

import { AdminSettlementsPage } from './AdminSettlementsPage'

const replace = vi.fn()
let search = ''
vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace, push: replace }),
  usePathname: () => '/admin/settlements',
  useSearchParams: () => new URLSearchParams(search),
}))

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <AdminSettlementsPage />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

describe('AdminSettlementsPage', () => {
  it('가짜 정산 행과 칩 건수를 보여 주지 않고 빈 상태를 보인다', () => {
    search = ''
    renderPage()

    expect(screen.queryByText(/ST-2026/)).toBeNull()
    expect(screen.getByRole('button', { name: '지급 예정' })).toBeInTheDocument()
    expect(screen.getByText('정산 목록을 아직 불러올 수 없어요')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /전체 지급 완료|지급 완료로 바꾸기/ })).toBeNull()
  })

  describe('지급보류 칩', () => {
    it('정산 내역 번호와 사유로 보류를 건다', async () => {
      search = 'tab=지급보류'
      let request: { path: string; body: unknown } | null = null
      server.use(
        http.post('*/api/v1/payout/payouts/:payoutId/holds', async ({ request: req }) => {
          request = { path: new URL(req.url).pathname, body: await req.json() }
          return HttpResponse.json(payoutMock.getHoldResponseMock(), { status: 201 })
        }),
      )
      const user = userEvent.setup()
      renderPage()

      await user.type(screen.getByRole('textbox', { name: '정산 내역 번호' }), '12')
      await user.click(screen.getByRole('combobox', { name: '보류 사유' }))
      await user.click(await screen.findByRole('option', { name: '정산 계좌 미등록' }))
      await user.click(screen.getByRole('button', { name: '보류 걸기' }))

      expect(await screen.findByText('지급보류를 걸었어요.')).toBeInTheDocument()
      expect(request).toEqual({
        path: '/api/v1/payout/payouts/12/holds',
        body: { reasonType: '정산_계좌_미등록' },
      })
    })

    it('번호가 숫자가 아니면 API를 부르지 않는다', async () => {
      search = 'tab=지급보류'
      const called = vi.fn()
      server.use(
        http.post('*/api/v1/payout/payouts/:payoutId/holds', () => {
          called()
          return HttpResponse.json(payoutMock.getHoldResponseMock(), { status: 201 })
        }),
      )
      const user = userEvent.setup()
      renderPage()

      await user.type(screen.getByRole('textbox', { name: '정산 내역 번호' }), 'ST-20261120-3F02')
      await user.click(screen.getByRole('button', { name: '보류 걸기' }))

      expect(await screen.findByText('정산 내역 번호를 숫자로 넣어 주세요.')).toBeInTheDocument()
      expect(called).not.toHaveBeenCalled()
    })
  })
})
