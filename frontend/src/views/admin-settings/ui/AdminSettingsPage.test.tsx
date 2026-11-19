import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'

import { setAccessToken } from '@/shared/api'
import { server } from '@/shared/testing'

import { AdminSettingsPage } from './AdminSettingsPage'

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <AdminSettingsPage />
    </QueryClientProvider>,
  )
}

beforeEach(() => setAccessToken('access-token'))

describe('AdminSettingsPage', () => {
  it('내 이름과 이메일을 보여 준다', async () => {
    server.use(
      http.get('*/api/v1/member/members/me', () =>
        HttpResponse.json({
          status: 200,
          message: '조회',
          data: { id: 1, name: '김운영', email: 'admin@crewrun.kr', nickname: '운영' },
        }),
      ),
    )
    renderPage()

    expect(await screen.findByText('김운영')).toBeInTheDocument()
    expect(screen.getByText('admin@crewrun.kr')).toBeInTheDocument()
  })

  it('내 정보를 못 불러오면 오류 안내를 보인다', async () => {
    server.use(
      http.get('*/api/v1/member/members/me', () =>
        HttpResponse.json({ status: 500, message: '오류', data: null }, { status: 500 }),
      ),
    )
    renderPage()

    expect(await screen.findByText('설정을 불러오지 못했어요')).toBeInTheDocument()
  })
})
