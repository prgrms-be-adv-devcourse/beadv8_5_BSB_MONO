// @vitest-environment node
import { describe, expect, it } from 'vitest'

import type { MyRecordCertSubmission } from '../model/types'
import { latestSubmission, netSeconds, prefill, toCourseClass } from './cert-form'
import { extractFields, type OcrLine } from './extract'

const bx = (x: number, y: number, w = 60) => [
  [x, y],
  [x + w, y],
  [x + w, y + 30],
  [x, y + 30],
]
const B = (text: string, x: number, y: number, w = 60): OcrLine => ({
  text,
  score: 1,
  box: bx(x, y, w),
})

describe('prefill', () => {
  it('읽은 칸을 채우고 서버 확인할 상자를 남긴다(배번은 상자 없음)', () => {
    const lines = [
      B('대회일 2023.04.12', 0, 0, 200),
      B('종목 하프', 0, 40, 200),
      B('넷타임', 0, 80),
      B('01:30:05', 0, 120),
      B('배번 M-8363', 0, 160, 200),
    ]
    const { values, boxes } = prefill(extractFields(lines))
    expect(values).toEqual({
      netTime: '1:30:05',
      raceDate: '2023-04-12',
      courseClass: 'HALF',
      bib: 'M-8363',
    })
    expect(boxes).toEqual({
      NET_TIME: bx(0, 120),
      NET_LABEL: bx(0, 80),
      RACE_DATE: bx(0, 0, 200),
      COURSE: bx(0, 40, 200),
    })
  })

  it('초를 모르는 넷타임(H:MM)은 비우고 상자도 보내지 않는다', () => {
    const { values, boxes } = prefill(
      extractFields([B('넷타임 3:14', 0, 0, 200), B('풀코스', 0, 40)]),
    )
    expect(values.netTime).toBe('')
    expect(boxes.NET_TIME).toBeUndefined()
    expect(boxes.NET_LABEL).toBeUndefined()
  })

  it('못 읽으면 모두 빈 칸', () => {
    expect(prefill(extractFields([])).values).toEqual({
      netTime: '',
      raceDate: '',
      courseClass: undefined,
      bib: '',
    })
  })
})

it('toCourseClass: 서버 CourseClass.ofKm과 같은 묶음', () => {
  expect(['FULL', 'HALF', '5K', '10K', '100K', '22.07K'].map(toCourseClass)).toEqual([
    'FULL',
    'HALF',
    'FIVE_K',
    'TEN_K',
    'ULTRA',
    'OTHER',
  ])
})

it('netSeconds', () => {
  expect(netSeconds('1:30:05')).toBe(5405)
  expect(netSeconds(' 01:30:05 ')).toBe(5405)
  expect(netSeconds('1:60:05')).toBeNull()
  expect(netSeconds('1:30')).toBeNull()
})

describe('latestSubmission', () => {
  const s = (status: MyRecordCertSubmission['status'], submittedAt: string, orderItemId = 1) => ({
    submissionId: submittedAt.length,
    orderItemId,
    status,
    reasons: [],
    submittedAt,
  })
  it('제출이 없으면 낼 수 있다', () =>
    expect(latestSubmission([], 1)).toEqual({ latest: undefined, canSubmit: true }))
  it('최근 제출이 반려면 다시 낼 수 있고, 확인 중이면 못 낸다', () => {
    const list = [
      s('CHECKING', '2026-11-10T10:00'),
      s('REJECTED', '2026-11-11T10:00'),
      s('CHECKING', '2026-11-12T10:00', 2),
    ]
    expect(latestSubmission(list, 1)).toEqual({ latest: list[1], canSubmit: true })
    expect(latestSubmission(list, 2).canSubmit).toBe(false)
  })
  it.each(['NEEDS_REVIEW', 'AUTO_APPROVED', 'APPROVED'] as const)('%s면 못 낸다', (status) => {
    expect(latestSubmission([s(status, '2026-11-10T10:00')], 1).canSubmit).toBe(false)
  })
})
