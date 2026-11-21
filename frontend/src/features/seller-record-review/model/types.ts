// TODO(BUK-276): member 명세(openapi/member.json)에 판매자 기록증 API가 들어오면 orval 생성 타입으로 바꾼다.
// 이름 · 값은 member-service recordcert의 SellerRecordCertSubmissionDto · SellerRecordCertDetailDto를 따랐다.

/** 제출 상태 (member SubmissionStatus) */
export type SubmissionStatus =
  'CHECKING' | 'AUTO_APPROVED' | 'REJECTED' | 'NEEDS_REVIEW' | 'APPROVED' | 'DENIED' | 'CANCELED'

/** 판정 사유 (member JudgementReason) */
export type JudgementReason =
  | 'BEFORE_RECOGNIZED_PERIOD'
  | 'NET_TIME_OVER'
  | 'COURSE_NOT_ALLOWED'
  | 'COURSE_OTHER'
  | 'FUTURE_RACE_DATE'
  | 'TOO_FAST'

/** 서버 확인에서 다시 읽는 칸 (member CheckField) */
export type CheckField = 'NET_TIME' | 'NET_LABEL' | 'RACE_DATE' | 'COURSE'
export type FieldCheck = 'MATCH' | 'MISMATCH' | 'UNREADABLE'

/** 종목 구분 (member CourseClass) */
export type CourseClass = 'FIVE_K' | 'TEN_K' | 'HALF' | 'FULL' | 'ULTRA' | 'OTHER'

/** 승인 불가 사유 (member DenyReason) */
export const DENY_REASONS = [
  'NAME_MISMATCH',
  'OTHER_RACE',
  'NOT_NET_TIME',
  'BLURRY',
  'OTHER',
] as const
export type DenyReason = (typeof DENY_REASONS)[number]

export type SellerRecordCertSubmission = {
  submissionId: number
  orderItemId: number
  raceCourseId: number
  status: SubmissionStatus
  reasons: JudgementReason[]
  /** 칸별 서버 확인 결과. 확인 중이면 빈 객체 */
  serverCheck: Partial<Record<CheckField, FieldCheck>>
  serverCheckMatched: boolean
  netTimeSeconds: number
  raceDate: string
  courseClass: CourseClass
  denyReason: DenyReason | null
  denyMemo: string | null
  submittedAt: string
  reviewedAt: string | null
}

export type SellerRecordCertDetail = {
  submission: SellerRecordCertSubmission
  /** 원본 사진 보기 주소(30분 유효). 사진 정리 배치가 지운 제출이면 null */
  fileUrl: string | null
  roi: { x: number; y: number; width: number; height: number }
  fieldBoxes: string | null
  bib: string | null
}

export type RecordCertDenyRequest = { reason: DenyReason; memo?: string }
