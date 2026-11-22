import { http, HttpResponse } from 'msw'

// TODO(BUK-274): 제출 API가 생기면 orval 생성 목으로 바꾼다. 경로는 features/record-cert의 RECORD_CERTS_PATH와 같다
let lastSubmissionId = 0

/** 기록증 제출 목. 제출은 늘 "확인 중"으로 받고, 내 제출 목록은 비어 있다(서버 메모리에 상태를 두지 않는다) */
export const recordCertHandlers = [
  http.get('*/api/v1/member/record-certs', () =>
    HttpResponse.json({ status: 200, message: '내 기록증 제출 목록을 조회했습니다.', data: [] }),
  ),
  http.post('*/api/v1/member/record-certs', async ({ request }) => {
    const body = (await request.json()) as Record<string, unknown>
    if (!body.netTimeSeconds || !body.raceDate || !body.courseClass || !body.fileId || !body.roi) {
      return HttpResponse.json(
        { status: 400, message: '넷타임 · 대회일 · 종목은 꼭 적어 주세요.', data: null },
        { status: 400 },
      )
    }
    lastSubmissionId += 1
    return HttpResponse.json(
      {
        status: 201,
        message: '기록증을 제출했습니다.',
        data: { submissionId: lastSubmissionId, status: 'CHECKING' },
      },
      { status: 201 },
    )
  }),
]

// 판매자 기록증 심사 목 (BUK-276). 대회 1에만 제출이 있고, 다른 대회는 빈 목록이다.
// 승인 · 승인 불가는 늘 성공으로 답하고 상태를 남기지 않는다. 제출 34 승인은 「이미 심사함」(409),
// 메모에 「오류 확인」을 넣은 승인 불가는 503으로 답해 오류 화면을 볼 수 있다.
const SELLER_PATH = '*/api/v1/member/seller'
const ALL_MATCH = { NET_TIME: 'MATCH', NET_LABEL: 'MATCH', RACE_DATE: 'MATCH', COURSE: 'MATCH' }

function sellerSubmission(
  submissionId: number,
  netTimeSeconds: number,
  override: Record<string, unknown> = {},
) {
  return {
    submissionId,
    orderItemId: 700 + submissionId,
    raceCourseId: 1,
    status: 'NEEDS_REVIEW',
    reasons: [],
    serverCheck: { ...ALL_MATCH, NET_TIME: 'MISMATCH' },
    serverCheckMatched: false,
    netTimeSeconds,
    raceDate: '2025-10-26',
    courseClass: 'TEN_K',
    denyReason: null,
    denyMemo: null,
    submittedAt: `2026-11-1${submissionId % 10}T09:00:00`,
    reviewedAt: null,
    ...override,
  }
}

const SELLER_SUBMISSIONS = [
  sellerSubmission(34, 3150, { reasons: ['TOO_FAST'], serverCheck: ALL_MATCH }),
  sellerSubmission(33, 2925, {
    serverCheck: {
      NET_TIME: 'UNREADABLE',
      NET_LABEL: 'UNREADABLE',
      RACE_DATE: 'UNREADABLE',
      COURSE: 'UNREADABLE',
    },
  }),
  sellerSubmission(32, 3310, { raceDate: '2026-04-12' }),
  sellerSubmission(31, 3605, { courseClass: 'OTHER', reasons: ['COURSE_OTHER'] }),
  sellerSubmission(30, 3420, {
    status: 'APPROVED',
    serverCheck: ALL_MATCH,
    serverCheckMatched: true,
    reviewedAt: '2026-11-11T14:00:00',
  }),
]

const ok = (message: string, data: unknown) => HttpResponse.json({ status: 200, message, data })
const fail = (status: number, message: string) =>
  HttpResponse.json({ status, message, data: null }, { status })

function findSellerSubmission(id: string | readonly string[] | undefined) {
  return SELLER_SUBMISSIONS.find((s) => s.submissionId === Number(id))
}

export const sellerRecordCertHandlers = [
  http.get(`${SELLER_PATH}/record-certs/review-count`, () =>
    ok('심사 필요 기록증 제출 수를 조회했습니다.', {
      waitingCount: SELLER_SUBMISSIONS.filter((s) => s.status === 'NEEDS_REVIEW').length,
    }),
  ),
  http.get(`${SELLER_PATH}/races/:raceId/record-certs`, ({ params, request }) => {
    const status = new URL(request.url).searchParams.get('status')
    const list = Number(params.raceId) === 1 ? SELLER_SUBMISSIONS : []
    return ok(
      '대회의 기록증 제출 목록을 조회했습니다.',
      list.filter((s) => !status || s.status === status),
    )
  }),
  http.get(`${SELLER_PATH}/record-certs/:submissionId`, ({ params }) => {
    const submission = findSellerSubmission(params.submissionId)
    if (!submission) return fail(404, '기록증 제출을 찾을 수 없습니다.')
    return ok('기록증 제출을 조회했습니다.', {
      submission,
      fileUrl: null,
      roi: { x: 0, y: 0, width: 800, height: 1100 },
      fieldBoxes: null,
      bib: submission.submissionId === 33 ? null : `M-${8300 + submission.submissionId}`,
    })
  }),
  http.post(`${SELLER_PATH}/record-certs/:submissionId/approve`, ({ params }) => {
    const submission = findSellerSubmission(params.submissionId)
    if (!submission) return fail(404, '기록증 제출을 찾을 수 없습니다.')
    if (submission.submissionId === 34)
      return fail(409, '심사 필요 상태인 제출만 심사할 수 있습니다.')
    return ok('기록증 제출을 승인했습니다.', { ...submission, status: 'APPROVED' })
  }),
  http.post(`${SELLER_PATH}/record-certs/:submissionId/deny`, async ({ params, request }) => {
    const submission = findSellerSubmission(params.submissionId)
    if (!submission) return fail(404, '기록증 제출을 찾을 수 없습니다.')
    const body = (await request.json()) as { reason?: string; memo?: string }
    if (!body.reason) return fail(400, '승인 불가 사유를 골라 주세요.')
    if (body.memo?.includes('오류 확인')) return fail(503, '잠시 후 다시 시도해 주세요.')
    return ok('기록증 제출을 승인 불가로 정했습니다.', {
      ...submission,
      status: 'DENIED',
      denyReason: body.reason,
      denyMemo: body.memo ?? null,
    })
  }),
]
