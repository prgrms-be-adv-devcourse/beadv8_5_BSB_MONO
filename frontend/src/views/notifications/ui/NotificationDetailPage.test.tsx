import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { clearAccessToken, setAccessToken } from '@/shared/api'
import { server } from '@/shared/testing'

import { NotificationDetailPage } from './NotificationDetailPage'

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), replace: vi.fn(), refresh: vi.fn() }),
  usePathname: () => '/notifications/race',
}))

const URL = '*/api/v1/notification/notifications'
const READ_URL = '*/api/v1/notification/notifications/:id/read'

afterEach(() => clearAccessToken())

function notice(id: string, kind: string, body: string, sentAt: string, extra = {}) {
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
    unread: true,
    ...extra,
  }
}

const seller = (status: 'APPROVED' | 'PENDING') =>
  http.get('*/api/v1/member/sellers/me', () =>
    HttpResponse.json({ status: 200, message: '조회', data: { status } }),
  )

const list = (data: unknown[]) =>
  http.get(URL, () => HttpResponse.json({ status: 200, message: '알림을 조회했습니다.', data }))

function renderPage(category: 'race' | 'payment' | 'crew' | 'seller') {
  setAccessToken('test-token')
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <NotificationDetailPage category={category} />
    </QueryClientProvider>,
  )
}

describe('NotificationDetailPage', () => {
  it('종류 이름을 머리말에 두고, 날짜 칩 아래에 알림과 서울 시각을 오래된 것부터 보여 준다', async () => {
    renderPage('race')

    expect(screen.getByRole('heading', { name: '대회 소식' })).toBeInTheDocument()
    const first = await screen.findByText('2026.10.30 (금)')
    const second = screen.getByText('2026.11.2 (월)')
    expect(first.compareDocumentPosition(second) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(screen.getByText('오후 6:00')).toBeInTheDocument()
    expect(screen.getByText('오전 10:00')).toBeInTheDocument()
  })

  it('알림에 이동 주소가 있을 때만 종류에 맞는 문구의 버튼으로 그 화면에 간다', async () => {
    server.use(
      list([
        notice('a', 'RACE_SCHEDULE_CHANGED', '일정 변경', '2026-11-02T10:00:00+09:00', {
          href: '/races/1',
        }),
        notice('b', 'RACE_CANCELED', '대회 취소', '2026-11-02T11:00:00+09:00'),
      ]),
    )
    renderPage('race')

    expect(await screen.findByText('대회 취소')).toBeInTheDocument()
    const links = screen.getAllByRole('link', { name: '대회 보러 가기' })
    expect(links).toHaveLength(1)
    expect(links[0]).toHaveAttribute('href', '/races/1')
  })

  it('앱 밖 주소나 스크립트 주소는 버튼으로 그리지 않는다', async () => {
    const hrefs = [
      '/\t/evil.example',
      '/\n/evil.example',
      '/api/v1/member/members/me',
      'https://evil.example',
      '//evil.example',
      'javascript:alert(1)',
    ]
    server.use(
      list(
        hrefs.map((href, i) =>
          notice(`x${i}`, 'RACE_CANCELED', `주소 ${i}`, `2026-11-02T1${i}:00:00+09:00`, { href }),
        ),
      ),
    )
    renderPage('race')

    expect(await screen.findByText('주소 5')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '대회 보러 가기' })).not.toBeInTheDocument()
  })

  it('그 종류의 알림이 없으면 빈 화면을 보여 준다', async () => {
    server.use(list([]))
    renderPage('crew')

    expect(await screen.findByText('아직 알림이 없어요')).toBeInTheDocument()
  })

  it('열면 그 종류의 안 읽은 알림만 하나씩 읽음으로 바꾸고, 알림을 다시 불러온다', async () => {
    let gets = 0
    const reads: string[] = []
    server.use(
      http.get(URL, () => {
        gets += 1
        // 다시 불러와도 안 읽음으로 오는 경우(폴링과 겹침): 같은 알림을 또 읽지 않아야 한다
        return HttpResponse.json({
          status: 200,
          message: '알림을 조회했습니다.',
          data: [
            notice('r1', 'RACE_CANCELED', '대회 취소', '2026-11-01T10:00:00+09:00'),
            notice('r2', 'RACE_SCHEDULE_CHANGED', '일정 변경', '2026-11-02T10:00:00+09:00'),
            notice('r3', 'RACE_CANCELED', '읽은 알림', '2026-11-02T11:00:00+09:00', {
              unread: false,
            }),
            notice('p1', 'REFUND_COMPLETED', '환불', '2026-11-02T12:00:00+09:00'),
          ],
        })
      }),
      http.post(READ_URL, ({ params }) => {
        reads.push(String(params.id))
        return HttpResponse.json({
          status: 200,
          message: '알림을 읽음으로 바꿨습니다.',
          data: null,
        })
      }),
    )
    renderPage('race')

    await waitFor(() => expect(gets).toBe(2))
    expect(reads.sort()).toEqual(['r1', 'r2'])
    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(reads).toHaveLength(2)
  })

  it('읽음 호출이 실패해도(남의 알림 404 등) 알림은 그대로 보인다', async () => {
    server.use(
      list([notice('r1', 'RACE_CANCELED', '대회 취소', '2026-11-01T10:00:00+09:00')]),
      http.post(READ_URL, () =>
        HttpResponse.json(
          {
            status: 404,
            message: '알림을 찾을 수 없습니다.',
            code: 'NOTIFICATION_NOT_FOUND',
            data: null,
          },
          { status: 404 },
        ),
      ),
    )
    renderPage('race')

    expect(await screen.findByText('대회 취소')).toBeInTheDocument()
    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(screen.getByText('대회 취소')).toBeInTheDocument()
    expect(screen.queryByText('정보를 불러오지 못했어요')).not.toBeInTheDocument()
  })

  it('판매 소식은 알림마다 제목 · 내용과 종류에 맞는 할 일 버튼을 보여 준다', async () => {
    server.use(
      seller('APPROVED'),
      list([
        notice('s1', 'PAYOUT_HELD', '1차 정산', '2026-11-07T14:00:00+09:00', {
          title: '정산이 보류됐어요',
          href: '/seller/settlements',
        }),
        notice('s2', 'KIT_TRACKING_STOPPED', '참가자 3명', '2026-11-08T09:00:00+09:00', {
          href: '/seller/kits',
        }),
        notice('s3', 'KIT_SHIPMENT_REQUIRED', '12명', '2026-11-09T10:00:00+09:00', {
          href: '/seller/kits',
        }),
        notice('s4', 'INQUIRY_RECEIVED', '키트 문의', '2026-11-09T11:30:00+09:00', {
          href: '/seller/inquiries',
        }),
        notice('s5', 'INQUIRY_RECEIVED', '주소 없는 문의', '2026-11-09T12:00:00+09:00'),
      ]),
    )
    renderPage('seller')

    expect(screen.getByRole('heading', { name: '판매 소식' })).toBeInTheDocument()
    expect(await screen.findByText('정산이 보류됐어요')).toBeInTheDocument()
    expect(screen.getByText('1차 정산')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '보류 사유 보기' })).toHaveAttribute(
      'href',
      '/seller/settlements',
    )
    expect(screen.getByRole('link', { name: '송장 번호 확인하기' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '송장 입력하기' })).toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: '답변하기' })).toHaveLength(1)
  })

  it('판매자가 아니면(심사 중) 판매 소식 주소로 들어와도 빈 화면이고 읽음으로 바꾸지 않는다', async () => {
    const reads: string[] = []
    server.use(
      seller('PENDING'),
      list([notice('s1', 'KIT_SHIPMENT_REQUIRED', '12명', '2026-11-09T10:00:00+09:00')]),
      http.post(READ_URL, ({ params }) => {
        reads.push(String(params.id))
        return HttpResponse.json({ status: 200, message: '읽음', data: null })
      }),
    )
    renderPage('seller')

    expect(await screen.findByText('아직 알림이 없어요')).toBeInTheDocument()
    expect(screen.queryByText('12명')).not.toBeInTheDocument()
    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(reads).toEqual([])
  })
})
