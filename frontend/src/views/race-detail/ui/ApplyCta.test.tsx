import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { PartyRaceStatus } from '@/entities/party'
import { RACE_DETAIL, type RaceDetail } from '@/entities/race'
import { clearAccessToken, setAccessToken } from '@/shared/api'

import { ApplyCta } from './ApplyCta'

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), replace: vi.fn() }),
  usePathname: () => '/races/1',
}))

// 크루 선예매 11/17 18:00까지, 개인 예매 11/19 10:00부터
const CREW_RACE: RaceDetail = {
  ...RACE_DETAIL,
  id: '1',
  options: [{ ...RACE_DETAIL.options[0], id: '3' }],
  phase: 'upcoming',
  individualOpenAt: '2026-11-19T10:00:00',
  crew: { startAt: '2026-11-10T10:00:00', endAt: '2026-11-17T18:00:00', minCount: 4 },
}
const IN_CREW_WINDOW = new Date('2026-11-15T12:00:00+09:00')
const AFTER_CREW_WINDOW = new Date('2026-11-18T12:00:00+09:00')

const PARTY: PartyRaceStatus = {
  role: 'LEADER',
  canApply: true,
  partyId: 7,
  raceCourseId: 3,
  partyStatus: 'RECRUITING',
  memberCount: 4,
  minCount: 4,
}
const NO_PARTY: PartyRaceStatus = { role: 'NONE', canApply: false }

function renderCta(now: Date, party?: PartyRaceStatus, loggedIn = true) {
  vi.setSystemTime(now)
  if (loggedIn) act(() => setAccessToken('access-token'))
  else clearAccessToken()
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <ApplyCta
        race={CREW_RACE}
        optionId="3"
        alreadyApplied={false}
        reapplyBlocked={false}
        party={party}
      />
    </QueryClientProvider>,
  )
}

describe('ApplyCta · 개인 예매 전', () => {
  beforeEach(() => vi.useFakeTimers({ toFake: ['Date'] }))
  afterEach(() => {
    cleanup()
    vi.useRealTimers()
  })

  it('크루 예매 기간에 파티가 있는 회원은 신청하기를 눌러 파티로 신청할 수 있다 (개인 신청은 오픈 시각 안내만)', async () => {
    renderCta(IN_CREW_WINDOW, PARTY)
    await userEvent
      .setup({ advanceTimers: vi.advanceTimersByTime })
      .click(await screen.findByRole('button', { name: '신청하기' }))
    expect(screen.getByText('11/19 (목) 10:00부터')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /개인으로 신청/ })).toBeDisabled()
    expect(screen.getByRole('button', { name: /파티로 신청/ })).toBeEnabled()
  })

  it('크루 예매 기간에 파티가 없는 회원은 시트에서 크루 홈으로 가는 길을 본다', async () => {
    renderCta(IN_CREW_WINDOW, NO_PARTY)
    await userEvent
      .setup({ advanceTimers: vi.advanceTimersByTime })
      .click(await screen.findByRole('button', { name: '신청하기' }))
    expect(screen.getByRole('button', { name: /개인으로 신청/ })).toBeDisabled()
    expect(screen.getByRole('button', { name: /크루 홈에서 파티 만들기/ })).toBeEnabled()
  })

  it('크루 예매 기간에 로그인 전이면 개인 오픈 시각을 알리는 꺼진 버튼만 보인다', async () => {
    renderCta(IN_CREW_WINDOW, undefined, false)
    expect(await screen.findByRole('button', { name: '11/19 (목) 10:00 접수 시작' })).toBeDisabled()
    expect(screen.queryByRole('link', { name: '신청하기' })).not.toBeInTheDocument()
  })

  it('크루 예매가 끝난 뒤 개인 예매 전에는 누구에게나 꺼진 접수 시작 버튼이다', async () => {
    renderCta(AFTER_CREW_WINDOW, PARTY)
    expect(await screen.findByRole('button', { name: '11/19 (목) 10:00 접수 시작' })).toBeDisabled()
    expect(screen.queryByRole('button', { name: '신청하기' })).not.toBeInTheDocument()
  })
})
