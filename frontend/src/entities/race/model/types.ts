// 화면이 쓰는 대회 모양. 상품 API 응답(productModel)은 model/mappers.ts에서 이 모양으로 바꾼다.

export type RaceBadge = { label: string; tone: 'brand' | 'warning' | 'neutral' }

/** 지금 신청할 수 있는지 (개인 예매 오픈 전 · 접수 중 · 마감 뒤) */
export type ApplicationPhase = 'upcoming' | 'open' | 'closed'

/** 홈 카드 (곧 마감 · 지금 접수 중) */
export type RaceSummary = {
  id: string
  title: string
  /** 없으면 대회 정보로 만든 색 포스터를 그린다 (목록 API는 이미지를 주지 않는다) */
  image?: string
  /** 포스터에 크게 쓰는 종목 (예: 10K · 5K) */
  posterLabel: string
  /** 이미지 위 상태 칩 (예: D-2 · 86자리 남음) */
  status: string
  /** 대회일 · 장소 */
  meta: string
  /** 예: 35,000원부터 */
  price: string
}

/** 대회 찾기 목록의 한 줄 */
export type RaceListItem = {
  id: string
  title: string
  image?: string
  posterLabel: string
  badge: RaceBadge
  meta: string
  price: string
  remaining: string
}

export type RaceOption = {
  id: string
  name: string
  /** 1인 참가권 구성 */
  includes: string
  price: number
  /** 종목 대표 이미지 */
  image?: string
  soldOut?: boolean
  /** 기념품 키트가 있으면 참가 신청 때 키트 배송 주소를 받는다 */
  kitIncluded?: boolean
  /** 고를 수 있는 기념 티셔츠 사이즈 (작은 것부터). 비어 있으면 티셔츠가 없는 종목 */
  shirtSizes?: string[]
}

export type RaceDetail = {
  id: string
  title: string
  /** 대표 이미지 → 소개 이미지 순. 비어 있으면 색 포스터를 그린다 */
  images: string[]
  heroTitle: string
  heroTags: string
  status: RaceBadge
  /** 신청하기 버튼은 'open'일 때만 켠다 */
  phase: ApplicationPhase
  startsAt: string
  place: string
  /** 판매자가 쓴 대회 소개 */
  description?: string
  /** 접수 마감까지 남은 날 (D-12) */
  dDay: string
  remaining: number
  capacity: number
  applied: number
  options: RaceOption[]
  schedule: { label: string; value: string }[]
  refund: { label: string; value: string }[]
  /** 판매자가 대회 상세에 올리는 자주 묻는 질문 */
  faq: { question: string; answer: string }[]
  seller: {
    /** 개인정보 제3자 제공 동의의 '받는 곳' */
    company: string
    name: string
    businessNo: string
    mailOrderNo: string
  }
}
