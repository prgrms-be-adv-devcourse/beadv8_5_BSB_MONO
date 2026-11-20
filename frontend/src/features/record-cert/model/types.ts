// TODO(BUK-274): 제출 API가 생기면 orval 생성 타입(memberModel)으로 바꾼다. 이름 · 값은 member-service recordcert 도메인을 따랐다.

/** 종목 구분 (member CourseClass) */
export const COURSE_CLASSES = ['FIVE_K', 'TEN_K', 'HALF', 'FULL', 'ULTRA', 'OTHER'] as const
export type CourseClass = (typeof COURSE_CLASSES)[number]

/** 서버 확인에서 다시 읽는 칸 (member CheckField). 배번은 확인하지 않는다 */
export type CheckField = 'NET_TIME' | 'NET_LABEL' | 'RACE_DATE' | 'COURSE'

/** 원본 픽셀 네 점 (EXIF 회전을 적용한 방향) */
export type Quad = number[][]

export type RecordCertSubmitRequest = {
  orderItemId: number
  fileId: number
  roi: { x: number; y: number; width: number; height: number }
  netTimeSeconds: number
  raceDate: string
  courseClass: CourseClass
  bib?: string
  /** 브라우저가 읽어 채운 칸의 상자. 참가자가 직접 입력한 칸은 빠진다 */
  fieldBoxes: Partial<Record<CheckField, Quad>>
  specVersion: string
  /** 브라우저가 실제로 받은 인식 모델의 sha256. 직접 입력만 했으면 null */
  recModelSha256: string | null
}

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

/** 내 제출 목록의 한 줄. 칸별 서버 확인 결과는 보이지 않는다(submit-spec) */
export type MyRecordCertSubmission = {
  submissionId: number
  orderItemId: number
  status: SubmissionStatus
  reasons: JudgementReason[]
  /** 판매자가 승인 불가로 둔 사유 */
  denyReason?: string | null
  submittedAt: string
}
