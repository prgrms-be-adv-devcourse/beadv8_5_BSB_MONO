import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { server } from '@/shared/testing'

import { AiChat } from './AiChat'

function renderChat() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <AiChat
        screen="seller"
        greeting="안녕하세요, 판매자님"
        placeholder="정산이나 주문을 물어보세요"
        scopeNote="내 판매자 정보로만 답해요"
        suggestions={['다음 정산 일정']}
        footnote="크루런 AI는 틀릴 수 있어요."
      />
    </QueryClientProvider>,
  )
}

const ANSWER = {
  answer: '송장 입력 대기 키트는 2건이에요.',
  sources: [
    {
      screen: '주문 관리 · 키트 배송 현황',
      asOf: '2026-11-03T14:03:00',
      buttonLabel: '송장 입력하러 가기',
      path: '/seller/kits',
    },
  ],
  list: {
    rows: [{ orderNo: 'OD-1', name: '김민지', detail: '하프 21K' }],
    moreCount: 1,
  },
}

describe('AiChat', () => {
  it('첫 화면에 인사와 추천 질문을 보여 준다', () => {
    renderChat()

    expect(screen.getByRole('heading', { name: '안녕하세요, 판매자님' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '다음 정산 일정' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '보내기' })).toBeDisabled()
  })

  it('질문을 보내면 답과 목록, 출처 버튼을 보여 준다', async () => {
    let body: unknown
    server.use(
      http.post('*/api/v1/ai/seller/chat', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({ status: 200, message: '답변했습니다.', data: ANSWER })
      }),
    )
    const user = userEvent.setup()
    renderChat()

    await user.type(screen.getByLabelText('질문'), '송장 입력 대기 키트는 몇 개예요?')
    await user.click(screen.getByRole('button', { name: '보내기' }))

    expect(await screen.findByText('송장 입력 대기 키트는 2건이에요.')).toBeInTheDocument()
    expect(body).toEqual({ message: '송장 입력 대기 키트는 몇 개예요?' })
    expect(screen.getByText('OD-1')).toBeInTheDocument()
    expect(screen.getByText('외 1건')).toBeInTheDocument()
    expect(screen.getByText('크루런 AI는 틀릴 수 있어요.')).toBeInTheDocument()
    expect(screen.getByText('주문 관리 · 키트 배송 현황 기준 · 11월 3일')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /송장 입력하러 가기/ })).toHaveAttribute(
      'href',
      '/seller/kits',
    )
  })

  it('출처 주소가 사이트 밖(https://…)이면 링크 없이 글자만 보인다', async () => {
    server.use(
      http.post('*/api/v1/ai/seller/chat', () =>
        HttpResponse.json({
          status: 200,
          message: '답변했습니다.',
          data: {
            ...ANSWER,
            sources: [{ ...ANSWER.sources[0], path: 'https://evil.example/phish' }],
          },
        }),
      ),
    )
    const user = userEvent.setup()
    renderChat()

    await user.click(screen.getByRole('button', { name: '다음 정산 일정' }))

    expect(await screen.findByText('송장 입력 대기 키트는 2건이에요.')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /송장 입력하러 가기/ })).not.toBeInTheDocument()
  })

  it('답을 못 받으면 서버 문구를 오류로 보여 준다', async () => {
    server.use(
      http.post('*/api/v1/ai/seller/chat', () =>
        HttpResponse.json(
          {
            status: 403,
            code: 'AI_FORBIDDEN',
            message: '판매자 센터를 이용할 수 있는 회원이 아닙니다.',
            data: null,
          },
          { status: 403 },
        ),
      ),
    )
    const user = userEvent.setup()
    renderChat()

    await user.click(screen.getByRole('button', { name: '다음 정산 일정' }))

    expect(await screen.findByText('답을 받지 못했어요')).toBeInTheDocument()
    expect(screen.getByText('판매자 센터를 이용할 수 있는 회원이 아닙니다.')).toBeInTheDocument()
  })

  it('새 대화를 누르면 서버 기록을 지우고 첫 화면으로 돌아간다', async () => {
    let cleared = false
    server.use(
      http.post('*/api/v1/ai/seller/chat', () =>
        HttpResponse.json({ status: 200, message: '답변했습니다.', data: ANSWER }),
      ),
      http.delete('*/api/v1/ai/seller/chat', () => {
        cleared = true
        return HttpResponse.json({ status: 200, message: '새 대화를 시작합니다.', data: null })
      }),
    )
    const user = userEvent.setup()
    renderChat()

    await user.click(screen.getByRole('button', { name: '다음 정산 일정' }))
    await screen.findByText('송장 입력 대기 키트는 2건이에요.')
    await user.click(screen.getByRole('button', { name: '새 대화' }))

    expect(await screen.findByRole('heading', { name: '안녕하세요, 판매자님' })).toBeInTheDocument()
    expect(cleared).toBe(true)
  })
})
