import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'

import { currentSearch, resetNavigationMock } from '@/shared/testing'
import { ToastProvider } from '@/shared/ui'

import { AdminRacesPage } from './AdminRacesPage'

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <AdminRacesPage />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

describe('AdminRacesPage 거르기', () => {
  it('거르기 값은 주소(?filter=)에 담고, 선택은 누름 상태로 알린다', async () => {
    const user = userEvent.setup()
    renderPage()

    const all = await screen.findByRole('button', { name: /^전체/ })
    expect(all).toHaveAttribute('aria-pressed', 'true')
    expect(screen.queryByRole('tab')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /^판매 중/ }))
    expect(currentSearch()).toContain('filter=ON_SALE')
    expect(screen.getByRole('button', { name: /^판매 중/ })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: /^전체/ })).toHaveAttribute('aria-pressed', 'false')
  })

  it('주소의 filter 값으로 처음부터 걸러 보여 준다', async () => {
    resetNavigationMock('filter=CLOSED')
    renderPage()

    expect(await screen.findByRole('button', { name: /^종료/ })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
  })
})

describe('AdminRacesPage 상세페이지 바로가기', () => {
  it('고른 대회의 구매자용 상세 페이지를 새 탭으로 연다', async () => {
    const user = userEvent.setup()
    renderPage()

    const link = await screen.findByRole('link', { name: '상세페이지' })
    expect(link).toHaveAttribute('href', expect.stringMatching(/^\/races\/\d+$/))
    expect(link).toHaveAttribute('target', '_blank')
    expect(link).toHaveAttribute('rel', 'noopener noreferrer')

    const before = link.getAttribute('href')
    const rows = screen.getAllByRole('row')
    await user.click(rows[rows.length - 1])
    expect(screen.getByRole('link', { name: '상세페이지' }).getAttribute('href')).not.toBe(before)
  })
})
