import { http, HttpResponse } from 'msw'

import { freshMemberOf } from './mock-member'

/**
 * 알림 목. 알림 서비스 NoticeDto 모양에 kind(서버 알림 종류 이름)와 sentAt(서울 시각)을 더한 응답이다.
 * 시연용 기존 회원(runner1)은 시안(시나리오 37)의 알림 4개를, 방금 가입한 회원은 알림이 없다.
 * href는 앱 안 주소만 쓴다. 화면은 앱 밖 주소를 버튼으로 그리지 않는다.
 */
export const MOCK_NOTIFICATIONS = [
  {
    id: 'n1',
    kind: 'RACE_CANCELED',
    icon: 'CircleAlert',
    title: '대회가 취소됐어요',
    body: '대회가 취소됐어요. 한강 나이트런 10K 참가비 45,000원을 예치금으로 돌려드렸어요.',
    at: '10/30',
    group: 'earlier',
    href: null,
    sentAt: '2026-10-30T18:00:00+09:00',
    unread: true,
  },
  {
    id: 'n2',
    kind: 'RACE_SCHEDULE_CHANGED',
    icon: 'Calendar',
    title: '대회 일정이 바뀌었어요',
    body: '대회 일정이 바뀌었어요. 춘천 나이트런 12/6 → 12/13',
    at: '11/2',
    group: 'earlier',
    href: '/races/1',
    sentAt: '2026-11-02T10:00:00+09:00',
    unread: true,
  },
  {
    id: 'n3',
    kind: 'WITHDRAWAL_COMPLETED',
    icon: 'CircleCheck',
    title: '환급이 끝났어요',
    body: '환급 5,000원을 계좌로 보냈어요.',
    at: '11/1',
    group: 'earlier',
    href: null,
    sentAt: '2026-11-01T14:00:00+09:00',
    unread: true,
  },
  {
    id: 'n4',
    kind: 'CREW_JOINED',
    icon: 'Users',
    title: '새 크루원이 들어왔어요',
    body: '러너크루에 새 크루원이 들어왔어요.',
    at: '10/30',
    group: 'earlier',
    href: null,
    sentAt: '2026-10-30T09:00:00+09:00',
    unread: false,
  },
]

// 읽은 알림 ID는 다른 목처럼 서버 메모리 대신 쿠키에 남긴다(새로고침해도 읽음이 유지된다)
const READ_COOKIE = 'mockReadNotices'
const readIdsOf = (cookies: Record<string, string>) =>
  new Set((cookies[READ_COOKIE] ?? '').split('.').filter(Boolean))

const ok = (message: string, readIds: Set<string>) =>
  HttpResponse.json(
    { status: 200, message, data: null },
    { headers: { 'Set-Cookie': `${READ_COOKIE}=${[...readIds].join('.')}; Path=/; SameSite=Lax` } },
  )

export const notificationHandlers = [
  http.get('*/api/v1/notification/notifications', ({ cookies }) => {
    const read = readIdsOf(cookies)
    return HttpResponse.json({
      status: 200,
      message: '알림을 조회했습니다.',
      data: freshMemberOf(cookies)
        ? []
        : MOCK_NOTIFICATIONS.map((n) => ({ ...n, unread: n.unread && !read.has(n.id) })),
    })
  }),
  http.post('*/api/v1/notification/notifications/read-all', () =>
    ok('알림을 모두 읽음으로 바꿨습니다.', new Set(MOCK_NOTIFICATIONS.map((n) => n.id))),
  ),
  http.post('*/api/v1/notification/notifications/:id/read', ({ params, cookies }) =>
    !freshMemberOf(cookies) && MOCK_NOTIFICATIONS.some((n) => n.id === params.id)
      ? ok('알림을 읽음으로 바꿨습니다.', readIdsOf(cookies).add(String(params.id)))
      : HttpResponse.json(
          {
            status: 404,
            message: '알림을 찾을 수 없습니다.',
            code: 'NOTIFICATION_NOT_FOUND',
            data: null,
          },
          { status: 404 },
        ),
  ),
]
