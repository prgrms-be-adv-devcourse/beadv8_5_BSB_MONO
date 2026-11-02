import { clientEnv } from '@/shared/config'

/** 장바구니 · 결제 이벤트에 싣는 상품 한 줄. 대회 하나의 종목 하나다. */
export type TrackItem = {
  raceId: string
  courseId: string
  price: number
  name?: string
}

/** 주문(결제 · 결제 완료 화면)의 상품 줄을 이벤트 상품으로 바꾼다. 주문 하나는 대회 하나다 */
export function orderTrackItems(order: {
  raceId?: number
  items?: { raceCourseId?: number; amount?: number; itemName?: string }[]
}): TrackItem[] {
  return (order.items ?? []).map((item) => ({
    raceId: String(order.raceId ?? ''),
    courseId: String(item.raceCourseId ?? ''),
    price: item.amount ?? 0,
    name: item.itemName,
  }))
}

// 이벤트 이름과 값 형식은 여기서만 정한다 (기획: docs/study/flow/tracking-plan-ga4.md 2장).
// 개인정보(이메일 · 이름 · 전화번호 · 회원 번호 · 판매자 번호)와 자유 입력 글은 넣지 않는다.
export type AnalyticsEvents = {
  view_item_list: { hasKeyword: boolean; resultCount: number }
  view_item: { raceId: string; raceName: string; saleStatus: string }
  queue_enter: { raceId: string }
  queue_pass: { raceId: string; waitSeconds: number }
  queue_leave: { raceId: string; waitSeconds: number }
  apply_start: { raceId: string; courseId: string }
  add_to_cart: { item: TrackItem }
  begin_checkout: { items: TrackItem[]; value: number }
  deposit_short: { shortage: number }
  deposit_charge_start: { amount: number; duringCheckout: boolean }
  deposit_charge_done: { amount: number; duringCheckout: boolean }
  purchase: { orderId: string; items: TrackItem[]; value: number }
  seller_apply_start: Record<string, never>
  seller_apply_submit: { isBusiness: boolean }
  seller_race_form_start: { entry: SellerRaceFormEntry }
  seller_race_form_error: { sections: string[] }
  seller_race_register: {
    raceId: string
    courseCount: number
    /** 내 대회 목록을 아직 못 받았으면 뺀다 */
    isFirstRace?: boolean
    /** 판매자 승인일을 모르거나 날짜를 읽지 못하면(NaN) 뺀다 */
    daysSinceApproval?: number
  }
}

export type AnalyticsEventName = keyof AnalyticsEvents

/** 대회 등록 화면에 들어온 곳. 링크의 ?from= 값이다 */
export type SellerRaceFormEntry = 'home' | 'races' | 'sales' | 'direct'

type DataLayerEntry = Record<string, unknown>

declare global {
  interface Window {
    dataLayer?: DataLayerEntry[]
  }
}

/**
 * GTM을 넣을지. GTM ID가 없거나 가짜 응답 모드면 넣지 않는다.
 * 로컬 · 테스트 · Vercel 시안 데이터가 GA4에 섞이지 않게 한다.
 */
export function trackingGtmId(): string | undefined {
  if (clientEnv.NEXT_PUBLIC_API_MOCKING === 'on') return undefined
  return clientEnv.NEXT_PUBLIC_GTM_ID
}

/** 화면 주소가 /seller로 시작하면(판매자 센터 · 판매자 신청은 /seller-apply) 판매자, 그 밖은 구매자 */
export function siteArea(pathname: string): 'seller' | 'shop' {
  return pathname.startsWith('/seller') ? 'seller' : 'shop'
}

// 어드민은 크루런 직원이라 숫자에 섞으면 안 된다. GTM 실행 조건에도 같은 조건을 넣지만,
// GTM 설정이 바뀌어도 어드민 기록이 나가지 않도록 여기서도 막는다.
function isAdminPath(pathname: string) {
  return pathname === '/admin' || pathname.startsWith('/admin/')
}

const KRW = 'KRW'

function ecommerceItems(items: TrackItem[]) {
  return items.map((item) => ({
    item_id: item.raceId,
    item_variant: item.courseId,
    ...(item.name ? { item_name: item.name } : {}),
    price: item.price,
    quantity: 1,
  }))
}

const queueWait = ({ raceId, waitSeconds }: AnalyticsEvents['queue_pass']) => ({
  race_id: raceId,
  wait_seconds: waitSeconds,
})

const charge = ({ amount, duringCheckout }: AnalyticsEvents['deposit_charge_start']) => ({
  amount,
  during_checkout: duringCheckout,
})

// GA4 추천 이벤트(view_item · add_to_cart · begin_checkout · purchase)는 ecommerce 값을 함께 보내야
// GA4 구매 보고서가 따로 설정 없이 채워진다.
const PAYLOADS: { [K in AnalyticsEventName]: (params: AnalyticsEvents[K]) => DataLayerEntry } = {
  view_item_list: ({ hasKeyword, resultCount }) => ({
    has_keyword: hasKeyword,
    result_count: resultCount,
  }),
  view_item: ({ raceId, raceName, saleStatus }) => ({
    race_id: raceId,
    race_name: raceName,
    sale_status: saleStatus,
    ecommerce: { items: [{ item_id: raceId, item_name: raceName }] },
  }),
  queue_enter: ({ raceId }) => ({ race_id: raceId }),
  queue_pass: queueWait,
  queue_leave: queueWait,
  apply_start: ({ raceId, courseId }) => ({ race_id: raceId, course_id: courseId }),
  add_to_cart: ({ item }) => ({
    race_id: item.raceId,
    course_id: item.courseId,
    value: item.price,
    ecommerce: { currency: KRW, value: item.price, items: ecommerceItems([item]) },
  }),
  begin_checkout: ({ items, value }) => ({
    item_count: items.length,
    value,
    ecommerce: { currency: KRW, value, items: ecommerceItems(items) },
  }),
  deposit_short: ({ shortage }) => ({ shortage }),
  deposit_charge_start: charge,
  deposit_charge_done: charge,
  purchase: ({ orderId, items, value }) => ({
    order_id: orderId,
    item_count: items.length,
    value,
    ecommerce: { transaction_id: orderId, currency: KRW, value, items: ecommerceItems(items) },
  }),
  seller_apply_start: () => ({}),
  seller_apply_submit: ({ isBusiness }) => ({ is_business: isBusiness }),
  seller_race_form_start: ({ entry }) => ({ entry }),
  seller_race_form_error: ({ sections }) => ({
    error_sections: sections.join(','),
    error_count: sections.length,
  }),
  seller_race_register: ({ raceId, courseCount, isFirstRace, daysSinceApproval }) => ({
    race_id: raceId,
    course_count: courseCount,
    ...(isFirstRace === undefined ? {} : { is_first_race: isFirstRace }),
    ...(Number.isFinite(daysSinceApproval) ? { days_since_approval: daysSinceApproval } : {}),
  }),
}

/**
 * GTM으로 이벤트를 보낸다(window.dataLayer). 화면 주소는 조회 값(? 뒤)을 빼고 붙인다.
 * 충전 결과 화면의 토스 결제 값, 로그인 화면의 돌아갈 주소가 이 함수가 보내는 이벤트에 남지 않게 한다.
 * GA4가 자동으로 보내는 페이지 조회(page_view)의 주소는 여기서 막을 수 없어 GTM 설정에서 조회 값을 뺀다.
 * GTM이 꺼져 있거나 어드민 화면이면 아무것도 보내지 않는다.
 * 추적이 실패해도 결제 · 충전 · 등록 같은 화면 동작이 멈추지 않도록 예외를 밖으로 던지지 않는다.
 */
export function trackEvent<K extends AnalyticsEventName>(name: K, params: AnalyticsEvents[K]) {
  try {
    if (typeof window === 'undefined' || !trackingGtmId()) return
    const { origin, pathname } = window.location
    if (isAdminPath(pathname)) return
    const payload = PAYLOADS[name](params)
    const dataLayer = (window.dataLayer ??= [])
    // 앞 이벤트의 ecommerce 값이 다음 이벤트에 섞이지 않게 먼저 비운다 (GTM 권장 방식)
    if ('ecommerce' in payload) dataLayer.push({ ecommerce: null })
    dataLayer.push({
      event: name,
      site_area: siteArea(pathname),
      page_location: `${origin}${pathname}`,
      ...payload,
    })
  } catch (error) {
    if (process.env.NODE_ENV === 'development') console.warn(`[analytics] ${name} 전송 실패`, error)
  }
}
