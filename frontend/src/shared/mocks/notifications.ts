import { http, HttpResponse } from 'msw'

import { freshMemberOf } from './mock-member'

/**
 * 알림 목. 알림 API는 백엔드에 아직 없어서 명세(openapi)도 생성 코드도 없다. 가짜 응답 모드에서만 이 응답이 있다.
 * 시연용 기존 회원(runner1)은 시안(시나리오 37)의 알림 4개를, 방금 가입한 회원은 알림이 없다.
 * category: race(대회 소식) · payment(결제 · 예치금) · crew(크루 소식). sentAt은 서울 시각(+09:00).
 * link.href는 앱 안 주소만 쓴다. 화면은 앱 밖 주소를 버튼으로 그리지 않는다.
 */
export const MOCK_NOTIFICATIONS = [
  {
    id: 'n1',
    category: 'race',
    body: '대회가 취소됐어요. 한강 나이트런 10K 참가비 45,000원을 예치금으로 돌려드렸어요.',
    sentAt: '2026-10-30T18:00:00+09:00',
    unread: true,
  },
  {
    id: 'n2',
    category: 'race',
    body: '대회 일정이 바뀌었어요. 춘천 나이트런 12/6 → 12/13',
    sentAt: '2026-11-02T10:00:00+09:00',
    link: { label: '대회 보러 가기', href: '/races/1' },
    unread: true,
  },
  {
    id: 'n3',
    category: 'payment',
    body: '환급 5,000원을 계좌로 보냈어요.',
    sentAt: '2026-11-01T14:00:00+09:00',
    unread: true,
  },
  {
    id: 'n4',
    category: 'crew',
    body: '러너크루에 새 크루원이 들어왔어요.',
    sentAt: '2026-10-30T09:00:00+09:00',
    unread: false,
  },
]

export const notificationHandlers = [
  http.get('*/api/v1/notification/notifications', ({ cookies }) =>
    HttpResponse.json({
      status: 200,
      message: '알림을 조회했습니다.',
      data: freshMemberOf(cookies) ? [] : MOCK_NOTIFICATIONS,
    }),
  ),
]
