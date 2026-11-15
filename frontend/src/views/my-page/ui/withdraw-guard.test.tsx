import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, expect, it, vi } from 'vitest'

import { ProtectedScreenGuard } from '@/features/auth'
import { setAccessToken } from '@/shared/api'
import { server } from '@/shared/testing'

import { WithdrawPage } from './WithdrawPages'

const replace = vi.fn()
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), replace }),
  usePathname: () => '/me/withdraw',
}))
const reloadTo = vi.hoisted(() => vi.fn())
vi.mock('@/features/auth/lib/login-redirect', async (importOriginal) => ({
  ...(await importOriginal<object>()),
  reloadTo,
}))

beforeEach(() => {
  replace.mockClear()
  reloadTo.mockClear()
})

// 실제 화면처럼 (shop) 레이아웃의 로그인 확인 장치 안에서 탈퇴한다.
// 탈퇴 직후 토큰을 비우면 장치가 탈퇴 화면을 내리고 로그인 화면으로 보내 완료 화면에 가지 못했다.
it('로그인 확인 장치 안에서 탈퇴해도 로그인 화면이 아니라 탈퇴 완료 화면으로 간다', async () => {
  server.use(
    // 크루장인 크루가 없는 회원 (목의 기본 크루 목록에는 크루장인 크루가 있다)
    http.get('*/api/v1/member/crews/me', () =>
      HttpResponse.json({ status: 200, message: '내 크루', data: [] }),
    ),
    http.get('*/api/v1/cash/wallets/balance', () =>
      HttpResponse.json({ status: 200, message: '잔액을 조회했습니다.', data: { balance: 0 } }),
    ),
    http.get('*/api/v1/order/orders', () =>
      HttpResponse.json({
        status: 200,
        message: '주문 내역을 조회했습니다.',
        data: { orders: [], page: 0, size: 100, totalElements: 0, totalPages: 0 },
      }),
    ),
  )
  setAccessToken('token')
  const user = userEvent.setup()
  render(
    <QueryClientProvider client={new QueryClient()}>
      <ProtectedScreenGuard>
        <WithdrawPage />
      </ProtectedScreenGuard>
    </QueryClientProvider>,
  )
  const checkbox = await screen.findByRole('checkbox', { name: '안내를 모두 확인했어요' })
  await vi.waitFor(() => expect(checkbox).toBeEnabled())
  await user.click(checkbox)
  await user.click(screen.getByRole('button', { name: '탈퇴하기' }))
  const dialog = await screen.findByRole('alertdialog')
  await user.click(within(dialog).getByRole('button', { name: '탈퇴하기' }))

  await vi.waitFor(() => expect(reloadTo).toHaveBeenCalledWith('/me/withdraw/done'))
  expect(replace).not.toHaveBeenCalled()
  expect(screen.queryByRole('progressbar', { name: '로그인 확인 중' })).not.toBeInTheDocument()
})
