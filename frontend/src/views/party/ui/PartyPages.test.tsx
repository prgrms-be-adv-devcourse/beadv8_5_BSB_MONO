import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import {
  completePostcode,
  installPostcodeFake,
  memberMock,
  pickDate,
  productMock,
  server,
} from '@/shared/testing'
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

vi.mock('next/script', async () => ({ default: (await import('@/shared/testing')).FakeScript }))

const COURSE = { raceCourseId: 2, name: '10K', price: 45000, crewRemaining: 86 }

beforeEach(() => {
  replace.mockClear()
  installPostcodeFake()
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
    expect(await screen.findByText('1명 더 모이면 참가할 수 있어요')).toBeInTheDocument()
    expect(screen.getByText('3명 / 최소 4명 · 최대 10명')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '파티 참가하기' })).toHaveAttribute(
      'aria-disabled',
      'true',
    )
  })

  it('머리에 모집 상태 글자, 대회 이름, 크루 · 종목을 보여 주고 진행 단계 줄은 그리지 않는다', async () => {
    renderPage(<PartyDetailPage partyId={1} />)
    expect(
      await screen.findByRole('heading', { name: '2026 용감한 쿠키RUN in 서울' }),
    ).toBeInTheDocument()
    expect(screen.getByText('모집 중')).toBeInTheDocument()
    expect(await screen.findByText('성수런 크루 · 10K')).toBeInTheDocument()
    expect(screen.queryByRole('list', { name: '파티 진행 단계' })).not.toBeInTheDocument()
  })

  it('초대 링크 상자에 쓸 수 있는 기한과 링크 복사 · 새로 만들기를 함께 둔다', async () => {
    renderPage(<PartyDetailPage partyId={1} />)
    const box = (await screen.findByText('초대 링크')).parentElement as HTMLElement
    expect(within(box).getByText(/까지 쓸 수 있어요/)).toBeInTheDocument()
    expect(screen.queryByText(/^링크 · /)).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '링크 복사' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '새로 만들기' })).toBeInTheDocument()
  })

  it('모집 중에는 파티원 줄에 이름과 파티장 · 파티원만 보이고 결제 칩은 없다', async () => {
    renderPage(<PartyDetailPage partyId={1} />)
    expect(await screen.findByText('달리는곰')).toBeInTheDocument()
    expect(screen.getAllByText('파티원')).toHaveLength(2)
    expect(screen.getByText('파티장')).toBeInTheDocument()
    expect(screen.queryByText('결제 전')).not.toBeInTheDocument()
  })

  it('파티 참가하기는 아래 고정 영역에 두고, 꺼진 동안은 연한 파랑으로 보인다', async () => {
    renderPage(<PartyDetailPage partyId={1} />)
    const cta = await screen.findByRole('link', { name: '파티 참가하기' })
    expect(cta).toHaveClass('bg-bg-brand-disabled')
    expect(cta).not.toHaveClass('opacity-40')
    expect(cta.closest('div.border-t')).not.toBeNull()
  })

  it('4명이 모이면 신청 버튼이 켜진다', async () => {
    renderPage(<PartyDetailPage partyId={4} />)
    expect(await screen.findByText('파티로 참가할 수 있어요')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '파티 참가하기' })).toHaveAttribute(
      'aria-disabled',
      'false',
    )
  })

  it('결제 진행 중이면 결제 인원을 세고 결제하지 않은 파티원은 결제 전으로 보여 준다', async () => {
    renderPage(<PartyDetailPage partyId={3} />)
    expect(await screen.findByText('결제 완료 1 / 3명')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '파티원' })).toBeInTheDocument()
    expect(screen.getAllByText('결제 전').length).toBeGreaterThan(0)
  })

  it('결제를 마친 파티장에게는 결제 마감 시각을, 파티원에게는 결제 인원을 보여 준다', async () => {
    const status = (role: string) =>
      http.get('*/api/v1/order/parties/me/race-status', () =>
        HttpResponse.json({
          status: 200,
          message: '성공',
          data: { role, partyId: 3, partyStatus: 'PAYING', myPaid: true },
        }),
      )
    server.use(status('LEADER'))
    const leader = renderPage(<PartyDetailPage partyId={3} />)
    expect(await screen.findByText('파티원 결제를 기다리고 있어요')).toBeInTheDocument()
    expect(screen.getByText('1월 1일 00:00까지 파티원이 결제할 수 있어요')).toBeInTheDocument()
    leader.unmount()

    server.use(status('MEMBER'))
    renderPage(<PartyDetailPage partyId={3} />)
    expect(await screen.findByText('3명 중 1명 결제했어요')).toBeInTheDocument()
    expect(screen.queryByText(/까지 파티원이 결제할 수 있어요/)).not.toBeInTheDocument()
  })

  it('없는 파티면 불러오지 못했다는 화면을 보여 준다', async () => {
    renderPage(<PartyDetailPage partyId={404} />)
    expect(await screen.findByText('파티 정보를 불러오지 못했어요')).toBeInTheDocument()
  })

  it('파티 나가기는 머리글 ⋮ 메뉴에서 열고, 확인하면 크루 홈으로 간다', async () => {
    const user = userEvent.setup()
    renderPage(<PartyDetailPage partyId={1} />)
    await user.click(await screen.findByRole('button', { name: '더보기' }))
    await user.click(await screen.findByRole('menuitem', { name: '파티 나가기' }))
    await user.click(await screen.findByRole('button', { name: '나가기' }))
    await waitFor(() => expect(replace).toHaveBeenCalledWith('/crews/7'))
  })

  it('⋮ 메뉴는 닫혀 있고, 화면 아래에는 파티 나가기 버튼이 없다', async () => {
    renderPage(<PartyDetailPage partyId={1} />)
    const more = await screen.findByRole('button', { name: '더보기' })
    expect(more).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByRole('menuitem', { name: '파티 나가기' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '파티 나가기' })).not.toBeInTheDocument()
  })

  it('⋮ 메뉴 버튼은 main 밖의 머리글(banner) 안에 있다', async () => {
    renderPage(<PartyDetailPage partyId={1} />)
    const more = await screen.findByRole('button', { name: '더보기' })
    expect(within(screen.getByRole('banner')).getByRole('button', { name: '더보기' })).toBe(more)
    expect(within(screen.getByRole('main')).queryByRole('banner')).not.toBeInTheDocument()
  })

  it('⋮ 메뉴는 Esc로 닫히고 나가기 확인 창을 띄우지 않는다', async () => {
    const user = userEvent.setup()
    renderPage(<PartyDetailPage partyId={1} />)
    await user.click(await screen.findByRole('button', { name: '더보기' }))
    expect(await screen.findByRole('menuitem', { name: '파티 나가기' })).toBeInTheDocument()
    await user.keyboard('{Escape}')
    await waitFor(() =>
      expect(screen.queryByRole('menuitem', { name: '파티 나가기' })).not.toBeInTheDocument(),
    )
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
    expect(replace).not.toHaveBeenCalled()
  })

  it('결제가 시작된 파티에는 ⋮ 메뉴가 없다(결제 중에는 나갈 수 없다)', async () => {
    renderPage(<PartyDetailPage partyId={3} />)
    await screen.findByText('결제 완료 1 / 3명')
    expect(screen.queryByRole('button', { name: '더보기' })).not.toBeInTheDocument()
  })
})

describe('파티 상세 · 친구 초대', () => {
  const crewAs = (myRole: 'LEADER' | 'MEMBER') =>
    memberMock.getFindCrewMockHandler({
      status: 200,
      message: '성공',
      data: {
        crewId: 7,
        name: '성수런',
        createdAt: '2026-10-01T00:00:00',
        myRole,
        memberCount: 3,
        members: [],
      },
    })
  const inviteCreated = (inviteToken: string) =>
    memberMock.getCreateCrewInviteMockHandler({
      status: 201,
      message: '성공',
      data: {
        inviteId: 3,
        inviteToken,
        createdAt: '2026-11-13T09:00:00',
        expiresAt: '2026-11-14T09:00:00',
      },
    })

  it('초대 권한이 있는 파티장은 크루 초대 링크에 파티 참가 토큰을 붙여 복사한다', async () => {
    server.use(crewAs('LEADER'), inviteCreated('q7Xk2LmZ9vBn4RtW8yCp1A'))
    const user = userEvent.setup()
    renderPage(<PartyDetailPage partyId={1} />)
    await user.click(await screen.findByRole('button', { name: '친구 초대' }))
    await waitFor(async () =>
      expect(await navigator.clipboard.readText()).toBe(
        `${window.location.origin}/invite/q7Xk2LmZ9vBn4RtW8yCp1A?party=k3Jx9`,
      ),
    )
  })

  it('초대 권한이 없는 파티장은 초대 링크를 만들지 않고 파티 참가 링크만 복사한다', async () => {
    const created = vi.fn()
    server.use(
      crewAs('MEMBER'),
      http.post('*/api/v1/member/crews/:crewId/invites', () => {
        created()
        return HttpResponse.json({}, { status: 403 })
      }),
    )
    const user = userEvent.setup()
    renderPage(<PartyDetailPage partyId={1} />)
    await screen.findByText('1명 더 모이면 참가할 수 있어요')
    expect(screen.queryByRole('button', { name: '친구 초대' })).not.toBeInTheDocument()
    await user.click(await screen.findByRole('button', { name: '링크 복사' }))
    await waitFor(async () =>
      expect(await navigator.clipboard.readText()).toBe(
        `${window.location.origin}/parties/join/k3Jx9`,
      ),
    )
    expect(created).not.toHaveBeenCalled()
  })

  it('초대 링크를 만들지 못하면 이유를 알리고 아무것도 복사하지 않는다', async () => {
    server.use(
      crewAs('LEADER'),
      http.post('*/api/v1/member/crews/:crewId/invites', () =>
        HttpResponse.json(
          { status: 403, code: 'MEMBER_CREW_FORBIDDEN', message: '초대할 권한이 없습니다.' },
          { status: 403 },
        ),
      ),
    )
    const user = userEvent.setup()
    await navigator.clipboard.writeText('처음 값')
    renderPage(<PartyDetailPage partyId={1} />)
    await user.click(await screen.findByRole('button', { name: '친구 초대' }))
    expect((await screen.findAllByText('초대할 권한이 없습니다.')).length).toBeGreaterThan(0)
    expect(await navigator.clipboard.readText()).toBe('처음 값')
  })

  it('만드는 동안 다시 눌러도 초대 링크를 하나만 만든다', async () => {
    const created = vi.fn()
    server.use(
      crewAs('LEADER'),
      http.post('*/api/v1/member/crews/:crewId/invites', async () => {
        created()
        await new Promise((resolve) => setTimeout(resolve, 50))
        return HttpResponse.json({
          status: 201,
          message: '성공',
          data: {
            inviteId: 3,
            inviteToken: 'tok',
            createdAt: '2026-11-13T09:00:00',
            expiresAt: '2026-11-14T09:00:00',
          },
        })
      }),
    )
    const user = userEvent.setup()
    renderPage(<PartyDetailPage partyId={1} />)
    const button = await screen.findByRole('button', { name: '친구 초대' })
    await user.dblClick(button)
    await waitFor(async () => expect(await navigator.clipboard.readText()).toContain('/invite/tok'))
    expect(created).toHaveBeenCalledTimes(1)
  })
})

describe('파티 상세 · 친구 초대 다시 만들기', () => {
  const invited = (count: () => void) =>
    http.post('*/api/v1/member/crews/:crewId/invites', () => {
      count()
      return HttpResponse.json({
        status: 201,
        message: '성공',
        data: {
          inviteId: 3,
          inviteToken: 'tok',
          createdAt: '2026-11-13T09:00:00',
          expiresAt: '2026-11-14T09:00:00',
        },
      })
    })
  const leaderCrew = memberMock.getFindCrewMockHandler({
    status: 200,
    message: '성공',
    data: {
      crewId: 7,
      name: '성수런',
      createdAt: '2026-10-01T00:00:00',
      myRole: 'LEADER',
      memberCount: 3,
      members: [],
    },
  })

  it('참가 링크를 새로 만들면 옛 토큰이 든 초대 링크를 다시 쓰지 않는다', async () => {
    let token = 'k3Jx9'
    const created = vi.fn()
    const detail = () =>
      HttpResponse.json({
        status: 200,
        message: '성공',
        data: {
          id: 1,
          crewId: 7,
          raceId: 100,
          raceCourseId: 2,
          leaderMemberId: 1,
          status: 'RECRUITING',
          paymentDueAt: null,
          joinToken: token,
          joinTokenExpiresAt: '2099-01-01T00:00:00',
          memberCount: 3,
          paidCount: 0,
          members: [],
        },
      })
    server.use(
      leaderCrew,
      invited(created),
      http.get('*/api/v1/order/parties/1', detail),
      http.post('*/api/v1/order/parties/1/join-link', () => {
        token = 'n3w'
        return detail()
      }),
    )
    const user = userEvent.setup()
    renderPage(<PartyDetailPage partyId={1} />)
    await user.click(await screen.findByRole('button', { name: '친구 초대' }))
    await waitFor(() => expect(created).toHaveBeenCalledTimes(1))
    await user.click(screen.getByRole('button', { name: '새로 만들기', hidden: true }))
    await screen.findByRole('dialog', { hidden: true })
    const buttons = screen.getAllByRole('button', { name: '새로 만들기', hidden: true })
    await user.click(buttons[buttons.length - 1])
    await waitFor(() => expect(token).toBe('n3w'))
    await user.click(await screen.findByRole('button', { name: '친구 초대' }))
    await waitFor(async () =>
      expect(await navigator.clipboard.readText()).toBe(
        `${window.location.origin}/invite/tok?party=n3w`,
      ),
    )
  })

  it('복사에 실패한 뒤 다시 눌러도 초대 링크를 새로 만들지 않는다', async () => {
    const created = vi.fn()
    server.use(leaderCrew, invited(created))
    const user = userEvent.setup()
    vi.spyOn(navigator.clipboard, 'writeText').mockRejectedValueOnce(new Error('denied'))
    renderPage(<PartyDetailPage partyId={1} />)
    await user.click(await screen.findByRole('button', { name: '친구 초대' }))
    await waitFor(() => expect(created).toHaveBeenCalledTimes(1))
    await user.click(screen.getByRole('button', { name: '친구 초대' }))
    await waitFor(async () => expect(await navigator.clipboard.readText()).toContain('/invite/tok'))
    expect(created).toHaveBeenCalledTimes(1)
  })

  it('응답에 초대 토큰이 없으면 깨진 링크를 복사하지 않고 오류를 알린다', async () => {
    server.use(
      leaderCrew,
      http.post('*/api/v1/member/crews/:crewId/invites', () =>
        HttpResponse.json({ status: 201, message: '성공', data: {} }),
      ),
    )
    const user = userEvent.setup()
    await navigator.clipboard.writeText('처음 값')
    renderPage(<PartyDetailPage partyId={1} />)
    await user.click(await screen.findByRole('button', { name: '친구 초대' }))
    expect((await screen.findAllByText('잠시 후 다시 시도해 주세요.')).length).toBeGreaterThan(0)
    expect(await navigator.clipboard.readText()).toBe('처음 값')
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

  it('막힌 화면은 이유와 홈으로 가는 버튼을 보여 준다', async () => {
    renderPage(<PartyJoinPage token="notfound" />)
    expect(await screen.findByText('참가 링크를 찾을 수 없어요')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '홈으로 가기' })).toHaveAttribute('href', '/')
  })

  async function fillAndSubmit(user: ReturnType<typeof userEvent.setup>) {
    await user.click(await screen.findByRole('checkbox'))
    await user.type(screen.getByLabelText('참가자 이름'), '김러너')
    await pickDate(user, '생년월일', '1994-06-25')
    await user.click(screen.getByRole('radio', { name: '남성' }))
    await user.type(screen.getByLabelText('휴대폰 번호'), '01012345678')
    await user.type(screen.getByLabelText('비상 연락처 이름'), '박보호')
    await user.type(screen.getByLabelText('비상 연락처 번호'), '01098765432')
    await user.click(screen.getByRole('button', { name: '합류하기' }))
  }

  it('대회 정보를 불러오는 동안은 합류하기를 막고, 불러오면 켠다', async () => {
    let open: () => void = () => {}
    const gate = new Promise<void>((resolve) => (open = resolve))
    server.use(
      http.get('*/api/v1/product/races/:raceId', async () => {
        await gate
        return HttpResponse.json({
          status: 200,
          message: '성공',
          data: { raceId: 100, title: '2026 용감한 쿠키RUN in 서울', courses: [COURSE] },
        })
      }),
    )
    renderPage(<PartyJoinPage token="ok" />)

    expect(await screen.findByRole('button', { name: '합류하기' })).toBeDisabled()
    expect(screen.queryByLabelText('참가자 이름')).not.toBeInTheDocument()
    open()
    expect(await screen.findByLabelText('참가자 이름')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '합류하기' })).toBeEnabled()
  })

  it('대회 정보를 불러오지 못하면 합류하기를 막고, 다시 불러오면 이어 간다', async () => {
    let failing = true
    server.use(
      http.get('*/api/v1/product/races/:raceId', () =>
        failing
          ? HttpResponse.json({ status: 500, message: '오류', data: null }, { status: 500 })
          : HttpResponse.json({
              status: 200,
              message: '성공',
              data: { raceId: 100, title: '2026 용감한 쿠키RUN in 서울', courses: [COURSE] },
            }),
      ),
    )
    const user = userEvent.setup()
    renderPage(<PartyJoinPage token="ok" />)

    expect(await screen.findByRole('alert')).toHaveTextContent('파티 정보를 불러오지 못했어요')
    expect(screen.getByRole('button', { name: '합류하기' })).toBeDisabled()
    failing = false
    await user.click(screen.getByRole('button', { name: '다시 불러오기' }))
    expect(await screen.findByLabelText('참가자 이름')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '합류하기' })).toBeEnabled()
  })

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
    await user.click(await screen.findByRole('button', { name: /명 파티 신청하기/ }))
    expect(await screen.findByText('크루 전용 자리가 2자리 모자랐어요')).toBeInTheDocument()
    expect(screen.getByText('청구된 금액 없음')).toBeInTheDocument()
  })

  it('신청에 성공하면 파티 상세로 간다', async () => {
    const user = userEvent.setup()
    renderPage(<PartyApplyPage partyId={1} />)
    await user.click(await screen.findByRole('button', { name: /명 파티 신청하기/ }))
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
      await screen.findByRole('radio', { name: /10K · 45,000원 · 남은 크루 전용 자리 86자리/ }),
    ).toBeInTheDocument()
  })

  it('크루가 참가 제한 중이면(403) 제한이 끝나는 날을 알리고 크루 홈으로 보낸다', async () => {
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
          ],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
        },
      }),
      http.post('*/api/v1/order/parties', () =>
        HttpResponse.json(
          {
            status: 403,
            code: 'ORDER_CREW_PARTY_RESTRICTED',
            message: '제한 중',
            data: { endAt: '2026-11-20T10:00:00' },
          },
          { status: 403 },
        ),
      ),
    )
    renderPage(<PartyNewPage crewId={7} />)
    await user.click(await screen.findByRole('button', { name: /2026 용감한 쿠키RUN in 서울/ }))
    await user.click(await screen.findByRole('radio', { name: /10K/ }))
    await user.click(screen.getByRole('button', { name: '10K로 참가 정보 적기' }))
    await user.click(await screen.findByRole('checkbox'))
    await user.type(screen.getByLabelText('참가자 이름'), '김러너')
    await pickDate(user, '생년월일', '1994-06-25')
    await user.click(screen.getByRole('radio', { name: '남성' }))
    await user.type(screen.getByLabelText('휴대폰 번호'), '01012345678')
    await user.type(screen.getByLabelText('비상 연락처 이름'), '박보호')
    await user.type(screen.getByLabelText('비상 연락처 번호'), '01098765432')
    await user.click(screen.getByRole('button', { name: '파티 만들기' }))
    expect(await screen.findByText('파티를 만들 수 없어요')).toBeInTheDocument()
    expect(screen.getByText('11월 20일까지 파티 만들기가 제한돼요.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '크루 홈으로' })).toBeInTheDocument()
  })

  it('제한이 끝나는 시각이 자정이면 전날까지 제한된다고 알린다', async () => {
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
          ],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
        },
      }),
      http.post('*/api/v1/order/parties', () =>
        HttpResponse.json(
          {
            status: 403,
            code: 'ORDER_CREW_PARTY_RESTRICTED',
            message: '제한 중',
            data: { endAt: '2026-12-12T00:00:00+09:00' },
          },
          { status: 403 },
        ),
      ),
    )
    renderPage(<PartyNewPage crewId={7} />)
    await user.click(await screen.findByRole('button', { name: /2026 용감한 쿠키RUN in 서울/ }))
    await user.click(await screen.findByRole('radio', { name: /10K/ }))
    await user.click(screen.getByRole('button', { name: '10K로 참가 정보 적기' }))
    await user.click(await screen.findByRole('checkbox'))
    await user.type(screen.getByLabelText('참가자 이름'), '김러너')
    await pickDate(user, '생년월일', '1994-06-25')
    await user.click(screen.getByRole('radio', { name: '남성' }))
    await user.type(screen.getByLabelText('휴대폰 번호'), '01012345678')
    await user.type(screen.getByLabelText('비상 연락처 이름'), '박보호')
    await user.type(screen.getByLabelText('비상 연락처 번호'), '01098765432')
    await user.click(screen.getByRole('button', { name: '파티 만들기' }))
    expect(await screen.findByText('12월 11일까지 파티 만들기가 제한돼요.')).toBeInTheDocument()
  })

  it('크루 없이 들어오면 크루 홈으로 안내한다', () => {
    renderPage(<PartyNewPage />)
    expect(screen.getByText('크루 홈에서 파티를 만들 수 있어요')).toBeInTheDocument()
  })
})

describe('파티 · 키트 배송지', () => {
  const kitCourse = { ...COURSE, kitIncluded: true }
  const raceWith = (course: typeof COURSE) =>
    productMock.getFindRaceMockHandler({
      status: 200,
      message: '성공',
      data: { raceId: 100, title: '2026 용감한 쿠키RUN in 서울', courses: [course] },
    })
  const SEARCH = productMock.getSearchMockHandler({
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
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    },
  })

  /** 합류 · 만들기 요청 본문을 받아 두고 기본 목처럼 성공으로 답한다 */
  function capture(path: 'join' | '') {
    const bodies: { participant: Record<string, unknown> }[] = []
    server.use(
      http.post(`*/api/v1/order/parties${path ? `/${path}` : ''}`, async ({ request }) => {
        bodies.push((await request.json()) as (typeof bodies)[number])
        const body = path
          ? { id: 1, partyId: 1 }
          : { party: { id: 1 }, participation: { id: 1, partyId: 1 } }
        return HttpResponse.json({ status: 201, message: '성공', data: body }, { status: 201 })
      }),
    )
    return bodies
  }

  async function fillParticipant(user: ReturnType<typeof userEvent.setup>) {
    await user.click(await screen.findByRole('checkbox'))
    await user.type(screen.getByLabelText('참가자 이름'), '김러너')
    await pickDate(user, '생년월일', '1994-06-25')
    await user.click(screen.getByRole('radio', { name: '남성' }))
    await user.type(screen.getByLabelText('휴대폰 번호'), '01012345678')
    await user.type(screen.getByLabelText('비상 연락처 이름'), '박보호')
    await user.type(screen.getByLabelText('비상 연락처 번호'), '01098765432')
  }

  async function pickAddress(user: ReturnType<typeof userEvent.setup>) {
    await user.click(screen.getByRole('button', { name: '주소 검색' }))
    completePostcode()
    await user.type(screen.getByLabelText('상세 주소'), '3층')
  }

  const SHIPPING = {
    recipientName: '김러너',
    recipientPhone: '010-1234-5678',
    zipcode: '04768',
    address: '서울특별시 성동구 뚝섬로 273',
    addressDetail: '3층',
  }

  async function openCreateForm(user: ReturnType<typeof userEvent.setup>) {
    renderPage(<PartyNewPage crewId={7} />)
    await user.click(await screen.findByRole('button', { name: /2026 용감한 쿠키RUN in 서울/ }))
    await user.click(await screen.findByRole('radio', { name: /10K/ }))
    await user.click(screen.getByRole('button', { name: '10K로 참가 정보 적기' }))
  }

  describe('합류', () => {
    it('키트가 든 종목이면 주소 검색 · 우편번호 · 주소 · 상세 주소를 받고, 화면 녹화에서 가린다', async () => {
      server.use(raceWith(kitCourse))
      renderPage(<PartyJoinPage token="ok" />)

      const zipcode = await screen.findByLabelText('키트 배송 우편번호')
      expect(zipcode).toHaveAttribute('readonly')
      expect(screen.getByLabelText('키트 배송 주소')).toHaveAttribute('readonly')
      expect(screen.getByRole('button', { name: '주소 검색' })).toBeInTheDocument()
      const detail = screen.getByLabelText('상세 주소')
      for (const field of [zipcode, detail, screen.getByLabelText('키트 배송 주소')]) {
        expect(field.closest('[data-clarity-mask="true"]')).not.toBeNull()
      }
    })

    it('키트가 없는 종목이면 배송지 칸을 보이지 않고 보내지도 않는다', async () => {
      const bodies = capture('join')
      const user = userEvent.setup()
      renderPage(<PartyJoinPage token="ok" />)

      await fillParticipant(user)
      expect(screen.queryByLabelText('키트 배송 주소')).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: '주소 검색' })).not.toBeInTheDocument()
      await user.click(screen.getByRole('button', { name: '합류하기' }))

      await waitFor(() => expect(replace).toHaveBeenCalledWith('/parties/1'))
      expect(bodies[0].participant).not.toHaveProperty('shippingAddress')
      // 달력으로 고른 생년월일이 YYYY-MM-DD로 나간다
      expect(bodies[0].participant.birth).toBe('1994-06-25')
    })

    it('키트가 든 종목에서 주소를 비우면 안내하고 요청을 보내지 않는다', async () => {
      server.use(raceWith(kitCourse))
      const bodies = capture('join')
      const user = userEvent.setup()
      renderPage(<PartyJoinPage token="ok" />)

      await fillParticipant(user)
      await user.click(screen.getByRole('button', { name: '합류하기' }))

      expect(await screen.findByText('키트를 받을 주소를 입력해 주세요.')).toBeInTheDocument()
      expect(bodies).toHaveLength(0)
    })

    it('주소를 고르면 받는 사람을 참가자 본인으로 해서 함께 보낸다', async () => {
      server.use(raceWith(kitCourse))
      const bodies = capture('join')
      const user = userEvent.setup()
      renderPage(<PartyJoinPage token="ok" />)

      await fillParticipant(user)
      await pickAddress(user)
      await user.click(screen.getByRole('button', { name: '합류하기' }))

      await waitFor(() => expect(replace).toHaveBeenCalledWith('/parties/1'))
      expect(bodies[0].participant.shippingAddress).toEqual(SHIPPING)
    })
  })

  describe('만들기', () => {
    it('키트가 든 종목이면 파티장의 배송지를 받아 함께 보낸다', async () => {
      server.use(SEARCH, raceWith(kitCourse))
      const bodies = capture('')
      const user = userEvent.setup()
      await openCreateForm(user)

      await fillParticipant(user)
      expect(
        screen.getByLabelText('키트 배송 주소').closest('[data-clarity-mask="true"]'),
      ).not.toBeNull()
      await pickAddress(user)
      await user.click(screen.getByRole('button', { name: '파티 만들기' }))

      await waitFor(() => expect(replace).toHaveBeenCalledWith('/parties/1'))
      expect(bodies[0].participant.shippingAddress).toEqual(SHIPPING)
    })

    it('키트가 든 종목에서 주소를 비우면 안내하고 요청을 보내지 않는다', async () => {
      server.use(SEARCH, raceWith(kitCourse))
      const bodies = capture('')
      const user = userEvent.setup()
      await openCreateForm(user)

      await fillParticipant(user)
      await user.click(screen.getByRole('button', { name: '파티 만들기' }))

      expect(await screen.findByText('키트를 받을 주소를 입력해 주세요.')).toBeInTheDocument()
      expect(bodies).toHaveLength(0)
    })

    it('키트가 없는 종목이면 배송지 칸을 보이지 않고 보내지도 않는다', async () => {
      server.use(SEARCH)
      const bodies = capture('')
      const user = userEvent.setup()
      await openCreateForm(user)

      await fillParticipant(user)
      expect(screen.queryByLabelText('키트 배송 주소')).not.toBeInTheDocument()
      await user.click(screen.getByRole('button', { name: '파티 만들기' }))

      await waitFor(() => expect(replace).toHaveBeenCalledWith('/parties/1'))
      expect(bodies[0].participant).not.toHaveProperty('shippingAddress')
    })
  })
})
