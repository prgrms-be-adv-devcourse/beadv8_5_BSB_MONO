import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { AdminAiPage } from './AdminAiPage'

describe('AdminAiPage', () => {
  it('추천 질문은 서버가 답할 수 있는 두 개만 보여 준다 (BUK-178)', () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <AdminAiPage />
      </QueryClientProvider>,
    )

    const main = screen.getByRole('main')
    const chips = within(main)
      .getAllByRole('button')
      .map((button) => button.textContent)
      .filter((text) => text !== '보내기')
    expect(chips).toEqual(['승인 대기 판매자', '답변 대기 문의'])
  })
})
