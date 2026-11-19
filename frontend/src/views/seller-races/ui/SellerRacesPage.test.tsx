import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'

import { server } from '@/shared/testing'
import { ToastProvider } from '@/shared/ui'

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

describe('SellerRacesPage', () => {
  it('등록 알림의 링크 복사가 막히면 실패를 알린다', async () => {
    const user = userEvent.setup()
    const writeText = vi
      .spyOn(navigator.clipboard, 'writeText')
      .mockRejectedValue(new Error('denied'))
    renderPage('7')
    await user.click(await screen.findByRole('button', { name: '링크 복사' }))

    // 오류 Toast는 화면 읽기 프로그램용 알림(role=alert)에도 같은 문구가 한 번 더 그려진다
    expect(
      await screen.findAllByText('복사하지 못했어요. 잠시 뒤 다시 시도해 주세요.'),
    ).not.toHaveLength(0)
    expect(writeText).toHaveBeenCalledWith(expect.stringMatching(/\/races\/7$/))
    writeText.mockRestore()
  })

  it('내 대회 목록을 판매 수 · 정원 · 판매 상태와 함께 보여 준다', async () => {
    renderPage()
    expect(await screen.findByRole('button', { name: '전체 1' })).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: /15 \/ 600/ })).toBeInTheDocument()
    expect(screen.getAllByText('판매 중').length).toBeGreaterThan(0)
    expect(screen.queryByText('작성 중')).not.toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: /쿠키RUN/ })[0]).toHaveAttribute(
      'href',
      '/seller/races/1',
    )
  })

  it('상태 칩으로 걸러 결과가 없으면 빈 상태를 보여 준다', async () => {
    renderPage()
    await userEvent.click(await screen.findByRole('button', { name: '취소됨 0' }))

    expect(await screen.findByText('해당하는 대회가 없어요')).toBeInTheDocument()
  })

  it('대회 이름으로 찾으면 맞는 대회만 남기고, 종목은 하프 · 10K처럼 이어 보여 준다', async () => {
    renderPage()
    await screen.findByRole('table', { name: '내 대회' })
    expect(screen.getByText('10K')).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('대회 이름으로 찾기'), '없는대회')

    expect(await screen.findByText('해당하는 대회가 없어요')).toBeInTheDocument()
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
})
