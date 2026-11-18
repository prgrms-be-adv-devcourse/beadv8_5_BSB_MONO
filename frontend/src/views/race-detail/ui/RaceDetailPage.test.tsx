import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, cleanup, render, screen, within } from '@testing-library/react'
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

  it('문의하기 링크는 이 대회의 문의 작성 화면으로 보낸다 (M02-7)', () => {
    renderPage()
    expect(screen.getByRole('link', { name: '이 대회에 문의하기' })).toHaveAttribute(
      'href',
      `/me/inquiries/new?raceId=${RACE_DETAIL.id}`,
    )
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

    // 오류 Toast는 화면 읽기 프로그램용 알림(role=alert)에도 같은 문구가 한 번 더 그려진다
    expect(
      await screen.findAllByText('복사하지 못했어요. 잠시 뒤 다시 시도해 주세요.'),
    ).not.toHaveLength(0)
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

describe('RaceDetailPage v3 배치', () => {
  it('대표 사진 위에는 쪽 번호만 있고 제목 글과 상태 배지는 없다', () => {
    renderPage()
    expect(screen.getByText(`1 / ${RACE_DETAIL.images.length}`)).toBeInTheDocument()
    expect(screen.queryByText(RACE_DETAIL.heroTitle)).not.toBeInTheDocument()
    expect(screen.queryByText(RACE_DETAIL.status.label)).not.toBeInTheDocument()
  })

  it('제목 아래에 대회일 · 장소 · 접수 마감과 정원 · 최저가를 보여 준다', () => {
    renderPage()
    expect(screen.getByRole('heading', { level: 2, name: RACE_DETAIL.title })).toBeInTheDocument()
    expect(screen.getByText(RACE_DETAIL.startsAt)).toBeInTheDocument()
    expect(screen.getByText(RACE_DETAIL.place)).toBeInTheDocument()
    expect(screen.getByText('접수 마감 D-12 · 정원 3,000명')).toBeInTheDocument()
    // 종목 5K도 35,000원이라 「부터」 바로 앞 글자로 최저가를 찾는다
    expect(screen.getByText('부터').previousElementSibling).toHaveTextContent('35,000원')
  })

  it('접수가 끝났으면 「접수 마감」을 한 번만 쓰고, 마감일이 없으면 정원만 쓴다', () => {
    renderPage({ race: { ...RACE_DETAIL, dDay: '마감' } })
    expect(screen.getByText('접수 마감 · 정원 3,000명')).toBeInTheDocument()
    cleanup()
    renderPage({ race: { ...RACE_DETAIL, dDay: '-' } })
    expect(screen.getByText('정원 3,000명')).toBeInTheDocument()
  })

  it('바로 가기 탭은 종목 · 일정 · 환불 · 상세이고, 누르면 그 자리로 스크롤한다', async () => {
    const scrollIntoView = vi.fn()
    Element.prototype.scrollIntoView = scrollIntoView
    renderPage()
    const nav = screen.getByRole('navigation', { name: '대회 상세 바로 가기' })
    const links = within(nav).getAllByRole('link')
    expect(links.map((link) => link.textContent)).toEqual(['종목', '일정', '환불', '상세'])
    expect(links[0]).toHaveAttribute('aria-current', 'location')

    await userEvent.setup().click(within(nav).getByRole('link', { name: '환불' }))
    expect(scrollIntoView).toHaveBeenCalled()
    expect(scrollIntoView.mock.instances.at(-1)).toHaveProperty('id', 'race-refund')
    expect(within(nav).getByRole('link', { name: '환불' })).toHaveAttribute(
      'aria-current',
      'location',
    )
  })

  it('설명도 사진도 없으면 바로 가기에서 상세를 뺀다', () => {
    renderPage({ race: { ...RACE_DETAIL, description: '', images: [] } })
    const nav = screen.getByRole('navigation', { name: '대회 상세 바로 가기' })
    expect(
      within(nav)
        .getAllByRole('link')
        .map((link) => link.textContent),
    ).toEqual(['종목', '일정', '환불'])
  })

  it('종목 카드는 사진 없이 이름 · 구성 · 가격이고, 눌러서 고른 종목을 바꾼다', async () => {
    renderPage()
    const first = screen.getByRole('button', { name: /^10K/ })
    const second = screen.getByRole('button', { name: /^5K/ })
    expect(first).toHaveTextContent('1인 · 기념 티셔츠 · 완주 메달')
    expect(first).toHaveTextContent('45,000원')
    expect(first.querySelector('img')).toBeNull()
    expect(first).toHaveAttribute('aria-pressed', 'true')

    await userEvent.setup().click(second)
    expect(second).toHaveAttribute('aria-pressed', 'true')
    expect(first).toHaveAttribute('aria-pressed', 'false')
  })

  it('정원이 찬 종목은 고를 수 없다', () => {
    const race: RaceDetail = {
      ...RACE_DETAIL,
      options: RACE_DETAIL.options.map((o) => (o.id === '10k' ? { ...o, soldOut: true } : o)),
    }
    renderPage({ race })
    expect(screen.getByRole('button', { name: /^10K/ })).toBeDisabled()
    expect(screen.getByRole('button', { name: /^5K/ })).toHaveAttribute('aria-pressed', 'true')
  })

  it('접수 · 취소 · 환불 · 키트 배송 · 양도를 이름 칸과 내용으로 안내한다', () => {
    renderPage()
    for (const name of ['접수', '취소·환불', '키트 배송', '양도']) {
      expect(screen.getByText(name)).toBeInTheDocument()
    }
    expect(screen.getByText('10/14 (수) 10:00 ~ 10/31 (토) 18:00')).toBeInTheDocument()
    expect(screen.getByText('정보 수정은 10/31 (토) 18:00까지')).toBeInTheDocument()
    expect(screen.getByText('10/31 (토) 23:59')).toBeInTheDocument()
    expect(screen.getByText('까지 취소하면 전액 환불')).toBeInTheDocument()
    expect(screen.getByText('환불 불가')).toBeInTheDocument()
    expect(screen.getByText('예치금')).toBeInTheDocument()
    expect(screen.getByText('양도할 수 없어요')).toBeInTheDocument()
  })

  it('환불 기한이 없는 대회는 기한 상자를 그리지 않는다', () => {
    const race: RaceDetail = {
      ...RACE_DETAIL,
      schedule: RACE_DETAIL.schedule.map((row) =>
        row.label === '전액 환불 기한' ? { ...row, value: '-' } : row,
      ),
    }
    renderPage({ race })
    expect(screen.queryByText('까지 취소하면 전액 환불')).not.toBeInTheDocument()
    expect(screen.queryByText('환불 불가')).not.toBeInTheDocument()
  })

  it('자주 묻는 질문은 눌러서 답을 펼치고, 질문이 없으면 영역을 숨긴다', async () => {
    const user = userEvent.setup()
    const { unmount } = render(
      <QueryClientProvider client={new QueryClient()}>
        <ToastProvider>
          <RaceDetailPage race={RACE_DETAIL} />
        </ToastProvider>
      </QueryClientProvider>,
    )
    expect(screen.getByRole('heading', { name: '자주 묻는 질문' })).toBeInTheDocument()
    const { question, answer } = RACE_DETAIL.faq[0]
    await user.click(screen.getByText(question))
    expect(screen.getByText(answer)).toBeVisible()
    unmount()

    renderPage({ race: { ...RACE_DETAIL, faq: [] } })
    expect(screen.queryByRole('heading', { name: '자주 묻는 질문' })).not.toBeInTheDocument()
  })

  it('상세 정보 제목 아래에 사진을 보여 주고, 판매자 정보 세 줄이 맨 아래에 있다', () => {
    renderPage()
    expect(screen.getByRole('heading', { name: '상세 정보' })).toBeInTheDocument()
    expect(screen.getAllByRole('img', { name: /상세 정보 이미지/ })).toHaveLength(
      RACE_DETAIL.images.length,
    )
    expect(screen.getByText(RACE_DETAIL.seller.name)).toBeInTheDocument()
    expect(screen.getByText(RACE_DETAIL.seller.businessNo)).toBeInTheDocument()
    expect(screen.getByText(RACE_DETAIL.seller.mailOrderNo)).toBeInTheDocument()
  })
})
