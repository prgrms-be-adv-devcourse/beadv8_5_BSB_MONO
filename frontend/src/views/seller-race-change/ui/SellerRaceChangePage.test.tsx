import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'

import type { productModel } from '@/shared/api'
import { server } from '@/shared/testing'
import { ToastProvider } from '@/shared/ui'

import { RESCHEDULE_ERRORS } from '../model/reschedule-form'
import { SellerRaceChangePage } from './SellerRaceChangePage'

const replace = vi.fn()
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), replace }),
  usePathname: () => '/seller/races/1/change',
}))

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

describe('SellerRaceChangePage 일정 변경', () => {
  it('새 대회일(날짜만)로 대회 전체를 PUT 하고, 시각은 지금 대회 시각을 그대로 둔다', async () => {
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
    const field = await screen.findByLabelText('새 대회일')
    expect(field).toHaveValue('2026-11-01')
    await userEvent.clear(field)
    await userEvent.type(field, '2026-11-08')
    await userEvent.click(screen.getByRole('button', { name: '일정 변경 알리기' }))

    expect(await screen.findByText('대회 일정을 바꿨어요')).toBeInTheDocument()
    expect(body).toMatchObject({
      raceDate: '2026-11-08T08:00:00',
      title: '2026 용감한 쿠키RUN in 서울',
    })
    expect(body?.courses[0].images.length).toBeGreaterThan(0)
  })

  it('지금과 같은 날이면 보내지 않고 알려 준다', async () => {
    renderPage()
    await screen.findByLabelText('새 대회일')
    await userEvent.click(screen.getByRole('button', { name: '일정 변경 알리기' }))
    expect(await screen.findByText(RESCHEDULE_ERRORS.sameDate)).toBeInTheDocument()
  })

  it('서버가 막으면(400) 그 문구를 보여 준다', async () => {
    server.use(
      http.put('*/api/v1/product/races/:raceId', () =>
        HttpResponse.json(
          { status: 400, message: '접수 마감 일시는 대회 일시보다 늦을 수 없습니다.', data: null },
          { status: 400 },
        ),
      ),
    )
    renderPage()
    const field = await screen.findByLabelText('새 대회일')
    await userEvent.clear(field)
    await userEvent.type(field, '2026-10-25')
    await userEvent.click(screen.getByRole('button', { name: '일정 변경 알리기' }))
    expect(
      await screen.findByText('접수 마감 일시는 대회 일시보다 늦을 수 없습니다.'),
    ).toBeInTheDocument()
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
