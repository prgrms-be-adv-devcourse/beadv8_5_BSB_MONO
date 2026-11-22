import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import {
  completePostcode,
  installPostcodeFake,
  orderMock,
  postcodeFake,
  productMock,
  server,
} from '@/shared/testing'

import { RaceChangeNoticePage } from './RaceChangeNoticePage'
import { TicketDetailPage } from './TicketDetailPage'
import { TicketEditPage } from './TicketEditPage'

const replace = vi.fn()
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), replace }),
  usePathname: () => '/tickets/1-1',
}))
vi.mock('next/script', async () => ({ default: (await import('@/shared/testing')).FakeScript }))

beforeEach(() => {
  replace.mockClear()
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

function mockRace(patch: Record<string, string>) {
  const race = productMock.getFindRaceResponseMock()
  server.use(
    http.get('*/api/v1/product/races/:raceId', () =>
      HttpResponse.json({ ...race, data: { ...race.data, ...patch } }),
    ),
  )
}

describe('TicketDetailPage', () => {
  it('주문 · 참가 신청 · 대회를 합쳐 참가권을 보여 준다', async () => {
    renderWithClient(<TicketDetailPage ticketId="1-1" />)

    expect(await screen.findByText('김러너 · 10K')).toBeInTheDocument()
    expect(screen.getByText('주문번호 OD-20261012-0001 · 11/1 (일) 08:00')).toBeInTheDocument()
    // v3 시안: 참가자 정보 상자 (줄마다 이름표 + 값)
    expect(screen.getByRole('heading', { name: '참가자 정보' })).toBeInTheDocument()
    expect(screen.getByText('키트 옵션')).toBeInTheDocument()
    // 키트 옵션 이름은 서버가 정하므로 고정 글자 없이 신청한 값만 보여 준다
    expect(screen.getByText('L')).toBeInTheDocument()
    expect(screen.queryByText(/기념 티셔츠/)).not.toBeInTheDocument()
    expect(screen.getByText('키트 배송지')).toBeInTheDocument()
    expect(screen.getByText('(04700) 서울 성동구 왕십리로 00 101동 1001호')).toBeInTheDocument()
    expect(screen.getByText('비상 연락처')).toBeInTheDocument()
    // Clarity 화면 녹화에서 참가자 이름 · 연락처를 가린다
    expect(screen.getByText('김러너 · 10K').closest('[data-clarity-mask="true"]')).not.toBeNull()
    expect(
      screen.getByText('김가족 · 010-9876-5432').closest('[data-clarity-mask="true"]'),
    ).not.toBeNull()
    expect(screen.getByRole('link', { name: '참가자 정보 수정하기' })).toHaveAttribute(
      'href',
      '/tickets/1-1/edit',
    )
    // v3 시안: 참가 확정 카드는 배지 없이 보여 준다
    expect(screen.queryByText('참가 확정')).not.toBeInTheDocument()
  })

  it('취소 · 환불 상자에 지금 취소하면 돌려받는 금액 · 전액 환불 기한 · 그 뒤 환불 불가 · 돌려받는 곳을 보여 준다', async () => {
    renderWithClient(<TicketDetailPage ticketId="1-1" />)

    expect(await screen.findByRole('heading', { name: '취소·환불' })).toBeInTheDocument()
    const textOf = (text: string) =>
      screen.getByText((_, el) => el?.tagName === 'P' && el.textContent === text)
    expect(textOf('지금 취소하면 50,000원 돌려받아요')).toBeInTheDocument()
    expect(screen.getByText('전액 환불')).toBeInTheDocument()
    expect(
      screen.getByText(
        (_, el) => el?.tagName === 'P' && /^.+ 23:59까지 취소$/.test(el.textContent ?? ''),
      ),
    ).toBeInTheDocument()
    expect(screen.getByText('그 뒤에 취소')).toBeInTheDocument()
    expect(screen.getByText('환불 불가')).toBeInTheDocument()
    expect(screen.getByText('돌려받는 곳')).toBeInTheDocument()
    expect(screen.getByText('예치금')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '참가 취소하기' })).toBeInTheDocument()
  })

  it('일정이 바뀌어 전액 환불 기간이 늘면 날짜 줄도 늘어난 마지막 날을 보여 준다', async () => {
    mockRace({ refundDeadline: '2026-10-10', scheduleChangedAt: '2026-10-14T10:00:00' })
    renderWithClient(<TicketDetailPage ticketId="1-1" />)

    expect(await screen.findByRole('heading', { name: '취소·환불' })).toBeInTheDocument()
    expect(
      screen.getByText(
        (_, el) => el?.tagName === 'P' && el.textContent === '지금 취소하면 50,000원 돌려받아요',
      ),
    ).toBeInTheDocument()
    expect(
      screen.getByText(
        (_, el) => el?.tagName === 'P' && el.textContent === '10/20 (화) 23:59까지 취소',
      ),
    ).toBeInTheDocument()
  })

  it('환불 기한이 지났으면 취소 확인 창은 돌려받는 금액이 없다고 안내하고 취소 요청은 그대로 보낸다', async () => {
    mockRace({ refundDeadline: '2026-10-10' })
    const user = userEvent.setup()
    renderWithClient(<TicketDetailPage ticketId="1-1" />)

    await user.click(await screen.findByRole('button', { name: '참가 취소하기' }))
    expect(await screen.findByText(/돌려받는 금액이 없어요/)).toBeInTheDocument()
    expect(screen.queryByText(/예치금으로 돌려받아요/)).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '취소하기' }))
    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/tickets/1-1/canceled'))
  })

  it('취소를 확정해 주문이 다시 로드돼도 확인 창의 환불 안내는 열 때 그대로다', async () => {
    let canceled = false
    server.use(
      http.post('*/api/v1/order/orders/:orderId/items/:orderItemId/cancel', () => {
        canceled = true
        return undefined
      }),
      http.get('*/api/v1/order/orders/:orderId', () => {
        const example = orderMock.getFindById1ResponseMock()
        return HttpResponse.json({
          ...example,
          data: {
            ...example.data,
            items: example.data?.items?.map((item) =>
              canceled ? { ...item, status: 'CANCELED' } : item,
            ),
          },
        })
      }),
    )
    const user = userEvent.setup()
    renderWithClient(<TicketDetailPage ticketId="1-1" />)

    await user.click(await screen.findByRole('button', { name: '참가 취소하기' }))
    expect(await screen.findByText(/예치금으로 돌려받아요/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '취소하기' }))

    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/tickets/1-1/canceled'))
    // 주문을 다시 받아 뒤 화면이 취소 상태로 바뀐 뒤에도
    await vi.waitFor(() => expect(screen.queryByText(/지금 취소하면/)).not.toBeInTheDocument())
    expect(screen.getByText(/예치금으로 돌려받아요/)).toBeInTheDocument()
    expect(screen.queryByText(/돌려받는 금액이 없어요/)).not.toBeInTheDocument()
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

    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/tickets/1-1/canceled'))
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
    expect(replace).not.toHaveBeenCalled()
  })

  it('결제 확인 중인 참가권은 배지만 보여 주고 고치기 · 취소 · 환불 안내를 숨긴다', async () => {
    server.use(payingOrder())
    renderWithClient(<TicketDetailPage ticketId="1-1" />)

    expect(await screen.findByText('김러너 · 10K')).toBeInTheDocument()
    expect(screen.getByText('결제 확인 중')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '참가자 정보 수정하기' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '참가 취소하기' })).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: '취소·환불' })).not.toBeInTheDocument()
    expect(screen.queryByText(/돌려받아요/)).not.toBeInTheDocument()
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

/** 명세 예시 주문 1을 결제 확인 중(PAYING, 상품은 ORDERED)으로 돌려준다 */
function payingOrder() {
  return http.get('*/api/v1/order/orders/:orderId', () => {
    const example = orderMock.getFindById1ResponseMock()
    return HttpResponse.json({
      ...example,
      data: {
        ...example.data,
        status: 'PAYING',
        items: example.data?.items?.map((item) => ({ ...item, status: 'ORDERED' })),
      },
    })
  })
}

describe('TicketEditPage', () => {
  it('회원 정보로 가는 링크는 보이는 글자 「변경」이 이름에 들어 있다', async () => {
    renderWithClient(<TicketEditPage ticketId="1-1" />)

    expect(await screen.findByRole('link', { name: /변경/ })).toHaveAttribute('href', '/me/edit')
  })

  it('결제 확인 중인 참가권은 주소로 열어도 수정할 수 없다', async () => {
    server.use(payingOrder())
    renderWithClient(<TicketEditPage ticketId="1-1" />)

    expect(await screen.findByText('참가권을 찾을 수 없어요')).toBeInTheDocument()
  })

  it('결제한 참가권은 티셔츠 사이즈를 바꿀 수 없고 취소 뒤 다시 신청하라고 안내한다', async () => {
    const example = orderMock.getFindByIdResponseMock()
    server.use(
      orderMock.getFindByIdMockHandler({
        ...example,
        data: { ...example.data, status: 'CONFIRMED' },
      }),
    )
    renderWithClient(<TicketEditPage ticketId="1-1" />)

    expect(
      await screen.findByText(
        '결제한 뒤에는 사이즈를 바꿀 수 없어요. 바꾸려면 환불 기한 안에 취소하고 다시 신청해 주세요.',
      ),
    ).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: '기념품 티셔츠 사이즈' })).toBeDisabled()
  })

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

    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/tickets/1-1'))
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

    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/tickets/1-1'))
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
    expect(replace).not.toHaveBeenCalled()
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
    expect(screen.getByText('10/20 (화) 23:59까지 전액 환불돼요')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '전액 환불받기' })).toBeInTheDocument()
  })

  it('새 날짜 라벨에 대회일의 연도를 붙인다 (해가 바뀌어도 날짜가 잘리지 않는다)', async () => {
    const race = productMock.getFindRaceResponseMock()
    server.use(
      http.get('*/api/v1/product/races/:raceId', () =>
        HttpResponse.json({
          ...race,
          data: {
            ...race.data,
            raceDate: '2027-01-09T08:00:00',
            refundDeadline: '2026-10-10',
            scheduleChangedAt: '2026-10-14T10:00:00',
          },
        }),
      ),
    )
    renderWithClient(<RaceChangeNoticePage ticketId="1-1" />)

    expect(await screen.findByText('새 날짜 · 2027년')).toBeInTheDocument()
    expect(screen.getByText('1.9')).toBeInTheDocument()
  })
})
