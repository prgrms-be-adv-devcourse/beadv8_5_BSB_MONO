import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getAuthStatus, setAccessToken } from '@/shared/api'
import { server } from '@/shared/testing'

import { WithdrawPage } from './WithdrawPages'

const push = vi.fn()
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push, back: vi.fn(), replace: vi.fn() }),
  usePathname: () => '/me/withdraw',
}))

const reloadTo = vi.hoisted(() => vi.fn())
vi.mock('@/features/auth/lib/login-redirect', async (importOriginal) => ({
  ...(await importOriginal<object>()),
  reloadTo,
}))

beforeEach(() => {
  push.mockClear()
  reloadTo.mockClear()
})

function remaining({ balance, orders }: { balance: number; orders: unknown[] }) {
  server.use(
    http.get('*/api/v1/cash/wallets/balance', () =>
      HttpResponse.json({ status: 200, message: '잔액을 조회했습니다.', data: { balance } }),
    ),
    http.get('*/api/v1/order/orders', () =>
      HttpResponse.json({
        status: 200,
        message: '주문 내역을 조회했습니다.',
        data: { orders, page: 0, size: 100, totalElements: orders.length, totalPages: 1 },
      }),
    ),
  )
}

function renderPage() {
  setAccessToken('token')
  render(
    <QueryClientProvider client={new QueryClient()}>
      <WithdrawPage />
    </QueryClientProvider>,
  )
}

// 남은 일을 모두 마친 상태(시안 M31-C)에서 안내를 확인하고 탈퇴하기를 누른다.
async function openConfirm() {
  remaining({ balance: 0, orders: [] })
  const user = userEvent.setup()
  renderPage()
  const checkbox = screen.getByRole('checkbox', { name: '안내를 모두 확인했어요' })
  await vi.waitFor(() => expect(checkbox).toBeEnabled())
  await user.click(checkbox)
  await user.click(screen.getByRole('button', { name: '탈퇴하기' }))
  return user
}

describe('WithdrawPage 최종 확인 창', () => {
  it('남은 예치금이 있으면 안내하고, 탈퇴하기가 꺼져 있어 창이 뜨지 않는다', async () => {
    remaining({ balance: 5000, orders: [] })
    renderPage()

    expect(await screen.findByText('남은 예치금 5,000원')).toBeInTheDocument()
    expect(screen.getByText('탈퇴하기 전에 남은 일이 있어요')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '탈퇴하기' })).toBeDisabled()
    expect(screen.getByRole('checkbox', { name: '안내를 모두 확인했어요' })).toHaveAttribute(
      'aria-disabled',
      'true',
    )
    expect(screen.queryByText('정말 탈퇴할까요?')).not.toBeInTheDocument()
  })

  it('끝나지 않은 참가권이 있으면 몇 장인지 안내하고 탈퇴하기를 막는다', async () => {
    // 목의 주문 목록에서 참가권이 되는 것은 결제한 주문 1개. 대회 날짜는 아직 지나지 않았다
    server.use(
      http.get('*/api/v1/product/races/:raceId', () =>
        HttpResponse.json({
          status: 200,
          message: '대회를 조회했습니다.',
          data: { id: 1, title: '용감한 쿠키RUN', raceDate: '2099-11-01' },
        }),
      ),
      http.get('*/api/v1/cash/wallets/balance', () =>
        HttpResponse.json({ status: 200, message: '잔액을 조회했습니다.', data: { balance: 0 } }),
      ),
    )
    renderPage()

    expect(await screen.findByText('끝나지 않은 참가권 1장')).toBeInTheDocument()
    expect(screen.getByText(/용감한 쿠키RUN\(11\/1\)을 취소하거나/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '탈퇴하기' })).toBeDisabled()
  })

  it('남은 일이 없으면 이제 탈퇴할 수 있다고 알린다', async () => {
    remaining({ balance: 0, orders: [] })
    renderPage()

    expect(await screen.findByText('이제 탈퇴할 수 있어요')).toBeInTheDocument()
  })

  it('탈퇴하기를 누르면 확인 창이 뜨고 아직 이동하지 않는다', async () => {
    await openConfirm()
    expect(await screen.findByText('정말 탈퇴할까요?')).toBeInTheDocument()
    expect(push).not.toHaveBeenCalled()
  })

  it('그대로 두기를 누르면 창이 닫히고 이동하지 않는다', async () => {
    const user = await openConfirm()
    await user.click(await screen.findByRole('button', { name: '그대로 두기' }))
    expect(screen.queryByText('정말 탈퇴할까요?')).not.toBeInTheDocument()
    expect(push).not.toHaveBeenCalled()
  })

  it('창에서 탈퇴하기를 누르면 문서를 새로 불러 완료 화면으로 간다', async () => {
    const user = await openConfirm()
    const dialog = await screen.findByRole('alertdialog')
    await user.click(within(dialog).getByRole('button', { name: '탈퇴하기' }))
    await vi.waitFor(() => expect(reloadTo).toHaveBeenCalledWith('/me/withdraw/done'))
  })

  it('탈퇴하면 완료 화면으로 떠날 때 이 브라우저가 로그아웃 상태가 된다', async () => {
    setAccessToken('token')
    expect(getAuthStatus()).toBe('authenticated')
    const user = await openConfirm()
    const dialog = await screen.findByRole('alertdialog')
    await user.click(within(dialog).getByRole('button', { name: '탈퇴하기' }))
    await vi.waitFor(() => expect(reloadTo).toHaveBeenCalled())
    window.dispatchEvent(new Event('pagehide'))
    expect(getAuthStatus()).not.toBe('authenticated')
  })
})

describe('WithdrawPage 탈퇴 실패', () => {
  async function confirmWithdraw(status: number, code: string) {
    server.use(
      http.delete('*/api/v1/member/members/me', () =>
        HttpResponse.json({ status, code, message: '서버 문구', data: null }, { status }),
      ),
    )
    setAccessToken('token')
    const user = await openConfirm()
    const dialog = await screen.findByRole('alertdialog')
    await user.click(within(dialog).getByRole('button', { name: '탈퇴하기' }))
  }

  it.each([
    [409, 'MEMBER_WITHDRAW_CASH_LEFT', '남은 예치금이 있어요'],
    [409, 'MEMBER_WITHDRAW_TICKET_LEFT', '끝나지 않은 참가권이 있어요'],
    [503, 'MEMBER_WITHDRAW_CHECK_FAILED', '확인하지 못했어요'],
    [404, 'MEMBER_NOT_FOUND', '지금은 탈퇴할 수 없어요'],
  ])('%s %s는 안내를 보여 주고 이동도 로그아웃도 하지 않는다', async (status, code, text) => {
    await confirmWithdraw(status, code)

    expect(await screen.findByRole('alert', { hidden: true })).toHaveTextContent(text)
    expect(screen.queryByText('서버 문구')).not.toBeInTheDocument()
    expect(push).not.toHaveBeenCalled()
    expect(getAuthStatus()).toBe('authenticated')
  })
})

describe('WithdrawPage 중복 요청', () => {
  it('요청 중에 탈퇴하기를 다시 눌러도 요청은 한 번만 간다', async () => {
    let calls = 0
    server.use(
      http.delete('*/api/v1/member/members/me', async () => {
        calls += 1
        await new Promise((resolve) => setTimeout(resolve, 100))
        return HttpResponse.json({ status: 200, message: '탈퇴했습니다.', data: null })
      }),
    )
    const user = await openConfirm()
    const dialog = await screen.findByRole('alertdialog')
    const button = within(dialog).getByRole('button', { name: '탈퇴하기' })
    await user.dblClick(button)

    await vi.waitFor(() => expect(reloadTo).toHaveBeenCalledWith('/me/withdraw/done'))
    expect(calls).toBe(1)
  })

  // 성공한 뒤 완료 화면이 뜨기까지 문서를 새로 불러오는 동안 토큰이 남아 있다
  it('성공한 뒤 완료 화면이 뜨기 전에 다시 눌러도 요청은 한 번만 간다', async () => {
    let calls = 0
    server.use(
      http.delete('*/api/v1/member/members/me', () => {
        calls += 1
        return HttpResponse.json({ status: 200, message: '탈퇴했습니다.', data: null })
      }),
    )
    const user = await openConfirm()
    const dialog = await screen.findByRole('alertdialog')
    const button = within(dialog).getByRole('button', { name: '탈퇴하기' })
    await user.click(button)
    await vi.waitFor(() => expect(reloadTo).toHaveBeenCalledWith('/me/withdraw/done'))

    await user.click(button)

    expect(calls).toBe(1)
    expect(screen.queryByRole('alert', { hidden: true })).not.toBeInTheDocument()
  })
})
