import {
  focusManager,
  onlineManager,
  QueryClient,
  QueryClientProvider,
} from '@tanstack/react-query'
import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { addDays, format } from 'date-fns'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'

import { raceAfterCloseMessage } from '@/entities/race'
import type { productModel } from '@/shared/api'
import { server } from '@/shared/testing'
import { ToastProvider } from '@/shared/ui'

import { SellerRaceChangePage } from './SellerRaceChangePage'

const replace = vi.fn()
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), replace }),
  usePathname: () => '/seller/races/1/change',
}))

/** 알림 확인 창에서 알리기를 누른다 */
async function confirmNotify() {
  const dialog = await screen.findByRole('alertdialog')
  await userEvent.click(within(dialog).getByRole('button', { name: '알리기' }))
}

const day = (days: number) => format(addDays(new Date(), days), 'yyyy-MM-dd')
const at = (days: number, time: string) => `${day(days)}T${time}`

/** 일정을 정해 둔 1번 대회(판매 예정, 크루 예매 있음). 대회는 접수 마감 3주 뒤 같은 시각이다 */
function useRace() {
  const detail = {
    raceId: 1,
    title: '2026 용감한 쿠키RUN in 서울',
    location: '서울숲 일대',
    regionSido: '서울특별시',
    regionSigungu: '성동구',
    saleStatus: 'SCHEDULED',
    raceDate: at(40, '08:00:00'),
    crewSaleStartAt: at(5, '10:00:00'),
    crewSaleEndAt: at(9, '23:59:59'),
    crewMaxCount: 12,
    individualOpenAt: at(10, '00:00:00'),
    applicationCloseAt: at(19, '08:00:00'),
    refundDeadline: day(25),
    courses: [
      {
        raceCourseId: 1,
        name: '10K',
        courseType: 'KM_10',
        distanceKm: 10,
        price: 45000,
        crewQuota: 20,
        individualQuota: 600,
      },
    ],
  }
  server.use(
    http.get('*/api/v1/product/races/mine', () =>
      HttpResponse.json({
        status: 200,
        message: 'ok',
        data: [{ raceId: 1, title: detail.title, saleStatus: 'SCHEDULED', courses: [] }],
      }),
    ),
    http.get('*/api/v1/product/races/:raceId', () =>
      HttpResponse.json({ status: 200, message: 'ok', data: detail }),
    ),
  )
  return detail
}

/** 시간 칸(예: 「접수 마감 시간」)의 시각을 바꾼다 */
async function typeTime(label: string, time: string) {
  const input = screen.getByLabelText(`${label} 시간`)
  await userEvent.clear(input)
  await userEvent.type(input, time)
}

function renderPage(confirmOpen = false) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <SellerRaceChangePage raceId={1} confirmOpen={confirmOpen} />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

describe('SellerRaceChangePage 알림 대상', () => {
  function soldRaces(sold: number) {
    server.use(
      http.get('*/api/v1/product/races/mine', () =>
        HttpResponse.json({
          status: 200,
          message: 'ok',
          data: [
            {
              raceId: 1,
              title: '테스트 대회',
              saleStatus: 'ON_SALE',
              courses: [
                { raceCourseId: 1, name: '10K', quota: 100, sold: sold - 2 },
                { raceCourseId: 2, name: '하프', quota: 100, sold: 2 },
              ],
            },
          ],
        }),
      ),
    )
  }

  it('일정 변경: 알림 글 칸 아래에 참가자 수와 알림 방식을 알려 주고, 전액 환불 7일을 보여 준다', async () => {
    soldRaces(12)
    renderPage()
    expect(await screen.findByText('12명에게 앱 알림과 문자로 보내요')).toBeInTheDocument()
    expect(screen.getByText('알린 날부터 7일')).toBeInTheDocument()
  })

  it('대회 취소: 공지 칸 아래에도 같은 안내를 보여 준다', async () => {
    soldRaces(12)
    renderPage()
    await userEvent.click(await screen.findByRole('radio', { name: /대회 취소/ }))
    expect(await screen.findByText('12명에게 앱 알림과 문자로 보내요')).toBeInTheDocument()
  })
})

describe('SellerRaceChangePage 일정 변경', () => {
  it('일정에 관한 모든 칸이 있다: 대회 일시, 장소, 크루 선예매, 크루 예매 시작·마감, 크루 최대 인원, 일반 판매 시작, 접수 마감, 환불 기한', async () => {
    useRace()
    renderPage()
    expect(await screen.findByRole('group', { name: '대회 일시' })).toBeInTheDocument()
    expect(screen.getByText('서울숲 일대')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '열기' })).toHaveAttribute('aria-pressed', 'true')
    for (const label of [
      '크루 예매 시작',
      '크루 예매 마감',
      '일반 판매 시작',
      '접수 마감',
      '환불 기한',
    ]) {
      expect(screen.getByRole('group', { name: new RegExp(label) })).toBeInTheDocument()
    }
    expect(screen.getByRole('textbox', { name: '크루 최대 인원' })).toHaveValue('12')
  })

  it('정원·참가비·이름 같은 일정이 아닌 칸은 없다', async () => {
    useRace()
    renderPage()
    await screen.findByRole('group', { name: '대회 일시' })
    expect(screen.queryByLabelText('모집 정원 (10K)')).not.toBeInTheDocument()
    expect(screen.queryByRole('textbox', { name: '대회 이름' })).not.toBeInTheDocument()
  })

  it('바꾼 일정만 바꾸고 나머지는 지금 값 그대로 대회 전체를 PUT 한다', async () => {
    const detail = useRace()
    let body: productModel.RaceSaveRequestDto | undefined
    server.use(
      http.put('*/api/v1/product/races/:raceId', async ({ request }) => {
        body = (await request.json()) as productModel.RaceSaveRequestDto
        return HttpResponse.json({
          status: 200,
          message: '대회를 수정했습니다.',
          data: { raceId: 1 },
        })
      }),
    )
    renderPage()
    await screen.findByRole('group', { name: '대회 일시' })
    await typeTime('접수 마감', '07:00')
    await userEvent.click(screen.getByRole('button', { name: '일정 변경 알리기' }))
    await confirmNotify()

    expect(await screen.findByText('대회 일정을 바꿨어요')).toBeInTheDocument()
    expect(body).toMatchObject({
      applicationCloseAt: at(19, '07:00:00'),
      raceDate: detail.raceDate,
      crewSaleStartAt: detail.crewSaleStartAt,
      crewSaleEndAt: detail.crewSaleEndAt,
      individualOpenAt: detail.individualOpenAt,
      refundDeadline: detail.refundDeadline,
      crewMaxCount: 12,
      title: detail.title,
    })
    // 종목은 지금 값 그대로, 연결된 이미지도 그대로 다시 보낸다 (CRU-6)
    expect(body?.courses[0]).toMatchObject({
      courseId: 1,
      price: 45000,
      individualQuota: 600,
      crewQuota: 20,
    })
    expect(body?.images.map((i) => i.fileType)).toEqual(['THUMBNAIL'])
  })

  it('대회 일시를 접수 마감 3주보다 앞으로 당기면 대회 일시 칸에 오류를 보이고 보내지 않는다', async () => {
    useRace()
    renderPage()
    await screen.findByRole('group', { name: '대회 일시' })
    // 접수 마감이 19일 뒤 08:00이면 대회는 40일 뒤 08:00부터. 07:59로 줄이면 1분 모자란다
    await typeTime('대회 일시', '07:59')
    await userEvent.click(screen.getByRole('button', { name: '일정 변경 알리기' }))
    expect(await screen.findByText(raceAfterCloseMessage(`${day(40)} 08:00`))).toBeInTheDocument()
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
  })

  it('바꾼 것이 없으면 일정 변경 알리기를 누를 수 없다', async () => {
    useRace()
    renderPage()
    await screen.findByRole('group', { name: '대회 일시' })
    expect(screen.getByRole('button', { name: '일정 변경 알리기' })).toBeDisabled()
  })

  it('서버가 막으면(400) 그 문구를 보여 준다', async () => {
    useRace()
    server.use(
      http.put('*/api/v1/product/races/:raceId', () =>
        HttpResponse.json(
          { status: 400, message: '접수 마감 일시는 대회 일시보다 늦을 수 없습니다.', data: null },
          { status: 400 },
        ),
      ),
    )
    renderPage()
    await screen.findByRole('group', { name: '대회 일시' })
    await typeTime('접수 마감', '07:00')
    await userEvent.click(screen.getByRole('button', { name: '일정 변경 알리기' }))
    await confirmNotify()
    expect(
      await screen.findByText('접수 마감 일시는 대회 일시보다 늦을 수 없습니다.'),
    ).toBeInTheDocument()
  })
})

describe('SellerRaceChangePage 입력 유지', () => {
  it('다른 창에 다녀오거나 네트워크가 다시 연결돼도 수정할 대회를 다시 받지 않아 쓰던 값이 남는다', async () => {
    useRace()
    let fetched = 0
    server.events.on('request:start', ({ request }) => {
      if (request.method === 'GET' && /\/product\/races\/1$/.test(new URL(request.url).pathname)) {
        fetched += 1
      }
    })
    renderPage()
    await screen.findByRole('group', { name: '대회 일시' })
    await typeTime('접수 마감', '07:00')

    act(() => {
      focusManager.setFocused(false)
      focusManager.setFocused(true)
      onlineManager.setOnline(false)
      onlineManager.setOnline(true)
    })
    await new Promise((resolve) => setTimeout(resolve, 50))

    server.events.removeAllListeners()
    focusManager.setFocused(undefined)
    expect(fetched).toBe(1)
    expect(screen.getByLabelText('접수 마감 시간')).toHaveValue('07:00')
  })
})

describe('SellerRaceChangePage 대회 취소', () => {
  it('확인하면 취소 API를 부르고 대회 관리로 돌아간다', async () => {
    let canceled = false
    server.use(
      http.post('*/api/v1/product/races/:raceId/cancel', () => {
        canceled = true
        return HttpResponse.json({
          status: 200,
          message: '대회를 취소했습니다.',
          data: { raceId: 1, saleStatus: 'CANCELED' },
        })
      }),
    )
    renderPage(true)
    await userEvent.click(
      await screen.findByRole('button', { name: '대회 취소하기', hidden: false }),
    )
    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/seller/races'))
    expect(canceled).toBe(true)
  })

  it('이미 취소된 대회면(409) 그 문구를 보여 준다', async () => {
    server.use(
      http.post('*/api/v1/product/races/:raceId/cancel', () =>
        HttpResponse.json(
          { status: 409, message: '이미 취소된 대회입니다.', data: null },
          { status: 409 },
        ),
      ),
    )
    renderPage(true)
    const dialog = await screen.findByRole('alertdialog')
    await userEvent.click(within(dialog).getByRole('button', { name: '대회 취소하기' }))
    expect(await screen.findByText('이미 취소된 대회입니다.')).toBeInTheDocument()
  })
})
