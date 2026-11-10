import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { setAccessToken } from '@/shared/api'
import { memberMock, server } from '@/shared/testing'
import { ToastProvider } from '@/shared/ui'

import { CrewInvitesPage } from './CrewInvitesPage'

const replace = vi.fn()
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), replace }),
  usePathname: () => '/crews/1/invites',
}))

const goToSocialLogin = vi.fn()
vi.mock('@/features/auth/lib/social-login', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/features/auth/lib/social-login')>()),
  goToSocialLogin: (href: string) => goToSocialLogin(href),
}))

beforeEach(() => {
  replace.mockClear()
  goToSocialLogin.mockClear()
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

const friend = (n: number) => ({
  uuid: `uuid-${n}`,
  nickname: `친구${n}`,
  profileImageUrl: null,
})

function mockFriends(count: number) {
  server.use(
    memberMock.getFindKakaoInviteFriendsMockHandler({
      status: 200,
      message: '조회',
      data: Array.from({ length: count }, (_, i) => friend(i + 1)),
    }),
  )
}

async function openSheet(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole('button', { name: '카카오톡 친구에게 보내기' }))
  return screen.findByRole('dialog')
}

describe('CrewInvitesPage 카카오톡 친구에게 보내기', () => {
  it('크루런을 쓰는 카카오 친구를 골라 보내면 고른 사람의 uuid를 보내고 보낸 사람 수를 알린다', async () => {
    mockFriends(3)
    let body: unknown
    server.use(
      http.post('*/api/v1/member/crews/:crewId/kakao-invites', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({
          status: 200,
          message: '보냄',
          data: { sentUuids: ['uuid-1', 'uuid-3'], failures: [] },
        })
      }),
    )
    const user = userEvent.setup()
    renderPage(<CrewInvitesPage crewId={1} />)
    const sheet = await openSheet(user)

    await user.click(await within(sheet).findByRole('checkbox', { name: '친구1' }))
    await user.click(within(sheet).getByRole('checkbox', { name: '친구3' }))
    await user.click(within(sheet).getByRole('button', { name: '2명에게 보내기' }))

    expect(await screen.findByText('2명에게 초대를 보냈어요')).toBeInTheDocument()
    expect(body).toEqual({ receiverUuids: ['uuid-1', 'uuid-3'] })
  })

  it('한 번에 5명까지만 고를 수 있다', async () => {
    mockFriends(6)
    const user = userEvent.setup()
    renderPage(<CrewInvitesPage crewId={1} />)
    const sheet = await openSheet(user)

    for (const n of [1, 2, 3, 4, 5]) {
      await user.click(await within(sheet).findByRole('checkbox', { name: `친구${n}` }))
    }

    // 체크박스는 disabled 속성 대신 aria-disabled로 막힌다
    expect(within(sheet).getByRole('checkbox', { name: '친구6' })).toHaveAttribute(
      'aria-disabled',
      'true',
    )
    expect(within(sheet).getByText('5명까지 고를 수 있어요')).toBeInTheDocument()
  })

  it('보낼 수 있는 친구가 없으면 링크 복사를 안내한다', async () => {
    mockFriends(0)
    const user = userEvent.setup()
    renderPage(<CrewInvitesPage crewId={1} />)
    const sheet = await openSheet(user)

    expect(
      await within(sheet).findByText(
        '크루런을 쓰는 카카오 친구가 없어요. 초대 링크를 복사해 보내 주세요.',
      ),
    ).toBeInTheDocument()
  })

  it('메시지 동의가 필요하면 안내하고, 동의하러 가면 동의 표를 받은 뒤 카카오 동의 화면으로 옮긴다', async () => {
    let ticketIssued = false
    server.use(
      http.get('*/api/v1/member/crews/:crewId/kakao-invites/friends', () =>
        failWith(403, 'KAKAO_MESSAGE_CONSENT_REQUIRED'),
      ),
      http.post('*/api/v1/member/members/me/kakao-message/consent-ticket', () => {
        ticketIssued = true
        return HttpResponse.json({ status: 201, message: '발급', data: null }, { status: 201 })
      }),
    )
    const user = userEvent.setup()
    renderPage(<CrewInvitesPage crewId={1} />)
    const sheet = await openSheet(user)

    expect(
      await within(sheet).findByText(
        '카카오톡으로 초대를 보내려면 친구 목록과 메시지 보내기 동의가 필요해요.',
      ),
    ).toBeInTheDocument()
    await user.click(within(sheet).getByRole('button', { name: '동의하러 가기' }))

    await vi.waitFor(() =>
      expect(goToSocialLogin).toHaveBeenCalledWith(
        '/api/v1/auth/oauth2/authorize/kakao?next=%2Fcrews%2F1%2Finvites',
      ),
    )
    expect(ticketIssued).toBe(true)
  })

  it('카카오가 연결되지 않았으면 계정 연결 화면으로 안내한다', async () => {
    server.use(
      http.get('*/api/v1/member/crews/:crewId/kakao-invites/friends', () =>
        failWith(409, 'KAKAO_NOT_LINKED'),
      ),
    )
    const user = userEvent.setup()
    renderPage(<CrewInvitesPage crewId={1} />)
    const sheet = await openSheet(user)

    expect(
      await within(sheet).findByText('마이 > 계정 연결에서 카카오를 먼저 연결해 주세요.'),
    ).toBeInTheDocument()
    expect(within(sheet).getByRole('link', { name: '계정 연결로 가기' })).toHaveAttribute(
      'href',
      '/me/accounts',
    )
  })

  it('카카오가 응답하지 않으면 다시 시도하거나 링크를 복사하라고 안내한다', async () => {
    server.use(
      http.get('*/api/v1/member/crews/:crewId/kakao-invites/friends', () =>
        failWith(502, 'KAKAO_UNAVAILABLE'),
      ),
    )
    const user = userEvent.setup()
    renderPage(<CrewInvitesPage crewId={1} />)
    const sheet = await openSheet(user)

    expect(
      await within(sheet).findByText(
        '카카오톡이 응답하지 않아요. 잠시 뒤 다시 보내거나 초대 링크를 복사해 보내 주세요.',
      ),
    ).toBeInTheDocument()
  })

  it('오늘 보낼 수 있는 양을 다 쓰면 링크 복사를 안내한다', async () => {
    mockFriends(1)
    server.use(
      http.post('*/api/v1/member/crews/:crewId/kakao-invites', () =>
        failWith(429, 'KAKAO_MESSAGE_DAILY_LIMIT'),
      ),
    )
    const user = userEvent.setup()
    renderPage(<CrewInvitesPage crewId={1} />)
    const sheet = await openSheet(user)
    await user.click(await within(sheet).findByRole('checkbox', { name: '친구1' }))
    await user.click(within(sheet).getByRole('button', { name: '1명에게 보내기' }))

    expect(
      await screen.findByText(
        '오늘은 카카오톡으로 더 보낼 수 없어요. 초대 링크를 복사해 보내 주세요.',
      ),
    ).toBeInTheDocument()
  })

  it('일부만 보내졌으면 보낸 수와 보내지 못한 사람을 보여 준다', async () => {
    mockFriends(2)
    server.use(
      http.post('*/api/v1/member/crews/:crewId/kakao-invites', () =>
        HttpResponse.json({
          status: 200,
          message: '보냄',
          data: { sentUuids: ['uuid-1'], failures: [{ uuid: 'uuid-2', reason: 'DAILY_LIMIT' }] },
        }),
      ),
    )
    const user = userEvent.setup()
    renderPage(<CrewInvitesPage crewId={1} />)
    const sheet = await openSheet(user)
    await user.click(await within(sheet).findByRole('checkbox', { name: '친구1' }))
    await user.click(within(sheet).getByRole('checkbox', { name: '친구2' }))
    await user.click(within(sheet).getByRole('button', { name: '2명에게 보내기' }))

    expect(
      await within(sheet).findByText('1명에게 보냈어요. 1명에게는 보내지 못했어요.'),
    ).toBeInTheDocument()
    expect(within(sheet).getByText('보내지 못한 사람: 친구2')).toBeInTheDocument()
  })

  it('보내기가 카카오 장애로 실패해도 고른 친구와 보내기 버튼이 남아 다시 보낼 수 있다', async () => {
    mockFriends(1)
    let calls = 0
    server.use(
      http.post('*/api/v1/member/crews/:crewId/kakao-invites', () => {
        calls += 1
        if (calls === 1) return failWith(502, 'KAKAO_UNAVAILABLE')
        return HttpResponse.json({
          status: 200,
          message: '보냄',
          data: { sentUuids: ['uuid-1'], failures: [] },
        })
      }),
    )
    const user = userEvent.setup()
    renderPage(<CrewInvitesPage crewId={1} />)
    const sheet = await openSheet(user)
    await user.click(await within(sheet).findByRole('checkbox', { name: '친구1' }))
    await user.click(within(sheet).getByRole('button', { name: '1명에게 보내기' }))

    expect(
      await within(sheet).findByText(
        '카카오톡이 응답하지 않아 보냈는지 확인하지 못했어요. 받았는지 확인한 뒤 다시 보내거나 초대 링크를 복사해 보내 주세요.',
      ),
    ).toBeInTheDocument()
    await user.click(within(sheet).getByRole('button', { name: '1명에게 보내기' }))

    expect(await screen.findByText('1명에게 초대를 보냈어요')).toBeInTheDocument()
    expect(calls).toBe(2)
  })

  it('보내기가 동의 필요로 거절되면 동의 안내로 바뀐다', async () => {
    mockFriends(1)
    server.use(
      http.post('*/api/v1/member/crews/:crewId/kakao-invites', () =>
        failWith(403, 'KAKAO_MESSAGE_CONSENT_REQUIRED'),
      ),
    )
    const user = userEvent.setup()
    renderPage(<CrewInvitesPage crewId={1} />)
    const sheet = await openSheet(user)
    await user.click(await within(sheet).findByRole('checkbox', { name: '친구1' }))
    await user.click(within(sheet).getByRole('button', { name: '1명에게 보내기' }))

    expect(await within(sheet).findByRole('button', { name: '동의하러 가기' })).toBeInTheDocument()
  })

  it('카카오와 관계없는 거절(초대 권한 없음 등)은 서버 문구로 알린다', async () => {
    server.use(
      http.get('*/api/v1/member/crews/:crewId/kakao-invites/friends', () =>
        failWith(403, 'CREW_INVITE_FORBIDDEN', '크루장과 운영진만 초대할 수 있습니다.'),
      ),
    )
    const user = userEvent.setup()
    renderPage(<CrewInvitesPage crewId={1} />)
    const sheet = await openSheet(user)

    expect(
      await within(sheet).findByText('크루장과 운영진만 초대할 수 있습니다.'),
    ).toBeInTheDocument()
    expect(within(sheet).queryByText(/카카오톡이 응답하지 않아요/)).not.toBeInTheDocument()
  })

  it('일부만 보내지면 보낸 친구는 목록에서 빠지고, 카카오 오류로 못 보낸 친구는 고른 채로 남는다', async () => {
    mockFriends(3)
    server.use(
      http.post('*/api/v1/member/crews/:crewId/kakao-invites', () =>
        HttpResponse.json({
          status: 200,
          message: '보냄',
          data: {
            sentUuids: ['uuid-1'],
            failures: [
              { uuid: 'uuid-2', reason: 'KAKAO_ERROR' },
              { uuid: 'uuid-3', reason: 'DAILY_LIMIT' },
            ],
          },
        }),
      ),
    )
    const user = userEvent.setup()
    renderPage(<CrewInvitesPage crewId={1} />)
    const sheet = await openSheet(user)
    for (const n of [1, 2, 3]) {
      await user.click(await within(sheet).findByRole('checkbox', { name: `친구${n}` }))
    }
    await user.click(within(sheet).getByRole('button', { name: '3명에게 보내기' }))

    await within(sheet).findByText('1명에게 보냈어요. 2명에게는 보내지 못했어요.')
    expect(within(sheet).queryByRole('checkbox', { name: '친구1' })).not.toBeInTheDocument()
    expect(within(sheet).getByRole('checkbox', { name: '친구2' })).toBeChecked()
    expect(within(sheet).getByRole('checkbox', { name: '친구3' })).not.toBeChecked()
  })

  it('카카오가 응답하지 않아 보냈는지 모르는 친구는 고르지 않은 채로 두고 받았는지 확인하라고 안내한다', async () => {
    mockFriends(2)
    server.use(
      http.post('*/api/v1/member/crews/:crewId/kakao-invites', () =>
        HttpResponse.json({
          status: 200,
          message: '보냄',
          data: { sentUuids: ['uuid-1'], failures: [{ uuid: 'uuid-2', reason: 'UNCONFIRMED' }] },
        }),
      ),
    )
    const user = userEvent.setup()
    renderPage(<CrewInvitesPage crewId={1} />)
    const sheet = await openSheet(user)
    await user.click(await within(sheet).findByRole('checkbox', { name: '친구1' }))
    await user.click(within(sheet).getByRole('checkbox', { name: '친구2' }))
    await user.click(within(sheet).getByRole('button', { name: '2명에게 보내기' }))

    expect(
      await within(sheet).findByText(
        '카카오톡이 응답하지 않아 보냈는지 확인하지 못한 사람이 있어요. 받았는지 확인한 뒤 다시 보내 주세요.',
      ),
    ).toBeInTheDocument()
    expect(within(sheet).getByRole('checkbox', { name: '친구2' })).not.toBeChecked()
  })

  it('동의를 마치고 돌아오면 친구 고르기를 바로 다시 열고 주소의 결과 값을 지운다', async () => {
    mockFriends(1)
    renderPage(<CrewInvitesPage crewId={1} kakaoConsent="granted" />)

    const sheet = await screen.findByRole('dialog')
    expect(await within(sheet).findByRole('checkbox', { name: '친구1' })).toBeInTheDocument()
    expect(replace).toHaveBeenCalledWith('/crews/1/invites')
  })

  it.each([
    ['KAKAO_CONSENT_DENIED', '동의하지 않아 카카오톡으로 보낼 수 없어요.'],
    [
      'KAKAO_ACCOUNT_MISMATCH',
      '연결된 카카오 계정과 다른 계정으로 동의했어요. 카카오에서 로그아웃한 뒤 다시 시도해 주세요.',
    ],
  ])('동의 결과가 %s면 알린다', async (error, message) => {
    renderPage(<CrewInvitesPage crewId={1} kakaoError={error} />)

    // 오류 알림은 화면 읽기용 안내 영역에도 한 번 더 그려진다
    expect((await screen.findAllByText(message)).length).toBeGreaterThan(0)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})
