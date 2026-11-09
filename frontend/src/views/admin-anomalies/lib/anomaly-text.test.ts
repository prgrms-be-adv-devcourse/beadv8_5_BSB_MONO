import { describe, expect, it } from 'vitest'

import type { payoutModel } from '@/shared/api'

import {
  captionText,
  courseDiffs,
  courseText,
  diffText,
  noteText,
  summaryText,
} from './anomaly-text'

const BASE: payoutModel.PayoutCreateFailureDto = {
  id: 1,
  recordedAt: '2026-11-06T03:00:12',
  runDate: '2026-11-06',
  payoutStage: '최종_정산',
  sellerId: 1,
  raceId: 10,
  raceCourseId: null,
  payoutTargetDate: '2026-11-05',
  failureType: '대사_불일치',
  actionTaken: '기록만',
  note: null,
  soldCount: null,
  settledCount: null,
  holdCode: null,
}

describe('anomaly-text', () => {
  it('정산이 판매보다 많으면 +장, 보류 ID를 안내에 쓴다 (시안 1행)', () => {
    const r = {
      ...BASE,
      actionTaken: '지급보류' as const,
      soldCount: 310,
      settledCount: 312,
      holdCode: 'HD-20261106-0D44',
      note: '더 들어감 2장 (코스 4: 2장)',
    }
    expect(diffText(r)).toBe('+2장')
    expect(summaryText(r)).toBe('정산이 판매보다 많음')
    expect(noteText(r)).toBe('판매 310장 · 정산 312장')
    expect(captionText(r)).toBe(
      '정산이 판매보다 많아 지급보류(HD-20261106-0D44)를 걸었어요. 참가권 ID는 서버 로그에 있어요.',
    )
  })

  it('빠진 참가권을 채웠으면 −장과 채운 장수를 쓴다 (시안 2행)', () => {
    const r = { ...BASE, actionTaken: '자동_보정' as const, soldCount: 310, settledCount: 307 }
    expect(diffText(r)).toBe('−3장')
    expect(summaryText(r)).toBe('빠진 참가권 3장 채움')
  })

  it('대사 외 실패는 장수 없이 종류 이름을 쓴다 (시안 3행)', () => {
    const r = { ...BASE, failureType: '주문_조회_실패' as const, payoutStage: '일차_정산' as const }
    expect(diffText(r)).toBe('—')
    expect(summaryText(r)).toBe('주문 조회 실패')
    expect(captionText(r)).toBe('기록만 남겼어요. 참가권 ID는 서버 로그에 있어요.')
  })

  it('비고에서 코스별 정산 − 판매 장수를 읽고, 대회의 다른 코스는 0장으로 보인다', () => {
    const note =
      '빠진 1장 추가 (코스 3: 1장), 더 들어감 2장 (코스 4: 2장), 가격 다름 1장 (코스 3: 1장)'
    expect([...courseDiffs(note)]).toEqual([
      [3, -1],
      [4, 2],
    ])
    expect(
      courseText({ ...BASE, note: '더 들어감 2장 (코스 4: 2장)' }, [
        { raceCourseId: 4, name: '하프' },
        { raceCourseId: 5, name: '10K' },
      ]),
    ).toBe('하프 +2장 · 10K 0장')
  })

  it('대회 코스를 모르면 비고의 코스 번호로, 어긋난 코스가 없으면 null', () => {
    expect(courseText({ ...BASE, note: '빠진 3장 (코스 3: 3장)' }, [])).toBe('코스 3 −3장')
    expect(
      courseText({ ...BASE, note: '더 들어감 2장 (코스 4: 2장)' }, [
        { raceCourseId: 5, name: '10K' },
      ]),
    ).toBe('10K 0장 · 코스 4 +2장')
    expect(courseText({ ...BASE, note: '판매 합 = 70,000, 정산 합 = 50,000' }, [])).toBeNull()
  })
})
