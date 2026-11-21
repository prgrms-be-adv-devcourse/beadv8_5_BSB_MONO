import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { server } from '@/shared/testing'
import { ToastProvider } from '@/shared/ui'

import { SellerRecordReviewPage } from './SellerRecordReviewPage'

function renderPage(raceId = 1) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <SellerRecordReviewPage raceId={raceId} />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

const list = () => screen.findByRole('list', { name: '심사 목록' })

describe('SellerRecordReviewPage', () => {
  it('심사 필요 제출만 건수와 함께 보여 주고, 첫 제출의 상세를 연다', async () => {
    renderPage()
    const items = within(await list()).getAllByRole('button')
    expect(items).toHaveLength(4)
    expect(screen.getByText('심사 필요')).toBeInTheDocument()
    expect(screen.getByText('4')).toBeInTheDocument()
    expect(within(items[0]).getByText('2025-10-26 · 10K · 0:52:30')).toBeInTheDocument()
    expect(within(items[0]).getByText('사유 · 기록 너무 빠름')).toBeInTheDocument()
    expect(items[0]).toHaveAttribute('aria-pressed', 'true')

    const panel = await screen.findByRole('region', { name: '참가자가 확인한 값' })
    expect(await within(panel).findByText('M-8334')).toBeInTheDocument()
    expect(within(panel).getByText('0:52:30')).toBeInTheDocument()
    expect(within(panel).getByText('풀 2시간보다 빠른 속도예요')).toBeInTheDocument()
  })

  it('다른 제출을 고르면 그 상세를 보여 준다', async () => {
    renderPage()
    const items = within(await list()).getAllByRole('button')
    await userEvent.click(items[1])
    const panel = screen.getByRole('region', { name: '참가자가 확인한 값' })
    expect(
      await within(panel).findByText('넷타임 · 서버가 사진에서 읽지 못했어요'),
    ).toBeInTheDocument()
    expect(within(panel).getByText('-')).toBeInTheDocument()
  })

  it('승인하면 승인 요청을 보내고, 서버가 막으면 그 문구를 알린다', async () => {
    const approved: string[] = []
    server.events.on('request:start', ({ request }) => {
      if (request.url.endsWith('/approve')) approved.push(request.url)
    })
    renderPage()
    await within(await list()).findAllByRole('button')
    await screen.findByText('M-8334')
    await userEvent.click(screen.getByRole('button', { name: '승인하기' }))
    // 토스트는 화면 낭독기용으로 문구를 한 번 더 그린다
    expect(
      (await screen.findAllByText('심사 필요 상태인 제출만 심사할 수 있습니다.')).length,
    ).toBeGreaterThan(0)

    await userEvent.click(within(await list()).getAllByRole('button')[2])
    await screen.findByText('M-8332')
    await userEvent.click(screen.getByRole('button', { name: '승인하기' }))
    expect((await screen.findAllByText('기록증을 승인했어요')).length).toBeGreaterThan(0)
    expect(approved.at(-1)).toContain('/record-certs/32/approve')
    server.events.removeAllListeners()
  })

  it('승인 불가는 사유를 골라야 보내고, 메모와 함께 보낸다', async () => {
    let body: unknown
    server.use(
      http.post('*/api/v1/member/seller/record-certs/:id/deny', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({ status: 200, message: 'ok', data: {} })
      }),
    )
    renderPage()
    await screen.findByText('M-8334')
    await userEvent.click(screen.getByRole('button', { name: '승인 불가' }))
    const dialog = await screen.findByRole('dialog', { name: '승인 불가 사유' })

    await userEvent.click(within(dialog).getByRole('button', { name: '승인 불가 보내기' }))
    expect(await within(dialog).findByText('승인 불가 사유를 골라 주세요.')).toBeInTheDocument()
    expect(body).toBeUndefined()

    await userEvent.click(within(dialog).getByRole('radio', { name: '기록증 사진이 흐려요' }))
    await userEvent.type(
      within(dialog).getByRole('textbox', { name: '참가자에게 보낼 말' }),
      '  다시 찍어 주세요 ',
    )
    await userEvent.click(within(dialog).getByRole('button', { name: '승인 불가 보내기' }))
    await waitFor(() => expect(body).toEqual({ reason: 'BLURRY', memo: '다시 찍어 주세요' }))
    expect((await screen.findAllByText('승인 불가를 보냈어요')).length).toBeGreaterThan(0)
    // 토스트도 dialog 역할이라 창 이름으로 찾는다
    await waitFor(() =>
      expect(screen.queryByRole('dialog', { name: '승인 불가 사유' })).not.toBeInTheDocument(),
    )
  })

  it('승인 불가가 실패하면 창을 닫지 않고 서버 문구를 보여 준다', async () => {
    renderPage()
    await screen.findByText('M-8334')
    await userEvent.click(screen.getByRole('button', { name: '승인 불가' }))
    const dialog = await screen.findByRole('dialog', { name: '승인 불가 사유' })
    await userEvent.click(within(dialog).getByRole('radio', { name: '기타' }))
    await userEvent.type(
      within(dialog).getByRole('textbox', { name: '참가자에게 보낼 말' }),
      '오류 확인',
    )
    await userEvent.click(within(dialog).getByRole('button', { name: '승인 불가 보내기' }))
    expect(await within(dialog).findByText('잠시 후 다시 시도해 주세요.')).toBeInTheDocument()
  })

  it('심사한 제출은 목록에서 빠지고 다음 제출이 열리며, 마지막이면 빈 화면이 된다', async () => {
    const reviewed = new Set<number>()
    const submission = (submissionId: number) => ({
      submissionId,
      orderItemId: 700 + submissionId,
      raceCourseId: 1,
      status: 'NEEDS_REVIEW',
      reasons: [],
      serverCheck: {
        NET_TIME: 'MISMATCH',
        NET_LABEL: 'MATCH',
        RACE_DATE: 'MATCH',
        COURSE: 'MATCH',
      },
      serverCheckMatched: false,
      netTimeSeconds: 3000 + submissionId,
      raceDate: '2025-10-26',
      courseClass: 'TEN_K',
      denyReason: null,
      denyMemo: null,
      submittedAt: '2026-11-18T09:00:00',
      reviewedAt: null,
    })
    server.use(
      http.get('*/api/v1/member/seller/races/:raceId/record-certs', () =>
        HttpResponse.json({
          status: 200,
          message: 'ok',
          data: [32, 31].filter((id) => !reviewed.has(id)).map(submission),
        }),
      ),
      http.post('*/api/v1/member/seller/record-certs/:id/approve', ({ params }) => {
        reviewed.add(Number(params.id))
        return HttpResponse.json({ status: 200, message: 'ok', data: {} })
      }),
    )
    renderPage()
    await screen.findByText('M-8332')
    await userEvent.click(screen.getByRole('button', { name: '승인하기' }))
    await waitFor(() =>
      expect(
        within(screen.getByRole('list', { name: '심사 목록' })).getAllByRole('button'),
      ).toHaveLength(1),
    )
    expect(screen.getByRole('button', { name: /제출 31/ })).toHaveAttribute('aria-pressed', 'true')
    expect(await screen.findByText('M-8331')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '승인하기' }))
    expect(await screen.findByText('심사할 기록증이 없어요')).toBeInTheDocument()
  })

  it('이미 심사한 제출이라 막히면 목록을 다시 받는다', async () => {
    let listCalls = 0
    server.events.on('request:start', ({ request }) => {
      if (request.method === 'GET' && /\/races\/1\/record-certs/.test(request.url)) listCalls += 1
    })
    renderPage()
    await screen.findByText('M-8334')
    const before = listCalls
    await userEvent.click(screen.getByRole('button', { name: '승인하기' }))
    await waitFor(() => expect(listCalls).toBeGreaterThan(before))
    server.events.removeAllListeners()
  })

  it('기록증을 불러오지 못하면 승인은 막고 승인 불가는 열어 둔다', async () => {
    server.use(
      http.get('*/api/v1/member/seller/record-certs/:id', () =>
        HttpResponse.json(
          { status: 503, message: '기록증 사진을 불러오지 못했습니다.', data: null },
          { status: 503 },
        ),
      ),
    )
    renderPage()
    expect(await screen.findByText('기록증 사진을 불러오지 못했습니다.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '승인하기' })).toBeDisabled()
    expect(screen.getByRole('button', { name: '승인 불가' })).toBeEnabled()
  })

  it('심사할 제출이 없으면 빈 화면을 보여 준다', async () => {
    renderPage(2)
    expect(await screen.findByText('심사할 기록증이 없어요')).toBeInTheDocument()
    expect(screen.queryByRole('list', { name: '심사 목록' })).not.toBeInTheDocument()
  })

  it('남의 대회면 서버 문구를 보여 준다', async () => {
    server.use(
      http.get('*/api/v1/member/seller/races/:raceId/record-certs', () =>
        HttpResponse.json(
          { status: 403, message: '내 대회의 기록증만 볼 수 있습니다.', data: null },
          { status: 403 },
        ),
      ),
    )
    renderPage()
    expect(await screen.findByText('내 대회의 기록증만 볼 수 있습니다.')).toBeInTheDocument()
  })
})
