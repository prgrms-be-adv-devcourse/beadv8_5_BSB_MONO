import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { server } from '@/shared/testing'
import { ToastProvider } from '@/shared/ui'

import { InquiryBoard, type RenderAnswer } from './InquiryBoard'

const replace = vi.fn()
// 거르기 탭이 URL에 있어 쿼리를 들고 있는 가짜 라우터를 쓰고, replace 호출은 따로 기록한다
vi.mock('next/navigation', async () => {
  const base = (await import('@/shared/testing')).navigationMock('/seller/inquiries')
  return {
    ...base,
    useRouter: () => {
      const router = base.useRouter()
      return {
        replace: (url: string) => {
          replace(url)
          router.replace(url)
        },
      }
    },
  }
})

// 답변칸은 판매자 · 관리자 화면이 AI 초안 부품을 끼워 넣는다. 여기서는 글만 쓰는 간단한 답변칸으로 갈음한다
const renderAnswer: RenderAnswer = ({ onSend }) => {
  let text = ''
  return (
    <div>
      <textarea aria-label="답변 내용" onChange={(e) => (text = e.target.value)} />
      <button type="button" onClick={() => onSend(text)}>
        답변 보내기
      </button>
    </div>
  )
}

function renderBoard(role: 'seller' | 'admin', initialId?: number) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <InquiryBoard role={role} initialId={initialId} renderAnswer={renderAnswer} />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

const ok = (data: unknown) => HttpResponse.json({ status: 200, message: '성공', data })
const row = (over: Record<string, unknown> = {}) => ({
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
  memberNickname: '달리는곰',
  order: null,
  ...over,
})
const order = (paymentAmount: number | null) => ({
  orderNo: 'OD-20261110-3F9A',
  paymentAmount,
  status: 'PAID',
  items: [
    {
      raceId: 1,
      raceName: '춘천 호수 하프마라톤',
      courseName: '5K',
      kitSummary: '티셔츠 M',
      deliveryStatus: 'PREPARING',
    },
  ],
})
const manage = (inquiries: unknown[]) => ({
  inquiries,
  page: 0,
  size: 20,
  totalElements: inquiries.length,
  totalPages: 1,
  waitingCount: inquiries.length,
  answeredCount: 0,
})

beforeEach(() => {
  replace.mockClear()
  server.use(
    http.get('*/api/v1/inquiry/seller/inquiries', () => ok(manage([row()]))),
    http.get('*/api/v1/inquiry/seller/inquiries/1', () => ok(row({ order: order(null) }))),
    http.post('*/api/v1/inquiry/seller/inquiries/1/answer', () =>
      ok(
        row({
          status: 'ANSWERED',
          answer: '맨 뒤 그룹으로 출발해 주세요.',
          answeredAt: '2026-11-10T14:30:00',
        }),
      ),
    ),
    http.get('*/api/v1/inquiry/admin/inquiries', () => ok(manage([row()]))),
    http.get('*/api/v1/inquiry/admin/inquiries/1', () => ok(row({ order: order(50000) }))),
  )
})

describe('문의 주소', () => {
  it('주소의 id로 문의를 열고, 다른 문의를 고르면 주소도 그 id로 바꾼다', async () => {
    const user = userEvent.setup()
    server.use(
      http.get('*/api/v1/inquiry/seller/inquiries', () =>
        ok(manage([row(), row({ id: 2, title: '주차는 어디에 하나요?' })])),
      ),
      http.get('*/api/v1/inquiry/seller/inquiries/2', () =>
        ok(row({ id: 2, title: '주차는 어디에 하나요?' })),
      ),
    )
    renderBoard('seller', 2)

    expect(
      await screen.findByRole('heading', { name: '주차는 어디에 하나요?' }),
    ).toBeInTheDocument()

    await user.click(await screen.findByRole('button', { name: /유모차를 끌고/ }))
    expect(replace).toHaveBeenCalledWith('/seller/inquiries/1')
  })
})

describe('판매자 문의', () => {
  it('답변 대기 문의가 먼저 열리고, 주문 정보가 보인다', async () => {
    renderBoard('seller')

    expect(
      await screen.findByRole('heading', { name: '유모차를 끌고 뛰어도 되나요?' }),
    ).toBeInTheDocument()
    expect(await screen.findByText('OD-20261110-3F9A')).toBeInTheDocument()
    expect(screen.getByText('춘천 호수 하프마라톤 · 5K')).toBeInTheDocument()
    // 결제 금액은 관리자 상세에만 있다
    expect(screen.queryByText('결제 금액')).not.toBeInTheDocument()
  })

  it('답변을 보내면 답변 완료로 바뀌고 보낸 답변이 보인다', async () => {
    const user = userEvent.setup()
    renderBoard('seller')

    await user.type(
      await screen.findByRole('textbox', { name: '답변 내용' }),
      '맨 뒤 그룹으로 출발해 주세요.',
    )
    server.use(
      http.get('*/api/v1/inquiry/seller/inquiries/1', () =>
        ok(
          row({
            status: 'ANSWERED',
            answer: '맨 뒤 그룹으로 출발해 주세요.',
            answeredAt: '2026-11-10T14:30:00',
          }),
        ),
      ),
    )
    await user.click(screen.getByRole('button', { name: '답변 보내기' }))

    expect(
      await screen.findByText('답변을 보냈어요. 답변 완료 목록으로 옮겼어요.'),
    ).toBeInTheDocument()
    // 답변 완료 목록으로 옮겨 가고, 방금 답한 문의가 그대로 열려 있다
    expect(screen.getByRole('button', { name: '답변 완료' })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
    expect(await screen.findByText(/에 보냈어요$/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '답변 보내기' })).not.toBeInTheDocument()
  })

  it('이미 답변된 문의면 서버 문구를 알린다', async () => {
    const user = userEvent.setup()
    server.use(
      http.post('*/api/v1/inquiry/seller/inquiries/1/answer', () =>
        HttpResponse.json(
          { status: 409, message: '이미 답변이 달린 문의입니다.', data: null },
          { status: 409 },
        ),
      ),
    )
    renderBoard('seller')

    await user.type(await screen.findByRole('textbox', { name: '답변 내용' }), '답변')
    await user.click(screen.getByRole('button', { name: '답변 보내기' }))

    expect((await screen.findAllByText('이미 답변이 달린 문의입니다.')).length).toBeGreaterThan(0)
  })

  it('문의가 없으면 빈 상태, 불러오지 못하면 다시 시도하기를 보여 준다', async () => {
    server.use(
      http.get('*/api/v1/inquiry/seller/inquiries', () =>
        HttpResponse.json({
          status: 200,
          message: '조회',
          data: {
            inquiries: [],
            page: 0,
            size: 20,
            totalElements: 0,
            totalPages: 0,
            waitingCount: 0,
            answeredCount: 0,
          },
        }),
      ),
    )
    const { unmount } = renderBoard('seller')
    expect(await screen.findByText('문의가 없어요')).toBeInTheDocument()
    unmount()

    server.use(
      http.get('*/api/v1/inquiry/seller/inquiries', () =>
        HttpResponse.json({ status: 500, message: '오류', data: null }, { status: 500 }),
      ),
    )
    renderBoard('seller')
    expect(await screen.findByRole('button', { name: '다시 시도하기' })).toBeInTheDocument()
  })
})

describe('탭', () => {
  it('답변 대기가 처음이고, 답변 완료 · 판매자 담당(관리자만)을 누르면 그 조건으로 다시 불러온다', async () => {
    const user = userEvent.setup()
    const urls: string[] = []
    server.use(
      http.get('*/api/v1/inquiry/admin/inquiries', ({ request }) => {
        urls.push(new URL(request.url).search)
        return ok(manage([row()]))
      }),
    )
    renderBoard('admin')

    expect(await screen.findByRole('button', { name: '답변 대기 1' })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
    expect(screen.queryByRole('button', { name: '전체' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '답변 완료' }))
    await user.click(screen.getByRole('button', { name: '판매자 담당' }))

    expect(urls[0]).toContain('status=WAITING')
    expect(urls.some((u) => u.includes('status=ANSWERED'))).toBe(true)
    expect(urls.at(-1)).toContain('assignee=SELLER')
    expect(urls.at(-1)).not.toContain('status=')
  })
})

describe('관리자 문의', () => {
  it('판매자 담당 문의는 볼 수만 있고 답변 칸이 없다', async () => {
    const user = userEvent.setup()
    renderBoard('admin')

    await user.click(await screen.findByRole('button', { name: '판매자 담당' }))
    expect(
      await screen.findByRole('heading', { name: '유모차를 끌고 뛰어도 되나요?' }),
    ).toBeInTheDocument()
    expect(screen.queryByRole('textbox', { name: '답변 내용' })).not.toBeInTheDocument()
  })

  it('관리자 담당 문의는 결제 금액을 보여 주고 답변할 수 있다', async () => {
    server.use(
      http.get('*/api/v1/inquiry/admin/inquiries/1', () =>
        ok(row({ assignee: 'ADMIN', type: 'APPLY_PAYMENT', order: order(50000) })),
      ),
    )
    renderBoard('admin')

    expect(await screen.findByText('₩50,000 · 예치금 결제')).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: '답변 내용' })).toBeInTheDocument()
  })
})
