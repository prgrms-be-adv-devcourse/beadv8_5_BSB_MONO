import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import type { productModel } from '@/shared/api'
import { server } from '@/shared/testing'
import { ToastProvider } from '@/shared/ui'

import { SALE_CHANGE_ERRORS } from '../model/sale-change-form'
import { SellerRaceDetailPage } from './SellerRaceDetailPage'

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <SellerRaceDetailPage raceId={1} />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

const saveButton = () => screen.findByRole('button', { name: '변경 저장' })

describe('SellerRaceDetailPage', () => {
  it('상태 글자와 종목 표를 보여 준다', async () => {
    renderPage()
    expect(await screen.findByRole('table', { name: '종목' })).toBeInTheDocument()
    expect(screen.getAllByText('판매 중').length).toBeGreaterThan(0)
    expect(screen.getByRole('button', { name: '판매 멈추기' })).toBeInTheDocument()
  })

  it('판매가 시작된 대회의 참가비를 바꾸려 하면 막는 문구를 보여 준다', async () => {
    renderPage()
    const price = await screen.findByLabelText('참가비 (10K)')
    await userEvent.clear(price)
    await userEvent.type(price, '40000')
    expect(await screen.findByText(SALE_CHANGE_ERRORS.priceChanged)).toBeInTheDocument()
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
})
