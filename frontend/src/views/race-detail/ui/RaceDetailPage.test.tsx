import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'

import { RACE_DETAIL, type RaceDetail } from '@/entities/race'
import { clearAccessToken, setAccessToken } from '@/shared/api'
import { productMock, server } from '@/shared/testing'
import { ToastProvider } from '@/shared/ui'

import { RaceDetailPage, type RaceDetailPageProps } from './RaceDetailPage'

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), replace: vi.fn() }),
  usePathname: () => '/races/1',
}))

function renderPage(props: Partial<RaceDetailPageProps> = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <RaceDetailPage race={RACE_DETAIL} {...props} />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

const applyLink = () => screen.getByRole('link', { name: '신청하기' })

describe('RaceDetailPage 신청하기', () => {
  it('로그인 전이면 로그인 화면으로 보내고, 로그인 뒤 갈 대기열 주소를 next로 넘긴다', () => {
    clearAccessToken()
    renderPage()
    const href = applyLink().getAttribute('href') ?? ''
    const url = new URL(href, 'http://localhost')
    expect(url.pathname).toBe('/login')
    expect(url.searchParams.get('next')).toMatch(
      new RegExp(`^/races/${RACE_DETAIL.id}/queue\\?option=`),
    )
  })

  it('로그인했으면 대기열(M05-Q1)로 보낸다', () => {
    renderPage()
    const link = applyLink()
    act(() => setAccessToken('access-token'))
    expect(link.getAttribute('href')).toMatch(
      new RegExp(`^/races/${RACE_DETAIL.id}/queue\\?option=`),
    )
  })

  it('이미 신청한 대회는 버튼을 끄고 안내를 보여 준다 (M02-1)', () => {
    renderPage({ alreadyApplied: true })
    expect(screen.getByRole('button', { name: '이미 신청한 대회예요' })).toBeDisabled()
    expect(screen.getByText('이 대회에 이미 신청했어요')).toBeInTheDocument()
  })

  it('다시 신청할 수 없는 대회는 버튼을 끈다 (M02-4)', () => {
    renderPage({ reapplyBlocked: true })
    expect(screen.getByRole('button', { name: '다시 신청할 수 없는 대회예요' })).toBeDisabled()
  })
})

describe('RaceDetailPage 공유 · 일정', () => {
  it('공유 아이콘을 누르면 카카오톡 · 링크 복사 · 다른 앱 시트가 열린다', async () => {
    renderPage()
    await userEvent.setup().click(screen.getByRole('button', { name: '공유하기' }))

    const sheet = await screen.findByRole('dialog')
    for (const name of ['카카오톡', '링크 복사', '다른 앱']) {
      expect(within(sheet).getByRole('button', { name })).toBeInTheDocument()
    }
  })

  it('링크 복사가 막히면 성공 대신 실패 안내를 띄운다', async () => {
    const user = userEvent.setup()
    const writeText = vi
      .spyOn(navigator.clipboard, 'writeText')
      .mockRejectedValue(new Error('denied'))
    renderPage()
    await user.click(screen.getByRole('button', { name: '공유하기' }))
    await user.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: '링크 복사' }),
    )

    expect(
      await screen.findByText('복사하지 못했어요. 링크를 길게 눌러 복사해 주세요.'),
    ).toBeInTheDocument()
    expect(screen.queryByText('링크를 복사했어요')).not.toBeInTheDocument()
    writeText.mockRestore()
  })

  it('링크 복사가 되면 복사했다고 알린다', async () => {
    const user = userEvent.setup()
    const writeText = vi.spyOn(navigator.clipboard, 'writeText').mockResolvedValue()
    renderPage()
    await user.click(screen.getByRole('button', { name: '공유하기' }))
    await user.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: '링크 복사' }),
    )

    expect(await screen.findByText('링크를 복사했어요')).toBeInTheDocument()
    expect(writeText).toHaveBeenCalledWith(
      expect.stringMatching(new RegExp(`/races/${RACE_DETAIL.id}$`)),
    )
    writeText.mockRestore()
  })

  it('접수 일정은 접수 기간과 정보 수정 기한 두 줄이다', () => {
    renderPage()
    expect(screen.getByText('정보 수정 기한')).toBeInTheDocument()
    expect(screen.queryByText('전액 환불 기한')).not.toBeInTheDocument()
  })
})

describe('RaceDetailPage 비슷한 대회 (M02-6)', () => {
  let asked = false
  const similar = (races: unknown) => {
    asked = false
    server.use(
      http.get('*/api/v1/product/races/:raceId/similar', () => {
        asked = true
        return HttpResponse.json({ status: 200, message: 'ok', data: races })
      }),
    )
  }
  const base = productMock.getSearchResponseMock().data!.races![0]

  it('추천 대회를 최대 3줄 보여 준다', async () => {
    similar(
      [1, 2, 3, 4].map((raceId) => ({ ...base, raceId: 100 + raceId, title: `추천 ${raceId}` })),
    )
    renderPage()

    expect(await screen.findByRole('heading', { name: '비슷한 대회' })).toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: /추천 \d/ })).toHaveLength(3)
  })

  it('추천이 없으면 영역을 숨긴다', async () => {
    similar([])
    renderPage()
    await vi.waitFor(() => expect(asked).toBe(true))
    await act(() => new Promise((resolve) => setTimeout(resolve, 50)))
    expect(screen.queryByRole('heading', { name: '비슷한 대회' })).not.toBeInTheDocument()
  })
})

describe('RaceDetailPage 크루 전용 자리 (M02-3)', () => {
  const crewRace: RaceDetail = { ...RACE_DETAIL, crew: { minCount: 4, maxCount: 10 } }
  const status = (data: unknown) =>
    server.use(
      http.get('*/api/v1/order/parties/me/race-status', () =>
        HttpResponse.json({ status: 200, message: 'ok', data }),
      ),
    )

  it('로그인하면 신청하기가 「어떻게 신청할까요?」 시트를 열고, 파티가 없으면 크루 홈으로 안내한다', async () => {
    status({ role: 'NONE', canApply: false, reason: 'NO_PARTY' })
    renderPage({ race: crewRace })
    act(() => setAccessToken('access-token'))
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: '신청하기' }))

    const sheet = await screen.findByRole('dialog')
    expect(within(sheet).getByText('어떻게 신청할까요?')).toBeInTheDocument()
    expect(within(sheet).getByText('개인으로 신청')).toBeInTheDocument()
    expect(await within(sheet).findByText('크루 홈에서 파티 만들기')).toBeInTheDocument()
  })

  it('파티장이 인원이 모자라면 파티 항목을 끄고 몇 명이 더 필요한지 알린다', async () => {
    status({
      role: 'LEADER',
      canApply: false,
      reason: 'TOO_FEW_MEMBERS',
      partyId: 7,
      raceCourseId: 1,
      memberCount: 3,
      minCount: 4,
    })
    renderPage({ race: crewRace })
    act(() => setAccessToken('access-token'))
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: '신청하기' }))

    const sheet = await screen.findByRole('dialog')
    expect(await within(sheet).findByText('1명 더 모이면 신청할 수 있어요')).toBeInTheDocument()
    expect(within(sheet).getByRole('button', { name: /파티로 신청/ })).toBeDisabled()
  })

  it('파티원이면 개인 신청을 막는다 (M02-5)', async () => {
    status({ role: 'MEMBER', canApply: false, partyId: 7, partyStatus: 'RECRUITING' })
    renderPage({ race: crewRace })
    act(() => setAccessToken('access-token'))

    expect(await screen.findByRole('button', { name: '파티로 신청 중이에요' })).toBeDisabled()
    expect(screen.getByText('파티로 신청 중인 대회예요')).toBeInTheDocument()
  })
})
