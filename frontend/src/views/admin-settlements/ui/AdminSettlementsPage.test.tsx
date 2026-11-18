import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

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
  it('지급 예정 정산에서 지급보류 걸기가 막혀 있고 전체 지급 완료 버튼은 없다', () => {
    search = ''
    renderPage()

    expect(screen.getByRole('button', { name: '지급보류 걸기' })).toBeDisabled()
    expect(screen.queryByRole('button', { name: /지급 완료로 바꾸기|전체 지급 완료/ })).toBeNull()
  })

  it('지급보류 칩에서는 지급보류 풀기가 막혀 있다', async () => {
    search = 'tab=지급보류'
    renderPage()

    expect(screen.getByRole('button', { name: '지급보류 풀기' })).toBeDisabled()
  })
})
