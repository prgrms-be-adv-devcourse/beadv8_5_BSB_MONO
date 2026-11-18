import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { server } from '@/shared/testing'

import { PayCheckRequiredCard, PayCheckRequiredTile } from './PayCheckRequired'

function renderBoth() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <PayCheckRequiredTile />
      <PayCheckRequiredCard />
    </QueryClientProvider>,
  )
}

describe('지급 확인 필요 (관리자 홈)', () => {
  it('최근 1개월의 지급 확인 필요 기록만 요청해 숫자와 표로 보여 준다', async () => {
    const queries: URLSearchParams[] = []
    server.use(
      http.get('*/api/v1/payout/admin/create-failures', ({ request }) => {
        queries.push(new URL(request.url).searchParams)
        return HttpResponse.json({
          status: 200,
          message: '이상 감지 기록을 조회했습니다.',
          data: {
            mismatchCount: 0,
            autoFilledCount: 0,
            orderLookupFailureCount: 0,
            records: {
              content: [
                {
                  id: 12,
                  recordedAt: '2026-11-15T09:00:04',
                  runDate: '2026-11-15',
                  payoutStage: '최종_정산',
                  sellerId: 3,
                  raceId: 21,
                  raceCourseId: null,
                  payoutTargetDate: null,
                  failureType: '지급_확인_필요',
                  actionTaken: null,
                  note: '정산 41(ST-20261115-7B2C) 결과 모름 3번',
                  soldCount: null,
                  settledCount: null,
                  holdCode: null,
                },
              ],
              page: 0,
              size: 10,
              totalElements: 1,
              totalPages: 1,
            },
          },
        })
      }),
    )
    renderBoth()

    expect(await screen.findByText('정산 41(ST-20261115-7B2C) 결과 모름 3번')).toBeInTheDocument()
    expect(screen.getByText('11/15')).toBeInTheDocument()
    expect(screen.getByText('최종')).toBeInTheDocument()
    // 타일과 표가 같은 쿼리를 써서 요청은 한 번
    expect(queries).toHaveLength(1)
    expect(queries[0].get('type')).toBe('지급_확인_필요')
    expect(queries[0].get('from')).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(queries[0].get('to')).toMatch(/^\d{4}-\d{2}-\d{2}$/)
  })

  it('기록이 없으면 빈 안내를 보여 준다', async () => {
    server.use(
      http.get('*/api/v1/payout/admin/create-failures', () =>
        HttpResponse.json({
          status: 200,
          message: '이상 감지 기록을 조회했습니다.',
          data: {
            mismatchCount: 0,
            autoFilledCount: 0,
            orderLookupFailureCount: 0,
            records: { content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 },
          },
        }),
      ),
    )
    renderBoth()

    expect(await screen.findByText('지급 결과를 확인해야 하는 정산이 없어요')).toBeInTheDocument()
  })

  it('요청이 실패하면 타일에 -를 보이고 로딩 모양을 남기지 않는다', async () => {
    server.use(
      http.get('*/api/v1/payout/admin/create-failures', () =>
        HttpResponse.json(
          { status: 503, message: '정산 서비스가 응답하지 않아요.' },
          { status: 503 },
        ),
      ),
    )
    const { container } = renderBoth()

    expect(await screen.findByText('지급 확인 필요 기록을 불러오지 못했어요')).toBeInTheDocument()
    expect(screen.getByText('-')).toBeInTheDocument()
    expect(container.querySelector('.animate-pulse')).toBeNull()
  })
})
