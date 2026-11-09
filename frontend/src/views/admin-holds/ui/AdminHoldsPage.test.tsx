import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'

import { payoutMock, server } from '@/shared/testing'
import { ToastProvider } from '@/shared/ui'

import { AdminHoldsPage } from './AdminHoldsPage'

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <AdminHoldsPage />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

describe('AdminHoldsPage', () => {
  it('예시 행의 보류 해제 버튼은 막혀 있다', () => {
    renderPage()

    expect(screen.getByRole('button', { name: '보류 해제' })).toBeDisabled()
  })

  it('정산 내역 번호와 사유로 보류를 건다', async () => {
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
