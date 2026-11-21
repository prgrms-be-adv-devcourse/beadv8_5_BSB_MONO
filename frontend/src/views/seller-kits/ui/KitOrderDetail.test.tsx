import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { resetNavigationMock, server } from '@/shared/testing'
import { ToastProvider } from '@/shared/ui'

import { SellerKitsPage } from './SellerKitsPage'

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <SellerKitsPage />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

/** 표의 줄을 눌러 주문 상세 창을 연다 */
async function openDetail(user: ReturnType<typeof userEvent.setup>, orderNo: string) {
  await user.click(await screen.findByRole('button', { name: `${orderNo} 주문 상세` }))
  return screen.findByRole('dialog', { name: orderNo })
}

describe('키트 주문 상세 창', () => {
  it('송장 입력 대기 줄을 누르면 주문 · 받는 사람 · 배송 입력이 있는 창이 열린다', async () => {
    const user = userEvent.setup()
    renderPage()
    const panel = await openDetail(user, 'OD-20261004-0101')

    expect(within(panel).getByText('송장 입력 대기')).toBeInTheDocument()
    expect(within(panel).getByText('10K')).toBeInTheDocument()
    expect(within(panel).getByText('티셔츠 M')).toBeInTheDocument()
    expect(within(panel).getByText('김러너')).toBeInTheDocument()
    expect(within(panel).getByText('010-1234-5678')).toBeInTheDocument()
    expect(within(panel).getByText('서울 마포구 월드컵로 00, 101동 1001호')).toBeInTheDocument()
    expect(within(panel).getByRole('combobox', { name: '택배사' })).toBeInTheDocument()
    expect(within(panel).getByPlaceholderText('숫자만 적어 주세요')).toBeInTheDocument()
    expect(within(panel).getByRole('button', { name: '배송 중으로 바꾸기' })).toBeInTheDocument()
  })

  it('줄 안의 고르기 칸을 눌러도 창은 열리지 않는다', async () => {
    const user = userEvent.setup()
    renderPage()
    await user.click(await screen.findByRole('checkbox', { name: 'OD-20261004-0101 고르기' }))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('송장 번호가 숫자 10~12자리가 아니면 칸 아래에 알리고 보내지 않는다', async () => {
    let sent = 0
    server.use(
      http.post('*/api/v1/order/seller/kits/:orderItemId/shipment', () => {
        sent += 1
        return HttpResponse.json({ status: 200, message: 'ok', data: {} })
      }),
    )
    const user = userEvent.setup()
    renderPage()
    const panel = await openDetail(user, 'OD-20261004-0101')
    await user.click(within(panel).getByRole('combobox', { name: '택배사' }))
    await user.click(await screen.findByRole('option', { name: 'CJ대한통운' }))
    await user.type(within(panel).getByLabelText('송장 번호'), '6849-12')
    await user.click(within(panel).getByRole('button', { name: '배송 중으로 바꾸기' }))

    expect(await within(panel).findByText('송장 번호는 숫자 10~12자리예요')).toBeInTheDocument()
    expect(sent).toBe(0)
  })

  it('택배사와 송장 번호를 넣어 보내면 창이 닫히고 배송 중으로 옮겨진다', async () => {
    const user = userEvent.setup()
    renderPage()
    const panel = await openDetail(user, 'OD-20261004-0101')
    await user.click(within(panel).getByRole('combobox', { name: '택배사' }))
    await user.click(await screen.findByRole('option', { name: '롯데택배' }))
    await user.type(within(panel).getByLabelText('송장 번호'), '684912013344')
    await user.click(within(panel).getByRole('button', { name: '배송 중으로 바꾸기' }))

    expect(await screen.findByText('1건을 배송 중으로 바꿨어요')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(await screen.findByRole('tab', { name: '배송 중2' })).toBeInTheDocument()
  })

  it('서버가 발송을 거절하면 창 안에 서버 문구를 보여 주고 창을 닫지 않는다', async () => {
    const user = userEvent.setup()
    renderPage()
    const panel = await openDetail(user, 'OD-20261005-0125')
    await user.click(within(panel).getByRole('combobox', { name: '택배사' }))
    await user.click(await screen.findByRole('option', { name: 'CJ대한통운' }))
    await user.type(within(panel).getByLabelText('송장 번호'), '684912013344')
    await user.click(within(panel).getByRole('button', { name: '배송 중으로 바꾸기' }))

    expect(await within(panel).findByText(/아직 환불할 수 있는 참가권/)).toBeInTheDocument()
    expect(screen.getByRole('dialog', { name: 'OD-20261005-0125' })).toBeInTheDocument()
  })

  it('배송 중 창은 택배사 · 네 자리씩 띄운 송장 번호 · 발송일을 보여 준다', async () => {
    resetNavigationMock('status=SHIPPING')
    const user = userEvent.setup()
    renderPage()
    const panel = await openDetail(user, 'OD-20261003-0093')

    expect(within(panel).getByText('배송 중')).toBeInTheDocument()
    expect(within(panel).getByText('CJ대한통운')).toBeInTheDocument()
    expect(within(panel).getByRole('button', { name: '6849 1234 5678' })).toBeInTheDocument()
    expect(within(panel).getByText('10/14 (수)')).toBeInTheDocument()
    expect(within(panel).getByRole('button', { name: '송장 수정하기' })).toBeInTheDocument()
    expect(within(panel).getByRole('button', { name: '배송 완료로 바꾸기' })).toBeInTheDocument()
  })

  it('송장 번호를 누르면 숫자만 복사하고 알림을 띄운다', async () => {
    resetNavigationMock('status=SHIPPING')
    const user = userEvent.setup()
    renderPage()
    const panel = await openDetail(user, 'OD-20261003-0093')
    await user.click(within(panel).getByRole('button', { name: '6849 1234 5678' }))

    expect(await screen.findByText('운송장 번호를 복사했어요')).toBeInTheDocument()
    expect(await navigator.clipboard.readText()).toBe('684912345678')
  })

  it('송장 수정하기로 택배사 · 송장 번호를 고쳐 저장한다', async () => {
    let body: unknown
    server.use(
      http.put('*/api/v1/order/seller/kits/:orderItemId/shipment', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({ status: 200, message: '송장 번호를 고쳤습니다.', data: {} })
      }),
    )
    resetNavigationMock('status=SHIPPING')
    const user = userEvent.setup()
    renderPage()
    const panel = await openDetail(user, 'OD-20261003-0093')
    await user.click(within(panel).getByRole('button', { name: '송장 수정하기' }))

    const input = within(panel).getByLabelText('송장 번호')
    expect(input).toHaveValue('684912345678')
    await user.clear(input)
    await user.type(input, '684912013344')
    await user.click(within(panel).getByRole('button', { name: '저장하기' }))

    expect(body).toEqual({ carrier: 'CJ대한통운', trackingNumber: '684912013344' })
    expect(await within(panel).findByRole('button', { name: '송장 수정하기' })).toBeInTheDocument()
  })

  it('송장 수정 중 취소를 누르면 보던 화면으로 돌아간다', async () => {
    resetNavigationMock('status=SHIPPING')
    const user = userEvent.setup()
    renderPage()
    const panel = await openDetail(user, 'OD-20261003-0093')
    await user.click(within(panel).getByRole('button', { name: '송장 수정하기' }))
    await user.click(within(panel).getByRole('button', { name: '취소' }))

    expect(within(panel).getByRole('button', { name: '6849 1234 5678' })).toBeInTheDocument()
  })

  it('배송 중 창에서 배송 완료로 바꾸면 창이 닫힌다', async () => {
    resetNavigationMock('status=SHIPPING')
    const user = userEvent.setup()
    renderPage()
    const panel = await openDetail(user, 'OD-20261003-0093')
    await user.click(within(panel).getByRole('button', { name: '배송 완료로 바꾸기' }))

    expect(await screen.findByText('1건을 배송 완료로 바꿨어요')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('배송 완료 창은 받은 날을 보여 주고 바꾸는 버튼이 없다', async () => {
    resetNavigationMock('status=DELIVERED')
    const user = userEvent.setup()
    renderPage()
    const panel = await openDetail(user, 'OD-20261002-0071')

    expect(within(panel).getByText('배송 완료')).toBeInTheDocument()
    expect(within(panel).getByText('받은 날')).toBeInTheDocument()
    expect(within(panel).getByText('10/12 (월)')).toBeInTheDocument()
    expect(within(panel).queryByRole('button', { name: /바꾸기|수정하기/ })).not.toBeInTheDocument()
  })

  it('취소된 주문은 머리에 빨간 「취소」를 보여 주고 바꾸는 버튼이 없다', async () => {
    resetNavigationMock('status=DELIVERED')
    const user = userEvent.setup()
    renderPage()
    const panel = await openDetail(user, 'OD-20261001-0064')

    expect(within(panel).getByText('취소')).toHaveClass('text-text-danger')
    expect(within(panel).queryByRole('button', { name: /바꾸기|수정하기/ })).not.toBeInTheDocument()
  })

  it('닫기 버튼으로 창을 닫는다', async () => {
    const user = userEvent.setup()
    renderPage()
    const panel = await openDetail(user, 'OD-20261004-0101')
    await user.click(within(panel).getByRole('button', { name: '닫기' }))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})
