import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

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
})
