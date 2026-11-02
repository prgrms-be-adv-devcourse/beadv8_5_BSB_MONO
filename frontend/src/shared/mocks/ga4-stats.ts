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

/** 대회 등록 입력 오류 묶음 조합별 사람 수 (GTM이 error_sections로 보내는 모양) */
export const MOCK_FORM_ERROR_ROWS = [
  { value: '종목 · 참가권', users: 6 },
  { value: '기본 정보,이미지', users: 4 },
  { value: '취소 · 환불 정책', users: 3 },
  { value: '이미지', users: 2 },
  { value: '키트 배송,종목 · 참가권', users: 1 },
]

/** 요일마다 오르내리는 예시 숫자 (시안 v9 일별 추이 카드와 같은 모양). 같은 날짜면 늘 같은 값이다 */
const DAILY_PATTERN = {
  visitors: [182, 240, 196, 285, 230, 356, 293],
  purchases: [12, 15, 14, 19, 27, 31, 24],
}

function dailyUsers(date: string, kind: keyof typeof DAILY_PATTERN, scale: number) {
  const day = Math.floor(Date.parse(`${date}T00:00:00Z`) / 86_400_000)
  const pattern = DAILY_PATTERN[kind]
  return Math.round(pattern[(day + 3) % pattern.length] * scale)
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
  async usersByDate({ eventName, dateRanges }) {
    const kind = eventName === 'purchase' ? 'purchases' : 'visitors'
    return Object.fromEntries(
      dateRanges.map((range, i) => {
        const days: Record<string, number> = {}
        for (
          let d = new Date(`${range.startDate}T00:00:00Z`);
          d <= new Date(`${range.endDate}T00:00:00Z`);
          d.setUTCDate(d.getUTCDate() + 1)
        ) {
          const date = d.toISOString().slice(0, 10)
          // 비교 기간은 조금 적게 보여 늘어난 모양을 흉내 낸다
          days[date] = dailyUsers(date, kind, i === 0 ? 1 : 0.85)
        }
        return [range.name, days]
      }),
    )
  },
  async usersByDimension() {
    return MOCK_FORM_ERROR_ROWS
  },
  async realtime({ topScreens }) {
    return { ...MOCK_REALTIME, topScreens: MOCK_REALTIME.topScreens.slice(0, topScreens) }
  },
}
