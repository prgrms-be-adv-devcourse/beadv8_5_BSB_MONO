import { CREATE_FAILURE_TYPE } from '@/entities/payout'
import type { payoutModel } from '@/shared/api'

// 시안 N4의 「차이」 · 「내용」 · 상세 문구를 기록 한 줄에서 만든다 (BUK-108)

type FailureRecord = payoutModel.PayoutCreateFailureDto
type Course = { raceCourseId?: number; name?: string }

const NONE = '—'

function signed(n: number) {
  return n > 0 ? `+${n}장` : n < 0 ? `−${-n}장` : '0장'
}

/** 정산 장수 − 판매 장수. 장수를 남기는 건 최종 정산 대사뿐이라 나머지는 「—」 */
export function diffText(r: FailureRecord) {
  return r.soldCount == null || r.settledCount == null ? NONE : signed(r.settledCount - r.soldCount)
}

/** 목록 「내용」: 무엇이 어긋났고 배치가 무엇을 했는지 짧게 */
export function summaryText(r: FailureRecord) {
  if (r.failureType !== '대사_불일치') {
    return CREATE_FAILURE_TYPE[r.failureType]
  }
  if (r.actionTaken === '지급보류') {
    return '정산이 판매보다 많음'
  }
  const missing =
    r.soldCount != null && r.settledCount != null ? r.soldCount - r.settledCount : undefined
  if (r.actionTaken === '자동_보정') {
    return missing ? `빠진 참가권 ${missing}장 채움` : '빠진 참가권 채움'
  }
  return missing && missing > 0 ? '정산이 판매보다 적음' : '판매와 정산이 다름'
}

/** 상세 「비고」: 최종은 비교한 장수, 그 밖은 서버 비고 그대로 */
export function noteText(r: FailureRecord) {
  return r.soldCount != null && r.settledCount != null
    ? `판매 ${r.soldCount}장 · 정산 ${r.settledCount}장`
    : (r.note ?? NONE)
}

/**
 * 비고 "빠진 1장 추가 (코스 3: 1장), 더 들어감 2장 (코스 4: 2장)"에서 코스별 정산 − 판매 장수를 읽는다.
 * 빠짐은 −, 더 들어감은 +, 가격 다름은 장수 차이가 아니라 뺀다. 비고 문구는 백엔드 PayoutReconcileStepUseCase가 만든다.
 */
export function courseDiffs(note: string | null | undefined): Map<number, number> {
  const diffs = new Map<number, number>()
  for (const part of (note ?? '').matchAll(/(빠진|더 들어감) \d+장[^(]*\(([^)]*)\)/g)) {
    const sign = part[1] === '빠진' ? -1 : 1
    for (const course of part[2].matchAll(/코스 (\d+): (\d+)장/g)) {
      const id = Number(course[1])
      diffs.set(id, (diffs.get(id) ?? 0) + sign * Number(course[2]))
    }
  }
  return diffs
}

/** 상세 「코스별」: 대회의 모든 코스를 「하프 +2장 · 10K 0장」처럼. 어긋난 코스가 없으면 null */
export function courseText(r: FailureRecord, courses: Course[]) {
  const diffs = courseDiffs(r.note)
  if (diffs.size === 0) {
    return null
  }
  const known = courses.filter((c) => c.raceCourseId != null)
  // 대회 코스 목록에 없는 코스(삭제된 코스 등)도 어긋남은 보여야 하므로 비고의 코스를 뒤에 붙인다
  const knownIds = known.map((c) => c.raceCourseId as number)
  const ids = [...knownIds, ...[...diffs.keys()].filter((id) => !knownIds.includes(id))]
  const nameOf = (id: number) => known.find((c) => c.raceCourseId === id)?.name ?? `코스 ${id}`
  return ids.map((id) => `${nameOf(id)} ${signed(diffs.get(id) ?? 0)}`).join(' · ')
}

/** 상세 아래 안내 */
export function captionText(r: FailureRecord) {
  const log = '참가권 ID는 서버 로그에 있어요.'
  if (r.actionTaken === '지급보류') {
    return `정산이 판매보다 많아 지급보류${r.holdCode ? `(${r.holdCode})` : ''}를 걸었어요. ${log}`
  }
  if (r.actionTaken === '자동_보정') {
    return `빠진 참가권을 최종 정산 내역에 채웠어요. ${log}`
  }
  return `기록만 남겼어요. ${log}`
}
