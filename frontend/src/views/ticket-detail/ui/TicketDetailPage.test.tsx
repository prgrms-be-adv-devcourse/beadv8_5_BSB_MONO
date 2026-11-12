import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import {
  completePostcode,
  installPostcodeFake,
  postcodeFake,
  productMock,
  server,
} from '@/shared/testing'

import { RaceChangeNoticePage } from './RaceChangeNoticePage'
import { TicketDetailPage } from './TicketDetailPage'
import { TicketEditPage } from './TicketEditPage'

const push = vi.fn()
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push, back: vi.fn(), replace: vi.fn() }),
  usePathname: () => '/tickets/1-1',
}))
vi.mock('next/script', async () => ({ default: (await import('@/shared/testing')).FakeScript }))

beforeEach(() => {
  push.mockClear()
  installPostcodeFake()
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date('2026-10-15T12:00:00+09:00'))
})
afterEach(() => vi.useRealTimers())

function renderWithClient(ui: React.ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>)
}

function cancelFails(status: number, message: string) {
  server.use(
    http.post('*/api/v1/order/orders/:orderId/items/:orderItemId/cancel', () =>
      HttpResponse.json({ status, message, data: null }, { status }),
    ),
  )
}

describe('TicketDetailPage', () => {
  it('주문 · 참가 신청 · 대회를 합쳐 참가권을 보여 준다', async () => {
    renderWithClient(<TicketDetailPage ticketId="1-1" />)

    expect(await screen.findByText('김러너 · 10K')).toBeInTheDocument()
    expect(screen.getByText('주문번호 OD-20261012-0001 · 11/1 (일) 08:00')).toBeInTheDocument()
    expect(screen.getByText('사이즈 L')).toBeInTheDocument()
    expect(screen.getByText('비상 연락처 010-98**-5432')).toBeInTheDocument()
    // Clarity 화면 녹화에서 참가자 이름 · 연락처를 가린다
    expect(screen.getByText('김러너 · 10K').closest('[data-clarity-mask="true"]')).not.toBeNull()
    expect(
      screen.getByText('비상 연락처 010-98**-5432').closest('[data-clarity-mask="true"]'),
    ).not.toBeNull()
    expect(screen.getByText('오늘 취소하면 50,000원을 예치금으로 돌려받아요')).toBeInTheDocument()
    // 환불 기준: 기한 안(지금) 전액 환불, 기한 다음 날 0시부터 환불 불가
    expect(screen.getByText(/23:59 \(지금\)$/)).toBeInTheDocument()
    expect(screen.getByText(/00:00 ~$/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '고치기' })).toHaveAttribute(
      'href',
      '/tickets/1-1/edit',
    )
  })

  it('참가 취소를 확인하면 취소 API를 부르고 취소 완료 화면으로 간다', async () => {
    const calls: string[] = []
    server.use(
      http.post('*/api/v1/order/orders/:orderId/items/:orderItemId/cancel', ({ params }) => {
        calls.push(`${params.orderId}/${params.orderItemId}`)
        return undefined
      }),
    )
    const user = userEvent.setup()
    renderWithClient(<TicketDetailPage ticketId="1-1" />)

    await user.click(await screen.findByRole('button', { name: '참가 취소하기' }))
    await user.click(await screen.findByRole('button', { name: '취소하기' }))

    await vi.waitFor(() => expect(push).toHaveBeenCalledWith('/tickets/1-1/canceled'))
    expect(calls).toEqual(['1/1'])
  })

  it.each([
    [409, '환불 기한(2026-07-15)이 지나 취소할 수 없습니다.'],
    [503, '정산 서비스에 연결하지 못했습니다. 잠시 뒤 다시 시도해주세요.'],
  ])('취소가 %i이면 서버 문구를 보여 준다', async (status, message) => {
    cancelFails(status, message)
    const user = userEvent.setup()
    renderWithClient(<TicketDetailPage ticketId="1-1" />)

    await user.click(await screen.findByRole('button', { name: '참가 취소하기' }))
    await user.click(await screen.findByRole('button', { name: '취소하기' }))

    expect(await screen.findByText(message)).toBeInTheDocument()
    expect(push).not.toHaveBeenCalled()
  })

  it('주소의 ID 모양이 틀리거나 없는 주문(404)이면 찾을 수 없다고 보여 준다', async () => {
    server.use(
      http.get('*/api/v1/order/orders/:orderId', () =>
        HttpResponse.json(
          { status: 404, message: '존재하지 않는 주문입니다.', data: null },
          { status: 404 },
        ),
      ),
    )
    const { unmount } = renderWithClient(<TicketDetailPage ticketId="9-9" />)
    expect(await screen.findByText('참가권을 찾을 수 없어요')).toBeInTheDocument()
    unmount()

    renderWithClient(<TicketDetailPage ticketId="R-20261005-0012" />)
    expect(await screen.findByText('참가권을 찾을 수 없어요')).toBeInTheDocument()
  })
})

describe('TicketEditPage', () => {
  it('고친 참가자 정보를 기존 값과 합쳐 보내고 상세로 돌아간다', async () => {
    let body: unknown
    server.use(
      http.put(
        '*/api/v1/order/participations/:participationId/participant',
        async ({ request }) => {
          body = await request.json()
          return undefined
        },
      ),
    )
    const user = userEvent.setup()
    renderWithClient(<TicketEditPage ticketId="1-1" />)

    const contactName = await screen.findByLabelText('이름')
    await user.clear(contactName)
    await user.type(contactName, '김엄마')
    await user.click(screen.getByRole('button', { name: '저장하기' }))

    await vi.waitFor(() => expect(push).toHaveBeenCalledWith('/tickets/1-1'))
    expect(body).toMatchObject({
      name: '김러너',
      birth: '1994-06-25',
      phone: '010-1234-5678',
      gender: 'MALE',
      tshirtSize: 'L',
      emergencyContactName: '김엄마',
      emergencyContactPhone: '010-9876-5432',
      shippingAddress: { zipcode: '04700', address: '서울 성동구 왕십리로 00' },
    })
  })

  it('주소를 다시 검색하면 우편번호 · 주소를 바꾸고 상세 주소를 비운 뒤, 바뀐 값으로 보낸다', async () => {
    let body: unknown
    server.use(
      http.put(
        '*/api/v1/order/participations/:participationId/participant',
        async ({ request }) => {
          body = await request.json()
          return undefined
        },
      ),
    )
    const user = userEvent.setup()
    renderWithClient(<TicketEditPage ticketId="1-1" />)

    const address = await screen.findByLabelText('주소')
    expect(address).toHaveValue('서울 성동구 왕십리로 00')
    expect(address).toHaveAttribute('readonly')
    expect(screen.getByLabelText('우편번호')).toHaveValue('04700')

    await user.click(screen.getByRole('button', { name: '주소 검색' }))
    completePostcode()
    expect(address).toHaveValue('서울특별시 성동구 뚝섬로 273')
    expect(screen.getByLabelText('우편번호')).toHaveValue('04768')
    // 예전 주소의 동 · 호수가 새 주소에 붙어 잘못 배송되지 않게 비운다
    expect(screen.getByLabelText('상세 주소')).toHaveValue('')

    await user.type(screen.getByLabelText('상세 주소'), '3층')
    await user.click(screen.getByRole('button', { name: '저장하기' }))

    await vi.waitFor(() => expect(push).toHaveBeenCalledWith('/tickets/1-1'))
    expect(body).toMatchObject({
      shippingAddress: {
        recipientName: '김러너',
        zipcode: '04768',
        address: '서울특별시 성동구 뚝섬로 273',
        addressDetail: '3층',
      },
    })
  })

  it('키트가 있는 참가권은 우편번호 · 주소를 비운 채 저장하지 않는다', async () => {
    let requested = false
    server.use(
      http.put('*/api/v1/order/participations/:participationId/participant', () => {
        requested = true
        return undefined
      }),
    )
    postcodeFake.failLoad = true
    const user = userEvent.setup()
    renderWithClient(<TicketEditPage ticketId="1-1" />)

    // 주소 검색을 못 불러와 직접 입력이 열린 상태
    await user.click(await screen.findByRole('button', { name: '주소 검색' }))
    const address = screen.getByLabelText('주소')
    await vi.waitFor(() => expect(address).not.toHaveAttribute('readonly'))
    await user.clear(screen.getByLabelText('우편번호'))
    await user.clear(address)
    await user.click(screen.getByRole('button', { name: '저장하기' }))

    expect(await screen.findByText('키트를 받을 주소를 입력해 주세요.')).toBeInTheDocument()
    expect(screen.getByText('우편번호 5자리를 입력해 주세요.')).toBeInTheDocument()
    expect(requested).toBe(false)
  })

  it('접수가 마감됐으면(409) 서버 문구를 보여 준다', async () => {
    const message = '접수가 마감되어 참가자 정보를 고칠 수 없습니다.'
    server.use(
      http.put('*/api/v1/order/participations/:participationId/participant', () =>
        HttpResponse.json({ status: 409, message, data: null }, { status: 409 }),
      ),
    )
    const user = userEvent.setup()
    renderWithClient(<TicketEditPage ticketId="1-1" />)

    await user.click(await screen.findByRole('button', { name: '저장하기' }))

    expect(await screen.findByText(message)).toBeInTheDocument()
    expect(push).not.toHaveBeenCalled()
  })
})

describe('RaceChangeNoticePage', () => {
  it('일정 변경 일시로 전액 환불 기간을 계산해 보여 준다', async () => {
    const race = productMock.getFindRaceResponseMock()
    server.use(
      http.get('*/api/v1/product/races/:raceId', () =>
        HttpResponse.json({
          ...race,
          data: {
            ...race.data,
            raceDate: '2026-11-08T08:00:00',
            refundDeadline: '2026-10-10',
            scheduleChangedAt: '2026-10-14T10:00:00',
          },
        }),
      ),
    )
    renderWithClient(<RaceChangeNoticePage ticketId="1-1" />)

    expect(
      await screen.findByText('2026 용감한 쿠키RUN in 서울 날짜가 바뀌었어요'),
    ).toBeInTheDocument()
    // 10/14 알림 → 1일째로 7일째인 10/20까지. 오늘(10/15) 기준 5일 남음
    expect(screen.getByText('5')).toBeInTheDocument()
    expect(
      screen.getByText('10/14 (수) 알림 · 10/20 (화) 23:59까지 전액 환불돼요.'),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '전액 환불받기' })).toBeInTheDocument()
  })
})
