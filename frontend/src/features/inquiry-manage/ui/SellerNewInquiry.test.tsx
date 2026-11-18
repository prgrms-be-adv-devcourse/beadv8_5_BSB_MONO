import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { expect, it, vi } from 'vitest'

import { server } from '@/shared/testing'
import { ToastProvider } from '@/shared/ui'

import { SellerNewInquiry } from './SellerNewInquiry'

const replace = vi.fn()
vi.mock('next/navigation', async () => {
  const base = (await import('@/shared/testing')).navigationMock('/seller/inquiries/new')
  return { ...base, useRouter: () => ({ ...base.useRouter(), replace }) }
})

it('문의를 보내면 방금 보낸 문의가 보이는 보낸 문의 탭으로 간다', async () => {
  const user = userEvent.setup()
  server.use(
    http.post('*/api/v1/inquiry/inquiries', () =>
      HttpResponse.json({ status: 201, message: '문의를 남겼습니다.', data: {} }, { status: 201 }),
    ),
  )
  render(
    <QueryClientProvider client={new QueryClient()}>
      <ToastProvider>
        <SellerNewInquiry />
      </ToastProvider>
    </QueryClientProvider>,
  )

  await user.type(screen.getByRole('textbox', { name: '제목' }), '정산 계좌를 바꾸고 싶어요')
  await user.type(screen.getByRole('textbox', { name: '문의 내용' }), '어디서 바꾸나요?')
  await user.click(screen.getByRole('button', { name: '문의 보내기' }))

  await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/seller/inquiries?filter=SENT'))
})
