import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { SellerAiPage } from './SellerAiPage'

describe('SellerAiPage', () => {
  it('추천 질문은 서버가 답할 수 있는 세 개만 보여 준다 (BUK-178)', () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <SellerAiPage />
      </QueryClientProvider>,
    )

    const main = screen.getByRole('main')
    const chips = within(main)
      .getAllByRole('button')
      .map((button) => button.textContent)
      .filter((text) => text !== '보내기')
    expect(chips).toEqual(['다음 정산', '송장 입력 대기 키트', '지급보류 이유'])
  })
})
