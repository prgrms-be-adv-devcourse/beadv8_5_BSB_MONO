import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getAuthStatus, setAccessToken } from '@/shared/api'
import { memberMock, orderMock, server } from '@/shared/testing'

import { MyPage } from './MyPage'

const replace = vi.fn()
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), replace }),
  usePathname: () => '/me',
}))

const reloadToLogin = vi.hoisted(() => vi.fn())
vi.mock('@/features/auth/lib/login-redirect', async (importOriginal) => ({
  ...(await importOriginal<object>()),
  reloadToLogin,
}))

beforeEach(() => {
  replace.mockClear()
  reloadToLogin.mockClear()
})

describe('MyPage 로그아웃', () => {
  it('로그아웃을 누르면 로그아웃 API를 부르고, 응답이 온 뒤 문서를 새로 불러 로그인 화면으로 간다', async () => {
    let authorization: string | null = null
    // 응답을 붙잡아 두고, 응답 전에는 이동하지 않는지 본다
    let release = () => {}
    const held = new Promise<void>((resolve) => (release = resolve))
    server.use(
      http.post('*/api/v1/auth/logout', async ({ request }) => {
        authorization = request.headers.get('Authorization')
        await held
        return HttpResponse.json({ status: 200, message: '로그아웃 성공', data: null })
      }),
    )
    setAccessToken('access-token')
    const user = userEvent.setup()
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MyPage />
      </QueryClientProvider>,
    )

    await user.click(screen.getByRole('button', { name: '로그아웃' }))

    await vi.waitFor(() => expect(authorization).toBe('Bearer access-token'))
    expect(reloadToLogin).not.toHaveBeenCalled()
    release()
    await vi.waitFor(() => expect(reloadToLogin).toHaveBeenCalledTimes(1))
    expect(authorization).toBe('Bearer access-token')
    expect(replace).not.toHaveBeenCalled()
    // 새로 불러오기 전까지 토큰을 그대로 두어 화면이 로그아웃 상태로 다시 그려지지 않는다
    expect(getAuthStatus()).toBe('authenticated')
  })

  it('로그아웃 요청이 실패해도 이 브라우저에서는 로그아웃 상태로 바꾼다', async () => {
    server.use(http.post('*/api/v1/auth/logout', () => HttpResponse.error()))
    setAccessToken('access-token')
    const user = userEvent.setup()
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MyPage />
      </QueryClientProvider>,
    )

    await user.click(screen.getByRole('button', { name: '로그아웃' }))

    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/login'))
    expect(getAuthStatus()).toBe('anonymous')
  })
})

describe('MyPage 회원 정보', () => {
  it('내 정보 조회 API의 닉네임 · 이메일 · 이름 · 휴대폰(가림) · 생년월일을 보여 준다', async () => {
    server.use(
      memberMock.getFindMeMockHandler({
        status: 200,
        message: '내 정보를 조회했습니다.',
        data: {
          id: 1,
          nickname: '달리는곰',
          email: 'bear@crewrun.kr',
          phone: '01098765432',
          name: '김곰',
          birthDate: '1990-01-31',
        },
      }),
    )
    setAccessToken('access-token')
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MyPage />
      </QueryClientProvider>,
    )

    expect(await screen.findByText('달리는곰')).toBeInTheDocument()
    expect(screen.getByText('bear@crewrun.kr')).toBeInTheDocument()
    expect(screen.getByText('김곰')).toBeInTheDocument()
    expect(screen.getByText('010-****-5432')).toBeInTheDocument()
    expect(screen.getByText('1990.01.31')).toBeInTheDocument()
    // Clarity 화면 녹화에서 회원 정보 글자를 가린다
    for (const text of ['달리는곰', 'bear@crewrun.kr', '김곰', '010-****-5432', '1990.01.31']) {
      expect(screen.getByText(text).closest('[data-clarity-mask="true"]')).not.toBeNull()
    }
  })

  it('마이는 보기만 한다: 수정 버튼 없이 계정 설정으로 들어가고, 탈퇴로 바로 가지 않는다', async () => {
    setAccessToken('access-token')
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MyPage />
      </QueryClientProvider>,
    )

    expect(await screen.findByRole('link', { name: /계정 설정/ })).toHaveAttribute(
      'href',
      '/me/settings',
    )
    expect(screen.queryByRole('link', { name: /수정/ })).not.toBeInTheDocument()
    expect(screen.queryByText(/직접 고칠 수 있어요/)).not.toBeInTheDocument()
    expect(document.querySelector('a[href="/me/withdraw"]')).toBeNull()
    expect(document.querySelector('a[href="/me/edit"]')).toBeNull()
  })

  it('내 정보를 불러오지 못하면 안내 문구를 띄운다', async () => {
    server.use(
      http.get('*/api/v1/member/members/me', () =>
        HttpResponse.json(
          { status: 404, message: '존재하지 않는 회원입니다.', data: null },
          { status: 404 },
        ),
      ),
    )
    setAccessToken('access-token')
    render(
      <QueryClientProvider
        client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
      >
        <MyPage />
      </QueryClientProvider>,
    )

    expect(await screen.findByRole('alert')).toHaveTextContent('회원 정보를 불러오지 못했어요')
  })
})

describe('MyPage 참가 대회 수', () => {
  const renderPage = () => {
    setAccessToken('access-token')
    render(
      <QueryClientProvider
        client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
      >
        <MyPage />
      </QueryClientProvider>,
    )
  }

  it('내 참가권 화면과 같은 목록(주문 API)의 개수를 보여 준다', async () => {
    renderPage()

    // 목의 주문 목록에서 참가권이 되는 것은 결제한 주문 1개다
    expect(await screen.findByText('1개')).toBeInTheDocument()
  })

  it('취소한 참가권은 세지 않는다', async () => {
    // 결제한 주문 1개에 참가권 2장: 하나는 참가 확정, 하나는 취소
    server.use(
      http.get('*/api/v1/order/orders/:orderId', () => {
        const example = orderMock.getFindById1ResponseMock()
        const item = example.data!.items![0]!
        return HttpResponse.json({
          ...example,
          data: {
            ...example.data,
            status: 'PARTIAL_CANCELED',
            items: [
              { ...item, id: 1, status: 'PAID' },
              { ...item, id: 2, participationId: 2, status: 'CANCELED' },
            ],
          },
        })
      }),
    )
    renderPage()

    expect(await screen.findByText('1개')).toBeInTheDocument()
  })

  it('참가권이 없으면(방금 가입한 회원) 0개로 보여 준다', async () => {
    server.use(
      http.get('*/api/v1/order/orders', () =>
        HttpResponse.json({
          status: 200,
          message: '주문 내역을 조회했습니다.',
          data: { orders: [], page: 0, size: 100, totalElements: 0, totalPages: 0 },
        }),
      ),
    )
    renderPage()

    expect(await screen.findByText('0개')).toBeInTheDocument()
  })
})

describe('MyPage 판매자 메뉴', () => {
  const mineResponds = (
    status: 'PENDING' | 'REJECTED' | 'APPROVED',
    resultCheckedAt?: string | null,
  ) =>
    server.use(
      memberMock.getFindMineMockHandler({
        status: 200,
        message: '판매자 신청 내역을 조회했습니다.',
        data: {
          id: 1,
          memberId: 1,
          sellerType: 'BUSINESS',
          businessName: '크루런 스포츠',
          businessNumber: '123-45-67890',
          representativeName: '김러너',
          status,
          requestedAt: '2026-10-05T21:25:35',
          resultCheckedAt,
        },
      }),
    )

  const renderPage = () => {
    setAccessToken('access-token')
    render(
      <QueryClientProvider
        client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
      >
        <MyPage />
      </QueryClientProvider>,
    )
  }

  const sellerLink = (name: RegExp) => screen.findByRole('link', { name })

  it('승인 결과를 아직 안 봤으면(resultCheckedAt null) 판매자 센터가 결과 화면으로 간다', async () => {
    mineResponds('APPROVED', null)
    renderPage()

    expect(await sellerLink(/판매자 센터/)).toHaveAttribute('href', '/seller-apply/result')
  })

  it('서버가 resultCheckedAt을 주지 않아도(#220 전) 안 본 것으로 보고 결과 화면으로 간다', async () => {
    mineResponds('APPROVED', undefined)
    renderPage()

    expect(await sellerLink(/판매자 센터/)).toHaveAttribute('href', '/seller-apply/result')
  })

  it('승인 결과를 이미 봤으면 판매자 센터가 판매자 홈으로 바로 간다', async () => {
    mineResponds('APPROVED', '2026-10-06T12:30:41')
    renderPage()

    expect(await sellerLink(/판매자 센터/)).toHaveAttribute('href', '/seller')
  })

  it.each([
    ['PENDING', null],
    ['REJECTED', null],
    ['REJECTED', '2026-10-06T12:30:41'],
  ] as const)(
    '%s(resultCheckedAt %s)이면 판매자 등록 신청 줄이 결과 화면으로 간다',
    async (status, resultCheckedAt) => {
      mineResponds(status, resultCheckedAt)
      renderPage()

      // 불러오기 전에도 같은 링크라, 응답이 반영될 때까지 판매자 센터가 끝내 안 나오는지도 본다
      await vi.waitFor(async () =>
        expect(await sellerLink(/판매자 등록 신청/)).toHaveAttribute(
          'href',
          '/seller-apply/result',
        ),
      )
      expect(screen.queryByRole('link', { name: /판매자 센터/ })).not.toBeInTheDocument()
    },
  )

  it('신청한 적이 없으면(404 SELLER_NOT_FOUND) 판매자 등록 신청 줄이 신청 화면으로 간다', async () => {
    server.use(
      http.get('*/api/v1/member/sellers/me', () =>
        HttpResponse.json(
          {
            status: 404,
            code: 'SELLER_NOT_FOUND',
            message: '판매자 신청 내역이 없습니다.',
            data: null,
          },
          { status: 404 },
        ),
      ),
    )
    renderPage()

    await vi.waitFor(async () =>
      expect(await sellerLink(/판매자 등록 신청/)).toHaveAttribute('href', '/seller-apply'),
    )
  })

  it('신청 상태를 불러오지 못하면 줄은 그대로 두고 결과 화면으로 보낸다', async () => {
    let called = false
    server.use(
      http.get('*/api/v1/member/sellers/me', () => {
        called = true
        return HttpResponse.error()
      }),
    )
    renderPage()

    await vi.waitFor(() => expect(called).toBe(true))
    expect(await sellerLink(/판매자 등록 신청/)).toHaveAttribute('href', '/seller-apply/result')
  })
})
