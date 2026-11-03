import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, it, vi } from 'vitest'

import { ProtectedScreenGuard } from '@/features/auth'
import { setAccessToken } from '@/shared/api'

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
  setAccessToken('token')
  const user = userEvent.setup()
  render(
    <QueryClientProvider client={new QueryClient()}>
      <ProtectedScreenGuard>
        <WithdrawPage blocked={false} />
      </ProtectedScreenGuard>
    </QueryClientProvider>,
  )
  await user.click(screen.getByRole('checkbox', { name: '안내를 모두 확인했어요' }))
  await user.click(screen.getByRole('button', { name: '탈퇴하기' }))
  const dialog = await screen.findByRole('alertdialog')
  await user.click(within(dialog).getByRole('button', { name: '탈퇴하기' }))

  await vi.waitFor(() => expect(reloadTo).toHaveBeenCalledWith('/me/withdraw/done'))
  expect(replace).not.toHaveBeenCalled()
  expect(screen.queryByRole('progressbar', { name: '로그인 확인 중' })).not.toBeInTheDocument()
})
