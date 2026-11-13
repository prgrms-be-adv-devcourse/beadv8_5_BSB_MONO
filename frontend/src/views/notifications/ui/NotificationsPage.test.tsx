import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { clearAccessToken, setAccessToken } from '@/shared/api'
import { server } from '@/shared/testing'
import { ToastProvider } from '@/shared/ui'

import { NotificationsPage } from './NotificationsPage'

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), replace: vi.fn(), refresh: vi.fn() }),
  usePathname: () => '/notifications',
}))

const URL = '*/api/v1/notification/notifications'

function notice(id: string, kind: string, body: string, sentAt: string, unread: boolean) {
  return {
    id,
    kind,
    icon: 'Bell',
    title: '제목',
    body,
    at: '방금',
    group: 'today',
    href: null,
    sentAt,
    unread,
  }
}

afterEach(() => clearAccessToken())

const SELLER_URL = '*/api/v1/member/sellers/me'
const READ_ALL_URL = '*/api/v1/notification/notifications/read-all'
const seller = (status: 'APPROVED' | 'PENDING' | null) =>
  http.get(SELLER_URL, () =>
    status
      ? HttpResponse.json({ status: 200, message: '조회', data: { status } })
      : HttpResponse.json(
          { status: 404, message: '없음', code: 'SELLER_NOT_FOUND', data: null },
          { status: 404 },
        ),
  )
const list = (data: unknown[]) =>
  http.get(URL, () => HttpResponse.json({ status: 200, message: '알림을 조회했습니다.', data }))

function renderPage() {
  setAccessToken('test-token')
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <NotificationsPage />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

describe('NotificationsPage', () => {
  it('문의 답변은 문의 묶음으로 가장 최근인 것이 위에 오고, 탭은 따로 없다', async () => {
    server.use(
      list([
        notice('a1', 'RACE_CANCELED', '대회 취소', '2026-11-02T10:00:00+09:00', true),
        notice('a2', 'INQUIRY_ANSWERED', '답변이 달렸어요', '2026-11-10T10:00:00+09:00', true),
      ]),
    )
    renderPage()

    const inquiry = await screen.findByRole('link', { name: /문의/ })
    expect(inquiry).toHaveAttribute('href', '/notifications/inquiry')
    const links = screen.getAllByRole('link')
    expect(links.indexOf(inquiry)).toBeLessThan(
      links.indexOf(screen.getByRole('link', { name: /대회 소식/ })),
    )
    expect(screen.queryByRole('tab', { name: '문의' })).not.toBeInTheDocument()
  })

  it('알림을 종류별 묶음으로 보여 주고, 묶음마다 마지막 날짜와 안 읽은 수를 단다', async () => {
    renderPage()

    const race = await screen.findByRole('link', { name: /대회 소식/ })
    expect(race).toHaveAttribute('href', '/notifications/race')
    expect(within(race).getByText('11.2')).toBeInTheDocument()
    expect(race).toHaveAccessibleName(/안 읽은 알림 2개/)

    const payment = screen.getByRole('link', { name: /결제 · 예치금/ })
    expect(payment).toHaveAttribute('href', '/notifications/payment')
    expect(within(payment).getByText('환급 5,000원을 계좌로 보냈어요.')).toBeInTheDocument()
    expect(within(payment).getByText('11.1')).toBeInTheDocument()
    expect(payment).toHaveAccessibleName(/안 읽은 알림 1개/)

    const crew = screen.getByRole('link', { name: /크루 소식/ })
    expect(within(crew).getByText('10.30')).toBeInTheDocument()
    expect(crew).not.toHaveAccessibleName(/안 읽은 알림/)

    // 전체 탭에는 안 읽은 알림을 모두 더한 수가 붙는다
    expect(screen.getByRole('tab', { name: /전체/ })).toHaveTextContent('3')
  })

  it('탭을 고르면 그 종류의 묶음만 보인다', async () => {
    renderPage()
    await screen.findByRole('link', { name: /대회 소식/ })

    await userEvent.click(screen.getByRole('tab', { name: '결제' }))

    expect(screen.getByRole('link', { name: /결제 · 예치금/ })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /대회 소식/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /크루 소식/ })).not.toBeInTheDocument()
  })

  it('고른 탭에 알림이 없으면 빈 화면을 보여 준다', async () => {
    server.use(
      http.get(URL, () =>
        HttpResponse.json({
          status: 200,
          message: '알림을 조회했습니다.',
          data: [notice('a', 'RACE_CANCELED', '대회 알림', '2026-11-02T10:00:00+09:00', false)],
        }),
      ),
    )
    renderPage()
    await screen.findByRole('link', { name: /대회 소식/ })

    await userEvent.click(screen.getByRole('tab', { name: '크루' }))

    expect(screen.getByText('아직 알림이 없어요')).toBeInTheDocument()
  })

  it('날짜를 읽을 수 없는 알림은 빼고 보여 준다', async () => {
    server.use(
      http.get(URL, () =>
        HttpResponse.json({
          status: 200,
          message: '알림을 조회했습니다.',
          data: [
            notice('bad', 'CREW_JOINED', '날짜 없음', '', true),
            notice('ok', 'RACE_CANCELED', '정상 알림', '2026-11-02T10:00:00+09:00', false),
          ],
        }),
      ),
    )
    renderPage()

    expect(await screen.findByRole('link', { name: /대회 소식/ })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /크루 소식/ })).not.toBeInTheDocument()
  })

  it('알림이 없으면(방금 가입한 회원) 빈 화면만 보여 주고 탭과 버튼이 없다', async () => {
    server.use(
      http.get(URL, () =>
        HttpResponse.json({ status: 200, message: '알림을 조회했습니다.', data: [] }),
      ),
    )
    renderPage()

    expect(await screen.findByText('아직 알림이 없어요')).toBeInTheDocument()
    expect(
      screen.getByText('대회 일정이 바뀌거나 환불이 끝나면 여기로 알려 드려요.'),
    ).toBeInTheDocument()
    expect(screen.queryByRole('tab')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /다시 시도/ })).not.toBeInTheDocument()
  })

  it('판매자 알림이나 모르는 종류는 목록에서 뺀다', async () => {
    server.use(
      http.get(URL, () =>
        HttpResponse.json({
          status: 200,
          message: '알림을 조회했습니다.',
          data: [
            notice(
              's',
              'KIT_SHIPMENT_REQUIRED',
              '키트 보내 주세요',
              '2026-11-02T10:00:00+09:00',
              true,
            ),
            notice('u', 'SOMETHING_NEW', '새 종류', '2026-11-02T11:00:00+09:00', true),
            notice('c', 'CREW_JOINED', '크루 알림', '2026-11-02T12:00:00+09:00', false),
          ],
        }),
      ),
    )
    renderPage()

    expect(await screen.findByRole('link', { name: /크루 소식/ })).toBeInTheDocument()
    expect(screen.queryByText('키트 보내 주세요')).not.toBeInTheDocument()
    expect(screen.queryByText('새 종류')).not.toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /전체/ })).not.toHaveTextContent(/\d/)
  })

  it('그 밖의 오류면 불러오지 못함 화면을 보여 주고, 다시 시도하기로 다시 불러온다', async () => {
    let calls = 0
    server.use(
      http.get(URL, () => {
        calls += 1
        return calls === 1
          ? HttpResponse.json({ status: 500, message: '오류', data: null }, { status: 500 })
          : HttpResponse.json({ status: 200, message: '알림을 조회했습니다.', data: [] })
      }),
    )
    renderPage()

    expect(await screen.findByText('정보를 불러오지 못했어요')).toBeInTheDocument()
    // 머리말은 그대로 남는다
    expect(screen.getByRole('heading', { name: '알림' })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '다시 시도하기' }))

    expect(await screen.findByText('아직 알림이 없어요')).toBeInTheDocument()
    expect(calls).toBe(2)
  })

  it('판매자에게는 판매 탭과 판매 소식 묶음을 맨 위에 보여 준다', async () => {
    server.use(
      seller('APPROVED'),
      list([
        notice(
          'k',
          'KIT_SHIPMENT_REQUIRED',
          '키트를 보낼 참가자가 있어요',
          '2026-11-09T10:00:00+09:00',
          true,
        ),
        notice('c', 'CREW_JOINED', '크루 알림', '2026-11-02T12:00:00+09:00', false),
      ]),
    )
    renderPage()

    const sales = await screen.findByRole('link', { name: /판매 소식/ })
    expect(sales).toHaveAttribute('href', '/notifications/seller')
    expect(screen.getAllByRole('link')[0]).toBe(sales)
    const tabs = screen
      .getAllByRole('tab')
      .map((tab) => tab.textContent?.replace(/안 읽은 알림 \d+개|\d/g, ''))
    expect(tabs).toEqual(['전체', '대회', '결제', '크루', '판매'])
  })

  it.each([null, 'PENDING'] as const)(
    '판매자가 아니면(%s) 판매 탭과 판매 소식 묶음이 없다',
    async (status) => {
      server.use(
        seller(status),
        list([
          notice(
            'k',
            'KIT_SHIPMENT_REQUIRED',
            '키트를 보낼 참가자가 있어요',
            '2026-11-09T10:00:00+09:00',
            true,
          ),
          notice('c', 'CREW_JOINED', '크루 알림', '2026-11-02T12:00:00+09:00', false),
        ]),
      )
      renderPage()

      await screen.findByRole('link', { name: /크루 소식/ })
      expect(screen.queryByRole('link', { name: /판매 소식/ })).not.toBeInTheDocument()
      expect(screen.queryByRole('tab', { name: '판매' })).not.toBeInTheDocument()
    },
  )

  it('모두 읽음을 누르면 모두 읽음 API를 부르고 알림을 다시 불러온다', async () => {
    let gets = 0
    let readAll = 0
    server.use(
      http.get(URL, () => {
        gets += 1
        return HttpResponse.json({
          status: 200,
          message: '알림을 조회했습니다.',
          data: [
            notice('r', 'RACE_CANCELED', '대회 알림', '2026-11-02T10:00:00+09:00', gets === 1),
          ],
        })
      }),
      http.post(READ_ALL_URL, () => {
        readAll += 1
        return HttpResponse.json({
          status: 200,
          message: '알림을 모두 읽음으로 바꿨습니다.',
          data: null,
        })
      }),
    )
    renderPage()
    await screen.findByRole('link', { name: /대회 소식/ })

    await userEvent.click(screen.getByRole('button', { name: '모두 읽음' }))

    await vi.waitFor(() => expect(gets).toBe(2))
    expect(readAll).toBe(1)
    expect(await screen.findByRole('button', { name: '모두 읽음' })).toBeDisabled()
    expect(screen.getByRole('link', { name: /대회 소식/ })).not.toHaveAccessibleName(/안 읽은 알림/)
  })

  it('안 읽은 알림이 없으면 모두 읽음을 누를 수 없다', async () => {
    server.use(
      list([notice('r', 'RACE_CANCELED', '대회 알림', '2026-11-02T10:00:00+09:00', false)]),
    )
    renderPage()
    await screen.findByRole('link', { name: /대회 소식/ })

    expect(screen.getByRole('button', { name: '모두 읽음' })).toBeDisabled()
  })

  it('목록에 안 보이는 알림만 안 읽었어도 모두 읽음을 누를 수 있다', async () => {
    server.use(
      seller(null),
      list([
        notice('k', 'KIT_SHIPMENT_REQUIRED', '키트 알림', '2026-11-09T10:00:00+09:00', true),
        notice('r', 'RACE_CANCELED', '대회 알림', '2026-11-02T10:00:00+09:00', false),
      ]),
    )
    renderPage()
    await screen.findByRole('link', { name: /대회 소식/ })

    expect(screen.getByRole('button', { name: '모두 읽음' })).toBeEnabled()
  })

  it('모두 읽음이 실패하면 알려 주고 다시 누를 수 있다', async () => {
    server.use(
      list([notice('r', 'RACE_CANCELED', '대회 알림', '2026-11-02T10:00:00+09:00', true)]),
      http.post(READ_ALL_URL, () =>
        HttpResponse.json({ status: 500, message: '오류', data: null }, { status: 500 }),
      ),
    )
    renderPage()
    await screen.findByRole('link', { name: /대회 소식/ })

    await userEvent.click(screen.getByRole('button', { name: '모두 읽음' }))

    expect((await screen.findAllByText('모두 읽음으로 바꾸지 못했어요')).length).toBeGreaterThan(0)
    expect(screen.getByRole('button', { name: '모두 읽음' })).toBeEnabled()
  })
})
