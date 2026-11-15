import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { server } from '@/shared/testing'
import { ToastProvider } from '@/shared/ui'

import { InquiryComposePage } from './InquiryComposePage'
import { InquiryDetailPage } from './InquiryDetailPage'
import { MyInquiriesPage } from './MyInquiriesPage'

const replace = vi.fn()
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), replace, refresh: vi.fn() }),
  usePathname: () => '/me/inquiries',
}))

function renderUi(ui: React.ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>{ui}</ToastProvider>
    </QueryClientProvider>,
  )
}

const ok = (data: unknown, status = 200) =>
  HttpResponse.json({ status, message: '성공', data }, { status })

const inquiry = (over: Record<string, unknown> = {}) => ({
  id: 1,
  raceId: 1,
  orderId: 501,
  type: 'RACE_VENUE',
  assignee: 'SELLER',
  title: '유모차를 끌고 뛰어도 되나요?',
  body: '출발 그룹을 따로 정해야 하나요?',
  status: 'WAITING',
  answer: null,
  answeredAt: null,
  createDate: '2026-11-10T09:12:00',
  memberNickname: null,
  order: null,
  ...over,
})
const answered = inquiry({
  id: 3,
  type: 'APPLY_PAYMENT',
  assignee: 'ADMIN',
  title: '취소하면 예치금은 언제 돌아오나요?',
  status: 'ANSWERED',
  answer: '취소하면 바로 예치금으로 돌려드려요.',
  answeredAt: '2026-10-28T11:05:00',
})
const page = (inquiries: unknown[]) => ({
  inquiries,
  page: 0,
  size: 20,
  totalElements: inquiries.length,
  totalPages: 1,
})

beforeEach(() => {
  replace.mockReset()
  server.use(
    http.get('*/api/v1/inquiry/inquiries/me', () => ok(page([inquiry(), answered]))),
    http.get('*/api/v1/inquiry/inquiries/:id', ({ params }) =>
      ok(params.id === '3' ? answered : inquiry()),
    ),
  )
})

describe('내 문의 목록', () => {
  it('문의 줄과 상태 칩을 보여 주고, 칩으로 거른다', async () => {
    const user = userEvent.setup()
    renderUi(<MyInquiriesPage />)

    expect(await screen.findByText('유모차를 끌고 뛰어도 되나요?')).toBeInTheDocument()
    expect(screen.getByText('취소하면 예치금은 언제 돌아오나요?')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '답변 완료' }))
    expect(screen.queryByText('유모차를 끌고 뛰어도 되나요?')).not.toBeInTheDocument()
    expect(screen.getByText('취소하면 예치금은 언제 돌아오나요?')).toBeInTheDocument()
  })

  it('다음 쪽이 있으면 더 보기로 이어 붙이고, 마지막 쪽이면 버튼이 사라진다', async () => {
    const user = userEvent.setup()
    const requested: (string | null)[] = []
    server.use(
      http.get('*/api/v1/inquiry/inquiries/me', ({ request }) => {
        const pageNo = new URL(request.url).searchParams.get('page')
        requested.push(pageNo)
        return pageNo === '0'
          ? ok({ ...page([inquiry()]), totalPages: 2, totalElements: 2 })
          : ok({ ...page([answered]), page: 1, totalPages: 2, totalElements: 2 })
      }),
    )
    renderUi(<MyInquiriesPage />)

    expect(await screen.findByText('유모차를 끌고 뛰어도 되나요?')).toBeInTheDocument()
    expect(screen.queryByText('취소하면 예치금은 언제 돌아오나요?')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '더 보기' }))
    expect(await screen.findByText('취소하면 예치금은 언제 돌아오나요?')).toBeInTheDocument()
    expect(screen.getByText('유모차를 끌고 뛰어도 되나요?')).toBeInTheDocument()
    expect(requested).toEqual(['0', '1'])
    expect(screen.queryByRole('button', { name: '더 보기' })).not.toBeInTheDocument()
  })

  it('보낸 문의가 없으면 빈 상태를 보여 준다', async () => {
    server.use(
      http.get('*/api/v1/inquiry/inquiries/me', () =>
        HttpResponse.json({
          status: 200,
          message: '문의 목록을 조회했습니다.',
          data: { inquiries: [], page: 0, size: 20, totalElements: 0, totalPages: 0 },
        }),
      ),
    )
    renderUi(<MyInquiriesPage />)

    expect(await screen.findByText('보낸 문의가 없어요')).toBeInTheDocument()
  })

  it('불러오지 못하면 다시 시도하기를 보여 준다', async () => {
    server.use(
      http.get('*/api/v1/inquiry/inquiries/me', () =>
        HttpResponse.json({ status: 500, message: '오류', data: null }, { status: 500 }),
      ),
    )
    renderUi(<MyInquiriesPage />)

    expect(await screen.findByRole('button', { name: '다시 시도하기' })).toBeInTheDocument()
  })

  it('보낸 직후에는 알림을 한 번 띄운다', async () => {
    renderUi(<MyInquiriesPage notice="문의를 보냈어요" />)

    expect(await screen.findByText('문의를 보냈어요')).toBeInTheDocument()
  })
})

describe('문의 상세', () => {
  it('답변 전에는 고치기 · 지우기가 있고, 지우면 목록으로 간다', async () => {
    const user = userEvent.setup()
    renderUi(<InquiryDetailPage inquiryId={1} />)

    expect(await screen.findByRole('link', { name: '고치기' })).toHaveAttribute(
      'href',
      '/me/inquiries/1/edit',
    )
    // 답변 전 배지는 「답변 기다림」(목록은 「답변 대기」)
    expect(screen.getByText('답변 기다림')).toBeInTheDocument()
    server.use(http.delete('*/api/v1/inquiry/inquiries/1', () => ok(null)))
    await user.click(screen.getByRole('button', { name: '지우기' }))
    expect(await screen.findByText('지운 문의는 되돌릴 수 없어요.')).toBeInTheDocument()
    const buttons = await screen.findAllByRole('button', { name: '지우기' })
    await user.click(buttons[buttons.length - 1])

    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/me/inquiries?deleted=1'))
  })

  it('답변이 달렸으면 답변을 보여 주고 고치기 · 지우기를 숨긴다', async () => {
    renderUi(<InquiryDetailPage inquiryId={3} />)

    expect(await screen.findByText('크루런 답변')).toBeInTheDocument()
    expect(screen.getByText('취소하면 바로 예치금으로 돌려드려요.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '지우기' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '고치기' })).not.toBeInTheDocument()
  })

  it('지우는 사이 답변이 달렸으면 서버 문구를 알린다', async () => {
    const user = userEvent.setup()
    server.use(
      http.delete('*/api/v1/inquiry/inquiries/1', () =>
        HttpResponse.json(
          { status: 409, message: '이미 답변이 달린 문의입니다.', data: null },
          { status: 409 },
        ),
      ),
    )
    renderUi(<InquiryDetailPage inquiryId={1} />)

    await user.click(await screen.findByRole('button', { name: '지우기' }))
    const buttons = await screen.findAllByRole('button', { name: '지우기' })
    await user.click(buttons[buttons.length - 1])

    expect((await screen.findAllByText('이미 답변이 달린 문의입니다.')).length).toBeGreaterThan(0)
    expect(replace).not.toHaveBeenCalled()
  })
})

describe('문의 쓰기', () => {
  it('대회 상세에서 오면 종류를 고르고 쓴 뒤 보낸다', async () => {
    const user = userEvent.setup()
    let sent: unknown
    server.use(
      http.post('*/api/v1/inquiry/inquiries', async ({ request }) => {
        sent = await request.json()
        return HttpResponse.json(
          { status: 201, message: '문의를 남겼습니다.', data: { id: 9 } },
          { status: 201 },
        )
      }),
    )
    renderUi(<InquiryComposePage raceId={1} />)

    await user.click(screen.getByRole('button', { name: /기념품 · 배송/ }))
    await user.click(screen.getByRole('button', { name: '다음' }))
    const send = screen.getByRole('button', { name: '문의 보내기' })
    expect(send).toBeDisabled()
    await user.type(screen.getByRole('textbox', { name: '제목' }), '사이즈 문의')
    await user.type(screen.getByRole('textbox', { name: '문의 내용' }), 'L로 바꿀 수 있나요?')
    await user.click(send)

    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/me/inquiries?sent=1'))
    expect(sent).toEqual({
      raceId: 1,
      orderId: null,
      type: 'KIT_DELIVERY',
      title: '사이즈 문의',
      body: 'L로 바꿀 수 있나요?',
    })
  })

  it('고객센터에서 오고 참가권이 없으면 바로 쓰기 화면이다', async () => {
    server.use(
      http.get('*/api/v1/order/orders', () =>
        HttpResponse.json({
          status: 200,
          message: '조회',
          data: { orders: [], page: 0, size: 100, totalElements: 0, totalPages: 0 },
        }),
      ),
    )
    renderUi(<InquiryComposePage />)

    expect(await screen.findByRole('textbox', { name: '제목' })).toBeInTheDocument()
  })

  it('고객센터에서 오고 참가권이 있으면 참가권을 고르고, 쓰기 위에 고른 참가권을 보여 준다', async () => {
    const user = userEvent.setup()
    server.use(
      http.get('*/api/v1/order/orders', () =>
        ok({
          orders: [{ id: 1, status: 'PAID' }],
          page: 0,
          size: 100,
          totalElements: 1,
          totalPages: 1,
        }),
      ),
      http.get('*/api/v1/order/orders/1', () =>
        ok({
          id: 1,
          orderNo: 'OD-20261030-0217',
          raceId: 1,
          status: 'PAID',
          items: [{ id: 10, itemName: '춘천 호수 하프마라톤 10K', status: 'PAID' }],
        }),
      ),
    )
    renderUi(<InquiryComposePage />)

    // 「대회 종목」 이름은 「대회 · 종목」으로 나눠 보이고, 그 아래에 날짜와 신청 · 참가 완료가 온다
    expect(await screen.findByText('춘천 호수 하프마라톤 · 10K')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '다음' }))

    expect(
      await screen.findByText('춘천 호수 하프마라톤 · 10K · 크루런이 답해요'),
    ).toBeInTheDocument()
  })

  it('서버가 거절하면 문구를 알리고 이동하지 않는다', async () => {
    const user = userEvent.setup()
    server.use(
      http.post('*/api/v1/inquiry/inquiries', () =>
        HttpResponse.json(
          { status: 404, message: '주문을 찾을 수 없습니다.', data: null },
          { status: 404 },
        ),
      ),
    )
    renderUi(<InquiryComposePage raceId={1} />)

    await user.click(screen.getByRole('button', { name: '다음' }))
    await user.type(screen.getByRole('textbox', { name: '제목' }), '제목')
    await user.type(screen.getByRole('textbox', { name: '문의 내용' }), '내용')
    await user.click(screen.getByRole('button', { name: '문의 보내기' }))

    expect((await screen.findAllByText('주문을 찾을 수 없습니다.')).length).toBeGreaterThan(0)
    expect(replace).not.toHaveBeenCalled()
  })
})
