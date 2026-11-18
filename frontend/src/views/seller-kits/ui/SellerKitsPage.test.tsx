import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { server } from '@/shared/testing'

import { SellerKitsPage } from './SellerKitsPage'

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <SellerKitsPage />
    </QueryClientProvider>,
  )
}

/** 건수 타일 (건수를 받아 온 뒤에 잡힌다) */
async function tile(label: string) {
  const group = screen.getByRole('group', { name: '키트 배송 건수' })
  return (await within(group).findByText(label)).parentElement as HTMLElement
}

describe('SellerKitsPage', () => {
  it('키트 목록과 건수를 API로 보여 준다', async () => {
    renderPage()

    expect(await screen.findByText('OD-20261004-0101')).toBeInTheDocument()
    expect(screen.getByText('달리는곰')).toBeInTheDocument()
    expect(screen.getByText('10K · 티셔츠 M')).toBeInTheDocument()
    expect(screen.getByText('서울 마포구')).toBeInTheDocument()
    expect(screen.getByText('주문 5건')).toBeInTheDocument()
    expect(await within(await tile('배송 준비')).findByText('3')).toBeInTheDocument()
    expect(within(await tile('배송 중')).getByText('1')).toBeInTheDocument()
    expect(within(await tile('배송 완료')).getByText('1')).toBeInTheDocument()
    expect(within(await tile('환불 기한 전')).getByText('1')).toBeInTheDocument()
  })

  it('송장 번호를 넣어 발송하면 배송 중이 되고 건수가 줄어든다', async () => {
    renderPage()
    await userEvent.click(await screen.findByRole('checkbox', { name: 'OD-20261004-0101 고르기' }))
    await userEvent.type(screen.getByLabelText('송장 번호'), '684912345678')
    await userEvent.click(screen.getByRole('button', { name: '배송 중으로 바꾸기' }))

    expect(await screen.findByText('1건을 배송 중으로 바꿨어요')).toBeInTheDocument()
    expect(await within(await tile('배송 준비')).findByText('2')).toBeInTheDocument()
    expect(within(await tile('배송 중')).getByText('2')).toBeInTheDocument()
  })

  it('송장 번호가 없으면 발송 버튼이 닫혀 있다', async () => {
    renderPage()
    await userEvent.click(await screen.findByRole('checkbox', { name: 'OD-20261004-0101 고르기' }))

    expect(screen.getByRole('button', { name: '배송 중으로 바꾸기' })).toBeDisabled()
  })

  it('배송 중인 건을 배송 완료로 바꾼다', async () => {
    renderPage()
    await userEvent.click(await screen.findByRole('checkbox', { name: 'OD-20261003-0093 고르기' }))
    await userEvent.click(screen.getByRole('button', { name: '배송 완료' }))
    await userEvent.click(screen.getByRole('button', { name: '배송 완료로 바꾸기' }))

    expect(await screen.findByText('1건을 배송 완료로 바꿨어요')).toBeInTheDocument()
    expect(await within(await tile('배송 완료')).findByText('2')).toBeInTheDocument()
    expect(within(await tile('배송 중')).getByText('0')).toBeInTheDocument()
  })

  it('상태가 맞지 않는 건을 고르면 바꿀 수 없다고 알린다', async () => {
    renderPage()
    await userEvent.click(await screen.findByRole('checkbox', { name: 'OD-20261003-0093 고르기' }))

    expect(
      screen.getByText('배송 준비 상태인 주문만 배송 중으로 바꿀 수 있어요.'),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '배송 중으로 바꾸기' })).toBeDisabled()
  })

  it('환불 기한 전이라 서버가 거절하면 서버 문구를 보여 주고 다른 건은 그대로 처리한다', async () => {
    renderPage()
    await userEvent.click(await screen.findByRole('checkbox', { name: 'OD-20261004-0101 고르기' }))
    await userEvent.click(screen.getByRole('checkbox', { name: 'OD-20261005-0125 고르기' }))
    await userEvent.type(screen.getByLabelText('송장 번호'), '1111{enter}2222')
    // 줄은 표 순서대로 주문에 붙으므로, 보내기 전에 짝을 보여 준다
    const pairs = within(screen.getByRole('list', { name: '발송 미리보기' })).getAllByRole(
      'listitem',
    )
    expect(pairs[0]).toHaveTextContent('OD-20261004-0101→1111')
    expect(pairs[1]).toHaveTextContent('OD-20261005-0125→2222')
    await userEvent.click(screen.getByRole('button', { name: '배송 중으로 바꾸기' }))

    expect(await screen.findByText('1건은 바꾸지 못했어요')).toBeInTheDocument()
    expect(screen.getByText(/OD-20261005-0125: 아직 환불할 수 있는 참가권/)).toBeInTheDocument()
    expect(screen.getByText('1건을 배송 중으로 바꿨어요')).toBeInTheDocument()
    expect(await within(await tile('배송 중')).findByText('2')).toBeInTheDocument()
  })

  it('목록 조회가 실패하면 서버 문구를 보여 준다', async () => {
    server.use(
      http.get('*/api/v1/order/seller/kits', () =>
        HttpResponse.json(
          {
            status: 403,
            code: 'ORDER_SELLER_FORBIDDEN',
            message: '승인된 판매자만 쓸 수 있습니다.',
            data: null,
          },
          { status: 403 },
        ),
      ),
    )
    renderPage()

    expect(await screen.findByText('키트 주문을 불러오지 못했어요')).toBeInTheDocument()
    expect(screen.getByText('승인된 판매자만 쓸 수 있습니다.')).toBeInTheDocument()
  })

  it('키트 주문이 없으면 빈 화면을 보여 준다', async () => {
    server.use(
      http.get('*/api/v1/order/seller/kits', () =>
        HttpResponse.json({
          status: 200,
          message: '키트 주문 목록을 조회했습니다.',
          data: { kits: [], page: 0, size: 100, totalElements: 0, totalPages: 0 },
        }),
      ),
    )
    renderPage()

    expect(await screen.findByText('키트 주문이 없어요')).toBeInTheDocument()
  })

  it('여러 건을 고르면 송장 번호 줄 수가 건수와 같아야 보낼 수 있고, 빈 줄은 세지 않는다', async () => {
    renderPage()
    await userEvent.click(await screen.findByRole('checkbox', { name: 'OD-20261004-0101 고르기' }))
    await userEvent.click(screen.getByRole('checkbox', { name: 'OD-20261004-0117 고르기' }))

    const box = screen.getByLabelText('송장 번호')
    await userEvent.type(box, '111')
    expect(screen.getByText('송장 번호를 선택한 2건만큼 줄마다 넣어 주세요.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '배송 중으로 바꾸기' })).toBeDisabled()

    await userEvent.type(box, '{Enter}{Enter}  222  ')
    expect(screen.getByRole('button', { name: '배송 중으로 바꾸기' })).toBeEnabled()
  })
})
