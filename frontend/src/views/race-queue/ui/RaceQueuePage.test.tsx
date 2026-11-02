import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, render, screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ADMISSION_POLL_MS } from '@/features/queue-admission'
import { orderMock, server } from '@/shared/testing'

import { RaceQueuePage } from './RaceQueuePage'

const replace = vi.fn()
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), replace }),
  usePathname: () => '/races/1/queue',
}))

const NOT_ADMITTED = { admitted: false, admissionEndsAt: null, remainingSeconds: 0 }
const admitted = (remainingSeconds: number) => ({
  admitted: true,
  admissionEndsAt: '2026-10-16T10:10:00',
  remainingSeconds,
})

const queueStatus = (ahead: number, canEnter = false) => ({
  status: 200,
  message: '대기열 상태를 조회했습니다.',
  data: { myRank: canEnter ? 0 : ahead + 1, ahead, total: canEnter ? 0 : 10, canEnter },
})
const queueFailure = (status: number) =>
  HttpResponse.json({ status, message: '실패', data: null }, { status })

/** 대기열 등록 · 순번 조회 응답을 차례로 정한다. 순번 답이 모자라면 마지막 답을 되풀이한다 */
function mockQueue(
  statuses: ReturnType<typeof queueStatus>[],
  registered: ReturnType<typeof queueStatus> | Response = queueStatus(2),
) {
  const calls = { register: 0, status: 0 }
  server.use(
    http.post('*/api/v1/queue', () => {
      calls.register += 1
      return registered instanceof Response ? registered.clone() : HttpResponse.json(registered)
    }),
    http.get('*/api/v1/queue/races/:raceId/racecourses/:raceCourseId', () => {
      const answer = statuses[Math.min(calls.status, statuses.length - 1)]
      calls.status += 1
      return HttpResponse.json(answer)
    }),
  )
  return calls
}

/** 차례가 이미 와 있는 대기열 */
const mockQueueOpen = () => mockQueue([queueStatus(0, true)])

function renderQueue(props: { previewRemainingSeconds?: number } = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <RaceQueuePage raceId="1" optionId="2" {...props} />
    </QueryClientProvider>,
  )
}

describe('RaceQueuePage', () => {
  beforeEach(() => {
    // MSW 응답(Promise)이 흐르도록 실제 시간도 같이 흘려보낸다.
    vi.useFakeTimers({ shouldAdvanceTime: true })
    replace.mockClear()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('입장 전이면 차례 기다리기(M05-Q1)를 값 없는 막대와 내 앞 인원과 함께 보여 준다', async () => {
    mockQueue([queueStatus(2)])
    let admissionAsked = 0
    server.use(
      orderMock.getFindAdmissionMockHandler(() => {
        admissionAsked += 1
        return { status: 200, data: NOT_ADMITTED }
      }),
    )
    renderQueue()

    expect(screen.getByRole('heading', { name: '대기열' })).toBeInTheDocument()
    expect(
      screen.getByRole('heading', { name: /지금 신청하는 분이 많아서\s*잠시 기다리고 있어요/ }),
    ).toBeInTheDocument()
    expect(
      await screen.findByText(/내 앞에 2명이 있어요\. 잠시만 기다려 주세요\./),
    ).toBeInTheDocument()
    expect(screen.getByText(/화면을 나가면 다시 줄을 서야 해요\./)).toBeInTheDocument()
    expect(screen.getByRole('progressbar')).not.toHaveAttribute('aria-valuenow')
    // 왼쪽 위 X는 대회 상세로 간다. 하단 버튼은 없다
    expect(screen.getByRole('link', { name: '닫기' })).toHaveAttribute('href', '/races/1')
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
    await act(() => vi.advanceTimersByTimeAsync(ADMISSION_POLL_MS))
    expect(screen.queryByRole('timer')).not.toBeInTheDocument()
    // 차례가 오기 전에는 주문 서비스에 입장 확인을 묻지 않는다
    expect(admissionAsked).toBe(0)
  })

  it('등록한 뒤 순번을 다시 묻다가 차례가 오면 버튼 없이 신청서로 넘어간다', async () => {
    // 순번은 두 번 줄고, 세 번째에 차례가 온다
    const calls = mockQueue([queueStatus(1), queueStatus(0), queueStatus(0, true)])
    let admissionAsked = 0
    server.use(
      orderMock.getFindAdmissionMockHandler(() => {
        admissionAsked += 1
        return { status: 200, data: admitted(600) }
      }),
    )
    renderQueue()

    await vi.waitFor(() => expect(calls.status).toBe(1))
    expect(calls.register).toBe(1)
    expect(await screen.findByText(/내 앞에 1명이 있어요/)).toBeInTheDocument()
    expect(admissionAsked).toBe(0)

    await act(() => vi.advanceTimersByTimeAsync(ADMISSION_POLL_MS))
    await vi.waitFor(() => expect(calls.status).toBe(2))
    expect(await screen.findByText(/내 앞에 0명이 있어요/)).toBeInTheDocument()
    expect(replace).not.toHaveBeenCalled()

    await act(() => vi.advanceTimersByTimeAsync(ADMISSION_POLL_MS))
    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/races/1/apply?option=2'))
    expect(admissionAsked).toBe(1)
    // 넘어가는 동안 내 차례 화면의 전체 화면 남은 시간을 그리지 않고 기다리는 화면을 둔다
    expect(screen.queryByRole('timer')).not.toBeInTheDocument()
    expect(screen.queryByText(/내 차례예요/)).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /잠시 기다리고 있어요/ })).toBeInTheDocument()

    // 차례가 오면 더 묻지 않는다
    await act(() => vi.advanceTimersByTimeAsync(ADMISSION_POLL_MS * 2))
    expect(calls.status).toBe(3)
    expect(calls.register).toBe(1)
  })

  it('이미 입장한 회원의 등록(409)은 곧바로 입장 확인으로 넘어간다', async () => {
    mockQueue([], queueFailure(409))
    server.use(orderMock.getFindAdmissionMockHandler({ status: 200, data: admitted(600) }))
    renderQueue()

    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/races/1/apply?option=2'))
  })

  it('줄에서 사라지면(순번 조회 404) 다시 등록한다', async () => {
    let statusAsked = 0
    let registered = 0
    server.use(
      http.post('*/api/v1/queue', () => {
        registered += 1
        return HttpResponse.json(queueStatus(1))
      }),
      http.get('*/api/v1/queue/races/:raceId/racecourses/:raceCourseId', () => {
        statusAsked += 1
        return statusAsked === 1 ? queueFailure(404) : HttpResponse.json(queueStatus(0, true))
      }),
      orderMock.getFindAdmissionMockHandler({ status: 200, data: admitted(600) }),
    )
    renderQueue()

    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/races/1/apply?option=2'))
    expect(registered).toBe(2)
  })

  it('다시 등록해도 계속 404면 안내와 다시 줄 서기 버튼을 보여 주고, 누르면 다시 등록한다', async () => {
    let registered = 0
    let lostForever = true
    server.use(
      http.post('*/api/v1/queue', () => {
        registered += 1
        return HttpResponse.json(queueStatus(1))
      }),
      http.get('*/api/v1/queue/races/:raceId/racecourses/:raceCourseId', () =>
        lostForever ? queueFailure(404) : HttpResponse.json(queueStatus(1)),
      ),
    )
    renderQueue()

    expect(await screen.findByText(/대기열에서 자리를 잃었어요/)).toBeInTheDocument()
    // 처음 등록 1번 + 다시 등록 2번
    expect(registered).toBe(3)

    lostForever = false
    await act(async () => screen.getByRole('button', { name: '다시 줄 서기' }).click())
    await vi.waitFor(() => expect(registered).toBe(4))
    expect(await screen.findByText(/내 앞에 1명이 있어요/)).toBeInTheDocument()
    expect(screen.queryByText(/대기열에서 자리를 잃었어요/)).not.toBeInTheDocument()
  })

  it('대기열 등록이 401이면 로그인 화면으로 보낸다', async () => {
    mockQueue([], queueFailure(401))
    renderQueue()

    await vi.waitFor(() =>
      expect(replace).toHaveBeenCalledWith(`/login?next=${encodeURIComponent('/races/1/queue')}`),
    )
  })

  it('남은 시간이 0이 되면 입장 시간 만료 화면(M07-1)으로 간다', async () => {
    mockQueueOpen()
    server.use(orderMock.getFindAdmissionMockHandler({ status: 200, data: admitted(2) }))
    renderQueue()

    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/races/1/apply?option=2'))
    await act(() => vi.advanceTimersByTimeAsync(1000))
    expect(replace).not.toHaveBeenCalledWith('/checkout?result=timeout&raceId=1')

    await act(() => vi.advanceTimersByTimeAsync(1000))
    await vi.waitFor(() =>
      expect(replace).toHaveBeenCalledWith('/checkout?result=timeout&raceId=1'),
    )
  })

  it('시안 미리보기는 API를 부르지 않고 시안의 남은 시간을 보여 준다', () => {
    renderQueue({ previewRemainingSeconds: 583 })

    expect(screen.getByRole('timer')).toHaveTextContent('남은 시간09:43')
    // 상단 띠는 신청서(M05)부터라 이 화면에는 없다
    expect(screen.queryByText(/시간 안에 신청과 결제를 마쳐 주세요$/)).not.toBeInTheDocument()
  })

  it('입장 확인이 401이면 로그인 화면으로 보낸다', async () => {
    mockQueueOpen()
    server.use(
      http.get('*/api/v1/order/queue/admission', () =>
        HttpResponse.json(
          { status: 401, message: '로그인이 필요해요', data: null },
          { status: 401 },
        ),
      ),
    )
    renderQueue()

    await vi.waitFor(() =>
      expect(replace).toHaveBeenCalledWith(`/login?next=${encodeURIComponent('/races/1/queue')}`),
    )
  })

  it('입장 확인이 500이면 다시 묻지 않는다', async () => {
    mockQueueOpen()
    let asked = 0
    server.use(
      http.get('*/api/v1/order/queue/admission', () => {
        asked += 1
        return HttpResponse.json({ status: 500, message: '서버 오류', data: null }, { status: 500 })
      }),
    )
    renderQueue()

    await vi.waitFor(() => expect(asked).toBe(1))
    await act(() => vi.advanceTimersByTimeAsync(ADMISSION_POLL_MS * 3))
    expect(asked).toBe(1)
    expect(replace).not.toHaveBeenCalled()
  })

  it.each([
    [403, /지금은 접수 기간이 아니에요/],
    [404, /대회 정보를 찾지 못했어요/],
    [503, /대기열이 잠시 불안정해요/],
  ])(
    '대기열 등록이 %i로 실패하면 입장 확인을 묻지 않고 이유와 대회로 돌아가기를 보여 준다',
    async (status, title) => {
      mockQueue([], queueFailure(status))
      let asked = 0
      server.use(
        http.get('*/api/v1/order/queue/admission', () => {
          asked += 1
          return HttpResponse.json({ status: 200, message: 'ok', data: admitted(600) })
        }),
      )
      renderQueue()

      const dialog = await screen.findByRole('alertdialog')
      expect(within(dialog).getByText(title)).toBeInTheDocument()
      await act(() => vi.advanceTimersByTimeAsync(ADMISSION_POLL_MS * 3))
      expect(asked).toBe(0)
      expect(replace).not.toHaveBeenCalled()

      await act(async () => within(dialog).getByRole('button', { name: '대회로 돌아가기' }).click())
      expect(replace).toHaveBeenCalledWith('/races/1')
    },
  )

  it('이미 입장한 회원(409)의 입장 확인이 입장 전이어도 계속 묻는다', async () => {
    mockQueue([], queueFailure(409))
    let asked = 0
    server.use(
      http.get('*/api/v1/order/queue/admission', () => {
        asked += 1
        return HttpResponse.json({ status: 200, message: 'ok', data: NOT_ADMITTED })
      }),
    )
    renderQueue()

    await vi.waitFor(() => expect(asked).toBe(1))
    await act(() => vi.advanceTimersByTimeAsync(ADMISSION_POLL_MS))
    await vi.waitFor(() => expect(asked).toBeGreaterThan(1))
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
  })
})
