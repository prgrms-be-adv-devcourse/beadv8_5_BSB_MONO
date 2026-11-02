import type { AnalyticsEventName } from '@/shared/lib'

// 어드민 이용 통계의 단계별 사람 수. 이벤트 이름은 shared/lib/analytics의 GTM 이벤트와 같아야 한다.
// 구매자는 site_area=shop, 판매자는 site_area=seller인 기록만 센다 (GTM이 화면 주소로 정한다).

export type FunnelStep = { event: AnalyticsEventName; label: string }

export type FunnelKind = 'buyer' | 'seller'

export const FUNNELS: Record<FunnelKind, { siteArea: 'shop' | 'seller'; steps: FunnelStep[] }> = {
  buyer: {
    siteArea: 'shop',
    steps: [
      { event: 'view_item_list', label: '대회 목록' },
      { event: 'view_item', label: '대회 상세' },
      { event: 'queue_enter', label: '대기열 진입' },
      { event: 'queue_pass', label: '대기열 통과' },
      { event: 'apply_start', label: '신청 시작' },
      { event: 'add_to_cart', label: '장바구니' },
      { event: 'begin_checkout', label: '결제 시작' },
      { event: 'purchase', label: '결제 완료' },
    ],
  },
  seller: {
    siteArea: 'seller',
    steps: [
      { event: 'seller_apply_start', label: '판매자 신청 시작' },
      { event: 'seller_apply_submit', label: '신청 제출' },
      { event: 'seller_race_form_start', label: '대회 등록 시작' },
      { event: 'seller_race_register', label: '대회 등록 성공' },
    ],
  },
}

export type FunnelStepStat = FunnelStep & {
  /** 고른 기간에 이 이벤트를 보낸 사람 수 */
  users: number
  /** 같은 길이의 바로 앞 기간 사람 수 */
  previousUsers: number
}

export type FunnelStepView = FunnelStepStat & {
  /** 앞 단계 대비 남은 비율(0~1). 첫 단계이거나 앞 단계가 0명이면 없음 */
  rate?: number
  /** 앞 단계에서 가장 많이 빠진 단계 */
  biggestDrop: boolean
}

/** 단계마다 앞 단계 대비 비율을 붙이고, 빠진 사람이 가장 많은 단계를 표시한다. */
export function toFunnelView(steps: FunnelStepStat[]): FunnelStepView[] {
  const drops = steps.map((step, i) => (i === 0 ? 0 : steps[i - 1].users - step.users))
  const maxDrop = Math.max(0, ...drops)
  return steps.map((step, i) => {
    const before = i === 0 ? undefined : steps[i - 1].users
    return {
      ...step,
      rate: before ? step.users / before : undefined,
      biggestDrop: maxDrop > 0 && drops[i] === maxDrop,
    }
  })
}
