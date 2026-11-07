import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import type { ReactElement } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { memberApi, setAccessToken, type memberModel } from '@/shared/api'
import { memberMock, server } from '@/shared/testing'

import { SELLER_API_ERRORS } from '../model/seller-apply-api'
import { SellerApplicationPage, SellerApplyPage, SellerApplyResultPage } from './SellerApplyPages'

const push = vi.fn()
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push, back: vi.fn(), replace: vi.fn() }),
  usePathname: () => '/seller-apply',
}))

beforeEach(() => {
  push.mockClear()
  // 기본은 신청 전(404). 명세 예시(반려)가 신청 폼을 미리 채우지 않게 한다.
  server.use(
    http.get('*/api/v1/member/sellers/me', () =>
      errorResponse(404, 'SELLER_NOT_FOUND', '판매자 신청 내역이 없습니다.'),
    ),
  )
})

const SELLER: memberModel.SellerDto = {
  id: 1,
  memberId: 1,
  sellerType: 'BUSINESS',
  businessName: '크루런 스포츠',
  businessNumber: '123-45-67890',
  representativeName: '김러너',
  status: 'PENDING',
  requestedAt: '2026-10-05T21:25:35',
  reviewedAt: undefined,
  rejectReason: undefined,
}

const sellerResponse = (seller: Partial<memberModel.SellerDto>, status = 200) => ({
  status,
  message: '성공',
  data: { ...SELLER, ...seller },
})

const errorResponse = (status: number, code: string, message: string) =>
  HttpResponse.json({ status, code, message, data: null }, { status })

function renderWithClient(ui: ReactElement) {
  setAccessToken('access-token')
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return {
    client,
    ...render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>),
  }
}

async function fillBusinessForm(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('사업자등록번호'), '123-45-67890')
  await user.type(screen.getByLabelText('상호'), '크루런 스포츠')
  await user.type(screen.getByLabelText('대표자'), '김러너')
  await user.type(screen.getByLabelText('계좌번호'), '110-123-000678')
  await user.click(screen.getByRole('checkbox', { name: /판매자 이용약관/ }))
}

describe('SellerApplyPage 신청 폼 검사', () => {
  it('약관에 동의하기 전에는 신청하기가 꺼져 있다', () => {
    renderWithClient(<SellerApplyPage />)
    expect(screen.getByRole('button', { name: '신청하기' })).toBeDisabled()
  })

  it('사업자 필수 칸을 비우면 오류를 보여 주고 이동하지 않는다', async () => {
    const user = userEvent.setup()
    renderWithClient(<SellerApplyPage />)
    await user.click(screen.getByRole('checkbox', { name: /판매자 이용약관/ }))
    await user.click(screen.getByRole('button', { name: '신청하기' }))
    expect(await screen.findByText('사업자등록번호를 입력해 주세요.')).toBeInTheDocument()
    expect(screen.getByText('상호를 입력해 주세요.')).toBeInTheDocument()
    expect(screen.getByText('대표자를 입력해 주세요.')).toBeInTheDocument()
    expect(push).not.toHaveBeenCalled()
  })

  it('사업자등록번호 형식이 틀리면 막는다', async () => {
    const user = userEvent.setup()
    renderWithClient(<SellerApplyPage />)
    await user.type(screen.getByLabelText('사업자등록번호'), '1234567890')
    await user.type(screen.getByLabelText('상호'), '(주)러닝')
    await user.type(screen.getByLabelText('대표자'), '김주최')
    await user.click(screen.getByRole('checkbox', { name: /판매자 이용약관/ }))
    await user.click(screen.getByRole('button', { name: '신청하기' }))
    expect(
      await screen.findByText('사업자등록번호를 123-45-67890처럼 입력해 주세요.'),
    ).toBeInTheDocument()
    expect(push).not.toHaveBeenCalled()
  })

  it('상호가 100자를 넘으면 막는다', async () => {
    const user = userEvent.setup()
    renderWithClient(<SellerApplyPage />)
    await user.type(screen.getByLabelText('사업자등록번호'), '123-45-67890')
    await user.click(screen.getByLabelText('상호'))
    await user.paste('가'.repeat(101))
    await user.type(screen.getByLabelText('대표자'), '김주최')
    await user.click(screen.getByRole('checkbox', { name: /판매자 이용약관/ }))
    await user.click(screen.getByRole('button', { name: '신청하기' }))
    expect(await screen.findByText('상호는 100자 이하로 입력해 주세요.')).toBeInTheDocument()
    expect(push).not.toHaveBeenCalled()
  })

  it('사업자가 아니면 성명 · 연락처만 검사한다', async () => {
    const user = userEvent.setup()
    renderWithClient(<SellerApplyPage />)
    await user.click(screen.getByRole('radio', { name: '사업자가 아니에요' }))
    await user.click(screen.getByRole('checkbox', { name: /판매자 이용약관/ }))
    await user.click(screen.getByRole('button', { name: '신청하기' }))
    expect(await screen.findByText('성명을 입력해 주세요.')).toBeInTheDocument()
    expect(screen.getByText('연락처를 입력해 주세요.')).toBeInTheDocument()
    expect(push).not.toHaveBeenCalled()
  })
})

describe('SellerApplyPage 신청 보내기', () => {
  it('사업자는 판매자 정보와 정산 계좌를 보내고 결과 화면으로 간다', async () => {
    let body: unknown
    server.use(
      http.post('*/api/v1/member/sellers', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json(sellerResponse({}, 201), { status: 201 })
      }),
    )
    const user = userEvent.setup()
    const { client } = renderWithClient(<SellerApplyPage />)
    await fillBusinessForm(user)
    await user.click(screen.getByRole('button', { name: '신청하기' }))

    await waitFor(() => expect(push).toHaveBeenCalledWith('/seller-apply/result'))
    expect(body).toEqual({
      sellerType: 'BUSINESS',
      businessName: '크루런 스포츠',
      businessNumber: '123-45-67890',
      representativeName: '김러너',
      bankCode: '004',
      // 하이픈은 빼고 숫자만 보낸다
      accountNumber: '110123000678',
      accountHolderType: 'PERSONAL',
    })
    // 결과 화면이 다시 부르지 않고 쓰도록 응답을 내 신청 캐시에 넣는다
    expect(client.getQueryData(memberApi.getFindMineQueryKey())).toMatchObject({
      data: { status: 'PENDING' },
    })
  })

  it('사업자가 아니면 성명을 대표자 칸으로 보내고 상호·사업자등록번호는 보내지 않는다', async () => {
    let body: unknown
    server.use(
      http.post('*/api/v1/member/sellers', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json(sellerResponse({ sellerType: 'INDIVIDUAL' }, 201), { status: 201 })
      }),
    )
    const user = userEvent.setup()
    renderWithClient(<SellerApplyPage />)
    await user.click(screen.getByRole('radio', { name: '사업자가 아니에요' }))
    await user.type(screen.getByLabelText('성명'), '이춘천')
    await user.type(screen.getByLabelText('연락처'), '010-1111-2222')
    await user.type(screen.getByLabelText('계좌번호'), '110123000678')
    await user.click(screen.getByRole('checkbox', { name: /판매자 이용약관/ }))
    await user.click(screen.getByRole('button', { name: '신청하기' }))

    await waitFor(() => expect(push).toHaveBeenCalledWith('/seller-apply/result'))
    expect(body).toEqual({
      sellerType: 'INDIVIDUAL',
      representativeName: '이춘천',
      bankCode: '004',
      accountNumber: '110123000678',
      accountHolderType: 'PERSONAL',
    })
  })

  it('409 SELLER_ALREADY_APPLIED는 이미 신청했다고 안내하고 이동하지 않는다', async () => {
    server.use(
      http.post('*/api/v1/member/sellers', () =>
        errorResponse(409, 'SELLER_ALREADY_APPLIED', '서버 문구'),
      ),
    )
    const user = userEvent.setup()
    renderWithClient(<SellerApplyPage />)
    await fillBusinessForm(user)
    await user.click(screen.getByRole('button', { name: '신청하기' }))

    expect(await screen.findByText(SELLER_API_ERRORS.alreadyApplied)).toBeInTheDocument()
    expect(push).not.toHaveBeenCalled()
  })

  it('400 같은 그 밖의 오류는 서버 문구를 그대로 보여 준다', async () => {
    server.use(
      http.post('*/api/v1/member/sellers', () =>
        errorResponse(400, 'INVALID_REQUEST', '사업자등록번호는 숫자 10자리로 작성해주세요.'),
      ),
    )
    const user = userEvent.setup()
    renderWithClient(<SellerApplyPage />)
    await fillBusinessForm(user)
    await user.click(screen.getByRole('button', { name: '신청하기' }))

    expect(
      await screen.findByText('사업자등록번호는 숫자 10자리로 작성해주세요.'),
    ).toBeInTheDocument()
    expect(push).not.toHaveBeenCalled()
  })

  it('서버 문구가 없는 오류는 일반 문구를 보여 준다', async () => {
    server.use(http.post('*/api/v1/member/sellers', () => new HttpResponse(null, { status: 502 })))
    const user = userEvent.setup()
    renderWithClient(<SellerApplyPage />)
    await fillBusinessForm(user)
    await user.click(screen.getByRole('button', { name: '신청하기' }))

    expect(await screen.findByText(SELLER_API_ERRORS.unavailable)).toBeInTheDocument()
  })
})

describe('SellerApplyResultPage 상태별 카드', () => {
  it.each([
    ['PENDING', '심사 중', '관리자가 신청 내용을 확인하고 있어요'],
    ['REJECTED', '반려', '신청이 반려됐어요'],
    ['APPROVED', '승인', '판매자로 승인됐어요'],
  ] as const)('%s 상태는 카드 하나만 보여 준다', async (status, badge, title) => {
    server.use(
      memberMock.getFindMineMockHandler(
        sellerResponse({
          status,
          rejectReason: status === 'REJECTED' ? '사업자등록번호를 확인할 수 없습니다.' : undefined,
        }),
      ),
    )
    renderWithClient(<SellerApplyResultPage />)

    expect(await screen.findByText(title)).toBeInTheDocument()
    expect(screen.getAllByRole('heading', { level: 2 })).toHaveLength(1)
    expect(screen.getByText(badge)).toBeInTheDocument()
  })

  it('반려 카드는 서버가 준 반려 사유를 그대로 보여 준다', async () => {
    server.use(
      memberMock.getFindMineMockHandler(
        sellerResponse({
          status: 'REJECTED',
          rejectReason: '사업자등록번호를 확인할 수 없습니다.',
        }),
      ),
    )
    renderWithClient(<SellerApplyResultPage />)

    expect(
      await screen.findByText('사유: 사업자등록번호를 확인할 수 없습니다.'),
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '고쳐서 다시 신청하기' })).toHaveAttribute(
      'href',
      '/seller-apply',
    )
  })

  it('심사 중 배지는 Neutral 톤이다', async () => {
    server.use(memberMock.getFindMineMockHandler(sellerResponse({ status: 'PENDING' })))
    renderWithClient(<SellerApplyResultPage />)
    expect((await screen.findByText('심사 중')).className).toContain('bg-bg-subtle')
  })

  it('404 SELLER_NOT_FOUND는 오류가 아니라 신청하러 가도록 안내한다', async () => {
    server.use(
      http.get('*/api/v1/member/sellers/me', () =>
        errorResponse(404, 'SELLER_NOT_FOUND', '판매자 신청 내역이 없습니다.'),
      ),
    )
    renderWithClient(<SellerApplyResultPage />)

    expect(await screen.findByText('아직 판매자 신청을 하지 않았어요')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '판매자 신청하러 가기' })).toHaveAttribute(
      'href',
      '/seller-apply',
    )
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('그 밖의 오류는 불러오지 못했다고 알린다', async () => {
    server.use(
      http.get('*/api/v1/member/sellers/me', () => new HttpResponse(null, { status: 500 })),
    )
    renderWithClient(<SellerApplyResultPage />)

    expect(await screen.findByText(SELLER_API_ERRORS.loadFailed)).toBeInTheDocument()
  })
})

describe('SellerApplyResultPage 결과 확인 기록', () => {
  const countResultChecks = (
    respond: () => Response = () => HttpResponse.json(sellerResponse({})),
  ) => {
    const calls = { count: 0 }
    server.use(
      http.post('*/api/v1/member/sellers/me/result-check', () => {
        calls.count += 1
        return respond()
      }),
    )
    return calls
  }

  it.each(['APPROVED', 'REJECTED'] as const)(
    '%s 결과를 처음 보여 주면 result-check를 한 번 부르고 내 신청을 다시 불러온다',
    async (status) => {
      let mineCalls = 0
      server.use(
        http.get('*/api/v1/member/sellers/me', () => {
          mineCalls += 1
          return HttpResponse.json(
            sellerResponse({
              status,
              resultCheckedAt: mineCalls === 1 ? null : '2026-10-06T12:30:41',
            }),
          )
        }),
      )
      const calls = countResultChecks()
      renderWithClient(<SellerApplyResultPage />)

      await screen.findAllByRole('heading', { level: 2 })
      await waitFor(() => expect(calls.count).toBe(1))
      // 다시 불러온 응답에는 시각이 있어 또 부르지 않는다
      await waitFor(() => expect(mineCalls).toBe(2))
      expect(calls.count).toBe(1)
    },
  )

  it('서버가 resultCheckedAt을 주지 않아도(#220 전) 부른다', async () => {
    server.use(memberMock.getFindMineMockHandler(sellerResponse({ status: 'APPROVED' })))
    const calls = countResultChecks()
    renderWithClient(<SellerApplyResultPage />)

    await waitFor(() => expect(calls.count).toBe(1))
  })

  it('이미 확인한 결과면 부르지 않는다', async () => {
    server.use(
      memberMock.getFindMineMockHandler(
        sellerResponse({ status: 'APPROVED', resultCheckedAt: '2026-10-06T12:30:41' }),
      ),
    )
    const calls = countResultChecks()
    renderWithClient(<SellerApplyResultPage />)

    expect(await screen.findByText('판매자로 승인됐어요')).toBeInTheDocument()
    expect(calls.count).toBe(0)
  })

  it('심사 중이면 부르지 않는다', async () => {
    server.use(
      memberMock.getFindMineMockHandler(
        sellerResponse({ status: 'PENDING', resultCheckedAt: null }),
      ),
    )
    const calls = countResultChecks()
    renderWithClient(<SellerApplyResultPage />)

    expect(await screen.findByText('관리자가 신청 내용을 확인하고 있어요')).toBeInTheDocument()
    expect(calls.count).toBe(0)
  })

  it.each([
    [
      '409 SELLER_NOT_REVIEWED',
      () => errorResponse(409, 'SELLER_NOT_REVIEWED', '아직 심사 중인 판매자 신청입니다.'),
    ],
    ['404(#220 전 백엔드)', () => new HttpResponse(null, { status: 404 })],
    ['500', () => new HttpResponse(null, { status: 500 })],
  ])(
    'result-check가 %s로 실패해도 결과 카드를 그대로 보여 주고 다시 부르지 않는다',
    async (_, respond) => {
      server.use(
        memberMock.getFindMineMockHandler(
          sellerResponse({ status: 'APPROVED', resultCheckedAt: null }),
        ),
      )
      const calls = countResultChecks(respond)
      renderWithClient(<SellerApplyResultPage />)

      expect(await screen.findByText('판매자로 승인됐어요')).toBeInTheDocument()
      await waitFor(() => expect(calls.count).toBe(1))
      expect(screen.getByRole('link', { name: '판매자 홈으로' })).toHaveAttribute('href', '/seller')
      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
      expect(calls.count).toBe(1)
    },
  )
})

describe('SellerApplyPage 정산 계좌', () => {
  it('신청 때 은행 · 계좌번호 · 계좌 명의를 받고, 판매자 설정에서 등록한다는 안내는 없다', () => {
    renderWithClient(<SellerApplyPage />)
    expect(screen.getByLabelText('계좌번호')).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: '본인 명의' })).toBeChecked()
    expect(screen.getByRole('radio', { name: '사업자 명의' })).toBeInTheDocument()
    expect(screen.queryByText('정산 계좌는 판매자 설정에서 등록해요')).not.toBeInTheDocument()
  })

  it('계좌번호를 비우거나 형식이 틀리면 막는다', async () => {
    const user = userEvent.setup()
    renderWithClient(<SellerApplyPage />)
    await user.click(screen.getByRole('radio', { name: '사업자가 아니에요' }))
    await user.type(screen.getByLabelText('성명'), '이춘천')
    await user.type(screen.getByLabelText('연락처'), '010-1111-2222')
    await user.click(screen.getByRole('checkbox', { name: /판매자 이용약관/ }))
    await user.click(screen.getByRole('button', { name: '신청하기' }))
    expect(await screen.findByText('계좌번호를 입력해 주세요.')).toBeInTheDocument()

    await user.type(screen.getByLabelText('계좌번호'), '12345')
    await user.click(screen.getByRole('button', { name: '신청하기' }))
    expect(await screen.findByText('계좌번호는 숫자 6~16자리로 입력해 주세요.')).toBeInTheDocument()
    expect(push).not.toHaveBeenCalled()
  })

  it('사업자가 아니면 사업자 명의를 고를 수 없다', async () => {
    const user = userEvent.setup()
    renderWithClient(<SellerApplyPage />)
    await user.click(screen.getByRole('radio', { name: '사업자 명의' }))
    expect(screen.getByRole('radio', { name: '사업자 명의' })).toBeChecked()

    await user.click(screen.getByRole('radio', { name: '사업자가 아니에요' }))
    // Base UI 라디오는 disabled 속성 대신 aria-disabled로 잠근다
    expect(screen.getByRole('radio', { name: '사업자 명의' })).toHaveAttribute(
      'aria-disabled',
      'true',
    )
    expect(screen.getByRole('radio', { name: '본인 명의' })).toBeChecked()
  })

  it('사업자 명의를 고르면 BUSINESS로 보낸다', async () => {
    let body: unknown
    server.use(
      http.post('*/api/v1/member/sellers', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json(sellerResponse({}, 201), { status: 201 })
      }),
    )
    const user = userEvent.setup()
    renderWithClient(<SellerApplyPage />)
    await fillBusinessForm(user)
    await user.click(screen.getByRole('radio', { name: '사업자 명의' }))
    await user.click(screen.getByRole('button', { name: '신청하기' }))

    await waitFor(() => expect(push).toHaveBeenCalledWith('/seller-apply/result'))
    expect(body).toMatchObject({ accountHolderType: 'BUSINESS' })
  })

  it.each([
    [
      400,
      'SELLER_ACCOUNT_NOT_VERIFIED',
      '예금주 정보가 맞지 않아요. 계좌번호와 계좌 명의를 확인해 주세요.',
    ],
    [
      429,
      'SELLER_ACCOUNT_VERIFY_TOO_MANY_REQUESTS',
      '계좌 확인은 하루 5번까지 할 수 있어요. 내일 다시 시도해 주세요.',
    ],
    [503, 'SELLER_ACCOUNT_VERIFY_UNAVAILABLE', '잠시 뒤 다시 시도해 주세요.'],
    [
      400,
      'SELLER_IDENTITY_REVERIFY_REQUIRED',
      '본인 명의 계좌는 휴대폰 번호를 다시 인증한 뒤 신청해 주세요.',
    ],
  ])('%i %s는 서버 문구를 그대로 보여 주고 이동하지 않는다', async (status, code, message) => {
    server.use(http.post('*/api/v1/member/sellers', () => errorResponse(status, code, message)))
    const user = userEvent.setup()
    renderWithClient(<SellerApplyPage />)
    await fillBusinessForm(user)
    await user.click(screen.getByRole('button', { name: '신청하기' }))

    expect(await screen.findByText(message)).toBeInTheDocument()
    expect(push).not.toHaveBeenCalled()
  })
})

describe('SellerApplyPage 반려 뒤 다시 신청', () => {
  it('반려된 신청의 값을 폼에 채운다', async () => {
    server.use(memberMock.getFindMineMockHandler(sellerResponse({ status: 'REJECTED' })))
    renderWithClient(<SellerApplyPage />)

    await waitFor(() => expect(screen.getByLabelText('사업자등록번호')).toHaveValue('123-45-67890'))
    expect(screen.getByLabelText('상호')).toHaveValue('크루런 스포츠')
    expect(screen.getByLabelText('대표자')).toHaveValue('김러너')
  })
})

describe('SellerApplyResultPage 신청 내용 보기', () => {
  it('심사 중 카드의 버튼은 잠근 신청 내용으로 간다', async () => {
    server.use(memberMock.getFindMineMockHandler(sellerResponse({ status: 'PENDING' })))
    renderWithClient(<SellerApplyResultPage />)
    expect(await screen.findByRole('link', { name: '신청 내용 보기' })).toHaveAttribute(
      'href',
      '/seller-apply/mine',
    )
  })
})

describe('SellerApplicationPage 잠근 신청 내용', () => {
  it('내가 낸 값을 잠근 칸에 채우고 상태 배지와 신청일을 보여 준다', async () => {
    server.use(memberMock.getFindMineMockHandler(sellerResponse({ status: 'PENDING' })))
    renderWithClient(<SellerApplicationPage />)

    expect(await screen.findByLabelText('사업자등록번호')).toHaveValue('123-45-67890')
    expect(screen.getByLabelText('사업자등록번호')).toBeDisabled()
    expect(screen.getByLabelText('상호')).toBeDisabled()
    expect(screen.getByLabelText('대표자')).toHaveValue('김러너')
    expect(screen.getByRole('radio', { name: '사업자예요' })).toBeChecked()
    expect(screen.getByText('심사 중')).toBeInTheDocument()
    expect(screen.getByText('신청일 2026. 10. 5.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '신청하기' })).not.toBeInTheDocument()
  })

  it('서버가 받지 않는 연락처 · 계좌 칸은 빈 칸으로 두지 않는다', async () => {
    server.use(
      memberMock.getFindMineMockHandler(
        sellerResponse({
          sellerType: 'INDIVIDUAL',
          businessName: null,
          businessNumber: null,
          representativeName: '이춘천',
        }),
      ),
    )
    renderWithClient(<SellerApplicationPage />)

    expect(await screen.findByLabelText('성명')).toHaveValue('이춘천')
    expect(screen.queryByLabelText('연락처')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('계좌번호')).not.toBeInTheDocument()
    expect(screen.queryByText('정산 계좌는 판매자 설정에서 등록해요')).not.toBeInTheDocument()
  })

  it('반려된 신청은 상태에 맞는 안내와 다시 신청하는 링크를 보여 준다', async () => {
    server.use(memberMock.getFindMineMockHandler(sellerResponse({ status: 'REJECTED' })))
    renderWithClient(<SellerApplicationPage />)

    expect(await screen.findByText(/반려된 신청이에요/)).toBeInTheDocument()
    expect(screen.queryByText(/심사 중에는 고칠 수 없어요/)).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: '고쳐서 다시 신청하기' })).toHaveAttribute(
      'href',
      '/seller-apply',
    )
  })
})
