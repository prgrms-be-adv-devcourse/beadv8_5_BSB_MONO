import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { resetNavigationMock, server } from '@/shared/testing'

import { SellerKitsPage } from './SellerKitsPage'

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <SellerKitsPage />
    </QueryClientProvider>,
  )
}

/** 탭 이름 옆 건수 (건수를 받아 온 뒤에 잡힌다) */
async function tabCount(label: string, count: string) {
  return screen.findByRole('tab', { name: `${label}${count}` })
}

describe('SellerKitsPage', () => {
  it('키트 목록과 건수를 API로 보여 준다', async () => {
    renderPage()

    expect(await screen.findByText('OD-20261004-0101')).toBeInTheDocument()
    expect(screen.getByText('달리는곰')).toBeInTheDocument()
    expect(screen.getByText('티셔츠 M')).toBeInTheDocument()
    await tabCount('송장 입력 대기', '3')
    await tabCount('배송 중', '1')
    await tabCount('배송 완료', '1')
    expect(await screen.findByText('환불 기한 전 1건')).toBeInTheDocument()
  })

  it('송장 번호를 넣어 발송하면 배송 중이 되고 건수가 줄어든다', async () => {
    renderPage()
    await userEvent.click(await screen.findByRole('checkbox', { name: 'OD-20261004-0101 고르기' }))
    await userEvent.type(screen.getByLabelText('OD-20261004-0101 송장 번호'), '684912345678')
    await userEvent.click(screen.getByRole('button', { name: /건 배송 중으로 바꾸기/ }))

    expect(await screen.findByText('1건을 배송 중으로 바꿨어요')).toBeInTheDocument()
    await tabCount('송장 입력 대기', '2')
    await tabCount('배송 중', '2')
  })

  it('송장 번호 칸에 숫자가 아닌 글자를 치면 값은 그대로이고 안내가 보인다', async () => {
    renderPage()
    const input = await screen.findByLabelText('OD-20261004-0101 송장 번호')
    await userEvent.type(input, '12a')
    expect(input).toHaveValue('12')
    expect((await screen.findAllByText('숫자만 입력할 수 있어요')).length).toBeGreaterThan(0)
  })

  it('송장 입력 대기 표의 줄마다 택배사와 송장 번호 칸이 있다', async () => {
    renderPage()
    await screen.findByText('OD-20261004-0101')
    expect(screen.getByLabelText('OD-20261004-0101 송장 번호')).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'OD-20261004-0101 택배사' })).toBeInTheDocument()
  })

  it('고른 주문이 있으면 표 아래에 선택 막대가 뜨고 선택 풀기로 닫는다', async () => {
    renderPage()
    expect(screen.queryByRole('region', { name: '선택한 주문' })).not.toBeInTheDocument()
    await userEvent.click(await screen.findByRole('checkbox', { name: 'OD-20261004-0101 고르기' }))

    const bar = screen.getByRole('region', { name: '선택한 주문' })
    expect(bar).toHaveTextContent('1건 선택')
    await userEvent.click(screen.getByRole('button', { name: '선택 풀기' }))
    expect(screen.queryByRole('region', { name: '선택한 주문' })).not.toBeInTheDocument()
  })

  it('주문번호나 이름으로 찾으면 맞는 줄만 남긴다', async () => {
    renderPage()
    await screen.findByText('OD-20261004-0101')
    await userEvent.type(screen.getByLabelText('주문번호 · 이름으로 찾기'), '달리는곰')

    expect(screen.getByText('OD-20261004-0101')).toBeInTheDocument()
    expect(screen.queryByText('OD-20261005-0125')).not.toBeInTheDocument()
  })

  it('송장 번호가 없으면 발송 버튼이 닫혀 있다', async () => {
    renderPage()
    await userEvent.click(await screen.findByRole('checkbox', { name: 'OD-20261004-0101 고르기' }))

    expect(screen.getByRole('button', { name: /건 배송 중으로 바꾸기/ })).toBeDisabled()
  })

  it('배송 중인 건을 배송 완료로 바꾼다', async () => {
    renderPage()
    await userEvent.click(await screen.findByRole('tab', { name: /^배송 중/ }))
    await userEvent.click(await screen.findByRole('checkbox', { name: 'OD-20261003-0093 고르기' }))
    await userEvent.click(screen.getByRole('button', { name: /건 배송 완료로 바꾸기/ }))

    expect(await screen.findByText('1건을 배송 완료로 바꿨어요')).toBeInTheDocument()
    await tabCount('배송 완료', '2')
    await tabCount('배송 중', '0')
  })

  it('주소에 ?status=SHIPPING으로 바로 들어오면 바꿀 단계가 배송 완료로 맞는다', async () => {
    resetNavigationMock('status=SHIPPING')
    renderPage()

    await userEvent.click(await screen.findByRole('checkbox', { name: 'OD-20261003-0093 고르기' }))

    expect(await screen.findByRole('button', { name: /건 배송 완료로 바꾸기/ })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /건 배송 중으로 바꾸기/ })).not.toBeInTheDocument()
  })

  it('환불 기한 전이라 서버가 거절하면 서버 문구를 보여 주고 다른 건은 그대로 처리한다', async () => {
    renderPage()
    await userEvent.click(await screen.findByRole('checkbox', { name: 'OD-20261004-0101 고르기' }))
    await userEvent.click(screen.getByRole('checkbox', { name: 'OD-20261005-0125 고르기' }))
    // 송장 번호는 줄 안에서 주문마다 넣는다
    await userEvent.type(screen.getByLabelText('OD-20261004-0101 송장 번호'), '1111')
    await userEvent.type(screen.getByLabelText('OD-20261005-0125 송장 번호'), '2222')
    await userEvent.click(screen.getByRole('button', { name: /건 배송 중으로 바꾸기/ }))

    expect(await screen.findByText('1건은 바꾸지 못했어요')).toBeInTheDocument()
    expect(screen.getByText(/OD-20261005-0125: 아직 환불할 수 있는 참가권/)).toBeInTheDocument()
    expect(screen.getByText('1건을 배송 중으로 바꿨어요')).toBeInTheDocument()
    await tabCount('배송 중', '2')
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

    expect(await screen.findByText('송장 입력 대기 키트가 없어요')).toBeInTheDocument()
  })

  it('여러 건을 고르면 줄마다 송장 번호가 채워져야 보낼 수 있다', async () => {
    renderPage()
    await userEvent.click(await screen.findByRole('checkbox', { name: 'OD-20261004-0101 고르기' }))
    await userEvent.click(screen.getByRole('checkbox', { name: 'OD-20261004-0117 고르기' }))

    await userEvent.type(screen.getByLabelText('OD-20261004-0101 송장 번호'), '111')
    expect(screen.getByText('고른 주문마다 송장 번호를 넣어 주세요.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /건 배송 중으로 바꾸기/ })).toBeDisabled()

    await userEvent.type(screen.getByLabelText('OD-20261004-0117 송장 번호'), '  222  ')
    expect(screen.getByRole('button', { name: /건 배송 중으로 바꾸기/ })).toBeEnabled()
  })

  it('여러 건을 보내면 줄마다 택배사와 송장 번호가 그 주문에 붙고, 앞뒤 공백은 빼고 보낸다', async () => {
    type Body = { carrier?: string; trackingNumber?: string }
    const sent: Record<string, Body> = {}
    server.use(
      http.post('*/api/v1/order/seller/kits/:orderItemId/shipment', async ({ params, request }) => {
        sent[String(params.orderItemId)] = (await request.json()) as Body
        return HttpResponse.json({ status: 200, message: 'ok', data: {} })
      }),
    )
    renderPage()
    await userEvent.click(await screen.findByRole('checkbox', { name: 'OD-20261004-0101 고르기' }))
    await userEvent.click(screen.getByRole('checkbox', { name: 'OD-20261004-0117 고르기' }))
    await userEvent.type(screen.getByLabelText('OD-20261004-0101 송장 번호'), '  1111  ')
    await userEvent.type(screen.getByLabelText('OD-20261004-0117 송장 번호'), '  2222  ')
    await userEvent.click(screen.getByRole('button', { name: /건 배송 중으로 바꾸기/ }))

    expect(await screen.findByText('2건을 배송 중으로 바꿨어요')).toBeInTheDocument()
    expect(sent).toEqual({
      '101': { carrier: 'CJ대한통운', trackingNumber: '1111' },
      '117': { carrier: 'CJ대한통운', trackingNumber: '2222' },
    })
  })
})
