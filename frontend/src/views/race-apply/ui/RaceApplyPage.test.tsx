import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { BookingFlowProvider, useBookingFlow, type BookingFlowInitial } from '@/features/booking'
import { RACE_DETAIL, type RaceOption } from '@/entities/race'
import {
  completePostcode,
  installPostcodeFake,
  orderMock,
  postcodeFake,
  server,
} from '@/shared/testing'

import { RaceApplyPage } from './RaceApplyPage'

const push = vi.fn()
const replace = vi.fn()
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push, back: vi.fn(), replace }),
  usePathname: () => '/races/1/apply',
}))
vi.mock('next/script', async () => ({ default: (await import('@/shared/testing')).FakeScript }))

// 키트 · 티셔츠가 있는 종목 (상품 API의 종목 ID는 숫자다)
const COURSE: RaceOption = {
  ...RACE_DETAIL.options[0],
  id: '3',
  kitIncluded: true,
  shirtSizes: ['S', 'M', 'L'],
}
// 같은 대회의 다른 종목
const OTHER_COURSE: RaceOption = { ...RACE_DETAIL.options[0], id: '4', name: '하프' }
const RACE = { ...RACE_DETAIL, options: [COURSE, OTHER_COURSE] }

beforeEach(() => {
  push.mockClear()
  replace.mockClear()
  installPostcodeFake()
})

// 결제 · 장바구니 화면이 읽을 스토어 값을 그대로 꺼내 본다.
function StoreProbe() {
  const selection = useBookingFlow((s) => s.selection)
  const applications = useBookingFlow((s) => s.applications)
  return <output data-testid="store">{JSON.stringify({ selection, applications })}</output>
}

function renderPage(
  initialState: BookingFlowInitial = {},
  {
    race = RACE,
    previewRemainingSeconds,
  }: { race?: typeof RACE; previewRemainingSeconds?: number } = {},
) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <BookingFlowProvider initialState={initialState}>
        <RaceApplyPage
          race={race}
          option={COURSE}
          previewRemainingSeconds={previewRemainingSeconds}
        />
        <StoreProbe />
      </BookingFlowProvider>
    </QueryClientProvider>,
  )
}

/** 참가 신청 요청 본문을 받아 두고, 응답은 명세 예시(참가 신청 ID 1)로 돌려준다 */
function captureApply() {
  const bodies: unknown[] = []
  server.use(
    http.post('*/api/v1/order/participations', async ({ request }) => {
      bodies.push(await request.json())
      return HttpResponse.json(orderMock.getApplyResponseMock(), { status: 201 })
    }),
  )
  return bodies
}

function captureAddItem() {
  const added: unknown[] = []
  server.use(
    http.post('*/api/v1/order/cart/items', async ({ request }) => {
      added.push(await request.json())
      return HttpResponse.json(orderMock.getAddItemResponseMock())
    }),
  )
  return added
}

function captureRemoveItem() {
  const removed: string[] = []
  server.use(
    http.delete('*/api/v1/order/cart/items/:cartItemId', ({ params }) => {
      removed.push(String(params.cartItemId))
      return HttpResponse.json(orderMock.getRemoveItemResponseMock())
    }),
  )
  return removed
}

const cartWith = (items: { id: number; createDate: string; raceCourseId: number }[]) =>
  orderMock.getFindCartMockHandler({
    status: 200,
    message: '장바구니를 조회했습니다.',
    data: { memberId: 1, items },
  })

const agree = (user: ReturnType<typeof userEvent.setup>) =>
  user.click(screen.getByRole('checkbox', { name: '[필수] 개인정보 제3자 제공 동의' }))

describe('참가 신청 · 참가자 정보', () => {
  it('지난 신청 값으로 채우고, 다음을 누르면 참가 신청을 저장한 뒤 결제로 간다', async () => {
    const bodies = captureApply()
    const user = userEvent.setup()
    renderPage()

    // 명세 예시의 지난 신청 값
    expect(await screen.findByLabelText('이름')).toHaveValue('김러너')
    expect(screen.getByLabelText('키트 배송 우편번호')).toHaveValue('04700')

    const emergencyName = screen.getByLabelText('비상 연락처 이름')
    await user.clear(emergencyName)
    await user.type(emergencyName, '박가족')
    await user.click(screen.getByRole('radio', { name: '여성' }))
    await agree(user)
    await user.click(screen.getByRole('button', { name: '다음' }))

    await vi.waitFor(() => expect(push).toHaveBeenCalledWith('/checkout?orderId=1'))
    expect(bodies[0]).toEqual({
      raceCourseId: 3,
      thirdPartyConsent: true,
      participant: {
        name: '김러너',
        birth: '1994-06-25',
        gender: 'FEMALE',
        phone: '010-1234-5678',
        emergencyContactName: '박가족',
        emergencyContactPhone: '010-9876-5432',
        tshirtSize: 'L',
        shippingAddress: {
          recipientName: '김러너',
          recipientPhone: '010-1234-5678',
          zipcode: '04700',
          address: '서울 성동구 왕십리로 00',
          addressDetail: '101동 1001호',
        },
      },
    })
    const stored = JSON.parse(screen.getByTestId('store').textContent ?? '{}')
    expect(stored.selection).toEqual({ raceId: RACE_DETAIL.id, optionId: '3' })
    expect(stored.applications['3']).toMatchObject({
      participationId: 1,
      participantName: '김러너',
    })
  })

  it('다음(결제)은 참가 신청 ID로 주문을 만들고(Idempotency-Key), 실패 뒤 다시 눌러도 같은 키를 보낸다', async () => {
    captureApply()
    const calls: { key: string | null; body: unknown }[] = []
    server.use(
      http.post('*/api/v1/order/orders', async ({ request }) => {
        calls.push({ key: request.headers.get('Idempotency-Key'), body: await request.json() })
        if (calls.length === 1) {
          return HttpResponse.json(
            { status: 409, message: '같은 주문 요청을 처리하고 있습니다.', data: null },
            { status: 409 },
          )
        }
        return HttpResponse.json(orderMock.getCreateResponseMock(), { status: 201 })
      }),
    )
    const user = userEvent.setup()
    renderPage()

    await screen.findByLabelText('이름')
    await agree(user)
    await user.click(screen.getByRole('button', { name: '다음' }))
    expect(await screen.findByText('같은 주문 요청을 처리하고 있습니다.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '다음' }))

    await vi.waitFor(() => expect(push).toHaveBeenCalledWith('/checkout?orderId=1'))
    expect(calls[0].body).toEqual({ participationId: 1 })
    expect(calls[0].key).toBeTruthy()
    expect(calls[1].key).toBe(calls[0].key)
  })

  it('남은 자리가 없으면(409) 서버 문구를 보여 준다', async () => {
    captureApply()
    const message = '남은 자리가 없습니다. 참가 신청서를 다시 작성해 신청할 수 있습니다.'
    server.use(
      http.post('*/api/v1/order/orders', () =>
        HttpResponse.json({ status: 409, message, data: null }, { status: 409 }),
      ),
    )
    const user = userEvent.setup()
    renderPage()

    await screen.findByLabelText('이름')
    await agree(user)
    await user.click(screen.getByRole('button', { name: '다음' }))

    expect(await screen.findByText(message)).toBeInTheDocument()
    expect(push).not.toHaveBeenCalled()
  })

  it('바로 결제가 대기열 입장 전이라 막히면(403) 서버 문구 없이 입장 시간 만료 화면(M07-1)으로 간다', async () => {
    captureApply()
    server.use(
      http.post('*/api/v1/order/orders', () =>
        HttpResponse.json(
          { status: 403, code: 'ORDER_QUEUE_NOT_ADMITTED', message: '서버 문구', data: null },
          { status: 403 },
        ),
      ),
    )
    const user = userEvent.setup()
    renderPage({}, { race: { ...RACE, id: '1' } })

    await screen.findByLabelText('이름')
    await agree(user)
    await user.click(screen.getByRole('button', { name: '다음' }))

    await vi.waitFor(() =>
      expect(replace).toHaveBeenCalledWith('/checkout?result=timeout&raceId=1'),
    )
    expect(screen.queryByText('서버 문구')).not.toBeInTheDocument()
    expect(push).not.toHaveBeenCalled()
  })

  it('지난 신청이 없으면 회원 정보의 휴대폰 번호만 채운다', async () => {
    server.use(
      orderMock.getFindLatestParticipantMockHandler({
        status: 200,
        message: '이전에 신청한 참가자 정보가 없습니다.',
        data: null,
      }),
    )
    renderPage()

    expect(await screen.findByLabelText('휴대폰 번호')).toHaveValue('010-1234-5678')
    expect(screen.getByLabelText('이름')).toHaveValue('')
  })

  it('장바구니가 비어 있으면 참가 신청을 저장하고 그 종목을 바로 담는다', async () => {
    captureApply()
    const added = captureAddItem()
    server.use(cartWith([]))
    const user = userEvent.setup()
    renderPage()

    await screen.findByLabelText('이름')
    await agree(user)
    await user.click(screen.getByRole('button', { name: '장바구니 담기' }))

    await vi.waitFor(() => expect(push).toHaveBeenCalledWith('/cart'))
    expect(added).toEqual([{ raceCourseId: 3 }])
    expect(screen.queryByText('장바구니를 바꿀까요?')).not.toBeInTheDocument()
  })

  it('장바구니에 다른 대회가 담겨 있으면 바꿀지 묻고, 바꾸면 그 항목을 빼고 담는다', async () => {
    captureApply()
    const added = captureAddItem()
    const removed = captureRemoveItem()
    server.use(cartWith([{ id: 9, createDate: '2026-10-12T10:15:30', raceCourseId: 1 }]))
    const user = userEvent.setup()
    renderPage()

    await screen.findByLabelText('이름')
    await agree(user)
    await user.click(screen.getByRole('button', { name: '장바구니 담기' }))

    expect(await screen.findByText('장바구니를 바꿀까요?')).toBeInTheDocument()
    // 이 기기에서 담지 않은 다른 대회라 이름을 모르면 "담아 둔 대회"로 쓴다
    expect(screen.getByText(/담아 둔 대회를 빼고/)).toBeInTheDocument()
    expect(added).toHaveLength(0)

    await user.click(screen.getByRole('button', { name: '바꾸기' }))

    await vi.waitFor(() => expect(push).toHaveBeenCalledWith('/cart'))
    expect(removed).toEqual(['9'])
    expect(added).toEqual([{ raceCourseId: 3 }])
  })

  it('같은 대회의 다른 종목이 담겨 있어도 바꿀지 묻는다', async () => {
    captureApply()
    const added = captureAddItem()
    const removed = captureRemoveItem()
    server.use(cartWith([{ id: 2, createDate: '2026-10-12T10:15:30', raceCourseId: 4 }]))
    const user = userEvent.setup()
    renderPage()

    await screen.findByLabelText('이름')
    await agree(user)
    await user.click(screen.getByRole('button', { name: '장바구니 담기' }))

    expect(await screen.findByText('장바구니를 바꿀까요?')).toBeInTheDocument()
    expect(
      screen.getByText(
        new RegExp(
          `담아 둔 「${RACE.title} · 하프」를 빼고「${RACE.title} · ${COURSE.name}」를 담아요`,
        ),
      ),
    ).toBeInTheDocument()
    expect(added).toHaveLength(0)

    await user.click(screen.getByRole('button', { name: '바꾸기' }))

    await vi.waitFor(() => expect(push).toHaveBeenCalledWith('/cart'))
    expect(removed).toEqual(['2'])
    expect(added).toEqual([{ raceCourseId: 3 }])
  })

  it('담을 때 서버가 다른 종목이 담겼다고(409) 하면 바꿀지 묻는다', async () => {
    captureApply()
    const removed = captureRemoveItem()
    // 미리 볼 때는 비어 있었는데 그 사이 다른 종목이 담겼다
    let cartCalls = 0
    server.use(
      http.get('*/api/v1/order/cart', () => {
        cartCalls += 1
        const items =
          cartCalls === 1 ? [] : [{ id: 2, createDate: '2026-10-12T10:15:30', raceCourseId: 4 }]
        return HttpResponse.json({ status: 200, message: '', data: { memberId: 1, items } })
      }),
    )
    let addCalls = 0
    server.use(
      http.post('*/api/v1/order/cart/items', () => {
        addCalls += 1
        if (addCalls === 1) {
          return HttpResponse.json(
            {
              status: 409,
              code: 'ORDER_CART_ONE_COURSE_ONLY',
              message: '장바구니에는 한 종목만 담을 수 있습니다. 담긴 종목을 뺀 뒤 담아주세요.',
              data: null,
            },
            { status: 409 },
          )
        }
        return HttpResponse.json(orderMock.getAddItemResponseMock())
      }),
    )
    const user = userEvent.setup()
    renderPage()

    await screen.findByLabelText('이름')
    await agree(user)
    await user.click(screen.getByRole('button', { name: '장바구니 담기' }))

    expect(await screen.findByText('장바구니를 바꿀까요?')).toBeInTheDocument()
    expect(push).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: '바꾸기' }))

    await vi.waitFor(() => expect(push).toHaveBeenCalledWith('/cart'))
    expect(removed).toEqual(['2'])
    expect(addCalls).toBe(2)
  })

  it('같은 종목이 담겨 있으면 묻지 않고 담는다', async () => {
    captureApply()
    captureAddItem()
    server.use(cartWith([{ id: 2, createDate: '2026-10-12T10:15:30', raceCourseId: 3 }]))
    const user = userEvent.setup()
    renderPage()

    await screen.findByLabelText('이름')
    await agree(user)
    await user.click(screen.getByRole('button', { name: '장바구니 담기' }))

    await vi.waitFor(() => expect(push).toHaveBeenCalledWith('/cart'))
    expect(screen.queryByText('장바구니를 바꿀까요?')).not.toBeInTheDocument()
  })

  it('같은 대회에 이미 신청했으면(409) 서버 문구를 보여 주고 넘어가지 않는다', async () => {
    server.use(
      http.post('*/api/v1/order/participations', () =>
        HttpResponse.json(
          { status: 409, message: '이미 이 대회에 신청했습니다.', data: null },
          { status: 409 },
        ),
      ),
    )
    const user = userEvent.setup()
    renderPage()

    await screen.findByLabelText('이름')
    await agree(user)
    await user.click(screen.getByRole('button', { name: '다음' }))

    expect(await screen.findByText('이미 이 대회에 신청했습니다.')).toBeInTheDocument()
    expect(push).not.toHaveBeenCalled()
  })

  it('나이 제한 밖이면(400) 서버 문구를 보여 준다', async () => {
    const message = '참가할 수 있는 나이가 아닙니다(대회 당일 만 14세 이상, 신청자 만 12세).'
    server.use(
      http.post('*/api/v1/order/participations', () =>
        HttpResponse.json({ status: 400, message, data: null }, { status: 400 }),
      ),
    )
    const user = userEvent.setup()
    renderPage()

    await screen.findByLabelText('이름')
    await agree(user)
    await user.click(screen.getByRole('button', { name: '다음' }))

    expect(await screen.findByText(message)).toBeInTheDocument()
  })

  it('빈 칸이 있으면 보내지 않고 칸 아래에 문구를 보여 준다', async () => {
    const bodies = captureApply()
    server.use(
      orderMock.getFindLatestParticipantMockHandler({ status: 200, message: '', data: null }),
    )
    const user = userEvent.setup()
    renderPage()

    await screen.findByLabelText('이름')
    await agree(user)
    await user.click(screen.getByRole('button', { name: '다음' }))

    // 참가자 이름 · 비상 연락처 이름
    expect(await screen.findAllByText('이름을 입력해 주세요.')).toHaveLength(2)
    expect(screen.getByText('키트를 받을 주소를 입력해 주세요.')).toBeInTheDocument()
    expect(bodies).toHaveLength(0)
  })

  it('이번에 이미 저장한 종목이면 새로 신청하지 않고 참가자 정보를 고친다', async () => {
    const bodies = captureApply()
    let changedId: string | undefined
    server.use(
      http.put('*/api/v1/order/participations/:participationId/participant', ({ params }) => {
        changedId = String(params.participationId)
        return HttpResponse.json(orderMock.getChangeParticipantResponseMock())
      }),
    )
    const user = userEvent.setup()
    renderPage({
      applications: {
        '3': {
          raceId: RACE_DETAIL.id,
          optionId: '3',
          participationId: 7,
          participantName: '김러너',
        },
      },
    })

    await screen.findByLabelText('이름')
    await agree(user)
    await user.click(screen.getByRole('button', { name: '다음' }))

    await vi.waitFor(() => expect(push).toHaveBeenCalledWith('/checkout?orderId=1'))
    expect(changedId).toBe('7')
    expect(bodies).toHaveLength(0)
  })
})

describe('RaceApplyPage 대기열 남은 시간 띠', () => {
  it('시안 미리보기는 시안의 남은 초로 헤더 아래에 mm:ss 띠를 붙인다', () => {
    renderPage({}, { previewRemainingSeconds: 583 })

    expect(screen.getByRole('timer')).toHaveTextContent(
      '내 차례예요. 시간 안에 신청과 결제를 마쳐 주세요09:43',
    )
  })

  it('대기열에 입장 중이 아니면 띠를 그리지 않는다', async () => {
    server.use(
      orderMock.getFindAdmissionMockHandler({
        status: 200,
        data: { admitted: false, admissionEndsAt: null, remainingSeconds: 0 },
      }),
    )
    renderPage({}, { race: { ...RACE, id: '1' } })

    await screen.findByLabelText('이름')
    expect(screen.queryByRole('timer')).not.toBeInTheDocument()
  })

  it('대기열에서 받은 남은 시간을 이어 받아 띠를 붙이고, 0이 되면 입장 시간 만료 화면(M07-1)으로 간다', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    try {
      server.use(
        orderMock.getFindAdmissionMockHandler({
          status: 200,
          data: { admitted: true, admissionEndsAt: '2026-10-16T10:10:00', remainingSeconds: 2 },
        }),
      )
      renderPage({}, { race: { ...RACE, id: '1' } })

      expect(await screen.findByRole('timer')).toHaveTextContent(
        '내 차례예요. 시간 안에 신청과 결제를 마쳐 주세요00:02',
      )
      await act(() => vi.advanceTimersByTimeAsync(2000))
      await vi.waitFor(() =>
        expect(replace).toHaveBeenCalledWith('/checkout?result=timeout&raceId=1'),
      )
    } finally {
      vi.useRealTimers()
    }
  })
})

describe('참가 신청 · 키트 배송 주소 검색', () => {
  const noLatest = () =>
    server.use(
      orderMock.getFindLatestParticipantMockHandler({ status: 200, message: '', data: null }),
    )

  it('우편번호 · 주소는 주소 검색으로만 채우고, 상세 주소만 직접 입력해 보낸다', async () => {
    const bodies = captureApply()
    const user = userEvent.setup()
    renderPage()

    const zipcode = await screen.findByLabelText('키트 배송 우편번호')
    expect(zipcode).toHaveAttribute('readonly')
    expect(screen.getByLabelText('키트 배송 주소')).toHaveAttribute('readonly')

    // 지난 신청 값(04700)이 있어도 다시 검색하면 바뀐다
    await user.click(screen.getByRole('button', { name: '주소 검색' }))
    completePostcode()
    expect(zipcode).toHaveValue('04768')
    expect(screen.getByLabelText('키트 배송 주소')).toHaveValue('서울특별시 성동구 뚝섬로 273')

    const detail = screen.getByLabelText('상세 주소')
    expect(detail).toHaveValue('')
    await user.type(detail, '3층')
    await agree(user)
    await user.click(screen.getByRole('button', { name: '다음' }))

    await vi.waitFor(() => expect(bodies).toHaveLength(1))
    expect(bodies[0]).toMatchObject({
      participant: {
        shippingAddress: {
          zipcode: '04768',
          address: '서울특별시 성동구 뚝섬로 273',
          addressDetail: '3층',
        },
      },
    })
  })

  it('주소가 비어 오류가 난 뒤 창을 닫기만 하면 오류가 남고, 주소를 고르면 사라진다', async () => {
    noLatest()
    const user = userEvent.setup()
    renderPage()

    await screen.findByLabelText('이름')
    await agree(user)
    await user.click(screen.getByRole('button', { name: '다음' }))
    expect(await screen.findByText('키트를 받을 주소를 입력해 주세요.')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '주소 검색' }))
    await user.click(screen.getByRole('button', { name: '주소 검색 닫기' }))
    expect(screen.getByText('키트를 받을 주소를 입력해 주세요.')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '주소 검색' }))
    completePostcode()
    await vi.waitFor(() =>
      expect(screen.queryByText('키트를 받을 주소를 입력해 주세요.')).not.toBeInTheDocument(),
    )
    expect(screen.queryByText('우편번호 5자리를 입력해 주세요.')).not.toBeInTheDocument()
  })

  it('주소 검색을 불러오지 못하면 우편번호 · 주소를 직접 입력할 수 있다', async () => {
    noLatest()
    postcodeFake.failLoad = true
    const user = userEvent.setup()
    renderPage()

    await screen.findByLabelText('이름')
    await user.click(screen.getByRole('button', { name: '주소 검색' }))

    const zipcode = screen.getByLabelText('키트 배송 우편번호')
    await vi.waitFor(() => expect(zipcode).not.toHaveAttribute('readonly'))
    await user.type(zipcode, '04768')
    await user.type(screen.getByLabelText('키트 배송 주소'), '서울특별시 성동구 뚝섬로 273')
    expect(zipcode).toHaveValue('04768')
    expect(screen.getByLabelText('키트 배송 주소')).toHaveValue('서울특별시 성동구 뚝섬로 273')
  })
})
