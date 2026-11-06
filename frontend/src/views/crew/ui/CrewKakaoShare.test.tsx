import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { setAccessToken } from '@/shared/api'
import { memberMock, server } from '@/shared/testing'
import { ToastProvider } from '@/shared/ui'

import { CrewInvitesPage } from './CrewInvitesPage'

// 실패 경우 점검표 (카카오톡으로 공유하기)
// - 보안: 키 형식은 client-env가 검사한다. SDK는 버전을 고정하고 integrity를 붙여 바뀐 파일을 막는다.
// - 두 번 누름: 공유 창만 다시 열리고 서버 상태는 바뀌지 않는다. 초기화는 한 번만 한다.
// - 카카오 SDK 실패: 불러오지 못했거나(Kakao 없음) sendDefault가 예외를 던지면 링크 복사를 안내한다.
// - 실패 격리: 공유가 실패해도 링크 복사하기와 친구에게 보내기는 그대로 쓸 수 있다.
// - 계약: sendDefault 인자는 카카오 피드 템플릿 이름(objectType, content.link, buttons[].link)을 따른다.

const KEY = '0123456789abcdef0123456789abcdef'
const env = vi.hoisted(() => ({
  NEXT_PUBLIC_API_MOCKING: 'off' as 'on' | 'off',
  NEXT_PUBLIC_KAKAO_JS_KEY: undefined as string | undefined,
}))
vi.mock('@/shared/config', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/shared/config')>()
  return { ...original, clientEnv: env }
})

// 테스트에서는 카카오 CDN을 부르지 않는다. 스크립트에 넘긴 값만 기록하고 window.Kakao는 테스트마다 끼운다
const scriptProps = vi.hoisted(() => [] as Record<string, unknown>[])
vi.mock('next/script', () => ({
  default: (props: Record<string, unknown>) => {
    scriptProps.push(props)
    return null
  },
}))

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), replace: vi.fn() }),
  usePathname: () => '/crews/1/invites',
}))

const SHARE = '카카오톡으로 공유하기'
const SHARE_FAILED = '카카오톡을 열지 못했어요. 링크를 복사해 보내 주세요.'
const INVITE_URL = `${window.location.origin}/invite/q7Xk2LmZ9vBn4RtW8yCp1A`

type FakeKakao = {
  init: ReturnType<typeof vi.fn>
  isInitialized: ReturnType<typeof vi.fn>
  Share: { sendDefault: ReturnType<typeof vi.fn> }
}

function fakeKakao(initialized = false): FakeKakao {
  const kakao: FakeKakao = {
    init: vi.fn(() => {
      kakao.isInitialized.mockReturnValue(true)
    }),
    isInitialized: vi.fn(() => initialized),
    Share: { sendDefault: vi.fn() },
  }
  ;(window as unknown as { Kakao?: FakeKakao }).Kakao = kakao
  return kakao
}

beforeEach(() => {
  env.NEXT_PUBLIC_KAKAO_JS_KEY = KEY
  scriptProps.length = 0
  setAccessToken('access-token')
  server.use(
    memberMock.getFindMeMockHandler({
      status: 200,
      message: '조회',
      data: {
        id: 1,
        createDate: '2026-10-05T21:25:35',
        modifyDate: '2026-10-05T21:25:35',
        nickname: '새벽러너',
        email: 'runner1@crewrun.com',
        phone: '010-1234-5678',
        name: '김러너',
        birthDate: '1995-04-12',
      },
    }),
  )
})

afterEach(() => {
  delete (window as unknown as { Kakao?: FakeKakao }).Kakao
})

function renderPage(page: ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>{page}</ToastProvider>
    </QueryClientProvider>,
  )
}

async function createLink(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole('button', { name: '초대 링크 만들기' }))
  await screen.findByRole('button', { name: '링크 복사하기' })
}

describe('CrewInvitesPage 카카오톡으로 공유하기', () => {
  it('링크를 만들기 전에는 공유 버튼이 없다', async () => {
    renderPage(<CrewInvitesPage crewId={1} />)
    await screen.findByRole('button', { name: '초대 링크 만들기' })
    expect(screen.queryByRole('button', { name: SHARE })).not.toBeInTheDocument()
  })

  it('카카오 키가 없으면 링크를 만들어도 공유 버튼이 없고 SDK도 불러오지 않는다', async () => {
    env.NEXT_PUBLIC_KAKAO_JS_KEY = undefined
    const user = userEvent.setup()
    renderPage(<CrewInvitesPage crewId={1} />)
    await createLink(user)
    expect(screen.queryByRole('button', { name: SHARE })).not.toBeInTheDocument()
    expect(scriptProps).toHaveLength(0)
  })

  it('SDK는 버전을 고정한 카카오 주소에서 integrity를 붙여 불러온다', async () => {
    renderPage(<CrewInvitesPage crewId={1} />)
    await screen.findByRole('button', { name: '초대 링크 만들기' })
    expect(scriptProps.at(-1)).toMatchObject({
      src: expect.stringMatching(
        /^https:\/\/t1\.kakaocdn\.net\/kakao_js_sdk\/\d+\.\d+\.\d+\/kakao\.min\.js$/,
      ),
      integrity: expect.stringMatching(/^sha384-/),
      crossOrigin: 'anonymous',
    })
  })

  it('누르면 크루 이름 · 내 닉네임 · 초대 링크로 피드 메시지를 보낸다', async () => {
    const kakao = fakeKakao()
    const user = userEvent.setup()
    renderPage(<CrewInvitesPage crewId={1} />)
    await createLink(user)
    await user.click(screen.getByRole('button', { name: SHARE }))

    expect(kakao.init).toHaveBeenCalledWith(KEY)
    expect(kakao.Share.sendDefault).toHaveBeenCalledWith({
      objectType: 'feed',
      content: {
        title: '새벽한강크루 크루 초대',
        description: '새벽러너 님이 크루런 크루에 초대했어요. 링크는 24시간 동안 쓸 수 있어요.',
        link: { webUrl: INVITE_URL, mobileWebUrl: INVITE_URL },
      },
      buttons: [{ title: '초대 받기', link: { webUrl: INVITE_URL, mobileWebUrl: INVITE_URL } }],
    })
  })

  it('두 번 눌러도 초기화는 한 번만 한다', async () => {
    const kakao = fakeKakao()
    const user = userEvent.setup()
    renderPage(<CrewInvitesPage crewId={1} />)
    await createLink(user)
    await user.click(screen.getByRole('button', { name: SHARE }))
    await user.click(screen.getByRole('button', { name: SHARE }))

    expect(kakao.init).toHaveBeenCalledTimes(1)
    expect(kakao.Share.sendDefault).toHaveBeenCalledTimes(2)
  })

  it('SDK를 불러오지 못해 Kakao가 없으면 링크 복사를 안내한다', async () => {
    const user = userEvent.setup()
    renderPage(<CrewInvitesPage crewId={1} />)
    await createLink(user)
    await user.click(screen.getByRole('button', { name: SHARE }))

    expect((await screen.findAllByText(SHARE_FAILED)).length).toBeGreaterThan(0)
  })

  it('sendDefault가 예외를 던지면 링크 복사를 안내하고, 복사는 그대로 된다', async () => {
    const kakao = fakeKakao(true)
    kakao.Share.sendDefault.mockImplementation(() => {
      throw new Error('도메인 미등록')
    })
    const writeText = vi.spyOn(navigator.clipboard, 'writeText').mockResolvedValue()
    const user = userEvent.setup()
    renderPage(<CrewInvitesPage crewId={1} />)
    await createLink(user)
    await user.click(screen.getByRole('button', { name: SHARE }))
    expect((await screen.findAllByText(SHARE_FAILED)).length).toBeGreaterThan(0)

    await user.click(screen.getByRole('button', { name: '링크 복사하기' }))
    expect(writeText).toHaveBeenCalledWith(INVITE_URL)
    expect(screen.getByRole('button', { name: '카카오톡 친구에게 보내기' })).toBeInTheDocument()
  })

  it('서버가 보내는 기존 버튼은 「카카오톡 친구에게 보내기」로 보인다', async () => {
    renderPage(<CrewInvitesPage crewId={1} />)
    expect(
      await screen.findByRole('button', { name: '카카오톡 친구에게 보내기' }),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '카카오톡으로 보내기' })).not.toBeInTheDocument()
  })
})
