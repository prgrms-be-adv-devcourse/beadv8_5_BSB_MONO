import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { memberMock, productMock, server } from '@/shared/testing'
import { ToastProvider } from '@/shared/ui'

import { PartyApplyPage } from './PartyApplyPage'
import { PartyDetailPage } from './PartyDetailPage'
import { PartyJoinPage } from './PartyJoinPage'
import { PartyNewPage } from './PartyNewPage'

const replace = vi.fn()
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), replace }),
  usePathname: () => '/parties/1',
}))

const COURSE = { raceCourseId: 2, name: '10K', price: 45000, crewRemaining: 86 }

beforeEach(() => {
  replace.mockClear()
  server.use(
    productMock.getFindRaceMockHandler({
      status: 200,
      message: '성공',
      data: {
        raceId: 100,
        title: '2026 용감한 쿠키RUN in 서울',
        crewMinCount: 4,
        crewMaxCount: 10,
        courses: [COURSE],
      },
    }),
    memberMock.getFindCrewMockHandler({
      status: 200,
      message: '성공',
      data: {
        crewId: 7,
        name: '성수런',
        createdAt: '2026-10-01T00:00:00',
        myRole: 'MEMBER',
        memberCount: 3,
        members: [],
      },
    }),
  )
})

function renderPage(ui: ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>{ui}</ToastProvider>
    </QueryClientProvider>,
  )
}

describe('파티 상세', () => {
  it('파티장이 모집 중이면 더 필요한 인원을 알리고 신청 버튼을 막는다', async () => {
    renderPage(<PartyDetailPage partyId={1} />)
    expect(await screen.findByText('1명 더 모이면 신청할 수 있어요')).toBeInTheDocument()
    expect(screen.getByText('3명 / 최소 4명 · 최대 10명')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '파티 신청하기' })).toHaveAttribute(
      'aria-disabled',
      'true',
    )
  })

  it('4명이 모이면 신청 버튼이 켜진다', async () => {
    renderPage(<PartyDetailPage partyId={4} />)
    expect(await screen.findByText('파티를 신청할 수 있어요')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '파티 신청하기' })).toHaveAttribute(
      'aria-disabled',
      'false',
    )
  })

  it('없는 파티면 불러오지 못했다는 화면을 보여 준다', async () => {
    renderPage(<PartyDetailPage partyId={404} />)
    expect(await screen.findByText('파티 정보를 불러오지 못했어요')).toBeInTheDocument()
  })

  it('파티 나가기를 확인하면 크루 홈으로 간다', async () => {
    const user = userEvent.setup()
    renderPage(<PartyDetailPage partyId={1} />)
    await user.click(await screen.findByRole('button', { name: '파티 나가기' }))
    await user.click(await screen.findByRole('button', { name: '나가기' }))
    await waitFor(() => expect(replace).toHaveBeenCalledWith('/crews/7'))
  })
})

describe('파티 합류', () => {
  it.each([
    ['expired', '참가 링크 기간이 끝났어요'],
    ['notfound', '참가 링크를 찾을 수 없어요'],
    ['closed', '모집이 끝난 파티예요'],
  ])('링크 %s는 합류 대신 이유를 보여 준다', async (token, title) => {
    renderPage(<PartyJoinPage token={token} />)
    expect(await screen.findByText(title)).toBeInTheDocument()
  })

  async function fillAndSubmit(user: ReturnType<typeof userEvent.setup>) {
    await user.click(await screen.findByRole('checkbox'))
    const [name, emergencyName] = screen.getAllByLabelText('이름')
    await user.type(name, '김러너')
    fireEvent.change(screen.getByLabelText('생년월일'), { target: { value: '1994-06-25' } })
    await user.click(screen.getByRole('radio', { name: '남성' }))
    await user.type(screen.getByLabelText('휴대폰 번호'), '01012345678')
    await user.type(emergencyName, '박보호')
    await user.type(screen.getByLabelText('전화번호'), '01098765432')
    await user.click(screen.getByRole('button', { name: '파티에 합류하기' }))
  }

  it('정보를 적고 합류하면 파티 상세로 간다', async () => {
    const user = userEvent.setup()
    renderPage(<PartyJoinPage token="ok" />)
    await fillAndSubmit(user)
    await waitFor(() => expect(replace).toHaveBeenCalledWith('/parties/1'))
  })

  it('합류가 인원 초과로 거절되면 인원이 다 찼다는 화면으로 바뀐다', async () => {
    const user = userEvent.setup()
    server.use(
      http.post('*/api/v1/order/parties/preview', () =>
        HttpResponse.json({
          status: 200,
          message: '성공',
          data: {
            id: 1,
            crewId: 7,
            raceId: 100,
            raceCourseId: 2,
            status: 'RECRUITING',
            leaderNickname: '달리는곰',
            memberCount: 10,
            joinTokenExpiresAt: '2099-01-01T00:00:00',
            joinLinkAlive: true,
          },
        }),
      ),
    )
    renderPage(<PartyJoinPage token="full" />)
    await fillAndSubmit(user)
    expect(await screen.findByText('파티 인원이 다 찼어요')).toBeInTheDocument()
  })

  it('개인 신청이 남아 있으면 취소할지 묻는다', async () => {
    const user = userEvent.setup()
    renderPage(<PartyJoinPage token="individual" />)
    await fillAndSubmit(user)
    expect(await screen.findByText('이 대회에 개인 신청이 있어요')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '개인 신청 취소하기' })).toBeInTheDocument()
  })
})

describe('파티 신청', () => {
  it('크루 전용 자리가 모자라면 결과 화면을 보여 준다', async () => {
    const user = userEvent.setup()
    renderPage(<PartyApplyPage partyId={9} />)
    await user.click(await screen.findByRole('button', { name: '파티 신청하기' }))
    expect(await screen.findByText('크루 전용 자리가 2자리 모자랐어요')).toBeInTheDocument()
    expect(screen.getByText('청구된 금액 없음')).toBeInTheDocument()
  })

  it('신청에 성공하면 파티 상세로 간다', async () => {
    const user = userEvent.setup()
    renderPage(<PartyApplyPage partyId={1} />)
    await user.click(await screen.findByRole('button', { name: '파티 신청하기' }))
    await waitFor(() => expect(replace).toHaveBeenCalledWith('/parties/1'))
  })
})

describe('파티 만들기', () => {
  it('크루 예매 중인 대회를 고르면 종목을 고를 수 있다', async () => {
    const user = userEvent.setup()
    server.use(
      productMock.getSearchMockHandler({
        status: 200,
        message: '성공',
        data: {
          races: [
            {
              raceId: 100,
              title: '2026 용감한 쿠키RUN in 서울',
              crewSaleStartAt: '2020-01-01T00:00:00',
              crewSaleEndAt: '2099-01-01T18:00:00',
            },
            {
              raceId: 101,
              title: 'CHUNCHEON 10K 2026',
              crewSaleStartAt: '2020-01-01T00:00:00',
              crewSaleEndAt: '2020-02-01T18:00:00',
            },
          ],
          page: 0,
          size: 20,
          totalElements: 2,
          totalPages: 1,
        },
      }),
    )
    renderPage(<PartyNewPage crewId={7} />)
    expect(await screen.findByRole('button', { name: /CHUNCHEON 10K 2026/ })).toBeDisabled()
    await user.click(await screen.findByRole('button', { name: /2026 용감한 쿠키RUN in 서울/ }))
    expect(
      await screen.findByRole('radio', { name: /10K · ₩45,000 · 남은 크루 전용 자리 86자리/ }),
    ).toBeInTheDocument()
  })

  it('크루 없이 들어오면 크루 홈으로 안내한다', () => {
    renderPage(<PartyNewPage />)
    expect(screen.getByText('크루 홈에서 파티를 만들 수 있어요')).toBeInTheDocument()
  })
})
