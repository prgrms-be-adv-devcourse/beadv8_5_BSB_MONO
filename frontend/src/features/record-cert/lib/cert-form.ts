import type {
  CheckField,
  CourseClass,
  JudgementReason,
  MyRecordCertSubmission,
  Quad,
  SubmissionStatus,
} from '../model/types'
import { courseKm, type Fields } from './extract'

/** P2 입력 칸 값. 못 읽은 칸은 빈 값(틀릴 바엔 비움, adr 0003) */
export type CertFormValues = {
  netTime: string
  raceDate: string
  courseClass: CourseClass | undefined
  bib: string
}

export const COURSE_LABEL: Record<CourseClass, string> = {
  FIVE_K: '5K',
  TEN_K: '10K',
  HALF: '하프',
  FULL: '풀',
  ULTRA: '울트라',
  OTHER: '기타',
}

/** 뽑기 규칙의 종목('FULL' · 'HALF' · '10K' · '22.07K') → 종목 구분. member CourseClass.ofKm과 같은 기준 */
export function toCourseClass(cls: string): CourseClass {
  if (cls === 'FULL') return 'FULL'
  if (cls === 'HALF') return 'HALF'
  const km = courseKm(cls) ?? 0
  if (Math.abs(km - 5) < 0.01) return 'FIVE_K'
  if (Math.abs(km - 10) < 0.01) return 'TEN_K'
  return km > 42.195 ? 'ULTRA' : 'OTHER'
}

const pad = (n: number) => String(n).padStart(2, '0')

/**
 * 읽은 값으로 칸을 채우고, 채운 칸의 상자를 남긴다.
 * 넷타임이 'H:MM'뿐이면(초를 모름) 채우지 않는다. 초를 지어내면 틀린 값이 된다.
 */
export function prefill(fields: Fields): {
  values: CertFormValues
  boxes: Partial<Record<CheckField, Quad>>
} {
  const net = fields.netTime?.value.length === 3 ? fields.netTime : null
  const boxes: Partial<Record<CheckField, Quad>> = {}
  if (net) {
    boxes.NET_TIME = net.quad
    if (fields.netLabel) boxes.NET_LABEL = fields.netLabel.quad
  }
  if (fields.raceDate) boxes.RACE_DATE = fields.raceDate.quad
  if (fields.course) boxes.COURSE = fields.course.quad
  return {
    values: {
      netTime: net ? `${net.value[0]}:${pad(net.value[1])}:${pad(net.value[2])}` : '',
      raceDate: fields.raceDate?.value ?? '',
      courseClass: fields.course ? toCourseClass(fields.course.value) : undefined,
      bib: fields.bib?.value ?? '',
    },
    boxes,
  }
}

export const EMPTY_VALUES: CertFormValues = {
  netTime: '',
  raceDate: '',
  courseClass: undefined,
  bib: '',
}

const NET_TIME = /^(\d{1,2}):([0-5]\d):([0-5]\d)$/

/** 'H:MM:SS' → 초. 꼴이 틀리면 null */
export function netSeconds(text: string): number | null {
  const m = NET_TIME.exec(text.trim())
  return m ? Number(m[1]) * 3600 + Number(m[2]) * 60 + Number(m[3]) : null
}

export const SUBMISSION_STATUS: Record<
  SubmissionStatus,
  { label: string; tone: 'info' | 'success' | 'danger' | 'neutral' }
> = {
  CHECKING: { label: '확인 중', tone: 'info' },
  NEEDS_REVIEW: { label: '판매자 확인 중', tone: 'info' },
  AUTO_APPROVED: { label: '인정됨', tone: 'success' },
  APPROVED: { label: '인정됨', tone: 'success' },
  REJECTED: { label: '인정 안 됨', tone: 'danger' },
  DENIED: { label: '인정 안 됨', tone: 'danger' },
  CANCELED: { label: '취소됨', tone: 'neutral' },
}

export const REASON_LABEL: Record<JudgementReason, string> = {
  BEFORE_RECOGNIZED_PERIOD: '기록 인정 기간보다 앞선 대회예요',
  NET_TIME_OVER: '넷타임이 기준보다 느려요',
  COURSE_NOT_ALLOWED: '인정하지 않는 종목이에요',
  COURSE_OTHER: '종목을 확인해야 해요',
  FUTURE_RACE_DATE: '대회일이 아직 지나지 않았어요',
  TOO_FAST: '기록을 확인해야 해요',
}

/** 반려 · 승인 불가 · 취소됨 뒤에만 다시 낼 수 있다 (submit-spec) */
const RESUBMITTABLE: SubmissionStatus[] = ['REJECTED', 'DENIED', 'CANCELED']

/** 이 참가권의 최근 제출과, 지금 제출할 수 있는지 */
export function latestSubmission(list: MyRecordCertSubmission[], orderItemId: number) {
  const latest = list
    .filter((s) => s.orderItemId === orderItemId)
    .sort((a, b) => b.submittedAt.localeCompare(a.submittedAt))[0]
  return { latest, canSubmit: !latest || RESUBMITTABLE.includes(latest.status) }
}
