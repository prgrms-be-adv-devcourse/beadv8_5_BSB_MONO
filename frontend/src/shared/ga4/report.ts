// GA4 Data API 요청을 만들고 응답을 숫자로 바꾸는 순수 함수. 실제 호출은 client.ts가 한다.
// 사람 수(totalUsers, activeUsers)만 받는다. 회원 번호·기기 ID 같은 개인을 알아볼 수 있는 측정기준은 요청하지 않는다.

export type Ga4DateRange = { name: string; startDate: string; endDate: string }

export type UsersByEventInput = {
  events: readonly string[]
  /** 맞춤 측정기준 site_area 값 (shop, seller) */
  siteArea: string
  dateRanges: Ga4DateRange[]
}

/** [기간 이름][이벤트 이름] = 그 이벤트를 보낸 사람 수. 기록이 없는 이벤트는 0 */
export type UsersByEvent = Record<string, Record<string, number>>

export type RealtimeOverview = {
  activeUsers: number
  topScreens: { name: string; activeUsers: number }[]
}

/** GA4 보고서를 부르는 곳. 실제 GA4와 가짜 응답 모드가 같은 모양을 쓴다 */
export interface Ga4Reporter {
  usersByEvent(input: UsersByEventInput): Promise<UsersByEvent>
  realtime(input: { topScreens: number }): Promise<RealtimeOverview>
}

/** 이벤트 범위 맞춤 측정기준은 customEvent: 를 붙여 부른다 (GA4 관리 → 맞춤 정의에 등록해야 한다) */
export const SITE_AREA_DIMENSION = 'customEvent:site_area'

export function usersByEventRequest(property: string, input: UsersByEventInput) {
  return {
    property,
    dateRanges: input.dateRanges,
    dimensions: [{ name: 'eventName' }],
    // 이벤트 횟수가 아니라 그 이벤트를 보낸 사람 수
    metrics: [{ name: 'totalUsers' }],
    dimensionFilter: {
      andGroup: {
        expressions: [
          { filter: { fieldName: 'eventName', inListFilter: { values: [...input.events] } } },
          {
            filter: {
              fieldName: SITE_AREA_DIMENSION,
              stringFilter: { matchType: 'EXACT' as const, value: input.siteArea },
            },
          },
        ],
      },
    },
  }
}

type Row = {
  dimensionValues?: { value?: string | null }[] | null
  metricValues?: { value?: string | null }[] | null
}

/**
 * 기간이 둘 이상이면 GA4가 dateRange 측정기준을 마지막에 붙여 기간 이름을 돌려준다.
 * 기간이 하나면 붙이지 않으므로 그 기간 이름으로 센다.
 */
export function parseUsersByEvent(
  rows: Row[] | null | undefined,
  input: UsersByEventInput,
): UsersByEvent {
  const result: UsersByEvent = {}
  for (const range of input.dateRanges) {
    result[range.name] = Object.fromEntries(input.events.map((event) => [event, 0]))
  }
  for (const row of rows ?? []) {
    const event = row.dimensionValues?.[0]?.value ?? ''
    const rangeName = row.dimensionValues?.[1]?.value ?? input.dateRanges[0]?.name ?? ''
    const users = Number(row.metricValues?.[0]?.value ?? 0)
    if (result[rangeName] && event in result[rangeName]) {
      result[rangeName][event] = Number.isFinite(users) ? users : 0
    }
  }
  return result
}

/** 실시간 보고서는 기본으로 최근 30분을 센다 */
export function realtimeTotalRequest(property: string) {
  return { property, metrics: [{ name: 'activeUsers' }] }
}

/** 실시간 보고서에는 화면 주소 측정기준이 없어 화면 제목(unifiedScreenName)으로 센다 */
export function realtimeScreensRequest(property: string, limit: number) {
  return {
    property,
    dimensions: [{ name: 'unifiedScreenName' }],
    metrics: [{ name: 'activeUsers' }],
    orderBys: [{ metric: { metricName: 'activeUsers' }, desc: true }],
    limit,
  }
}

export function parseRealtime(
  totalRows: Row[] | null | undefined,
  screenRows: Row[] | null | undefined,
): RealtimeOverview {
  return {
    activeUsers: Number(totalRows?.[0]?.metricValues?.[0]?.value ?? 0),
    topScreens: (screenRows ?? []).map((row) => ({
      name: row.dimensionValues?.[0]?.value || '(제목 없음)',
      activeUsers: Number(row.metricValues?.[0]?.value ?? 0),
    })),
  }
}
