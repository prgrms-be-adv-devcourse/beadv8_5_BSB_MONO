import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { server } from '@/shared/testing'

import { AxBriefing } from './AxBriefing'

function renderBriefing() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <AxBriefing screen="seller" askHref="/seller/ai" basisHref="/seller/kits" />
    </QueryClientProvider>,
  )
}

const BRIEFING = {
  headline: '키트 송장 입력 마감이 이틀 남았어요.',
  basis: '오전 9:00 기준',
  todos: [
    {
      title: '송장 입력 대기 4건',
      description: '11/10까지 넣지 않으면 발송이 늦어져요',
      linkLabel: '주문 관리',
      href: '/seller/kits',
    },
  ],
  metrics: [{ label: '취소율 · 최근 7일', value: '4.2%', note: '평소 1.6%', noteTone: 'bad' }],
}

describe('AxBriefing', () => {
  it('한 문장 요약과 먼저 할 일, 지표를 보여 주고 오늘 숨기기를 누르면 사라진다', async () => {
    server.use(
      http.get('*/api/v1/ai/seller/briefing', () =>
        HttpResponse.json({ status: 200, message: 'ok', data: BRIEFING }),
      ),
    )
    const user = userEvent.setup()
    renderBriefing()

    expect(await screen.findByText('키트 송장 입력 마감이 이틀 남았어요.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /송장 입력 대기 4건/ })).toHaveAttribute(
      'href',
      '/seller/kits',
    )
    expect(screen.getByRole('link', { name: '크루런 AI에 물어보기' })).toHaveAttribute(
      'href',
      '/seller/ai',
    )
    expect(screen.getByText('4.2%')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '오늘 숨기기' }))
    expect(screen.queryByText('키트 송장 입력 마감이 이틀 남았어요.')).not.toBeInTheDocument()
  })

  it('먼저 할 일의 주소가 사이트 밖(https://…)이면 링크 없이 글자만 보인다', async () => {
    server.use(
      http.get('*/api/v1/ai/seller/briefing', () =>
        HttpResponse.json({
          status: 200,
          message: 'ok',
          data: {
            ...BRIEFING,
            todos: [{ ...BRIEFING.todos[0], href: 'https://evil.example/phish' }],
          },
        }),
      ),
    )
    renderBriefing()

    expect(await screen.findByText('송장 입력 대기 4건')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /송장 입력 대기 4건/ })).not.toBeInTheDocument()
  })

  it('불러오지 못하면 서버 문구와 함께 오류를 보여 준다', async () => {
    server.use(
      http.get('*/api/v1/ai/seller/briefing', () =>
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
    renderBriefing()

    expect(await screen.findByText('크루런 AI 브리핑을 불러오지 못했어요')).toBeInTheDocument()
    expect(screen.getByText('지금은 답변을 만들 수 없습니다.')).toBeInTheDocument()
  })
})
