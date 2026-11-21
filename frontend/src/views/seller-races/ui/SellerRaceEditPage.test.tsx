import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { addDays, format } from 'date-fns'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'

import type { productModel } from '@/shared/api'
import { server } from '@/shared/testing'
import { ToastProvider } from '@/shared/ui'

import { SellerRaceEditPage } from './SellerRaceEditPage'

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <SellerRaceEditPage raceId={1} />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

const at = (days: number, time: string) =>
  `${format(addDays(new Date(), days), 'yyyy-MM-dd')}T${time}`

/** 일정을 정해 둔 1번 대회. 목록(판매 상태)과 상세(일정·크루 정원)를 함께 바꾼다 */
function useRace(
  saleStatus: 'SCHEDULED' | 'ON_SALE',
  schedule: Record<string, string | null>,
  crewQuota = 0,
) {
  const detail = {
    raceId: 1,
    title: '2026 용감한 쿠키RUN in 서울',
    raceDate: at(40, '08:00:00'),
    refundDeadline: format(addDays(new Date(), 20), 'yyyy-MM-dd'),
    courses: [
      {
        raceCourseId: 1,
        name: '10K',
        courseType: 'KM_10',
        distanceKm: 10,
        price: 45000,
        individualQuota: 600,
      },
    ],
  }
  server.use(
    http.get('*/api/v1/product/races/mine', () =>
      HttpResponse.json({
        status: 200,
        message: 'ok',
        data: [
          { raceId: 1, title: detail.title, raceDate: detail.raceDate, saleStatus, courses: [] },
        ],
      }),
    ),
    http.get('*/api/v1/product/races/:raceId', () =>
      HttpResponse.json({
        status: 200,
        message: 'ok',
        data: {
          ...detail,
          saleStatus,
          ...schedule,
          courses: detail.courses.map((c) => ({ ...c, crewQuota })),
        },
      }),
    ),
  )
}
const CREW_AHEAD = {
  crewSaleStartAt: at(5, '10:00:00'),
  crewSaleEndAt: at(9, '23:59:59'),
  individualOpenAt: at(10, '00:00:00'),
  applicationCloseAt: at(30, '18:00:00'),
}

const saveButton = () => screen.findByRole('button', { name: '저장하기' })

describe('SellerRaceEditPage', () => {
  it('판매가 시작된 대회의 참가비는 입력 칸 없이 바꿀 수 없다고 알려 준다', async () => {
    renderPage()
    expect(await screen.findByLabelText('모집 정원 (10K)')).toBeInTheDocument()
    expect(screen.queryByLabelText('참가비 (10K)')).not.toBeInTheDocument()
    expect(screen.getByText('참가비는 바꿀 수 없어요')).toBeInTheDocument()
  })

  it('정원 칸에 숫자 아닌 글자를 치면 값은 그대로이고 안내가 보인다', async () => {
    renderPage()
    const quota = await screen.findByLabelText('모집 정원 (10K)')
    await userEvent.type(quota, 'a')
    expect(quota).toHaveValue('600')
    expect(screen.getAllByText('숫자만 입력할 수 있어요').length).toBeGreaterThan(0)
  })

  it('정원을 늘려 저장하면 대회 전체(이미지 포함)를 PUT 한다', async () => {
    let body: productModel.RaceSaveRequestDto | undefined
    server.use(
      http.put('*/api/v1/product/races/:raceId', async ({ request }) => {
        body = (await request.json()) as productModel.RaceSaveRequestDto
        return HttpResponse.json({
          status: 200,
          message: '대회를 수정했습니다.',
          data: { raceId: 1, saleStatus: 'ON_SALE', saleOpenDate: '2026-10-20', courseIds: [1] },
        })
      }),
    )
    renderPage()
    const quota = await screen.findByLabelText('모집 정원 (10K)')
    await userEvent.clear(quota)
    await userEvent.type(quota, '700')
    await userEvent.click(await saveButton())

    expect(await screen.findByText('변경을 저장했어요')).toBeInTheDocument()
    expect(body?.courses[0]).toMatchObject({ courseId: 1, individualQuota: 700, price: 45000 })
    // 대표 이미지는 대회 쪽에, 소개 이미지는 종목 쪽에 그대로 다시 보낸다 (CRU-6)
    expect(body?.images.map((i) => i.fileType)).toEqual(['THUMBNAIL'])
    expect(body?.courses[0].images.map((i) => i.fileType)).toEqual(['DETAIL'])
    expect(body).toMatchObject({
      title: '2026 용감한 쿠키RUN in 서울',
      refundDeadline: '2026-10-31',
    })
  })

  it('서버가 막으면(409) 그 문구를 보여 준다', async () => {
    server.use(
      http.put('*/api/v1/product/races/:raceId', () =>
        HttpResponse.json(
          { status: 409, message: '판매가 끝난 대회는 수정할 수 없습니다.', data: null },
          { status: 409 },
        ),
      ),
    )
    renderPage()
    const quota = await screen.findByLabelText('모집 정원 (10K)')
    await userEvent.clear(quota)
    await userEvent.type(quota, '700')
    await userEvent.click(await saveButton())
    expect(await screen.findByText('판매가 끝난 대회는 수정할 수 없습니다.')).toBeInTheDocument()
  })
  it('소개 이미지를 바꿔 저장하면 새로 올린 파일을 그 자리에 넣어 PUT 한다', async () => {
    let body: productModel.RaceSaveRequestDto | undefined
    server.use(
      http.put('*/api/v1/product/races/:raceId', async ({ request }) => {
        body = (await request.json()) as productModel.RaceSaveRequestDto
        return HttpResponse.json({
          status: 200,
          message: '대회를 수정했습니다.',
          data: { raceId: 1, saleStatus: 'ON_SALE', saleOpenDate: '2026-10-20', courseIds: [1] },
        })
      }),
    )
    // 저장 뒤 보낸 이미지가 연결된 것처럼 답한다 (화면은 연결을 확인하고 나서 끝낸다)
    server.use(
      http.get('*/api/v1/file/files', ({ request }) => {
        // 저장 전에는 기본 응답을 쓴다
        if (!body) return undefined
        const url = new URL(request.url)
        const refId = Number(url.searchParams.get('refId'))
        const sent =
          url.searchParams.get('refType') === 'Race'
            ? body?.images
            : body?.courses.find((c) => c.courseId === refId)?.images
        const data = (sent ?? []).map((i) => ({
          id: i.fileId,
          fileType: i.fileType,
          status: 'ACTIVE',
        }))
        return HttpResponse.json({ status: 200, message: 'ok', data })
      }),
    )
    URL.createObjectURL = vi.fn(() => 'blob:new')
    URL.revokeObjectURL = vi.fn()
    // jsdom은 이미지를 읽지 않으므로 가로 1080px 이상으로 바로 읽힌 것처럼 한다
    vi.stubGlobal(
      'Image',
      class {
        naturalWidth = 1200
        naturalHeight = 800
        onload: (() => void) | null = null
        set src(_: string) {
          queueMicrotask(() => this.onload?.())
        }
      },
    )
    renderPage()
    const before = (await screen.findAllByRole('img', { name: /소개 이미지/ })).length
    expect(await saveButton()).toBeDisabled()

    await userEvent.upload(
      screen.getByTestId('intro-image-input'),
      new File(['x'], 'intro.png', { type: 'image/png' }),
    )
    await userEvent.click(screen.getByRole('button', { name: '소개 이미지 1 바꾸기' }))
    await userEvent.upload(
      screen.getByTestId('intro-image-input'),
      new File(['y'], 'swap.png', { type: 'image/png' }),
    )
    await userEvent.click(await saveButton())

    expect(await screen.findByText('변경을 저장했어요')).toBeInTheDocument()
    const detail = body?.courses[0].images.filter((i) => i.fileType === 'DETAIL') ?? []
    expect(detail).toHaveLength(before + 1)
    expect(detail.map((i) => i.sortNo)).toEqual(detail.map((_, i) => i))
    vi.unstubAllGlobals()
  })
  it('판매가 시작된 대회를 저장해도 저장된 일정을 그대로 보낸다', async () => {
    let body: productModel.RaceSaveRequestDto | undefined
    useRace('ON_SALE', { ...CREW_AHEAD, crewSaleStartAt: at(-2, '10:00:00') })
    server.use(
      http.put('*/api/v1/product/races/:raceId', async ({ request }) => {
        body = (await request.json()) as productModel.RaceSaveRequestDto
        return HttpResponse.json({
          status: 200,
          message: '대회를 수정했습니다.',
          data: { raceId: 1, saleStatus: 'ON_SALE', saleOpenDate: '2026-12-01', courseIds: [1] },
        })
      }),
    )
    renderPage()
    const quota = await screen.findByLabelText('모집 정원 (10K)')
    await userEvent.clear(quota)
    await userEvent.type(quota, '700')
    await userEvent.click(await saveButton())
    expect(await screen.findByText('변경을 저장했어요')).toBeInTheDocument()
    expect(body).toMatchObject({
      crewSaleStartAt: at(-2, '10:00:00'),
      crewSaleEndAt: CREW_AHEAD.crewSaleEndAt,
      individualOpenAt: CREW_AHEAD.individualOpenAt,
      applicationCloseAt: CREW_AHEAD.applicationCloseAt,
    })
  })

  it('대회 수정 화면에는 일정 칸(판매 일정 카드, 환불 기한)이 없다. 일정은 일정 변경 화면에서 바꾼다', async () => {
    useRace('SCHEDULED', CREW_AHEAD, 20)
    renderPage()
    expect(await screen.findByLabelText('모집 정원 (10K)')).toBeInTheDocument()
    expect(screen.queryByText('판매 일정')).not.toBeInTheDocument()
    expect(screen.queryByText('환불 기한')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '열지 않기' })).not.toBeInTheDocument()
    expect(screen.queryByRole('textbox', { name: '크루 최대 인원' })).not.toBeInTheDocument()
  })

  it('판매 예정 대회를 저장해도 일정과 크루 정원은 지금 값 그대로 보낸다', async () => {
    let body: productModel.RaceSaveRequestDto | undefined
    useRace('SCHEDULED', { ...CREW_AHEAD, refundDeadline: at(25, '00:00:00').slice(0, 10) }, 20)
    server.use(
      http.put('*/api/v1/product/races/:raceId', async ({ request }) => {
        body = (await request.json()) as productModel.RaceSaveRequestDto
        return HttpResponse.json({
          status: 200,
          message: '대회를 수정했습니다.',
          data: { raceId: 1, saleStatus: 'SCHEDULED', saleOpenDate: '2026-12-01', courseIds: [1] },
        })
      }),
    )
    renderPage()
    const quota = await screen.findByLabelText('모집 정원 (10K)')
    await userEvent.clear(quota)
    await userEvent.type(quota, '700')
    await userEvent.click(await saveButton())
    expect(await screen.findByText('변경을 저장했어요')).toBeInTheDocument()
    expect(body).toMatchObject({
      crewSaleStartAt: CREW_AHEAD.crewSaleStartAt,
      crewSaleEndAt: CREW_AHEAD.crewSaleEndAt,
      individualOpenAt: CREW_AHEAD.individualOpenAt,
      applicationCloseAt: CREW_AHEAD.applicationCloseAt,
    })
    expect(body?.courses[0]).toMatchObject({ individualQuota: 700, crewQuota: 20 })
  })
})
