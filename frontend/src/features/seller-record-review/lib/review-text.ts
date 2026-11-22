import type {
  CheckField,
  CourseClass,
  DenyReason,
  JudgementReason,
  SellerRecordCertSubmission,
} from '../model/types'

export const COURSE_CLASS_LABEL: Record<CourseClass, string> = {
  FIVE_K: '5K',
  TEN_K: '10K',
  HALF: '하프',
  FULL: '풀',
  ULTRA: '울트라',
  OTHER: '기타',
}

export const DENY_REASON_LABEL: Record<DenyReason, string> = {
  NAME_MISMATCH: '사진 속 이름이 달라요',
  OTHER_RACE: '다른 대회 기록이에요',
  NOT_NET_TIME: '넷타임 표시가 아니에요',
  BLURRY: '기록증 사진이 흐려요',
  OTHER: '기타',
}

// 목록 한 줄의 「사유 · …」 짧은 말. 반려 사유는 심사 목록에 오지 않지만 타입을 채우려고 둔다
const REASON_SHORT: Record<JudgementReason, string> = {
  BEFORE_RECOGNIZED_PERIOD: '기간 지남',
  NET_TIME_OVER: '넷타임 넘음',
  COURSE_NOT_ALLOWED: '종목 다름',
  COURSE_OTHER: '종목 기타',
  FUTURE_RACE_DATE: '대회일 전',
  TOO_FAST: '기록 너무 빠름',
}

const REASON_SENTENCE: Record<JudgementReason, string> = {
  BEFORE_RECOGNIZED_PERIOD: '기록 인정 기간보다 앞선 대회예요',
  NET_TIME_OVER: '넷타임이 기준보다 느려요',
  COURSE_NOT_ALLOWED: '인정하지 않는 종목이에요',
  COURSE_OTHER: '종목이 5K · 10K · 하프 · 풀 · 울트라가 아니에요',
  FUTURE_RACE_DATE: '대회일이 아직 지나지 않았어요',
  TOO_FAST: '풀 2시간보다 빠른 속도예요',
}

const FIELD_LABEL: Record<CheckField, string> = {
  NET_TIME: '넷타임',
  NET_LABEL: '넷타임 표시',
  RACE_DATE: '대회일',
  COURSE: '종목',
}
const FIELDS = Object.keys(FIELD_LABEL) as CheckField[]

/** 5405 → 1:30:05 */
export function formatNetTime(seconds: number): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${Math.floor(seconds / 3600)}:${pad(Math.floor(seconds / 60) % 60)}:${pad(seconds % 60)}`
}

type Reviewable = Pick<SellerRecordCertSubmission, 'reasons' | 'serverCheck'>

/** 심사가 필요한 이유를 짧게. 판정 사유 다음에 서버 확인 결과(다름 · 읽지 못함)를 붙인다 */
export function reviewReasonShort(s: Reviewable): string {
  const checks = FIELDS.map((f) => s.serverCheck[f])
  return [
    ...s.reasons.map((r) => REASON_SHORT[r]),
    ...(checks.includes('MISMATCH') ? ['읽은 값 다름'] : []),
    ...(checks.includes('UNREADABLE') ? ['읽지 못함'] : []),
  ].join(' · ')
}

/** 상세의 「확인할 점」 문장들 */
export function reviewReasonSentences(s: Reviewable): string[] {
  return [
    ...s.reasons.map((r) => REASON_SENTENCE[r]),
    ...FIELDS.flatMap((f) => {
      const check = s.serverCheck[f]
      if (check === 'MISMATCH') return [`${FIELD_LABEL[f]} · 서버가 사진에서 읽은 값과 달라요`]
      if (check === 'UNREADABLE') return [`${FIELD_LABEL[f]} · 서버가 사진에서 읽지 못했어요`]
      return []
    }),
  ]
}
