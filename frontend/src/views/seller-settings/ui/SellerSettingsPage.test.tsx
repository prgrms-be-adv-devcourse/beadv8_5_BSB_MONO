import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { setAccessToken, type memberModel } from '@/shared/api'
import { server } from '@/shared/testing'

import { SellerSettingsPage } from './SellerSettingsPage'

const SELLER: memberModel.SellerDto = {
  id: 1,
  memberId: 1,
  sellerType: 'BUSINESS',
  businessName: '크루런 스포츠',
  businessNumber: '123-45-67890',
  representativeName: '김러너',
  status: 'APPROVED',
  requestedAt: '2026-10-05T21:25:35',
  reviewedAt: '2026-10-06T10:12:08',
  rejectReason: null,
}

function renderPage(seller: Partial<memberModel.SellerDto> = {}) {
  server.use(
    http.get('*/api/v1/member/sellers/me', () =>
      HttpResponse.json({ status: 200, message: '성공', data: { ...SELLER, ...seller } }),
    ),
  )
  setAccessToken('access-token')
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <SellerSettingsPage />
    </QueryClientProvider>,
  )
}

describe('SellerSettingsPage', () => {
  it('내 판매자 정보를 보기 전용으로 보여 준다', async () => {
    renderPage()

    expect(await screen.findByText('123-45-67890')).toBeInTheDocument()
    expect(screen.getByText('크루런 스포츠')).toBeInTheDocument()
    expect(screen.getByText('김러너')).toBeInTheDocument()
    expect(screen.getByText('승인')).toBeInTheDocument()
    expect(screen.getByText('2026. 10. 6.')).toBeInTheDocument()
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
    // Clarity 화면 녹화에서 사업자등록번호 · 상호 · 대표자 이름을 가린다
    for (const text of ['123-45-67890', '크루런 스포츠', '김러너']) {
      expect(screen.getByText(text).closest('[data-clarity-mask="true"]')).not.toBeNull()
    }
  })

  it('비사업자는 판매자 유형과 성명만 보여 준다', async () => {
    renderPage({ sellerType: 'INDIVIDUAL', businessName: null, businessNumber: null })

    expect(await screen.findByText('비사업자')).toBeInTheDocument()
    expect(screen.getByText('성명')).toBeInTheDocument()
    expect(screen.queryByText('사업자등록번호')).not.toBeInTheDocument()
  })

  it('정산 계좌는 입력 칸 없이 준비 중 안내만 보여 준다', async () => {
    renderPage()

    expect(await screen.findByText('정산 계좌 등록은 준비 중이에요')).toBeInTheDocument()
    expect(screen.queryByLabelText('계좌번호')).not.toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('신청 내역이 없으면(404) 오류 대신 신청하러 가도록 안내한다', async () => {
    let calls = 0
    server.use(
      http.get('*/api/v1/member/sellers/me', () => {
        calls += 1
        return HttpResponse.json(
          {
            status: 404,
            code: 'SELLER_NOT_FOUND',
            message: '판매자 신청 내역이 없습니다.',
            data: null,
          },
          { status: 404 },
        )
      }),
    )
    setAccessToken('access-token')
    // 화면이 재시도를 끄는지 보려고 기본 재시도를 켠 채로 그린다
    const client = new QueryClient()
    render(
      <QueryClientProvider client={client}>
        <SellerSettingsPage />
      </QueryClientProvider>,
    )

    expect(await screen.findByText('아직 판매자 신청을 하지 않았어요')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '판매자 신청하러 가기' })).toHaveAttribute(
      'href',
      '/seller-apply',
    )
    expect(calls).toBe(1)
  })
})
