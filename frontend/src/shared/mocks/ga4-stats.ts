import type { Ga4Reporter } from '@/shared/ga4/report'

// 가짜 응답 모드(API_MOCKING=on)의 GA4 이용 통계 예시. GA4는 gRPC라 MSW가 가로챌 수 없어서,
// 서버가 실제 GA4 대신 이 값을 쓴다. 숫자는 단계마다 줄어드는 모양만 흉내 낸 예시다.

/** [이벤트 이름] = [고른 기간 사람 수, 앞 기간 사람 수] */
const USERS: Record<string, [number, number]> = {
  view_item_list: [1240, 1105],
  view_item: [986, 870],
  queue_enter: [612, 540],
  queue_pass: [574, 498],
  apply_start: [521, 455],
  add_to_cart: [388, 342],
  begin_checkout: [301, 276],
  purchase: [214, 188],
  seller_apply_start: [42, 35],
  seller_apply_submit: [27, 24],
  seller_race_form_start: [19, 15],
  seller_race_register: [11, 9],
}

export const MOCK_REALTIME = {
  activeUsers: 23,
  topScreens: [
    { name: '대회 목록 | 크루런', activeUsers: 9 },
    { name: '2026 서울 한강 나이트런 | 크루런', activeUsers: 6 },
    { name: '결제 | 크루런', activeUsers: 4 },
    { name: '판매자 센터 | 크루런', activeUsers: 3 },
    { name: '내 참가권 | 크루런', activeUsers: 1 },
  ],
}

export const mockGa4Reporter: Ga4Reporter = {
  async usersByEvent({ events, dateRanges }) {
    return Object.fromEntries(
      dateRanges.map((range, i) => [
        range.name,
        Object.fromEntries(events.map((event) => [event, USERS[event]?.[i] ?? 0])),
      ]),
    )
  },
  async realtime({ topScreens }) {
    return { ...MOCK_REALTIME, topScreens: MOCK_REALTIME.topScreens.slice(0, topScreens) }
  },
}
