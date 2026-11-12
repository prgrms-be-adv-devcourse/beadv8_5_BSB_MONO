import type { BadgeTone } from '@/shared/ui'

import type { InquiryAssignee, InquiryStatus, InquiryType } from '../model/types'

/** 종류 이름 (피그마 M37 · M39). 목록 줄에는 「 · 」 앞뒤를 띄워 쓴다 */
export const TYPE_LABEL: Record<InquiryType, string> = {
  APPLY_PAYMENT: '신청 · 결제',
  RACE_VENUE: '대회 · 현장',
  KIT_DELIVERY: '기념품 · 배송',
  ETC: '기타',
  SELLER_SUPPORT: '판매자 문의',
}

/** 문의 종류를 고르는 카드 (M37). 답하는 쪽은 종류로 정해진다 */
export const TYPE_CHOICES: { type: InquiryType; hint: string }[] = [
  { type: 'APPLY_PAYMENT', hint: '참가 신청 · 결제 · 취소 · 환불' },
  { type: 'RACE_VENUE', hint: '코스 · 규정 · 현장 운영' },
  { type: 'KIT_DELIVERY', hint: '키트 사양 · 배송 일정' },
  { type: 'ETC', hint: '그 밖의 문의' },
]

/** 종류로 정해지는 담당 (백엔드 InquiryType.assignee와 같다) */
export const ASSIGNEE_OF: Record<InquiryType, InquiryAssignee> = {
  APPLY_PAYMENT: 'ADMIN',
  RACE_VENUE: 'SELLER',
  KIT_DELIVERY: 'SELLER',
  ETC: 'ADMIN',
  SELLER_SUPPORT: 'ADMIN',
}

export const ASSIGNEE_ANSWERER: Record<InquiryAssignee, string> = {
  SELLER: '판매자',
  ADMIN: '크루런',
}

export const STATUS: Record<InquiryStatus, { label: string; tone: BadgeTone }> = {
  WAITING: { label: '답변 대기', tone: 'neutral' },
  ANSWERED: { label: '답변 완료', tone: 'success' },
}

const pad = (n: number) => String(n).padStart(2, '0')
// 서버가 주는 LocalDateTime(타임존 없음, 서울 시각)을 그대로 쪼갠다
const parts = (iso: string) => {
  const [, m, d, h, min] = /^\d{4}-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(iso) ?? []
  return { m: Number(m), d: Number(d), h: Number(h), min: Number(min) }
}

/** 11.10 (회원 목록) */
export function dotDate(iso: string) {
  const { m, d } = parts(iso)
  return `${m}.${d}`
}

/** 11/10 (판매자 · 관리자 목록) */
export function slashDate(iso: string) {
  const { m, d } = parts(iso)
  return `${m}/${d}`
}

/** 11.10 오전 9:12 (회원 상세) */
export function dotDateTime(iso: string) {
  const { m, d, h, min } = parts(iso)
  return `${m}.${d} ${h < 12 ? '오전' : '오후'} ${h % 12 || 12}:${pad(min)}`
}

/** 11/10 09:12 (판매자 · 관리자 상세) */
export function slashDateTime(iso: string) {
  const { m, d, h, min } = parts(iso)
  return `${m}/${d} ${pad(h)}:${pad(min)}`
}
