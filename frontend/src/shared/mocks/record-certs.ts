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
