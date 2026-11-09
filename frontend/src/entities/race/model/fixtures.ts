// 시안(피그마 세미프로젝트 페이지)의 예시 값을 그대로 옮긴 화면용 임시 데이터.
// 홈·대회 찾기·대회 상세·참가 신청은 상품 API(PRO-148)를 쓴다. 남은 것은 API가 없는 배너와
// 장바구니·결제 완료 시안(PRO-150·151에서 지운다)이 쓰는 기본 대회뿐이다.

import { MAIL_ORDER_NOTICE, refundRows } from './copy'
import type { RaceDetail } from './types'

export type RaceBanner = {
  id: string
  image: string
  /** 글자가 박힌 배너 이미지는 글을 비운다 */
  chip?: string
  title?: string
  description?: string
}

export const RACE_BANNERS: RaceBanner[] = [
  {
    id: 'cookierun',
    image: '/mock/races/cookierun.webp',
    chip: '이번 주 접수 마감',
    title: '2026 용감한 쿠키RUN in 서울',
    description: '10/31까지 · 5K · 10K · 서울숲 일대',
  },
  {
    id: 'youth10k',
    image: '/mock/races/youth10k.webp',
    title: '긍정 하프 마라톤 YOUTH 10K',
    description: '10/12 (월) 18:00 접수 마감',
  },
  { id: 'jtbc', image: '/mock/races/jtbc.webp' },
]

// 판매자가 올리는 자주 묻는 질문 예시. 정책(1인 1건, 양도 불가, 예치금 환불)과 맞춘다.
const FAQ_ROWS = [
  {
    question: '한 사람이 여러 종목에 신청할 수 있나요?',
    answer:
      '한 사람은 한 대회에 1건만 신청할 수 있어요. 다른 종목으로 바꾸려면 신청을 취소하고 다시 신청해 주세요.',
  },
  {
    question: '참가권을 친구에게 넘길 수 있나요?',
    answer: '참가권은 다른 사람에게 양도할 수 없어요. 신청한 본인만 대회에 참가할 수 있어요.',
  },
  {
    question: '환불하면 돈은 어디로 들어오나요?',
    answer:
      '환불 금액은 크루런 예치금으로 돌려드려요. 예치금은 다른 대회나 키트를 살 때 쓸 수 있어요.',
  },
  {
    question: '기념품 키트는 언제 받나요?',
    answer:
      '환불 기한이 지난 뒤 신청할 때 적은 주소로 배송해요. 배송이 시작되면 알림으로 알려 드려요.',
  },
  {
    question: '기념품 사이즈는 바꿀 수 있나요?',
    answer:
      '환불 기한 전까지는 내 참가 대회에서 사이즈를 바꿀 수 있어요. 사이즈표는 상세 정보 이미지에서 확인해 주세요.',
  },
]

/** 장바구니 · 참가 확정처럼 대회 id를 받지 않는 시안 화면이 쓰는 기본 대회 */
export const RACE_DETAIL: RaceDetail = {
  id: 'cookierun',
  title: '2026 용감한 쿠키RUN in 서울',
  images: [
    '/mock/races/cookierun.webp',
    '/mock/races/cookierun.webp',
    '/mock/races/cookierun.webp',
    '/mock/races/cookierun.webp',
  ],
  heroTitle: '「10K · 5K 접수 중」',
  heroTags: '#함께뛰자  #5K  #10K',
  status: { label: '접수중', tone: 'brand' },
  phase: 'open',
  startsAt: '11/1 (일) 08:00 출발',
  place: '서울숲 일대 · 5K / 10K',
  dDay: 'D-12',
  remaining: 590,
  capacity: 3000,
  applied: 2410,
  options: [
    { id: '10k', name: '10K', includes: '1인 · 기념 티셔츠 · 완주 메달', price: 45000 },
    { id: '5k', name: '5K', includes: '1인 · 기념 티셔츠', price: 35000 },
  ],
  schedule: [
    { label: '접수 기간', value: '10/14 (수) 10:00 ~ 10/31 (토) 18:00' },
    { label: '정보 수정 기한', value: '10/31 (토) 18:00' },
    { label: '전액 환불 기한', value: '10/31 (토) 23:59' },
  ],
  refund: refundRows('10/31 (토) 23:59'),
  faq: FAQ_ROWS,
  seller: {
    company: '(주)데브시스터즈러닝',
    name: '판매자 (주)데브시스터즈러닝 · 대표 김주최',
    businessNo: '사업자 123-45-67890 · 통신판매업 2026-서울성동-0000',
    mailOrderNo: MAIL_ORDER_NOTICE,
  },
}
