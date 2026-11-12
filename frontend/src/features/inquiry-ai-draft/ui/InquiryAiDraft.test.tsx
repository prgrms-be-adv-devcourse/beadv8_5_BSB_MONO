import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'

import { server } from '@/shared/testing'

import { InquiryAiDraft } from './InquiryAiDraft'

function renderDraft(onSend = vi.fn()) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <InquiryAiDraft screen="seller" inquiryId={5} onSend={onSend} />
    </QueryClientProvider>,
  )
  return onSend
}

const DRAFT = {
  draft: '안녕하세요. 보내는 날짜는 [확인 필요: 발송 예정일]이에요.',
  references: ['주문 OD-20261030-0217'],
  missing: [
    {
      title: '발송 날짜를 넣지 못했어요',
      reason: '발송 예정일이 대회 정보에 없어서 넣지 못했어요.',
    },
  ],
}

function mockDraft() {
  server.use(
    http.post('*/api/v1/ai/seller/inquiries/5/draft', () =>
      HttpResponse.json({ status: 200, message: 'ok', data: DRAFT }),
    ),
  )
}

describe('InquiryAiDraft', () => {
  it('문의를 열면 초안이 들어와 확인 필요 표시와 안내를 보여 주고, 비워 둔 곳을 고치고 확인해야 보낼 수 있다', async () => {
    mockDraft()
    const user = userEvent.setup()
    const onSend = renderDraft()

    // 문의를 열면 초안이 저절로 들어온다
    expect(await screen.findByText('확인 필요 1곳')).toBeInTheDocument()
    expect(screen.getByText('발송 날짜를 넣지 못했어요')).toBeInTheDocument()
    expect(screen.getByText('참고한 정보 · 주문 OD-20261030-0217')).toBeInTheDocument()

    await user.click(screen.getByRole('checkbox', { name: '초안 내용을 확인했어요' }))
    // 비워 둔 곳이 남아 있으면 확인해도 보낼 수 없다
    expect(screen.getByRole('button', { name: '답변 보내기' })).toBeDisabled()

    const box = screen.getByLabelText('답변')
    await user.clear(box)
    await user.type(box, '11월 10일에 보내 드려요.')
    expect(screen.queryByText('확인 필요 1곳')).not.toBeInTheDocument()
    // 고치면 확인이 풀려서 다시 확인해야 한다
    expect(screen.getByRole('button', { name: '답변 보내기' })).toBeDisabled()
    await user.click(screen.getByRole('checkbox', { name: '초안 내용을 확인했어요' }))
    await user.click(screen.getByRole('button', { name: '답변 보내기' }))

    expect(onSend).toHaveBeenCalledWith('11월 10일에 보내 드려요.')
  })

  it('초안을 만들지 못하면 서버 문구와 안내를 보여 주고, 직접 써서 보내거나 다시 만들 수 있다', async () => {
    server.use(
      http.post('*/api/v1/ai/seller/inquiries/5/draft', () =>
        HttpResponse.json(
          {
            status: 503,
            code: 'AI_MODEL_UNAVAILABLE',
            message: '지금은 답변을 만들 수 없습니다.',
            data: null,
          },
          { status: 503 },
        ),
      ),
    )
    const user = userEvent.setup()
    const onSend = renderDraft()

    expect(await screen.findByText('답변 초안을 만들지 못했어요')).toBeInTheDocument()
    expect(screen.getByText(/지금은 답변을 만들 수 없습니다\./)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '답변 보내기' })).toBeDisabled()

    // 초안이 없으면 확인 체크 없이 직접 쓴 답을 보낼 수 있다
    await user.type(screen.getByLabelText('답변'), '직접 쓴 답이에요.')
    await user.click(screen.getByRole('button', { name: '답변 보내기' }))
    expect(onSend).toHaveBeenCalledWith('직접 쓴 답이에요.')

    mockDraft()
    await user.click(screen.getByRole('button', { name: '다시 만들기' }))
    expect(await screen.findByText('확인 필요 1곳')).toBeInTheDocument()
  })
})
