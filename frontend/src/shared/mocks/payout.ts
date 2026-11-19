import { http, HttpResponse } from 'msw'

import type { payoutModel } from '@/shared/api'

/**
 * 이상 감지 기록 목. 명세 예시에는 대사 불일치 기록만 있어, 종류를 고른 요청은 그 종류만 돌려준다.
 * 지급 확인 필요 기록은 백엔드 PayoutCreateFailure.payCheckRequired의 비고 형식을 따른다 (BUK-175)
 */
const RECORDS: payoutModel.PayoutCreateFailureDto[] = [
  {
    id: 12,
    recordedAt: '2026-11-15T09:00:04',
    runDate: '2026-11-15',
    payoutStage: '최종_정산',
    sellerId: 3,
    raceId: 21,
    raceCourseId: null,
    payoutTargetDate: null,
    failureType: '지급_확인_필요',
    actionTaken: null,
    note: '정산 41(ST-20261115-7B2C) 지급액 1840000원 · 수수료 160000원, 결과 모름 3번',
    soldCount: null,
    settledCount: null,
    holdCode: null,
  },
  {
    id: 7,
    recordedAt: '2026-11-06T03:00:12',
    runDate: '2026-11-06',
    payoutStage: '최종_정산',
    sellerId: 1,
    raceId: 10,
    raceCourseId: 4,
    payoutTargetDate: '2026-11-05',
    failureType: '대사_불일치',
    actionTaken: '지급보류',
    note: '더 들어감 2장 (코스 4: 2장)',
    soldCount: 310,
    settledCount: 312,
    holdCode: 'HD-20261106-0D44',
  },
]

export const payoutHandlers = [
  http.get('*/api/v1/payout/admin/create-failures', ({ request }) => {
    const type = new URL(request.url).searchParams.get('type')
    const content = type ? RECORDS.filter((r) => r.failureType === type) : RECORDS
    return HttpResponse.json({
      status: 200,
      message: '이상 감지 기록을 조회했습니다.',
      data: {
        mismatchCount: 1,
        autoFilledCount: 0,
        orderLookupFailureCount: 0,
        records: { content, page: 0, size: 50, totalElements: content.length, totalPages: 1 },
      },
    })
  }),
]
