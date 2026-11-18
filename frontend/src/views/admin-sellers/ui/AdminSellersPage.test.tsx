import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'

import { setAccessToken, type memberModel } from '@/shared/api'
import { server } from '@/shared/testing'

import { ADMIN_SELLER_ERRORS } from '../model/admin-sellers-api'
import { AdminSellersPage } from './AdminSellersPage'

const BUSINESS: memberModel.SellerDto = {
  id: 1,
  memberId: 11,
  sellerType: 'BUSINESS',
  businessName: '데브시스터즈러닝',
  businessNumber: '123-45-67890',
  representativeName: '김주최',
  status: 'PENDING',
  requestedAt: '2026-10-04T10:12:00',
  reviewedAt: undefined,
  rejectReason: undefined,
}
const INDIVIDUAL: memberModel.SellerDto = {
  ...BUSINESS,
  id: 2,
  memberId: 12,
  sellerType: 'INDIVIDUAL',
  businessName: undefined,
  businessNumber: undefined,
  representativeName: '이춘천',
  requestedAt: '2026-10-05T09:40:00',
}

const LIST = '*/api/v1/member/admin/sellers'

const listResponse = (data: memberModel.SellerDto[]) => ({ status: 200, message: '조회', data })
const oneResponse = (data: memberModel.SellerDto) => ({ status: 200, message: '처리', data })
const errorResponse = (status: number, code: string, message = '서버 문구') =>
  HttpResponse.json({ status, code, message, data: null }, { status })

/** status 쿼리별 목록. 승인·반려가 끝나면 바뀐 목록을 돌려주도록 테스트가 고친다. */
function useLists(lists: Partial<Record<string, memberModel.SellerDto[]>>) {
  server.use(
    http.get(LIST, ({ request }) => {
      const status = new URL(request.url).searchParams.get('status') ?? ''
      return HttpResponse.json(listResponse(lists[status] ?? []))
    }),
  )
}

function renderPage() {
  setAccessToken('access-token')
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <AdminSellersPage />
    </QueryClientProvider>,
  )
}

beforeEach(() => setAccessToken('access-token'))

describe('AdminSellersPage 목록', () => {
  it('승인 대기 신청을 표로 보이고 개수를 탭에 붙인다 (비사업자는 대표자 이름 · 번호 —)', async () => {
    useLists({ PENDING: [BUSINESS, INDIVIDUAL] })
    renderPage()

    const table = await screen.findByRole('table', { name: '판매자 신청' })
    await screen.findAllByText('데브시스터즈러닝')
    const rows = within(table).getAllByRole('row')
    expect(rows).toHaveLength(3)
    expect(within(rows[1]).getByText('10/4')).toBeInTheDocument()
    expect(within(rows[1]).getByText('데브시스터즈러닝')).toBeInTheDocument()
    expect(within(rows[1]).getByText('사업자')).toBeInTheDocument()
    expect(within(rows[2]).getByText('이춘천')).toBeInTheDocument()
    expect(within(rows[2]).getByText('비사업자')).toBeInTheDocument()
    expect(within(rows[2]).getByText('—')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /승인 대기/ })).toHaveTextContent('2')
  })

  it('승인·반려 탭은 그 상태 목록을 보이고 처리 버튼 없이 상태와 반려 사유를 보인다', async () => {
    useLists({
      PENDING: [BUSINESS],
      REJECTED: [{ ...INDIVIDUAL, status: 'REJECTED', rejectReason: '성명이 맞지 않아요.' }],
    })
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('tab', { name: '반려' }))

    expect(await screen.findByText('사유: 성명이 맞지 않아요.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '승인' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '반려' })).not.toBeInTheDocument()
  })

  it('목록 조회가 403 MEMBER_NOT_ADMIN이면 관리자만 이용할 수 있다고 알린다', async () => {
    server.use(http.get(LIST, () => errorResponse(403, 'MEMBER_NOT_ADMIN')))
    renderPage()

    expect(await screen.findByText(ADMIN_SELLER_ERRORS.notAdmin)).toBeInTheDocument()
  })

  it('목록 조회가 그 밖의 오류면 불러오지 못했다고 알린다', async () => {
    server.use(http.get(LIST, () => new HttpResponse(null, { status: 500 })))
    renderPage()

    expect(await screen.findByText(ADMIN_SELLER_ERRORS.loadFailed)).toBeInTheDocument()
  })
})

describe('AdminSellersPage 승인', () => {
  it('승인하면 approval을 보내고 목록을 다시 불러온다', async () => {
    const lists: Partial<Record<string, memberModel.SellerDto[]>> = { PENDING: [BUSINESS] }
    let approvedId: string | undefined
    useLists(lists)
    server.use(
      http.post(`${LIST}/:id/approval`, ({ params }) => {
        approvedId = String(params.id)
        lists.PENDING = []
        return HttpResponse.json(oneResponse({ ...BUSINESS, status: 'APPROVED' }))
      }),
    )
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: '승인' }))

    expect(await screen.findByText('이 상태의 신청이 없어요.')).toBeInTheDocument()
    expect(approvedId).toBe('1')
  })

  it.each([
    [409, 'SELLER_ALREADY_REVIEWED', ADMIN_SELLER_ERRORS.alreadyReviewed],
    [404, 'SELLER_NOT_FOUND', ADMIN_SELLER_ERRORS.notFound],
    [403, 'MEMBER_NOT_ADMIN', ADMIN_SELLER_ERRORS.notAdmin],
  ])('승인이 %i %s로 실패하면 그 코드에 맞는 문구를 보인다', async (status, code, message) => {
    useLists({ PENDING: [BUSINESS] })
    server.use(http.post(`${LIST}/:id/approval`, () => errorResponse(status, code)))
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: '승인' }))

    expect(await screen.findByText(message)).toBeInTheDocument()
  })

  it('이미 심사한 신청(409)이면 목록을 새로 불러온다', async () => {
    const lists: Partial<Record<string, memberModel.SellerDto[]>> = { PENDING: [BUSINESS] }
    useLists(lists)
    server.use(
      http.post(`${LIST}/:id/approval`, () => {
        lists.PENDING = []
        return errorResponse(409, 'SELLER_ALREADY_REVIEWED')
      }),
    )
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: '승인' }))

    expect(await screen.findByText('이 상태의 신청이 없어요.')).toBeInTheDocument()
  })
})

describe('AdminSellersPage 반려', () => {
  async function openRejectDialog(user: ReturnType<typeof userEvent.setup>) {
    await user.click(await screen.findByRole('button', { name: '반려' }))
    return screen.findByRole('alertdialog')
  }

  it('사유를 적고 반려하면 rejection에 사유를 담아 보내고 목록을 다시 불러온다', async () => {
    const lists: Partial<Record<string, memberModel.SellerDto[]>> = { PENDING: [BUSINESS] }
    let body: unknown
    useLists(lists)
    server.use(
      http.post(`${LIST}/:id/rejection`, async ({ request }) => {
        body = await request.json()
        lists.PENDING = []
        return HttpResponse.json(oneResponse({ ...BUSINESS, status: 'REJECTED' }))
      }),
    )
    const user = userEvent.setup()
    renderPage()

    const dialog = await openRejectDialog(user)
    await user.type(within(dialog).getByLabelText(/반려 사유/), '  번호를 확인할 수 없어요.  ')
    await user.click(within(dialog).getByRole('button', { name: '반려하기' }))

    expect(await screen.findByText('이 상태의 신청이 없어요.')).toBeInTheDocument()
    expect(body).toEqual({ reason: '번호를 확인할 수 없어요.' })
  })

  it('사유가 비어 있으면 보내지 않고 사유를 입력하라고 알린다', async () => {
    useLists({ PENDING: [BUSINESS] })
    let called = false
    server.use(
      http.post(`${LIST}/:id/rejection`, () => {
        called = true
        return HttpResponse.json(oneResponse(BUSINESS))
      }),
    )
    const user = userEvent.setup()
    renderPage()

    const dialog = await openRejectDialog(user)
    await user.click(within(dialog).getByRole('button', { name: '반려하기' }))

    expect(await within(dialog).findByText(ADMIN_SELLER_ERRORS.reasonRequired)).toBeInTheDocument()
    expect(called).toBe(false)
  })

  it.each([
    [409, 'SELLER_ALREADY_REVIEWED', ADMIN_SELLER_ERRORS.alreadyReviewed],
    [404, 'SELLER_NOT_FOUND', ADMIN_SELLER_ERRORS.notFound],
    [403, 'MEMBER_NOT_ADMIN', ADMIN_SELLER_ERRORS.notAdmin],
  ])(
    '반려가 %i %s로 실패하면 창을 닫고 그 코드에 맞는 문구를 보인다',
    async (status, code, message) => {
      useLists({ PENDING: [BUSINESS] })
      server.use(http.post(`${LIST}/:id/rejection`, () => errorResponse(status, code)))
      const user = userEvent.setup()
      renderPage()

      const dialog = await openRejectDialog(user)
      await user.type(within(dialog).getByLabelText(/반려 사유/), '사유')
      await user.click(within(dialog).getByRole('button', { name: '반려하기' }))

      expect(await screen.findByText(message)).toBeInTheDocument()
      expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
    },
  )

  it('400 같은 그 밖의 오류는 서버 문구를 그대로 보인다', async () => {
    useLists({ PENDING: [BUSINESS] })
    server.use(
      http.post(`${LIST}/:id/rejection`, () =>
        errorResponse(400, 'INVALID_REQUEST', '반려 사유는 500자 이하로 작성해주세요.'),
      ),
    )
    const user = userEvent.setup()
    renderPage()

    const dialog = await openRejectDialog(user)
    await user.type(within(dialog).getByLabelText(/반려 사유/), '사유')
    await user.click(within(dialog).getByRole('button', { name: '반려하기' }))

    expect(await screen.findByText('반려 사유는 500자 이하로 작성해주세요.')).toBeInTheDocument()
  })
})
