import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { setAccessToken } from '@/shared/api'
import { currentSearch, memberMock, resetNavigationMock, server } from '@/shared/testing'
import { ToastProvider } from '@/shared/ui'

import { CrewHomePage } from './CrewHomePage'
import { CrewInvitePage } from './CrewInvitePage'
import { CrewInvitesPage } from './CrewInvitesPage'
import { CrewMembersPage } from './CrewMembersPage'
import { CrewNewPage } from './CrewNewPage'
import { MyCrewsPage } from './MyCrewsPage'

const replace = vi.fn()
vi.mock('next/navigation', async () => {
  const nav = (await import('@/shared/testing')).navigationMock('/crews')
  return {
    ...nav,
    useRouter: () => {
      const router = nav.useRouter()
      // 탭 바꾸기(scroll: false)는 주소 쿼리만 바꾸고, 화면 이동만 replace 기록에 남긴다
      return {
        ...router,
        replace: (url: string, options?: { scroll?: boolean }) =>
          options?.scroll === false ? router.replace(url) : replace(url),
      }
    },
  }
})

beforeEach(() => {
  replace.mockClear()
  resetNavigationMock()
  setAccessToken('access-token')
})

function renderPage(page: ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>{page}</ToastProvider>
    </QueryClientProvider>,
  )
}

const failWith = (status: number, code: string, message = '서버 문구') =>
  HttpResponse.json({ status, code, message, data: null }, { status })

describe('MyCrewsPage (M09-0)', () => {
  it('가입한 크루를 크루원 수 · 역할과 함께 보여 주고 크루 홈으로 잇는다', async () => {
    renderPage(<MyCrewsPage />)
    expect(await screen.findByText('가입한 크루 2')).toBeInTheDocument()
    const leaderRow = screen.getByRole('link', { name: /새벽한강크루/ })
    expect(leaderRow).toHaveAttribute('href', '/crews/1')
    expect(within(leaderRow).getByText(/크루원 \d+명 · 크루장/)).toBeInTheDocument()
    expect(
      within(screen.getByRole('link', { name: /판교점심런/ })).getByText(/크루원 \d+명 · 크루원/),
    ).toBeInTheDocument()
  })

  it('새 크루 만들기는 제목 줄 오른쪽 링크이고, 아래 고정 버튼은 없다', async () => {
    renderPage(<MyCrewsPage />)
    await screen.findByText('가입한 크루 2')
    expect(screen.getByRole('link', { name: '+ 새 크루 만들기' })).toHaveAttribute(
      'href',
      '/crews/new',
    )
    expect(screen.queryByRole('link', { name: '새 크루 만들기' })).not.toBeInTheDocument()
  })

  it('머리글은 로고와 알림이고 아래에 주요 메뉴가 있다', async () => {
    renderPage(<MyCrewsPage />)
    await screen.findByText('가입한 크루 2')
    expect(screen.getByRole('link', { name: '홈' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '대회 찾기' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '뒤로 가기' })).not.toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: '주요 메뉴' })).toBeInTheDocument()
  })

  it('불러오는 동안 자리만 잡아 둔다', () => {
    renderPage(<MyCrewsPage />)
    expect(screen.getByRole('status')).toHaveTextContent('내 크루를 불러오는 중')
  })

  it('가입한 크루가 없으면 크루 만들기를 안내한다', async () => {
    server.use(memberMock.getFindMyCrewsMockHandler({ status: 200, message: '조회', data: [] }))
    renderPage(<MyCrewsPage />)
    expect(await screen.findByText('아직 가입한 크루가 없어요')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '크루 만들기' })).toHaveAttribute('href', '/crews/new')
    expect(screen.queryByRole('link', { name: '+ 새 크루 만들기' })).not.toBeInTheDocument()
  })

  it('불러오지 못하면 다시 불러오기를 보여 준다', async () => {
    server.use(http.get('*/api/v1/member/crews/me', () => failWith(500, 'INTERNAL_ERROR')))
    renderPage(<MyCrewsPage />)
    expect(await screen.findByRole('button', { name: '다시 시도' })).toBeInTheDocument()
  })
})

describe('CrewNewPage (M26)', () => {
  it('이름이 비어 있으면 만들기 버튼이 꺼져 있고, 칸을 떠나면 안내가 보인다', async () => {
    const user = userEvent.setup()
    renderPage(<CrewNewPage />)
    expect(screen.getByRole('button', { name: '크루 만들기' })).toBeDisabled()
    await user.click(screen.getByLabelText('크루 이름'))
    await user.tab()
    expect(await screen.findByText('크루 이름을 입력해 주세요')).toBeInTheDocument()
  })

  it('이름을 넣고 만들면 앞뒤 공백을 빼고 보내고 크루 홈으로 간다', async () => {
    let sent: unknown
    server.use(
      memberMock.getCreateCrewMockHandler(async ({ request }) => {
        sent = await request.json()
        return memberMock.getCreateCrewResponseMock()
      }),
    )
    const user = userEvent.setup()
    renderPage(<CrewNewPage />)
    await user.type(screen.getByLabelText('크루 이름'), '  성수런 ')
    await user.click(screen.getByRole('button', { name: '크루 만들기' }))
    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/crews/1'))
    expect(sent).toEqual({ name: '성수런' })
  })

  it('이름은 20자까지만 들어간다', () => {
    renderPage(<CrewNewPage />)
    expect(screen.getByLabelText('크루 이름')).toHaveAttribute('maxLength', '20')
  })
})

// 시안 M09 크루 홈 v3: 프로필(가운데) → 「파티 만들기」 · 「초대 링크 만들기」 → 탭(파티 · 크루원).
// 기본 크루 예시는 1번 회원이 크루장인 3명 크루, 5번 크루는 내가 크루원인 크루다.
describe('CrewHomePage (M09)', () => {
  const partyResponse = (myParty: boolean, leaderNickname: string) =>
    http.get('*/api/v1/order/parties/crews/:crewId/active', () =>
      HttpResponse.json({
        status: 200,
        message: '조회',
        data: [
          {
            id: 9,
            crewId: 1,
            raceId: 100,
            raceCourseId: 2,
            status: 'RECRUITING',
            leaderMemberId: myParty ? 1 : 2,
            memberCount: 3,
            paidCount: 0,
            paymentDueAt: null,
            leaderNickname,
            myParty,
          },
        ],
      }),
    )

  const openMembersTab = async (user: ReturnType<typeof userEvent.setup>) =>
    user.click(await screen.findByRole('tab', { name: /^크루원/ }))

  it('불러오는 동안 자리만 잡아 둔다', () => {
    renderPage(<CrewHomePage crewId={1} />)
    expect(screen.getByRole('status')).toHaveTextContent('크루를 불러오는 중')
  })

  it('크루장은 프로필 아래에서 파티 만들기와 초대 링크 만들기를 본다', async () => {
    renderPage(<CrewHomePage crewId={1} />)
    expect(await screen.findByText('나는 크루장 · 크루원 3명')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '파티 만들기' })).toHaveAttribute(
      'href',
      '/parties/new?crewId=1',
    )
    expect(screen.getByRole('link', { name: '초대 링크 만들기' })).toHaveAttribute(
      'href',
      '/crews/1/invites',
    )
  })

  it('아래에 주요 메뉴가 있고, 초대 링크 만들기는 선(테두리) 버튼이다', async () => {
    renderPage(<CrewHomePage crewId={1} />)
    expect(await screen.findByRole('navigation', { name: '주요 메뉴' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '초대 링크 만들기' })).toHaveClass('inset-ring')
  })

  it('크루원은 파티 만들기를 보고 초대 링크 만들기는 보지 못한다', async () => {
    renderPage(<CrewHomePage crewId={5} />)
    expect(await screen.findByText('나는 크루원 · 크루원 3명')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '파티 만들기' })).toHaveAttribute(
      'href',
      '/parties/new?crewId=5',
    )
    expect(screen.queryByRole('link', { name: '초대 링크 만들기' })).not.toBeInTheDocument()
  })

  it('파티 탭이 먼저 열리고, 파티가 없으면 숫자 없는 탭과 빈 안내 제목만 보여 준다', async () => {
    renderPage(<CrewHomePage crewId={1} />)
    expect(await screen.findByText('진행 중인 파티가 없어요')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: '파티' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('tab', { name: '크루원 3' })).toHaveAttribute('aria-selected', 'false')
    expect(screen.queryByText(/크루로 신청/)).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '대회 둘러보기' })).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: /진행 중인 파티/ })).not.toBeInTheDocument()
    expect(screen.queryByText('한강페이서')).not.toBeInTheDocument()
  })

  it('진행 중인 파티를 카드로 보여 주고 탭에 개수를 붙인다', async () => {
    server.use(partyResponse(true, '달리는곰'))
    renderPage(<CrewHomePage crewId={1} />)
    expect(await screen.findByText(/파티장 달리는곰 · 3명/)).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: '파티 1' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: /진행 중인 파티/ })).not.toBeInTheDocument()
  })

  it('내 파티 카드는 카드 전체가 파티 상세로 이어진다', async () => {
    server.use(partyResponse(true, '달리는곰'))
    renderPage(<CrewHomePage crewId={1} />)
    const card = await screen.findByRole('link', { name: /파티장 달리는곰/ })
    expect(card).toHaveAttribute('href', '/parties/9')
    expect(within(card).getByText('모집 중')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '파티 보기' })).not.toBeInTheDocument()
    expect(screen.queryByText('내가 없는 파티')).not.toBeInTheDocument()
  })

  it('내 파티가 아닌 파티 카드는 링크 없이 「내가 없는 파티」로 보여 준다(남의 파티는 403이다)', async () => {
    server.use(partyResponse(false, '남의곰'))
    renderPage(<CrewHomePage crewId={1} />)
    expect(await screen.findByText(/파티장 남의곰/)).toBeInTheDocument()
    expect(screen.getByText('내가 없는 파티')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /파티장 남의곰/ })).not.toBeInTheDocument()
    expect(screen.queryByText(/링크로 합류/)).not.toBeInTheDocument()
  })

  it('크루장은 크루원 탭에서 크루원과 크루 관리를 보고, 크루 떠나기는 어디에도 없다', async () => {
    const user = userEvent.setup()
    renderPage(<CrewHomePage crewId={1} />)
    await screen.findByText('진행 중인 파티가 없어요')
    expect(screen.queryByRole('button', { name: '크루 떠나기' })).not.toBeInTheDocument()
    await openMembersTab(user)
    expect(await screen.findByText('한강페이서')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '크루 관리' })).toHaveAttribute(
      'href',
      '/crews/1/manage',
    )
    expect(screen.queryByRole('heading', { name: /크루원 3명/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '크루 떠나기' })).not.toBeInTheDocument()
  })

  it('크루원은 크루원 탭에서만 크루 떠나기를 본다', async () => {
    const user = userEvent.setup()
    renderPage(<CrewHomePage crewId={5} />)
    await screen.findByText('진행 중인 파티가 없어요')
    expect(screen.queryByRole('button', { name: '크루 떠나기' })).not.toBeInTheDocument()
    await openMembersTab(user)
    expect(await screen.findByRole('button', { name: '크루 떠나기' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '크루 관리' })).not.toBeInTheDocument()
  })

  it('크루원 탭을 고르면 주소에 남아, 뒤로 가기로 돌아와도 크루원 탭이 열린다', async () => {
    const user = userEvent.setup()
    const { unmount } = renderPage(<CrewHomePage crewId={1} />)
    await openMembersTab(user)
    expect(currentSearch()).toBe('tab=members')
    expect(replace).not.toHaveBeenCalled()
    unmount()
    renderPage(<CrewHomePage crewId={1} />)
    expect(await screen.findByRole('tab', { name: '크루원 3' })).toHaveAttribute(
      'aria-selected',
      'true',
    )
    expect(await screen.findByText('한강페이서')).toBeInTheDocument()
  })

  it('주소의 탭 값이 목록 밖이면 파티 탭을 연다', async () => {
    resetNavigationMock('tab=nope')
    renderPage(<CrewHomePage crewId={1} />)
    expect(await screen.findByRole('tab', { name: '파티' })).toHaveAttribute(
      'aria-selected',
      'true',
    )
  })

  it('파티를 불러오지 못해도 크루원 탭은 그대로 볼 수 있다', async () => {
    server.use(
      http.get('*/api/v1/order/parties/crews/:crewId/active', () =>
        failWith(500, 'INTERNAL_ERROR'),
      ),
    )
    const user = userEvent.setup()
    renderPage(<CrewHomePage crewId={1} />)
    expect(await screen.findByRole('button', { name: '다시 시도' })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: '파티' })).toBeInTheDocument()
    await openMembersTab(user)
    expect(await screen.findByText('한강페이서')).toBeInTheDocument()
  })

  it('크루원이 아니면 가입 안내를 보여 준다', async () => {
    renderPage(<CrewHomePage crewId={403} />)
    expect(await screen.findByText('크루원만 볼 수 있어요')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '내 크루로 가기' })).toHaveAttribute('href', '/crews')
  })

  it('없는 크루면 없는 크루라고 안내한다', async () => {
    renderPage(<CrewHomePage crewId={404} />)
    expect(await screen.findByText('없는 크루예요')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '내 크루로 가기' })).toHaveAttribute('href', '/crews')
  })

  it('크루원이 5명을 넘으면 나머지는 크루원 목록 화면으로 이어 준다', async () => {
    const members = Array.from({ length: 7 }, (_, i) => ({
      memberId: i + 1,
      nickname: `러너${i + 1}`,
      role: i === 0 ? ('LEADER' as const) : ('MEMBER' as const),
    }))
    const example = memberMock.getFindCrewResponseMock()
    server.use(
      memberMock.getFindCrewMockHandler({
        ...example,
        data: { ...example.data!, memberCount: 7, members },
      }),
    )
    const user = userEvent.setup()
    renderPage(<CrewHomePage crewId={1} />)
    await openMembersTab(user)
    expect(await screen.findByRole('link', { name: '크루원 2명 더 보기' })).toHaveAttribute(
      'href',
      '/crews/1/members',
    )
    expect(screen.queryByText('러너7')).not.toBeInTheDocument()
  })
})

describe('CrewMembersPage 크루원 목록', () => {
  it('나와 다른 크루원을 나눠 보여 주고, 화살표는 그리지 않는다', async () => {
    const crew = memberMock.getFindCrewResponseMock()
    const members = [
      { memberId: 1, nickname: '새벽곰', role: 'LEADER' as const },
      { memberId: 7, nickname: '나의닉네임', role: 'MEMBER' as const },
      ...Array.from({ length: 5 }, (_, i) => ({
        memberId: 20 + i,
        nickname: `러너${i}`,
        role: 'MEMBER' as const,
      })),
    ]
    server.use(
      memberMock.getFindCrewMockHandler({
        ...crew,
        data: { ...crew.data!, name: '새벽한강크루', memberCount: 7, members },
      }),
      memberMock.getFindMeMockHandler({
        ...memberMock.getFindMeResponseMock(),
        data: { ...memberMock.getFindMeResponseMock().data!, id: 7 },
      }),
    )
    renderPage(<CrewMembersPage crewId={1} />)
    expect(
      await screen.findByRole('heading', { name: '새벽한강크루 크루원 7명' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '나' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '크루원 6명' })).toBeInTheDocument()
    expect(screen.getByText('나의닉네임')).toBeInTheDocument()
    expect(screen.getByText('러너4')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /크루원 .*더 보기/ })).not.toBeInTheDocument()
  })
})

describe('CrewMembersPage 내 정보가 늦게 올 때', () => {
  it('내 정보가 올 때까지 목록을 보여 주지 않아 「나」가 크루원에 섞이지 않는다', async () => {
    const crew = memberMock.getFindCrewResponseMock()
    const members = [
      { memberId: 1, nickname: '새벽곰', role: 'LEADER' as const },
      { memberId: 7, nickname: '나의닉네임', role: 'MEMBER' as const },
    ]
    server.use(
      memberMock.getFindCrewMockHandler({
        ...crew,
        data: { ...crew.data!, name: '새벽한강크루', memberCount: 2, members },
      }),
      http.get('*/api/v1/member/members/me', async () => {
        await delay(150)
        return HttpResponse.json({
          ...memberMock.getFindMeResponseMock(),
          data: { ...memberMock.getFindMeResponseMock().data!, id: 7 },
        })
      }),
    )
    renderPage(<CrewMembersPage crewId={1} />)
    await act(() => delay(50))
    expect(screen.getByRole('status')).toHaveTextContent('크루원을 불러오는 중')
    expect(screen.queryByText('나의닉네임')).not.toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: '나' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '크루원 1명' })).toBeInTheDocument()
  })
})

describe('CrewInvitesPage (M27)', () => {
  it('링크를 만들면 주소와 복사 버튼을 보여 주고, 복사하면 알린다', async () => {
    const user = userEvent.setup()
    const writeText = vi.spyOn(navigator.clipboard, 'writeText').mockResolvedValue()
    renderPage(<CrewInvitesPage crewId={1} />)
    await user.click(await screen.findByRole('button', { name: '초대 링크 만들기' }))
    expect(await screen.findByText('초대 링크를 만들었어요')).toBeInTheDocument()
    expect(screen.getByText(/\/invite\/q7Xk2LmZ9vBn4RtW8yCp1A$/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '링크 복사하기' }))
    expect(writeText).toHaveBeenCalledWith(
      `${window.location.origin}/invite/q7Xk2LmZ9vBn4RtW8yCp1A`,
    )
    expect(await screen.findByText('초대 링크를 복사했어요')).toBeInTheDocument()
  })

  it('보낸 초대를 취소하기 전에 한 번 더 묻는다', async () => {
    let canceled = ''
    server.use(
      http.delete('*/api/v1/member/crews/:crewId/invites/:inviteId', ({ request }) => {
        canceled = new URL(request.url).pathname
        return HttpResponse.json({ status: 200, message: '취소', data: null })
      }),
    )
    const user = userEvent.setup()
    renderPage(<CrewInvitesPage crewId={1} />)
    await user.click(await screen.findByRole('button', { name: '초대 취소' }))
    const dialog = await screen.findByRole('alertdialog')
    expect(within(dialog).getByText('이 초대를 취소할까요?')).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: '초대 취소하기' }))
    await vi.waitFor(() => expect(canceled).toBe('/api/v1/member/crews/1/invites/3'))
  })
})

describe('CrewInvitesPage 보낸 초대 링크 (BUK-195)', () => {
  it('링크 아이콘에 마우스를 올리면 초대 링크를 보여 주고, 누르면 복사한다', async () => {
    const user = userEvent.setup()
    const writeText = vi.spyOn(navigator.clipboard, 'writeText').mockResolvedValue()
    renderPage(<CrewInvitesPage crewId={1} />)
    const linkButton = await screen.findByRole('button', { name: '초대 링크 복사' })
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()

    await user.hover(linkButton)
    expect(screen.getByRole('tooltip')).toHaveTextContent(
      `${window.location.origin}/invite/q3Xv9kLm2NpR7tYw4ZaB1c`,
    )
    await user.unhover(linkButton)
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()

    // 키보드로 포커스해도 보이고, Esc로 닫는다
    act(() => linkButton.focus())
    expect(screen.getByRole('tooltip')).toBeInTheDocument()
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()

    await user.click(linkButton)
    expect(writeText).toHaveBeenCalledWith(
      `${window.location.origin}/invite/q3Xv9kLm2NpR7tYw4ZaB1c`,
    )
  })

  it('토큰이 없는 옛 초대에는 링크 아이콘이 없다', async () => {
    const example = memberMock.getFindCrewInvitesResponseMock()
    server.use(
      memberMock.getFindCrewInvitesMockHandler({
        ...example,
        data: example.data!.map((invite) => ({ ...invite, inviteToken: null })),
      }),
    )
    renderPage(<CrewInvitesPage crewId={1} />)
    expect(await screen.findByRole('button', { name: '초대 취소' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '초대 링크 복사' })).not.toBeInTheDocument()
  })
})

/** 크루 홈 응답의 내 역할만 바꾼다 */
function mockCrewRole(myRole: 'LEADER' | 'MANAGER' | 'MEMBER') {
  const example = memberMock.getFindCrewResponseMock()
  server.use(memberMock.getFindCrewMockHandler({ ...example, data: { ...example.data!, myRole } }))
}

/** 보낸 초대 두 개: 1번 회원(나)이 보낸 것, 9번 회원이 보낸 것 */
function mockTwoInvites() {
  const invite = (inviteId: number, memberId: number, nickname: string) => ({
    inviteId,
    createdBy: { memberId, nickname },
    createdAt: '2026-11-02T20:30:00',
    expiresAt: '2099-11-03T20:30:00',
  })
  server.use(
    memberMock.getFindCrewInvitesMockHandler({
      status: 200,
      message: '조회',
      data: [invite(3, 1, '새벽러너'), invite(4, 9, '달리는곰')],
    }),
  )
}

const cancelButtons = () => screen.queryAllByRole('button', { name: '초대 취소' })

describe('CrewInvitesPage 역할별 권한', () => {
  it('크루장은 다른 사람이 보낸 초대도 취소할 수 있다', async () => {
    mockCrewRole('LEADER')
    mockTwoInvites()
    renderPage(<CrewInvitesPage crewId={1} />)
    await screen.findByText(/달리는곰이 보냄/)
    expect(cancelButtons()).toHaveLength(2)
  })

  it('운영진은 자기가 보낸 초대만 취소할 수 있다', async () => {
    mockCrewRole('MANAGER')
    mockTwoInvites()
    renderPage(<CrewInvitesPage crewId={1} />)
    const others = (await screen.findByText(/달리는곰이 보냄/)).closest('li')!
    expect(within(others).queryByRole('button', { name: '초대 취소' })).not.toBeInTheDocument()
    const mine = screen.getByText(/새벽러너가 보냄/).closest('li')!
    expect(within(mine).getByRole('button', { name: '초대 취소' })).toBeInTheDocument()
  })

  it('크루원이 주소를 직접 열면 초대 권한이 없다고 안내하고 목록을 부르지 않는다', async () => {
    let listed = false
    server.use(
      http.get('*/api/v1/member/crews/:crewId/invites', () => {
        listed = true
        return failWith(403, 'CREW_INVITE_FORBIDDEN')
      }),
    )
    mockCrewRole('MEMBER')
    renderPage(<CrewInvitesPage crewId={1} />)
    expect(await screen.findByText('크루장과 운영진만 초대할 수 있어요')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '크루 홈으로 가기' })).toHaveAttribute(
      'href',
      '/crews/1',
    )
    expect(screen.queryByRole('button', { name: '초대 링크 만들기' })).not.toBeInTheDocument()
    expect(listed).toBe(false)
  })

  it('방금 만든 링크를 취소하면 위쪽 링크 카드도 사라진다', async () => {
    server.use(
      http.delete('*/api/v1/member/crews/:crewId/invites/:inviteId', () =>
        HttpResponse.json({ status: 200, message: '취소', data: null }),
      ),
    )
    const user = userEvent.setup()
    renderPage(<CrewInvitesPage crewId={1} />)
    await user.click(await screen.findByRole('button', { name: '초대 링크 만들기' }))
    expect(await screen.findByRole('button', { name: '링크 복사하기' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '초대 취소' }))
    const dialog = await screen.findByRole('alertdialog')
    await user.click(within(dialog).getByRole('button', { name: '초대 취소하기' }))
    await vi.waitFor(() =>
      expect(screen.queryByRole('button', { name: '링크 복사하기' })).not.toBeInTheDocument(),
    )
    expect(screen.getByRole('button', { name: '초대 링크 만들기' })).toBeInTheDocument()
  })
})

describe('CrewInvitePage (M10)', () => {
  it('크루 이름과 남은 시간을 보여 주고, 가입하면 크루 홈으로 간다', async () => {
    const user = userEvent.setup()
    renderPage(<CrewInvitePage inviteToken="q7Xk2LmZ9vBn4RtW8yCp1A" />)
    expect(await screen.findByText('새벽한강크루에서 초대했어요')).toBeInTheDocument()
    expect(screen.getByText(/^이 초대 링크는 23시간 \d+분 뒤에 끝나요$/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '새벽한강크루에 가입하기' }))
    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/crews/1'))
  })

  it('이미 크루원이면 안내 창을 띄우고, 확인하면 크루 홈으로 보낸다', async () => {
    const user = userEvent.setup()
    renderPage(<CrewInvitePage inviteToken="member" />)
    const dialog = await screen.findByRole('alertdialog')
    expect(dialog).toHaveTextContent('이미 가입한 크루의 초대 링크예요!')
    expect(screen.queryByRole('button', { name: /가입하기/ })).not.toBeInTheDocument()
    expect(replace).not.toHaveBeenCalled()
    await user.click(within(dialog).getByRole('button', { name: '크루 홈으로 가기' }))
    expect(replace).toHaveBeenCalledWith('/crews/1')
  })

  it('이미 크루원 안내 창을 Esc로 닫아도 크루 홈으로 보낸다', async () => {
    const user = userEvent.setup()
    renderPage(<CrewInvitePage inviteToken="member" />)
    await screen.findByRole('alertdialog')
    await user.keyboard('{Escape}')
    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/crews/1'))
  })

  it.each([
    ['expired', '링크는 만든 뒤 24시간 동안만 쓸 수 있어요. 크루장에게 새 링크를 받아 주세요.'],
    ['canceled', '크루장이 이 초대를 취소했어요. 크루장에게 새 링크를 받아 주세요.'],
    ['used', '이미 가입에 쓰인 초대 링크입니다. 새 초대 링크를 받아주세요.'],
  ])('쓸 수 없는 링크(%s)는 끝났다고 안내한다', async (token, description) => {
    renderPage(<CrewInvitePage inviteToken={token} />)
    expect(await screen.findByText('초대 링크가 끝났어요')).toBeInTheDocument()
    expect(screen.getByText(description)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '홈으로 가기' })).toHaveAttribute('href', '/')
  })

  it('가입하는 사이 링크가 끝나면 끝났다고 안내한다', async () => {
    server.use(
      http.post('*/api/v1/member/crews/invites/:inviteToken/join', () =>
        failWith(410, 'CREW_INVITE_EXPIRED'),
      ),
    )
    const user = userEvent.setup()
    renderPage(<CrewInvitePage inviteToken="q7Xk2LmZ9vBn4RtW8yCp1A" />)
    await user.click(await screen.findByRole('button', { name: '새벽한강크루에 가입하기' }))
    expect(await screen.findByText('초대 링크가 끝났어요')).toBeInTheDocument()
  })

  it('가입하는 사이 이미 크루원이 됐으면(409) 크루 홈으로 보낸다', async () => {
    server.use(
      http.post('*/api/v1/member/crews/invites/:inviteToken/join', () =>
        failWith(409, 'CREW_ALREADY_MEMBER'),
      ),
    )
    const user = userEvent.setup()
    renderPage(<CrewInvitePage inviteToken="q7Xk2LmZ9vBn4RtW8yCp1A" />)
    await user.click(await screen.findByRole('button', { name: '새벽한강크루에 가입하기' }))
    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/crews/1'))
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})

describe('CrewInvitePage 파티 참가 링크가 함께 온 초대 (BUK-98)', () => {
  it('가입하면 크루 홈 대신 파티 합류 화면으로 간다', async () => {
    const user = userEvent.setup()
    renderPage(<CrewInvitePage inviteToken="q7Xk2LmZ9vBn4RtW8yCp1A" partyJoinToken="pJ0in" />)
    await user.click(await screen.findByRole('button', { name: '새벽한강크루에 가입하기' }))
    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/parties/join/pJ0in'))
    expect(replace).not.toHaveBeenCalledWith('/crews/1')
  })

  it('이미 크루원이면 바로 파티 합류 화면으로 보낸다', async () => {
    renderPage(<CrewInvitePage inviteToken="member" partyJoinToken="pJ0in" />)
    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/parties/join/pJ0in'))
    expect(replace).not.toHaveBeenCalledWith('/crews/1')
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
  })

  it('가입하는 사이 이미 크루원이 됐어도(409) 파티 합류 화면으로 보낸다', async () => {
    server.use(
      http.post('*/api/v1/member/crews/invites/:inviteToken/join', () =>
        failWith(409, 'CREW_ALREADY_MEMBER'),
      ),
    )
    const user = userEvent.setup()
    renderPage(<CrewInvitePage inviteToken="q7Xk2LmZ9vBn4RtW8yCp1A" partyJoinToken="pJ0in" />)
    await user.click(await screen.findByRole('button', { name: '새벽한강크루에 가입하기' }))
    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/parties/join/pJ0in'))
  })

  it('파티 토큰에 주소 기호가 섞여도 다른 경로로 빠지지 않는다', async () => {
    renderPage(<CrewInvitePage inviteToken="member" partyJoinToken="../../admin?x=1" />)
    await vi.waitFor(() =>
      expect(replace).toHaveBeenCalledWith('/parties/join/..%2F..%2Fadmin%3Fx%3D1'),
    )
  })
})
