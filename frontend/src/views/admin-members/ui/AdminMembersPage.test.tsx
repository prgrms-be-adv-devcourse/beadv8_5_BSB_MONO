import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'

import { setAccessToken } from '@/shared/api'
import { server } from '@/shared/testing'

import { ADMIN_MEMBER_ERRORS } from '../model/admin-members-api'
import { AdminMembersPage } from './AdminMembersPage'

const CHARGES = '*/api/v1/cash/admin/charge-reviews'
const IDENTITY = '*/api/v1/member/admin/members/identity-review'

const REVIEW = {
  memberId: 12,
  nickname: '러너1',
  joinedAt: '2026-11-10T09:00:00',
  chargeAmount: 300000,
  chargedAt: '2026-11-10T21:25:35',
  joinWindowChargeTotal: 600000,
}
const member = (id: number, ciVerified: boolean) => ({
  id,
  nickname: `러너${id}`,
  maskedName: '홍*동',
  maskedPhone: '010-****-5678',
  maskedEmail: 'ho***@crewrun.com',
  ciVerified,
  joinedAt: '2026-11-02T09:00:00',
})

const ok = (data: unknown) => HttpResponse.json({ status: 200, message: '조회', data })
const chargePage = (content: unknown[], totalElements = content.length, totalPages = 1) =>
  ok({ content, page: 0, size: 20, totalElements, totalPages })
const identityPage = (groups: unknown[], totalElements = groups.length, totalPages = 1) =>
  ok({ groups, page: 0, size: 20, totalElements, totalPages })
const errorResponse = (status: number, code: string) =>
  HttpResponse.json({ status, code, message: '서버 문구', data: null }, { status })

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <AdminMembersPage />
    </QueryClientProvider>,
  )
}

beforeEach(() => setAccessToken('access-token'))

describe('AdminMembersPage 탭', () => {
  it('탭 셋을 보이고 두 API 탭에 totalElements를 배지로 붙인다 (누르기 전에도)', async () => {
    server.use(
      http.get(CHARGES, () => chargePage([REVIEW], 7)),
      http.get(IDENTITY, () => identityPage([{ members: [member(12, false)] }], 3)),
    )
    renderPage()

    expect(screen.getByRole('tab', { name: /전체 회원/ })).toBeInTheDocument()
    await waitFor(() =>
      expect(screen.getByRole('tab', { name: /확인이 필요한 계정/ })).toHaveTextContent('3'),
    )
    await waitFor(() =>
      expect(screen.getByRole('tab', { name: /고액 충전 확인/ })).toHaveTextContent('7'),
    )
  })

  it('전체 회원 탭에는 기존 화면이 보인다', () => {
    renderPage()

    expect(screen.getByText('개인정보를 조회하면 기록이 남아요')).toBeInTheDocument()
    expect(screen.getByRole('table', { name: '회원 주문' })).toBeInTheDocument()
    expect(screen.getByText('회원 상세')).toBeInTheDocument()
  })
})

describe('AdminMembersPage 고액 충전 확인', () => {
  it('회원 한 명을 한 줄로 보이고 금액과 날짜를 형식에 맞춘다', async () => {
    server.use(http.get(CHARGES, () => chargePage([REVIEW])))
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('tab', { name: /고액 충전 확인/ }))

    const table = await screen.findByRole('table', { name: '고액 충전 확인' })
    const rows = within(table).getAllByRole('row')
    expect(rows).toHaveLength(2)
    expect(within(rows[1]).getByText('러너1')).toBeInTheDocument()
    expect(within(rows[1]).getByText('12')).toBeInTheDocument()
    expect(within(rows[1]).getByText('2026. 11. 10 09:00')).toBeInTheDocument()
    expect(within(rows[1]).getByText('₩300,000')).toBeInTheDocument()
    expect(within(rows[1]).getByText('2026. 11. 10 21:25')).toBeInTheDocument()
    expect(within(rows[1]).getByText('₩600,000')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '확인 완료' })).not.toBeInTheDocument()
    expect(screen.queryByRole('navigation', { name: '페이지' })).not.toBeInTheDocument()
  })

  it('비어 있으면 빈 화면 문구를 보인다', async () => {
    server.use(http.get(CHARGES, () => chargePage([])))
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('tab', { name: /고액 충전 확인/ }))

    expect(await screen.findByText('확인할 충전이 없어요')).toBeInTheDocument()
  })

  it('403이면 관리자만 이용할 수 있다고 알린다', async () => {
    server.use(http.get(CHARGES, () => errorResponse(403, 'CASH_ADMIN_FORBIDDEN')))
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('tab', { name: /고액 충전 확인/ }))

    expect(await screen.findByText(ADMIN_MEMBER_ERRORS.notAdmin)).toBeInTheDocument()
  })

  it('503이면 회원 정보를 확인하지 못했다고 알린다', async () => {
    server.use(http.get(CHARGES, () => errorResponse(503, 'CASH_MEMBER_SERVICE_UNAVAILABLE')))
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('tab', { name: /고액 충전 확인/ }))

    expect(await screen.findByText(ADMIN_MEMBER_ERRORS.memberUnavailable)).toBeInTheDocument()
  })

  it('페이지가 둘 이상이면 다음 페이지를 불러온다 (page는 0부터)', async () => {
    const requested: string[] = []
    server.use(
      http.get(CHARGES, ({ request }) => {
        requested.push(new URL(request.url).searchParams.get('page') ?? '')
        return chargePage([REVIEW], 25, 2)
      }),
    )
    const user = userEvent.setup()
    renderPage()
    await user.click(await screen.findByRole('tab', { name: /고액 충전 확인/ }))

    await user.click(await screen.findByRole('button', { name: '다음 페이지' }))

    await screen.findByRole('table', { name: '고액 충전 확인' })
    expect(requested).toContain('1')
  })
})

describe('AdminMembersPage 확인이 필요한 계정', () => {
  it('묶음마다 표를 만들고 CI 있음·없음 배지를 보인다', async () => {
    server.use(
      http.get(IDENTITY, () => identityPage([{ members: [member(12, false), member(13, true)] }])),
    )
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('tab', { name: /확인이 필요한 계정/ }))

    const tables = await screen.findAllByRole('table', { name: '확인이 필요한 계정' })
    expect(tables).toHaveLength(1)
    const rows = within(tables[0]).getAllByRole('row')
    expect(rows).toHaveLength(3)
    expect(within(rows[1]).getByText('러너12')).toBeInTheDocument()
    expect(within(rows[1]).getByText('홍*동')).toBeInTheDocument()
    expect(within(rows[1]).getByText('010-****-5678')).toBeInTheDocument()
    expect(within(rows[1]).getByText('ho***@crewrun.com')).toBeInTheDocument()
    expect(within(rows[1]).getByText('CI 없음')).toBeInTheDocument()
    expect(within(rows[1]).getByText('2026. 11. 02 09:00')).toBeInTheDocument()
    expect(within(rows[2]).getByText('CI 있음')).toBeInTheDocument()
  })

  it('비어 있으면 빈 화면 문구를 보인다', async () => {
    server.use(http.get(IDENTITY, () => identityPage([])))
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('tab', { name: /확인이 필요한 계정/ }))

    expect(await screen.findByText('확인이 필요한 계정이 없어요')).toBeInTheDocument()
  })

  it('403 MEMBER_NOT_ADMIN이면 관리자만 이용할 수 있다고 알린다', async () => {
    server.use(http.get(IDENTITY, () => errorResponse(403, 'MEMBER_NOT_ADMIN')))
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('tab', { name: /확인이 필요한 계정/ }))

    expect(await screen.findByText(ADMIN_MEMBER_ERRORS.notAdmin)).toBeInTheDocument()
  })

  it('그 밖의 오류면 불러오지 못했다고 알린다', async () => {
    server.use(http.get(IDENTITY, () => new HttpResponse(null, { status: 500 })))
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('tab', { name: /확인이 필요한 계정/ }))

    expect(await screen.findByText(ADMIN_MEMBER_ERRORS.loadFailed)).toBeInTheDocument()
  })
})
