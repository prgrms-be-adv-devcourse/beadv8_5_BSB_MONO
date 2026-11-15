import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { setAccessToken } from '@/shared/api'
import { memberMock, server } from '@/shared/testing'
import { ToastProvider } from '@/shared/ui'

import { CrewHomePage } from './CrewHomePage'
import { CrewManagePage } from './CrewManagePage'

// 크루 관리(2그룹): 탈퇴 · 내보내기 · 크루장 넘기기 · 없애기.
// 크루 홈 예시 응답은 1번 회원(새벽러너)이 크루장, 4번 한강페이서가 운영진, 2번 주말10K가 크루원인 3명 크루다.

const replace = vi.fn()
const push = vi.fn()
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push, back: vi.fn(), replace }),
  usePathname: () => '/crews/1',
}))

beforeEach(() => {
  replace.mockClear()
  push.mockClear()
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

const failWith = (status: number, code?: string, message = '서버 문구') =>
  HttpResponse.json({ status, code, message, data: null }, { status })
const ok = () => HttpResponse.json({ status: 200, message: '처리', data: null })

type Role = 'LEADER' | 'MANAGER' | 'MEMBER'

/** 크루 홈 응답의 내 역할과 크루원 수를 바꾼다. 불러온 횟수를 돌려준다. */
function mockCrew({ myRole = 'LEADER', alone = false }: { myRole?: Role; alone?: boolean } = {}) {
  const example = memberMock.getFindCrewResponseMock()
  const members = alone ? example.data!.members.slice(0, 1) : example.data!.members
  const loads = { count: 0 }
  server.use(
    http.get('*/api/v1/member/crews/:crewId', () => {
      loads.count += 1
      return HttpResponse.json({
        ...example,
        data: { ...example.data!, myRole, memberCount: members.length, members },
      })
    }),
  )
  return loads
}

/** 요청을 받아 둔다. 응답은 handler가 정한다. */
function capture(
  method: 'delete' | 'put',
  path: string,
  handler: () => Response | Promise<Response> = ok,
) {
  const requests: { url: string; body: unknown }[] = []
  server.use(
    http[method](path, async ({ request }) => {
      const text = await request.text()
      requests.push({ url: new URL(request.url).pathname, body: text ? JSON.parse(text) : null })
      return handler()
    }),
  )
  return requests
}

// 오류 토스트는 화면 낭독기가 바로 읽도록 같은 문구를 한 번 더 그린다(Toast priority high). 보이는 쪽을 고른다.
async function findErrorToast(text: string) {
  return (await screen.findAllByText(text))[0]
}

async function openMenu(user: ReturnType<typeof userEvent.setup>, nickname: string) {
  const row = (await screen.findByText(nickname)).closest('li')!
  await user.click(within(row).getByRole('button', { name: '관리' }))
}

describe('CrewHomePage 크루 탈퇴', () => {
  it('크루원이 확인을 거쳐 나가면 내 크루 목록으로 간다', async () => {
    mockCrew({ myRole: 'MEMBER' })
    const requests = capture('delete', '*/api/v1/member/crews/:crewId/members/me')
    const user = userEvent.setup()
    renderPage(<CrewHomePage crewId={1} />)
    await user.click(await screen.findByRole('button', { name: '크루 떠나기' }))
    const dialog = await screen.findByRole('alertdialog')
    expect(within(dialog).getByText('새벽한강크루에서 떠날까요?')).toBeInTheDocument()
    expect(requests).toHaveLength(0)
    await user.click(within(dialog).getByRole('button', { name: '떠나기' }))
    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/crews'))
    expect(requests[0].url).toBe('/api/v1/member/crews/1/members/me')
    expect(await screen.findByText('새벽한강크루에서 나왔어요')).toBeInTheDocument()
  })

  it('진행 중인 파티에 들어 있으면 떠나기 요청 없이 막힘 안내만 띄운다', async () => {
    mockCrew({ myRole: 'MEMBER' })
    server.use(
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
              leaderMemberId: 1,
              memberCount: 3,
              paidCount: 0,
              paymentDueAt: null,
              leaderNickname: '달리는곰',
              myParty: true,
            },
          ],
        }),
      ),
    )
    const requests = capture('delete', '*/api/v1/member/crews/:crewId/members/me')
    const user = userEvent.setup()
    renderPage(<CrewHomePage crewId={1} />)
    await screen.findByText(/파티장 달리는곰/)
    await user.click(screen.getByRole('button', { name: '크루 떠나기' }))
    const dialog = await screen.findByRole('alertdialog')
    expect(within(dialog).getByText('진행 중인 파티가 끝난 뒤 떠날 수 있어요')).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: '확인' }))
    expect(requests).toHaveLength(0)
  })

  it('확인을 두 번 눌러도 요청은 한 번만 보낸다', async () => {
    mockCrew({ myRole: 'MEMBER' })
    const requests = capture('delete', '*/api/v1/member/crews/:crewId/members/me', async () => {
      await delay(100)
      return ok()
    })
    const user = userEvent.setup()
    renderPage(<CrewHomePage crewId={1} />)
    await user.click(await screen.findByRole('button', { name: '크루 떠나기' }))
    const confirm = within(await screen.findByRole('alertdialog')).getByRole('button', {
      name: '떠나기',
    })
    await user.click(confirm)
    await user.click(confirm)
    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/crews'))
    expect(requests).toHaveLength(1)
  })

  it('크루장이 누르면 요청 없이 크루장을 넘기라고 안내한다', async () => {
    mockCrew({ myRole: 'LEADER' })
    const requests = capture('delete', '*/api/v1/member/crews/:crewId/members/me')
    const user = userEvent.setup()
    renderPage(<CrewHomePage crewId={1} />)
    await user.click(await screen.findByRole('button', { name: '크루 떠나기' }))
    const dialog = await screen.findByRole('alertdialog')
    expect(within(dialog).getByText('크루장을 넘긴 뒤에 나갈 수 있어요')).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: '크루 관리로 가기' }))
    expect(push).toHaveBeenCalledWith('/crews/1/manage')
    expect(requests).toHaveLength(0)
  })

  it('혼자 남은 크루장에게는 크루 없애기를 안내한다', async () => {
    mockCrew({ myRole: 'LEADER', alone: true })
    const user = userEvent.setup()
    renderPage(<CrewHomePage crewId={1} />)
    await user.click(await screen.findByRole('button', { name: '크루 떠나기' }))
    const dialog = await screen.findByRole('alertdialog')
    expect(
      within(dialog).getByText('크루원이 나뿐이라 크루 관리에서 크루를 없앨 수 있어요.'),
    ).toBeInTheDocument()
  })

  it('그사이 크루장을 넘겨받았으면(409) 성공처럼 보이지 않고 넘기라고 안내한다', async () => {
    const loads = mockCrew({ myRole: 'MEMBER' })
    capture('delete', '*/api/v1/member/crews/:crewId/members/me', () =>
      failWith(409, 'CREW_LEADER_CANNOT_LEAVE'),
    )
    const user = userEvent.setup()
    renderPage(<CrewHomePage crewId={1} />)
    await user.click(await screen.findByRole('button', { name: '크루 떠나기' }))
    await user.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', { name: '떠나기' }),
    )
    expect(await screen.findByText('크루장을 넘긴 뒤에 나갈 수 있어요')).toBeInTheDocument()
    expect(replace).not.toHaveBeenCalled()
    expect(screen.queryByText('새벽한강크루에서 나왔어요')).not.toBeInTheDocument()
    await vi.waitFor(() => expect(loads.count).toBeGreaterThan(1))
  })

  it('서버가 잠시 멈추면(503) 다시 시도해 달라고 알리고 화면에 남는다', async () => {
    mockCrew({ myRole: 'MEMBER' })
    capture('delete', '*/api/v1/member/crews/:crewId/members/me', () => failWith(503))
    const user = userEvent.setup()
    renderPage(<CrewHomePage crewId={1} />)
    await user.click(await screen.findByRole('button', { name: '크루 떠나기' }))
    await user.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', { name: '떠나기' }),
    )
    expect(await findErrorToast('잠시 후 다시 시도해 주세요.')).toBeInTheDocument()
    expect(replace).not.toHaveBeenCalled()
  })
})

describe('CrewHomePage 크루 관리 입구', () => {
  it('크루장은 크루원 목록 옆에서 크루 관리로 간다', async () => {
    mockCrew({ myRole: 'LEADER' })
    renderPage(<CrewHomePage crewId={1} />)
    expect(await screen.findByRole('link', { name: '크루 관리' })).toHaveAttribute(
      'href',
      '/crews/1/manage',
    )
  })

  it.each<Role>(['MANAGER', 'MEMBER'])('%s는 크루 관리를 보지 못한다', async (myRole) => {
    mockCrew({ myRole })
    renderPage(<CrewHomePage crewId={1} />)
    await screen.findByRole('heading', { name: '크루원 3명' })
    expect(screen.queryByRole('link', { name: '크루 관리' })).not.toBeInTheDocument()
  })
})

describe('CrewManagePage (M28) 권한', () => {
  it.each<Role>(['MANAGER', 'MEMBER'])(
    '%s가 주소로 열면 크루장만 관리할 수 있다고 안내하고 관리 버튼을 숨긴다',
    async (myRole) => {
      mockCrew({ myRole })
      renderPage(<CrewManagePage crewId={1} />)
      expect(await screen.findByText('크루장만 크루를 관리할 수 있어요')).toBeInTheDocument()
      expect(screen.getByRole('link', { name: '크루 홈으로' })).toHaveAttribute('href', '/crews/1')
      expect(screen.queryByRole('button', { name: '관리' })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: '크루 없애기' })).not.toBeInTheDocument()
    },
  )

  it('크루장은 나를 뺀 크루원에게만 관리 버튼을 본다', async () => {
    mockCrew()
    renderPage(<CrewManagePage crewId={1} />)
    expect(await screen.findByText('새벽한강크루 크루원 3명')).toBeInTheDocument()
    const me = screen.getByText('새벽러너').closest('li')!
    expect(within(me).queryByRole('button', { name: '관리' })).not.toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: '관리' })).toHaveLength(2)
  })

  it('크루원이 아니거나 없는 크루면 크루 홈과 같은 안내를 보여 준다', async () => {
    server.use(http.get('*/api/v1/member/crews/:crewId', () => failWith(404, 'CREW_NOT_FOUND')))
    renderPage(<CrewManagePage crewId={1} />)
    expect(await screen.findByText('없는 크루예요')).toBeInTheDocument()
  })
})

describe('CrewManagePage 크루원 내보내기', () => {
  it('확인을 거쳐 내보내고 크루원 목록을 다시 불러온다', async () => {
    const loads = mockCrew()
    const requests = capture('delete', '*/api/v1/member/crews/:crewId/members/:memberId')
    const user = userEvent.setup()
    renderPage(<CrewManagePage crewId={1} />)
    await openMenu(user, '주말10K')
    await user.click(await screen.findByRole('button', { name: '크루에서 내보내기' }))
    const dialog = await screen.findByRole('alertdialog')
    expect(within(dialog).getByText('주말10K 님을 크루에서 내보낼까요?')).toBeInTheDocument()
    expect(requests).toHaveLength(0)
    await user.click(within(dialog).getByRole('button', { name: '내보내기' }))
    expect(await screen.findByText('주말10K 님을 크루에서 내보냈어요')).toBeInTheDocument()
    expect(requests.map((request) => request.url)).toEqual(['/api/v1/member/crews/1/members/2'])
    await vi.waitFor(() => expect(loads.count).toBeGreaterThan(1))
  })

  it('확인을 두 번 눌러도 한 번만 보낸다', async () => {
    mockCrew()
    const requests = capture(
      'delete',
      '*/api/v1/member/crews/:crewId/members/:memberId',
      async () => {
        await delay(100)
        return ok()
      },
    )
    const user = userEvent.setup()
    renderPage(<CrewManagePage crewId={1} />)
    await openMenu(user, '주말10K')
    await user.click(await screen.findByRole('button', { name: '크루에서 내보내기' }))
    const confirm = within(await screen.findByRole('alertdialog')).getByRole('button', {
      name: '내보내기',
    })
    await user.click(confirm)
    await user.click(confirm)
    await screen.findByText('주말10K 님을 크루에서 내보냈어요')
    expect(requests).toHaveLength(1)
  })

  it('그사이 크루장이 아니게 됐으면(403) 성공처럼 보이지 않고 화면을 다시 불러온다', async () => {
    const loads = mockCrew()
    capture('delete', '*/api/v1/member/crews/:crewId/members/:memberId', () =>
      failWith(403, 'CREW_LEADER_ONLY'),
    )
    const user = userEvent.setup()
    renderPage(<CrewManagePage crewId={1} />)
    await openMenu(user, '주말10K')
    await user.click(await screen.findByRole('button', { name: '크루에서 내보내기' }))
    await user.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', { name: '내보내기' }),
    )
    expect(
      await findErrorToast('크루장만 할 수 있어요. 바뀐 크루 정보를 다시 불러왔어요.'),
    ).toBeInTheDocument()
    expect(screen.queryByText('주말10K 님을 크루에서 내보냈어요')).not.toBeInTheDocument()
    await vi.waitFor(() => expect(loads.count).toBeGreaterThan(1))
  })

  it('다른 사람이 먼저 내보냈으면(404) 이미 떠났다고 알린다', async () => {
    mockCrew()
    capture('delete', '*/api/v1/member/crews/:crewId/members/:memberId', () =>
      failWith(404, 'CREW_MEMBER_NOT_FOUND'),
    )
    const user = userEvent.setup()
    renderPage(<CrewManagePage crewId={1} />)
    await openMenu(user, '주말10K')
    await user.click(await screen.findByRole('button', { name: '크루에서 내보내기' }))
    await user.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', { name: '내보내기' }),
    )
    expect(await findErrorToast('이미 크루를 떠난 크루원이에요.')).toBeInTheDocument()
  })
})

describe('CrewManagePage 크루장 넘기기', () => {
  it('넘길 크루원을 골라 확인하면 넘기고 바뀐 역할로 크루 홈을 연다', async () => {
    mockCrew()
    const requests = capture('put', '*/api/v1/member/crews/:crewId/leader', () =>
      HttpResponse.json(memberMock.getTransferCrewLeaderResponseMock()),
    )
    const user = userEvent.setup()
    renderPage(<CrewManagePage crewId={1} />)
    await openMenu(user, '주말10K')
    await user.click(await screen.findByRole('button', { name: '크루장 넘기기' }))
    const dialog = await screen.findByRole('alertdialog')
    expect(within(dialog).getByText('주말10K 님에게 크루장을 넘길까요?')).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: '크루장 넘기기' }))
    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/crews/1'))
    expect(requests).toEqual([{ url: '/api/v1/member/crews/1/leader', body: { memberId: 2 } }])
    expect(await screen.findByText('주말10K 님에게 크루장을 넘겼어요')).toBeInTheDocument()
  })

  it('넘기지 못하면(409 CREW_ALREADY_LEADER) 이유를 알리고 이 화면에 남는다', async () => {
    mockCrew()
    capture('put', '*/api/v1/member/crews/:crewId/leader', () =>
      failWith(409, 'CREW_ALREADY_LEADER'),
    )
    const user = userEvent.setup()
    renderPage(<CrewManagePage crewId={1} />)
    await openMenu(user, '주말10K')
    await user.click(await screen.findByRole('button', { name: '크루장 넘기기' }))
    await user.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', { name: '크루장 넘기기' }),
    )
    expect(await findErrorToast('이미 크루장이에요.')).toBeInTheDocument()
    expect(replace).not.toHaveBeenCalled()
  })

  it('네트워크 오류면 다시 시도해 달라고 알린다', async () => {
    mockCrew()
    capture('put', '*/api/v1/member/crews/:crewId/leader', () => HttpResponse.error())
    const user = userEvent.setup()
    renderPage(<CrewManagePage crewId={1} />)
    await openMenu(user, '주말10K')
    await user.click(await screen.findByRole('button', { name: '크루장 넘기기' }))
    await user.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', { name: '크루장 넘기기' }),
    )
    expect(await findErrorToast('잠시 후 다시 시도해 주세요.')).toBeInTheDocument()
    expect(replace).not.toHaveBeenCalled()
  })
})

describe('CrewManagePage 초대 링크', () => {
  it('크루원이 몇 명이든 초대 링크 만들기가 항상 보인다', async () => {
    mockCrew()
    renderPage(<CrewManagePage crewId={1} />)
    expect(await screen.findByRole('link', { name: '초대 링크 만들기' })).toHaveAttribute(
      'href',
      '/crews/1/invites',
    )
  })
})

describe('CrewManagePage 크루 없애기', () => {
  it('다른 크루원이 있으면 누르면 막힘 확인창이 뜨고 삭제 요청은 가지 않는다', async () => {
    mockCrew()
    const requests = capture('delete', '*/api/v1/member/crews/:crewId')
    const user = userEvent.setup()
    renderPage(<CrewManagePage crewId={1} />)
    await user.click(await screen.findByRole('button', { name: '크루 없애기' }))
    const dialog = await screen.findByRole('alertdialog')
    expect(within(dialog).getByText('크루원이 남아 있어 없앨 수 없어요')).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: '확인' }))
    await vi.waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument())
    expect(requests).toHaveLength(0)
    expect(replace).not.toHaveBeenCalled()
  })

  it('혼자면 확인을 거쳐 없애고 내 크루 목록으로 간다', async () => {
    mockCrew({ alone: true })
    const requests = capture('delete', '*/api/v1/member/crews/:crewId')
    const user = userEvent.setup()
    renderPage(<CrewManagePage crewId={1} />)
    await user.click(await screen.findByRole('button', { name: '크루 없애기' }))
    const dialog = await screen.findByRole('alertdialog')
    expect(within(dialog).getByText('새벽한강크루를 없앨까요?')).toBeInTheDocument()
    expect(requests).toHaveLength(0)
    await user.click(within(dialog).getByRole('button', { name: '크루 없애기' }))
    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/crews'))
    expect(requests.map((request) => request.url)).toEqual(['/api/v1/member/crews/1'])
    expect(await screen.findByText('새벽한강크루를 없앴어요')).toBeInTheDocument()
  })

  it('그사이 다른 크루원이 가입했으면(409) 없애지 못했다고 알리고 다시 불러온다', async () => {
    const loads = mockCrew({ alone: true })
    capture('delete', '*/api/v1/member/crews/:crewId', () =>
      failWith(409, 'CREW_HAS_OTHER_MEMBERS'),
    )
    const user = userEvent.setup()
    renderPage(<CrewManagePage crewId={1} />)
    await user.click(await screen.findByRole('button', { name: '크루 없애기' }))
    await user.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', { name: '크루 없애기' }),
    )
    expect(
      await findErrorToast(
        '다른 크루원이 있어 크루를 없앨 수 없어요. 크루장을 넘긴 뒤 크루를 떠나 주세요.',
      ),
    ).toBeInTheDocument()
    expect(replace).not.toHaveBeenCalled()
    await vi.waitFor(() => expect(loads.count).toBeGreaterThan(1))
  })
})

describe('CrewManagePage 크루 이름 바꾸기', () => {
  function capturePatch(handler: () => Response | Promise<Response> = renamed) {
    const requests: unknown[] = []
    server.use(
      http.patch('*/api/v1/member/crews/:crewId', async ({ request }) => {
        requests.push(await request.json())
        return handler()
      }),
    )
    return requests
  }

  function renamed() {
    const example = memberMock.getFindCrewResponseMock()
    return HttpResponse.json({ ...example, data: { ...example.data!, name: '성수런' } })
  }

  async function openRename(user: ReturnType<typeof userEvent.setup>) {
    await user.click(await screen.findByRole('button', { name: '이름 바꾸기' }))
    return screen.findByLabelText('크루 이름')
  }

  it('지금 이름을 채워 보여 주고, 앞뒤 공백을 뺀 새 이름으로 바꾼다', async () => {
    const loads = mockCrew()
    const requests = capturePatch()
    const user = userEvent.setup()
    renderPage(<CrewManagePage crewId={1} />)
    const field = await openRename(user)
    expect(field).toHaveValue('새벽한강크루')
    expect(field).toHaveAttribute('maxLength', '20')
    await user.clear(field)
    await user.type(field, '  성수런 ')
    await user.click(screen.getByRole('button', { name: '저장하기' }))
    expect(await screen.findByText('크루 이름을 바꿨어요')).toBeInTheDocument()
    expect(requests).toEqual([{ name: '성수런' }])
    await vi.waitFor(() => expect(loads.count).toBeGreaterThan(1))
  })

  it('이름이 비어 있으면 저장하기가 꺼져 있다', async () => {
    mockCrew()
    const user = userEvent.setup()
    renderPage(<CrewManagePage crewId={1} />)
    await user.clear(await openRename(user))
    expect(screen.getByRole('button', { name: '저장하기' })).toBeDisabled()
  })

  it('저장을 두 번 눌러도 한 번만 보낸다', async () => {
    mockCrew()
    const requests = capturePatch(async () => {
      await delay(100)
      return renamed()
    })
    const user = userEvent.setup()
    renderPage(<CrewManagePage crewId={1} />)
    await openRename(user)
    const save = screen.getByRole('button', { name: '저장하기' })
    await user.click(save)
    await user.click(save)
    await screen.findByText('크루 이름을 바꿨어요')
    expect(requests).toHaveLength(1)
  })

  it('서버가 이름을 받지 않으면(400) 입력칸 아래에 서버 문구를 보여 준다', async () => {
    mockCrew()
    capturePatch(() =>
      failWith(400, 'INVALID_REQUEST', '크루 이름은 1자 이상 20자 이하로 입력해주세요.'),
    )
    const user = userEvent.setup()
    renderPage(<CrewManagePage crewId={1} />)
    await openRename(user)
    await user.click(screen.getByRole('button', { name: '저장하기' }))
    expect(
      await screen.findByText('크루 이름은 1자 이상 20자 이하로 입력해주세요.'),
    ).toBeInTheDocument()
    expect(screen.queryByText('크루 이름을 바꿨어요')).not.toBeInTheDocument()
  })

  it('그사이 크루장이 아니게 됐으면(403) 성공처럼 보이지 않고 다시 불러온다', async () => {
    const loads = mockCrew()
    capturePatch(() => failWith(403, 'CREW_LEADER_ONLY'))
    const user = userEvent.setup()
    renderPage(<CrewManagePage crewId={1} />)
    await openRename(user)
    await user.click(screen.getByRole('button', { name: '저장하기' }))
    expect(
      await findErrorToast('크루장만 할 수 있어요. 바뀐 크루 정보를 다시 불러왔어요.'),
    ).toBeInTheDocument()
    expect(screen.queryByText('크루 이름을 바꿨어요')).not.toBeInTheDocument()
    await vi.waitFor(() => expect(loads.count).toBeGreaterThan(1))
  })

  it('크루장이 아니면 이름 바꾸기를 보지 못한다', async () => {
    mockCrew({ myRole: 'MANAGER' })
    renderPage(<CrewManagePage crewId={1} />)
    await screen.findByText('크루장만 크루를 관리할 수 있어요')
    expect(screen.queryByRole('button', { name: '이름 바꾸기' })).not.toBeInTheDocument()
  })
})

describe('크루원 목록 줄 맞춤', () => {
  // 닉네임 최대 길이: 한글 8자 · 영문 16자(가입 규칙)
  const LONGEST = ['가나다라마바사아', 'WWWWWWWWWWWWWWWW']

  function mockLongNames() {
    const example = memberMock.getFindCrewResponseMock()
    const members = [
      { memberId: 1, nickname: '곰돌', role: 'LEADER' as const },
      { memberId: 2, nickname: LONGEST[0], role: 'MANAGER' as const },
      { memberId: 3, nickname: LONGEST[1], role: 'MEMBER' as const },
    ]
    server.use(
      memberMock.getFindCrewMockHandler({
        ...example,
        data: { ...example.data!, memberCount: members.length, members },
      }),
    )
  }

  /** 줄마다 닉네임 칸에는 닉네임만 있고(말줄임 · 전체 이름 툴팁), 뱃지는 늘 세 번째 칸에 있다 */
  function expectAlignedRows(nicknames: string[]) {
    for (const nickname of nicknames) {
      const name = screen.getByText(nickname)
      expect(name).toHaveClass('truncate')
      expect(name).toHaveAttribute('title', nickname)
      const row = name.closest('li')!
      expect(row.children[2]).toHaveTextContent(/^(크루장|운영진|크루원)$/)
    }
  }

  it('크루 관리: 가장 긴 닉네임과 내 줄도 뱃지가 같은 칸에 있고, 내 표시는 없다', async () => {
    mockLongNames()
    renderPage(<CrewManagePage crewId={1} />)
    await screen.findByText(LONGEST[1])
    expectAlignedRows(['곰돌', ...LONGEST])
    expect(screen.queryByText(/^나$|\(나\)/)).not.toBeInTheDocument()
  })

  it('크루 홈: 가장 긴 닉네임도 뱃지가 같은 칸에 있다', async () => {
    mockLongNames()
    renderPage(<CrewHomePage crewId={1} />)
    await screen.findByText(LONGEST[1])
    expectAlignedRows(['곰돌', ...LONGEST])
  })
})
