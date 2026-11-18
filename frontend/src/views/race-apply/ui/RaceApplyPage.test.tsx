import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import {
  BookingFlowProvider,
  readKitOptionIds,
  useBookingFlow,
  type BookingFlowInitial,
} from '@/features/booking'
import { RACE_DETAIL, type RaceOption } from '@/entities/race'
import {
  completePostcode,
  installPostcodeFake,
  orderMock,
  postcodeFake,
  productMock,
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

// 키트 옵션: 티셔츠(종목의 사이즈 중 하나) + 양말
const KIT_OPTIONS = productMock.getFindKitOptionsMockHandler({
  status: 200,
  message: '',
  data: {
    raceId: 1,
    kitOptions: [
      {
        name: '기념 티셔츠',
        values: [
          { kitOptionId: 11, value: 'M', stock: null },
          { kitOptionId: 12, value: 'L', stock: null },
        ],
      },
      {
        name: '양말',
        values: [
          { kitOptionId: 21, value: '230-250', stock: null },
          { kitOptionId: 22, value: '255-280', stock: null },
        ],
      },
    ],
  },
})

beforeEach(() => {
  // 기본은 키트 옵션이 없는 대회
  server.use(
    productMock.getFindKitOptionsMockHandler({
      status: 200,
      message: '',
      data: { raceId: 1, kitOptions: [] },
    }),
  )
  push.mockClear()
  replace.mockClear()
  sessionStorage.clear()
  installPostcodeFake()
})

// 결제 · 장바구니 화면이 읽을 스토어 값을 그대로 꺼내 본다.
function StoreProbe() {
  const selection = useBookingFlow((s) => s.selection)
  const applications = useBookingFlow((s) => s.applications)
  return <output data-testid="store">{JSON.stringify({ selection, applications })}</output>
}

let lastClient: QueryClient

function renderPage(
  initialState: BookingFlowInitial = {},
  {
    race = RACE,
    previewRemainingSeconds,
  }: { race?: typeof RACE; previewRemainingSeconds?: number } = {},
) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  lastClient = client
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

const agree = (user: ReturnType<typeof userEvent.setup>) =>
  user.click(screen.getByRole('checkbox', { name: '[필수] 개인정보 제3자 제공 동의' }))

describe('참가 신청 · 참가자 정보', () => {
  it('지난 신청 값으로 채우고, 다음을 누르면 참가 신청을 저장한 뒤 결제로 간다', async () => {
    const bodies = captureApply()
    const user = userEvent.setup()
    renderPage()

    // 명세 예시의 지난 신청 값
    // 이름 · 생년월일은 회원 정보를 글로만 보여 주고, 휴대폰 번호와 우편번호는 화면에 없다
    expect(await screen.findByText('김러너')).toBeInTheDocument()
    expect(screen.getByText('1995.04.12')).toBeInTheDocument()
    expect(screen.queryByLabelText('이름')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('휴대폰 번호')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('키트 배송 우편번호')).not.toBeInTheDocument()
    expect(screen.getByText('서울 성동구 왕십리로 00')).toBeInTheDocument()

    const emergencyName = screen.getByLabelText('비상 연락처 이름')
    await user.clear(emergencyName)
    await user.type(emergencyName, '박가족')
    await user.click(screen.getByRole('radio', { name: '여성' }))
    await agree(user)
    await user.click(screen.getByRole('button', { name: '다음' }))

    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/checkout?orderId=1'))
    expect(bodies[0]).toEqual({
      raceCourseId: 3,
      thirdPartyConsent: true,
      participant: {
        name: '김러너',
        birth: '1995-04-12',
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

    await screen.findByLabelText('비상 연락처 이름')
    await agree(user)
    await user.click(screen.getByRole('button', { name: '다음' }))
    expect(await screen.findByText('같은 주문 요청을 처리하고 있습니다.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '다음' }))

    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/checkout?orderId=1'))
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

    await screen.findByLabelText('비상 연락처 이름')
    await agree(user)
    await user.click(screen.getByRole('button', { name: '다음' }))

    expect(await screen.findByText(message)).toBeInTheDocument()
    expect(replace).not.toHaveBeenCalledWith('/checkout?orderId=1')
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

    await screen.findByLabelText('비상 연락처 이름')
    await agree(user)
    await user.click(screen.getByRole('button', { name: '다음' }))

    await vi.waitFor(() =>
      expect(replace).toHaveBeenCalledWith('/checkout?result=timeout&raceId=1'),
    )
    expect(screen.queryByText('서버 문구')).not.toBeInTheDocument()
    expect(replace).not.toHaveBeenCalledWith('/checkout?orderId=1')
  })

  it('지난 신청이 없어도 이름 · 생년월일은 회원 정보 값이고, 받는 사람 칸은 비어 있다', async () => {
    const bodies = captureApply()
    // 지난 신청에서 받는 사람 · 주소만 비운다 (사이즈는 남겨 둔다)
    const example = orderMock.getFindLatestParticipantResponseMock()
    server.use(
      orderMock.getFindLatestParticipantMockHandler({
        ...example,
        data: {
          ...example.data,
          emergencyContactName: '',
          emergencyContactPhone: '',
          shippingAddress: undefined,
        },
      }),
    )
    const user = userEvent.setup()
    renderPage()

    expect(await screen.findByText('1995.04.12')).toBeInTheDocument()
    expect(screen.getByLabelText('받는 사람')).toHaveValue('')
    await user.type(screen.getByLabelText('받는 사람'), '박받는')
    await user.type(screen.getByLabelText('받는 사람 연락처'), '01055556666')
    await user.type(screen.getByLabelText('비상 연락처 이름'), '박가족')
    await user.type(screen.getByLabelText('비상 연락처 전화번호'), '01098765432')
    await user.click(screen.getByRole('radio', { name: '남성' }))
    await user.click(screen.getByRole('button', { name: '주소 검색' }))
    completePostcode()
    await agree(user)
    await user.click(screen.getByRole('button', { name: '다음' }))

    // 회원 정보의 이름 · 생년월일 · 휴대폰 번호를 그대로 보내고, 키트 받는 사람은 입력값을 보낸다
    await vi.waitFor(() => expect(bodies).toHaveLength(1))
    expect(bodies[0]).toMatchObject({
      participant: {
        name: '김러너',
        birth: '1995-04-12',
        phone: '010-1234-5678',
        shippingAddress: { recipientName: '박받는', recipientPhone: '010-5555-6666' },
      },
    })
  })

  it('회원 정보를 불러오지 못하면 안내와 다시 시도를 보여 주고 다음을 누를 수 없다', async () => {
    let fail = true
    server.use(
      http.get('*/api/v1/member/members/me', () =>
        fail
          ? HttpResponse.json({ status: 500, message: '오류', data: null }, { status: 500 })
          : HttpResponse.json({
              status: 200,
              message: '',
              data: { id: 1, name: '김러너', birthDate: '1995-04-12', phone: '010-1234-5678' },
            }),
      ),
    )
    const user = userEvent.setup()
    renderPage()

    expect(await screen.findByText('회원 정보를 불러오지 못했어요')).toBeInTheDocument()
    await agree(user)
    expect(screen.getByRole('button', { name: '다음' })).toBeDisabled()

    fail = false
    await user.click(screen.getByRole('button', { name: '다시 시도하기' }))

    expect(await screen.findByText('1995.04.12')).toBeInTheDocument()
    expect(screen.queryByText('회원 정보를 불러오지 못했어요')).not.toBeInTheDocument()
    // 폼을 다시 만들므로 동의는 다시 받는다
    await agree(user)
    expect(screen.getByRole('button', { name: '다음' })).toBeEnabled()
  })

  it('장바구니 담기 버튼은 없고 하단 버튼은 다음 하나다', async () => {
    renderPage()

    await screen.findByLabelText('비상 연락처 이름')
    expect(screen.queryByRole('button', { name: '장바구니 담기' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '다음' })).toBeInTheDocument()
  })

  it('키트 옵션이 있으면 다음에서 키트 옵션 단계로 넘어가고, 다 골라야 결제하기가 켜진다', async () => {
    const bodies = captureApply()
    server.use(KIT_OPTIONS)
    const user = userEvent.setup()
    renderPage()

    await screen.findByLabelText('비상 연락처 이름')
    // 티셔츠 사이즈는 키트 옵션 단계에서 고른다
    expect(screen.queryByLabelText('기념 티셔츠 사이즈')).not.toBeInTheDocument()
    await agree(user)
    await user.click(screen.getByRole('button', { name: '다음' }))

    expect(await screen.findByRole('group', { name: '기념 티셔츠' })).toBeInTheDocument()
    // 참가자 정보는 키트 옵션을 고른 뒤 결제할 때 저장한다
    expect(bodies).toHaveLength(0)
    const payButton = screen.getByRole('button', { name: '결제하기' })
    expect(payButton).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'M' }))
    expect(payButton).toBeDisabled()
    await user.click(screen.getByRole('button', { name: '255-280' }))
    expect(payButton).toBeEnabled()
    await user.click(payButton)

    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/checkout?orderId=1'))
    expect(bodies[0]).toMatchObject({ participant: { tshirtSize: 'M' } })
    // 결제가 옵션 이름마다 하나씩 보낼 수 있도록 고른 kitOptionId를 주문에 붙여 남긴다
    expect(readKitOptionIds(1)).toEqual([11, 22])
  })

  it('키트 옵션을 불러오지 못하면 옵션 없음으로 보지 않고 진행을 막고 다시 시도를 보여 준다', async () => {
    let fail = true
    server.use(
      http.get('*/api/v1/product/races/:raceId/kit-options', () =>
        fail
          ? HttpResponse.json({ status: 500, message: '오류', data: null }, { status: 500 })
          : HttpResponse.json({
              status: 200,
              message: '',
              data: {
                raceId: 1,
                kitOptions: [
                  { name: '양말', values: [{ kitOptionId: 21, value: '230', stock: null }] },
                ],
              },
            }),
      ),
    )
    const user = userEvent.setup()
    renderPage()

    expect(await screen.findByText('키트 옵션을 불러오지 못했어요')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '다음' })).not.toBeInTheDocument()
    fail = false
    await user.click(screen.getByRole('button', { name: '다시 시도하기' }))

    expect(await screen.findByLabelText('비상 연락처 이름')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '다음' })).toBeInTheDocument()
  })

  it('키트 옵션 단계에서 이전 단계를 누르면 입력한 값이 남아 있다', async () => {
    server.use(KIT_OPTIONS)
    const user = userEvent.setup()
    renderPage()

    const emergencyName = await screen.findByLabelText('비상 연락처 이름')
    await user.clear(emergencyName)
    await user.type(emergencyName, '박가족')
    await agree(user)
    await user.click(screen.getByRole('button', { name: '다음' }))
    // 헤더의 뒤로 가기는 키트 옵션 단계에서 참가자 정보 단계로 돌아간다
    await user.click(await screen.findByRole('button', { name: '뒤로 가기' }))

    expect(await screen.findByLabelText('비상 연락처 이름')).toHaveValue('박가족')
  })

  it('키트 옵션 단계에서 결제를 저장하는 중에는 뒤로 가기로 단계를 바꿀 수 없다', async () => {
    server.use(
      KIT_OPTIONS,
      http.post('*/api/v1/order/participations', async () => {
        await delay('infinite')
      }),
    )
    const user = userEvent.setup()
    renderPage()

    await screen.findByLabelText('비상 연락처 이름')
    await agree(user)
    await user.click(screen.getByRole('button', { name: '다음' }))
    await screen.findByRole('group', { name: '기념 티셔츠' })
    await user.click(screen.getByRole('button', { name: 'M' }))
    await user.click(screen.getByRole('button', { name: '255-280' }))
    await user.click(screen.getByRole('button', { name: '결제하기' }))
    await user.click(screen.getByRole('button', { name: '뒤로 가기' }))

    expect(screen.getByRole('group', { name: '기념 티셔츠' })).toBeInTheDocument()
  })

  it('키트 옵션 단계에서 키트 조회 오류 화면이 떠 있으면 뒤로 가기는 단계가 아니라 이전 페이지로 보낸다', async () => {
    server.use(KIT_OPTIONS)
    const user = userEvent.setup()
    renderPage()
    await screen.findByLabelText('비상 연락처 이름')
    await agree(user)
    await user.click(screen.getByRole('button', { name: '다음' }))
    await screen.findByRole('button', { name: '결제하기' })

    // 키트 옵션을 다시 받다가 실패한다
    server.use(
      http.get('*/api/v1/product/races/:raceId/kit-options', () =>
        HttpResponse.json({ status: 500, message: '오류', data: null }, { status: 500 }),
      ),
    )
    await act(() =>
      lastClient
        .refetchQueries({
          predicate: (query) => String(query.queryKey[0]).endsWith('/kit-options'),
        })
        .catch(() => undefined),
    )
    expect(await screen.findByText('키트 옵션을 불러오지 못했어요')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '뒤로 가기' }))
    expect(replace).toHaveBeenCalledWith('/')
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

    await screen.findByLabelText('비상 연락처 이름')
    await agree(user)
    await user.click(screen.getByRole('button', { name: '다음' }))

    expect(await screen.findByText('이미 이 대회에 신청했습니다.')).toBeInTheDocument()
    expect(replace).not.toHaveBeenCalledWith('/checkout?orderId=1')
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

    await screen.findByLabelText('비상 연락처 이름')
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

    await screen.findByLabelText('비상 연락처 이름')
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

    await screen.findByLabelText('비상 연락처 이름')
    await agree(user)
    await user.click(screen.getByRole('button', { name: '다음' }))

    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/checkout?orderId=1'))
    expect(changedId).toBe('7')
    expect(bodies).toHaveLength(0)
  })
})

describe('RaceApplyPage 대기열 남은 시간', () => {
  it('시안 미리보기는 시안의 남은 초를 헤더 오른쪽에 mm:ss로만 보여 준다', () => {
    renderPage({}, { previewRemainingSeconds: 583 })

    expect(within(screen.getByRole('banner')).getByRole('timer')).toHaveTextContent(/^09:43$/)
    expect(screen.queryByText(/시간 안에 신청과 결제를 마쳐 주세요/)).not.toBeInTheDocument()
  })

  it('대기열에 입장 중이 아니면 남은 시간을 그리지 않는다', async () => {
    server.use(
      orderMock.getFindAdmissionMockHandler({
        status: 200,
        data: { admitted: false, admissionEndsAt: null, remainingSeconds: 0 },
      }),
    )
    renderPage({}, { race: { ...RACE, id: '1' } })

    await screen.findByLabelText('비상 연락처 이름')
    expect(screen.queryByRole('timer')).not.toBeInTheDocument()
  })

  it('대기열에서 받은 남은 시간을 이어 받아 헤더에 보여 주고, 0이 되면 입장 시간 만료 화면(M07-1)으로 간다', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    try {
      server.use(
        orderMock.getFindAdmissionMockHandler({
          status: 200,
          data: { admitted: true, admissionEndsAt: '2026-10-16T10:10:00', remainingSeconds: 2 },
        }),
      )
      renderPage({}, { race: { ...RACE, id: '1' } })

      expect(await screen.findByRole('timer')).toHaveTextContent(/^00:02$/)
      await act(() => vi.advanceTimersByTimeAsync(2000))
      await vi.waitFor(() =>
        expect(replace).toHaveBeenCalledWith('/checkout?result=timeout&raceId=1'),
      )
    } finally {
      vi.useRealTimers()
    }
  })
})

describe('참가 신청 v3 배치', () => {
  it('참가자 정보 단계는 단계 제목 · 대회 요약(변경) · 회원 정보 묶음 순이고 진행 막대는 없다', async () => {
    renderPage()

    expect(
      await screen.findByRole('heading', { level: 2, name: '참가자 정보' }),
    ).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: '선택한 대회' })).not.toBeInTheDocument()
    expect(document.querySelector('[aria-current="step"]')).toBeNull()
    expect(screen.getByRole('link', { name: '변경' })).toHaveAttribute('href', `/races/${RACE.id}`)
    expect(screen.getByRole('heading', { level: 2, name: '회원 정보' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '회원 정보에서 바꾸기' })).toHaveAttribute(
      'href',
      '/me/edit',
    )
  })

  it('개인정보 제3자 제공은 카드 없이 받는 곳 · 목적 · 항목 · 보유 기간 줄이고, 시안에 없는 양도 안내는 없다', async () => {
    renderPage()

    const heading = await screen.findByRole('heading', { name: '개인정보 제3자 제공' })
    const block = heading.closest('section')!
    for (const label of ['받는 곳', '목적', '항목', '보유 기간']) {
      expect(within(block).getByText(label)).toBeInTheDocument()
    }
    expect(within(block).getByText('대회 종료 후 1년')).toBeInTheDocument()
    expect(within(block).getByText('동의하지 않으면 신청할 수 없어요.')).toBeInTheDocument()
    expect(screen.queryByText(/양도할 수 없어요/)).not.toBeInTheDocument()
  })

  it('키트 옵션 단계는 단계 제목 아래에 옵션 이름마다 칩 묶음을 보여 준다', async () => {
    server.use(KIT_OPTIONS)
    const user = userEvent.setup()
    renderPage()

    await screen.findByLabelText('비상 연락처 이름')
    await agree(user)
    await user.click(screen.getByRole('button', { name: '다음' }))

    expect(await screen.findByRole('heading', { level: 2, name: '키트 옵션' })).toBeInTheDocument()
    expect(screen.queryByLabelText('이름')).not.toBeInTheDocument()
    expect(screen.getByRole('group', { name: '기념 티셔츠' })).toBeInTheDocument()
    expect(screen.getByRole('group', { name: '양말' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '회원 정보에서 바꾸기' })).not.toBeInTheDocument()
  })
})

describe('참가 신청 · 키트 배송 주소 검색', () => {
  const noLatest = () =>
    server.use(
      orderMock.getFindLatestParticipantMockHandler({ status: 200, message: '', data: null }),
    )

  it('주소는 주소 검색으로만 채우고, 고른 뒤에는 글로 보이며 다시 검색할 수 있다', async () => {
    const bodies = captureApply()
    const user = userEvent.setup()
    renderPage()

    // 지난 신청 값이 있으면 글로 보이고 버튼은 「다시 검색」이다
    expect(await screen.findByText('서울 성동구 왕십리로 00')).toBeInTheDocument()
    expect(screen.queryByLabelText('키트 배송 주소')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '다시 검색' }))
    completePostcode()
    expect(await screen.findByText('서울특별시 성동구 뚝섬로 273')).toBeInTheDocument()

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

    await screen.findByLabelText('비상 연락처 이름')
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

    await screen.findByLabelText('비상 연락처 이름')
    await user.click(screen.getByRole('button', { name: '주소 검색' }))

    const zipcode = await screen.findByLabelText('키트 배송 우편번호')
    await user.type(zipcode, '04768')
    await user.type(screen.getByLabelText('키트 배송 주소'), '서울특별시 성동구 뚝섬로 273')
    expect(zipcode).toHaveValue('04768')
    expect(screen.getByLabelText('키트 배송 주소')).toHaveValue('서울특별시 성동구 뚝섬로 273')
  })
})
