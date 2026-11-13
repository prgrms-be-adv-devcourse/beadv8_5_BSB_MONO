import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'

import type { productModel } from '@/shared/api'
import { server } from '@/shared/testing'
import { ToastProvider } from '@/shared/ui'

import { SALE_CHANGE_ERRORS } from '../model/sale-change-form'
import { SellerRacesPage } from './SellerRacesPage'

function renderPage(registeredRaceId?: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <SellerRacesPage registeredRaceId={registeredRaceId} />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

const saveButton = () => screen.findByRole('button', { name: '변경 저장' })

describe('SellerRacesPage', () => {
  it('등록 알림의 링크 복사가 막히면 실패를 알린다', async () => {
    const user = userEvent.setup()
    const writeText = vi
      .spyOn(navigator.clipboard, 'writeText')
      .mockRejectedValue(new Error('denied'))
    renderPage('7')
    await user.click(await screen.findByRole('button', { name: '링크 복사' }))

    expect(
      await screen.findByText('복사하지 못했어요. 링크를 길게 눌러 복사해 주세요.'),
    ).toBeInTheDocument()
    expect(writeText).toHaveBeenCalledWith(expect.stringMatching(/\/races\/7$/))
    writeText.mockRestore()
  })

  it('내 대회 목록을 판매 수 · 정원 · 판매 상태와 함께 보여 준다', async () => {
    renderPage()
    expect(await screen.findByText('대회 1개')).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: '15 / 600' })).toBeInTheDocument()
    expect(screen.getAllByText('판매 중').length).toBeGreaterThan(0)
    expect(screen.queryByText('작성 중')).not.toBeInTheDocument()
  })

  it('승인되지 않은 판매자면(403) 서버 문구를 보여 준다', async () => {
    server.use(
      http.get('*/api/v1/product/races/mine', () =>
        HttpResponse.json(
          { status: 403, message: '승인된 판매자만 대회를 등록·수정할 수 있습니다.', data: null },
          { status: 403 },
        ),
      ),
    )
    renderPage()
    expect(
      await screen.findByText('승인된 판매자만 대회를 등록·수정할 수 있습니다.'),
    ).toBeInTheDocument()
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
    expect(body?.courses[0].images.map((i) => i.fileType)).toEqual(['THUMBNAIL', 'DETAIL'])
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
