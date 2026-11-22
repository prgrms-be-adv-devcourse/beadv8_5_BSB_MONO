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

/** 화면 주소의 ?case= 값. 가짜 응답 모드에서 빈 목록 · 계좌 없음 상태를 띄울 때 쓴다 */
function screenCase(referer: string | null) {
  try {
    return new URL(referer ?? '').searchParams.get('case')
  } catch {
    return null
  }
}

/** 정산 상세 목(시안 2070:69311 최종 지급 예정, 2070:70381 1차 지급보류). 정산 ID로 찾기에만 쓴다 */
const SELLER_PAYOUTS: payoutModel.PayoutDto[] = [
  {
    id: 41,
    payoutCode: 'ST-20261019-B204',
    raceId: 21,
    raceName: '춘천 하프마라톤',
    payoutStage: '최종_정산',
    payoutMonth: '2026-11',
    salesAmount: 14_000_000,
    refundAmount: 440_000,
    feeAmount: 745_800,
    advanceDeductionAmount: 5_424_000,
    payoutAmount: 7_390_200,
    status: '지급_예정',
    settledAt: '2026-11-02T03:00:00',
    scheduledPayDate: '2026-11-20',
    paidAt: null,
  },
  {
    id: 40,
    payoutCode: 'ST-20261019-A118',
    raceId: 21,
    raceName: '춘천 하프마라톤',
    payoutStage: '일차_정산',
    payoutMonth: '2026-10',
    salesAmount: 14_000_000,
    refundAmount: 440_000,
    feeAmount: 0,
    advanceDeductionAmount: 0,
    payoutAmount: 5_424_000,
    status: '지급보류',
    settledAt: '2026-10-20T03:00:00',
    scheduledPayDate: '2026-11-20',
    paidAt: null,
  },
]

/** 회수 목(시안 2070:69648). 입금 대기 · 입금 완료 · 회수 취소 */
const SELLER_CLAIMS: payoutModel.SellerRecoveryDto[] = [
  {
    id: 3,
    claimCode: 'RC-20261113-0001',
    raceId: 21,
    raceName: '춘천 하프마라톤',
    payoutCode: 'ST-20261019-B204',
    amount: 60_000,
    reasonType: '일정_변경',
    status: '회수_요청',
    requestedAt: '2026-11-13T09:00:00',
    completedAt: null,
    virtualBankName: '국민은행',
    virtualAccountNumber: '904-2201-3344-12',
    dueDate: '2026-12-13',
  },
  {
    id: 2,
    claimCode: 'RC-20260930-0002',
    raceId: 12,
    raceName: '자라섬 10K',
    payoutCode: 'ST-20260926-0002',
    amount: 35_000,
    reasonType: '대회_취소',
    status: '회수_완료',
    requestedAt: '2026-09-30T09:00:00',
    completedAt: '2026-10-28T14:00:00',
    virtualBankName: '국민은행',
    virtualAccountNumber: '904-2201-1180-07',
    dueDate: '2026-10-30',
  },
  {
    id: 1,
    claimCode: 'RC-20260812-0001',
    raceId: 9,
    raceName: '여름 나이트런',
    payoutCode: null,
    amount: 42_000,
    reasonType: '일정_변경',
    status: '회수_취소',
    requestedAt: '2026-08-12T09:00:00',
    completedAt: null,
    virtualBankName: null,
    virtualAccountNumber: null,
    dueDate: '2026-09-11',
  },
]

/** 세금계산서 목(시안 2070:69773). ?case=empty 화면이면 빈 목록 */
const TAX_INVOICES: payoutModel.PayoutTaxInvoiceDto[] = [
  {
    id: 3,
    sellerId: 3,
    supplyMonth: '2026-10',
    totalAmount: 277_200,
    supplyAmount: 252_000,
    taxAmount: 25_200,
    status: '발행_완료',
    approvalNumber: '20261101-41000000-01234567',
    businessName: '나이트런컴퍼니',
    businessNumber: '123-45-67890',
    representativeName: '김러너',
    issuedAt: '2026-11-01T06:10:00',
    failReason: null,
  },
  {
    id: 2,
    sellerId: 3,
    supplyMonth: '2026-09',
    totalAmount: 207_900,
    supplyAmount: 189_000,
    taxAmount: 18_900,
    status: '발행_실패',
    approvalNumber: null,
    businessName: '나이트런컴퍼니',
    businessNumber: '123-45-67890',
    representativeName: '김러너',
    issuedAt: '2026-10-01T06:10:00',
    failReason: '공급받는 자의 사업자등록번호가 휴업 상태예요',
  },
  {
    id: 1,
    sellerId: 3,
    supplyMonth: '2026-08',
    totalAmount: 154_000,
    supplyAmount: 140_000,
    taxAmount: 14_000,
    status: '발행_완료',
    approvalNumber: '20260901-41000000-00872210',
    businessName: '나이트런컴퍼니',
    businessNumber: '123-45-67890',
    representativeName: '김러너',
    issuedAt: '2026-09-01T06:10:00',
    failReason: null,
  },
]

export const payoutHandlers = [
  // 정산 ID로 찾을 때만 답한다. 기간 조회는 명세 예시(생성 목)로 넘긴다
  http.get('*/api/v1/payout/seller/payouts', ({ request }) => {
    const code = new URL(request.url).searchParams.get('code')
    if (!code) return undefined
    const found = SELLER_PAYOUTS.filter((p) => p.payoutCode === code)
    if (found.length === 0) {
      return HttpResponse.json(
        { status: 404, message: '정산 내역을 찾을 수 없습니다.', data: null },
        { status: 404 },
      )
    }
    return HttpResponse.json({ status: 200, message: '정산 내역을 조회했습니다.', data: found })
  }),
  http.get('*/api/v1/payout/seller/claims', () =>
    HttpResponse.json({ status: 200, message: '회수 내역을 조회했습니다.', data: SELLER_CLAIMS }),
  ),
  http.get('*/api/v1/payout/seller/tax-invoices', ({ request }) =>
    HttpResponse.json({
      status: 200,
      message: '세금계산서를 조회했습니다.',
      data: screenCase(request.headers.get('referer')) === 'empty' ? [] : TAX_INVOICES,
    }),
  ),
  // 정산 상세 주소에 ?case=no-account를 붙이면 정산 계좌가 없는 판매자로 답한다(시안 2070:70381)
  http.get('*/api/v1/member/sellers/me/payout-account', ({ request }) => {
    if (screenCase(request.headers.get('referer')) !== 'no-account') return undefined
    return HttpResponse.json({
      status: 200,
      message: '정산 계좌를 조회했습니다.',
      data: { registered: false },
    })
  }),
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
