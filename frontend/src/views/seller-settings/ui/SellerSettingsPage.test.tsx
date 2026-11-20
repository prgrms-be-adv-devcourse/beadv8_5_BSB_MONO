import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
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

const ACCOUNT: memberModel.MyPayoutAccountDto = {
  registered: true,
  bankCode: '004',
  accountHolderName: '김러너',
  accountHolderType: 'PERSONAL',
  accountNumberLast4: '0678',
}
const NO_ACCOUNT: memberModel.MyPayoutAccountDto = { registered: false }
const ME: memberModel.MemberDto = {
  id: 1,
  email: 'runner@crewrun.kr',
  phone: '01012345678',
  createDate: '2026-09-01T09:00:00',
}
const ACCOUNT_URL = '*/api/v1/member/sellers/me/payout-account'

function renderPage(
  seller: Partial<memberModel.SellerDto> = {},
  account: memberModel.MyPayoutAccountDto = ACCOUNT,
) {
  server.use(
    http.get('*/api/v1/member/sellers/me', () =>
      HttpResponse.json({ status: 200, message: '성공', data: { ...SELLER, ...seller } }),
    ),
    http.get(ACCOUNT_URL, () => HttpResponse.json({ status: 200, message: '성공', data: account })),
    http.get('*/api/v1/member/members/me', () =>
      HttpResponse.json({ status: 200, message: '성공', data: ME }),
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
    // 피그마 S06-5: 화면 제목과 사이드바 메뉴 이름이 같은 「판매자 정보」
    expect(screen.getByRole('heading', { level: 1, name: '판매자 정보' })).toBeInTheDocument()
    expect(screen.getByText('판매자 종류')).toBeInTheDocument()
    expect(screen.getByText('크루런 스포츠')).toBeInTheDocument()
    expect(screen.getByText('김러너')).toBeInTheDocument()
    expect(screen.getByText('승인')).toBeInTheDocument()
    expect(screen.getByText('2026. 10. 6.')).toBeInTheDocument()
    // Clarity 화면 녹화에서 사업자등록번호 · 상호 · 대표자 이름을 가린다
    for (const text of ['123-45-67890', '크루런 스포츠', '김러너']) {
      expect(screen.getByText(text).closest('[data-clarity-mask="true"]')).not.toBeNull()
    }
  })

  it('연락처(휴대전화 · 이메일)와 가입일 · 신청일 · 승인일을 함께 보여 주고 녹화에서 가린다', async () => {
    renderPage()

    expect(await screen.findByText('010-1234-5678')).toBeInTheDocument()
    expect(screen.getByText('runner@crewrun.kr')).toBeInTheDocument()
    expect(screen.getByText('2026. 9. 1.')).toBeInTheDocument()
    expect(screen.getByText('2026. 10. 5.')).toBeInTheDocument()
    expect(screen.getByText('2026. 10. 6.')).toBeInTheDocument()
    for (const label of ['휴대전화', '이메일', '가입일', '신청일', '승인일']) {
      expect(screen.getByText(label)).toBeInTheDocument()
    }
    for (const text of ['010-1234-5678', 'runner@crewrun.kr']) {
      expect(screen.getByText(text).closest('[data-clarity-mask="true"]')).not.toBeNull()
    }
  })

  it('심사 중이면 승인일 없이 신청일만 보여 준다', async () => {
    renderPage({ status: 'PENDING', reviewedAt: null })

    expect(await screen.findByText('신청일')).toBeInTheDocument()
    expect(screen.queryByText('승인일')).not.toBeInTheDocument()
  })

  it('비사업자는 판매자 종류와 성명만 보여 준다', async () => {
    renderPage({ sellerType: 'INDIVIDUAL', businessName: null, businessNumber: null })

    expect(await screen.findByText('비사업자')).toBeInTheDocument()
    expect(screen.getByText('성명')).toBeInTheDocument()
    expect(screen.queryByText('사업자등록번호')).not.toBeInTheDocument()
  })

  it('계좌가 있으면 은행 · 예금주 · 계좌번호 끝 4자리만 보여 주고 입력 칸은 숨긴다', async () => {
    renderPage()

    expect(await screen.findByText('국민은행')).toBeInTheDocument()
    expect(screen.getByText('**** 0678')).toBeInTheDocument()
    expect(screen.getAllByText('김러너').length).toBeGreaterThan(1)
    expect(screen.getByRole('button', { name: '수정하기' })).toBeInTheDocument()
    expect(screen.queryByLabelText('계좌번호')).not.toBeInTheDocument()
  })

  it('계좌가 없으면 지급 보류 안내와 등록 입력을 보여 준다', async () => {
    renderPage({}, NO_ACCOUNT)

    expect(await screen.findByText('등록된 정산 계좌가 없어요')).toBeInTheDocument()
    expect(screen.getByText('등록 전까지는 정산금 지급이 보류돼요.')).toBeInTheDocument()
    expect(screen.getByLabelText('계좌번호')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '저장하기' })).toBeInTheDocument()
    // 계좌가 없으면 돌아갈 화면이 없어 취소 버튼을 두지 않는다
    expect(screen.queryByRole('button', { name: '취소' })).not.toBeInTheDocument()
  })

  it('승인 전 판매자는 계좌를 부르지 않고 승인 뒤 등록하라고 안내한다', async () => {
    let calls = 0
    renderPage({ status: 'PENDING', reviewedAt: null })
    server.use(
      http.get(ACCOUNT_URL, () => {
        calls += 1
        return HttpResponse.json({ status: 200, message: '성공', data: ACCOUNT })
      }),
    )

    expect(await screen.findByText('승인된 뒤에 정산 계좌를 등록할 수 있어요')).toBeInTheDocument()
    expect(screen.queryByLabelText('계좌번호')).not.toBeInTheDocument()
    expect(calls).toBe(0)
  })

  it('계좌를 입력해 저장하면 숫자만 보내고 저장된 계좌 끝 4자리를 보여 준다', async () => {
    const user = userEvent.setup()
    let body: unknown
    renderPage({}, NO_ACCOUNT)
    server.use(
      http.put(ACCOUNT_URL, async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({
          status: 200,
          message: '저장',
          data: { ...ACCOUNT, bankCode: '004', accountNumberLast4: '0123' },
        })
      }),
    )

    await user.type(await screen.findByLabelText('계좌번호'), '333-2000-100123')
    await user.click(screen.getByRole('button', { name: '저장하기' }))

    expect(await screen.findByText('**** 0123')).toBeInTheDocument()
    expect(body).toEqual({
      bankCode: '004',
      accountNumber: '3332000100123',
      accountHolderType: 'PERSONAL',
    })
    expect(screen.queryByLabelText('계좌번호')).not.toBeInTheDocument()
  })

  it('예금주 확인에 실패하면 서버 문구를 보여 주고 입력은 그대로 둔다', async () => {
    const user = userEvent.setup()
    renderPage()
    server.use(
      http.put(ACCOUNT_URL, () =>
        HttpResponse.json(
          {
            status: 400,
            code: 'SELLER_ACCOUNT_NOT_VERIFIED',
            message: '예금주 정보가 맞지 않아요. 계좌번호와 계좌 명의를 확인해 주세요.',
            data: null,
          },
          { status: 400 },
        ),
      ),
    )

    await user.click(await screen.findByRole('button', { name: '수정하기' }))
    await user.type(screen.getByLabelText('계좌번호'), '1234567890')
    await user.click(screen.getByRole('button', { name: '저장하기' }))

    expect(
      await screen.findByText('예금주 정보가 맞지 않아요. 계좌번호와 계좌 명의를 확인해 주세요.'),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('계좌번호')).toHaveValue('1234567890')
  })

  it('계좌번호 형식이 틀리면 요청을 보내지 않는다', async () => {
    const user = userEvent.setup()
    let puts = 0
    renderPage({}, NO_ACCOUNT)
    server.use(
      http.put(ACCOUNT_URL, () => {
        puts += 1
        return HttpResponse.json({ status: 200, message: '저장', data: ACCOUNT })
      }),
    )

    await user.click(await screen.findByRole('button', { name: '저장하기' }))
    expect(await screen.findByText('계좌번호를 입력해 주세요.')).toBeInTheDocument()
    await user.type(screen.getByLabelText('계좌번호'), '12345')
    await user.click(screen.getByRole('button', { name: '저장하기' }))
    expect(await screen.findByText('계좌번호는 숫자 6~16자리로 입력해 주세요.')).toBeInTheDocument()
    await waitFor(() => expect(puts).toBe(0))
  })

  it('수정하다가 취소하면 계좌 보기로 돌아간다', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: '수정하기' }))
    await user.click(screen.getByRole('button', { name: '취소' }))

    expect(screen.getByText('**** 0678')).toBeInTheDocument()
  })

  it('비사업자는 사업자 명의를 고를 수 없다', async () => {
    renderPage({ sellerType: 'INDIVIDUAL', businessName: null, businessNumber: null }, NO_ACCOUNT)

    expect(await screen.findByRole('radio', { name: '사업자 명의' })).toHaveAttribute(
      'aria-disabled',
      'true',
    )
    expect(screen.getByRole('radio', { name: '본인 명의' })).not.toHaveAttribute(
      'aria-disabled',
      'true',
    )
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

    expect(await screen.findByText('판매자만 쓸 수 있어요')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '판매자 신청하기' })).toHaveAttribute(
      'href',
      '/seller-apply',
    )
    expect(calls).toBe(1)
  })
})
