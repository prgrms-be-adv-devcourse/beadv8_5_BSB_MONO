import { http, HttpResponse } from 'msw'

/**
 * 문의 목. 백엔드 inquiry-service(ApiV1InquiryController)의 경로 · 응답 모양을 따른다.
 * 처음 값은 시안(문의 · 11/10)의 내 문의 3건이다. 남기기 · 고치기 · 지우기 · 답변이 메모리 목록에 반영된다.
 * 호출한 회원이 누구인지는 따지지 않는다(가짜 응답 모드 전용).
 */
type Row = {
  id: number
  raceId: number | null
  orderId: number | null
  type: string
  assignee: 'SELLER' | 'ADMIN'
  title: string
  body: string
  status: 'WAITING' | 'ANSWERED'
  answer: string | null
  answeredAt: string | null
  createDate: string
  memberNickname: string | null
  deleted?: boolean
}

const ASSIGNEE: Record<string, 'SELLER' | 'ADMIN'> = {
  APPLY_PAYMENT: 'ADMIN',
  RACE_VENUE: 'SELLER',
  KIT_DELIVERY: 'SELLER',
  ETC: 'ADMIN',
  SELLER_SUPPORT: 'ADMIN',
}

const seed = (): Row[] => [
  {
    id: 3,
    raceId: 2,
    orderId: 502,
    type: 'APPLY_PAYMENT',
    assignee: 'ADMIN',
    title: '취소하면 예치금은 언제 돌아오나요?',
    body: '참가를 취소하면 환불은 언제 예치금에 들어오나요?',
    status: 'ANSWERED',
    answer: '취소하면 바로 예치금으로 돌려드려요.',
    answeredAt: '2026-10-28T11:05:00',
    createDate: '2026-10-28T10:20:00',
    memberNickname: '달리는곰',
  },
  {
    id: 2,
    raceId: 1,
    orderId: 501,
    type: 'KIT_DELIVERY',
    assignee: 'SELLER',
    title: '기념 티셔츠 실측이 궁금해요',
    body: '평소 M을 입는데 기념 티셔츠 가슴 단면과 총장이 몇 cm인지 알고 싶어요.',
    status: 'WAITING',
    answer: null,
    answeredAt: null,
    createDate: '2026-11-06T18:40:00',
    memberNickname: '새벽토끼',
  },
  {
    id: 1,
    raceId: 1,
    orderId: 501,
    type: 'RACE_VENUE',
    assignee: 'SELLER',
    title: '유모차를 끌고 뛰어도 되나요?',
    body: '5K 코스에 아이와 함께 유모차를 끌고 참가하려고 해요. 출발 그룹을 따로 정해야 하나요?',
    status: 'WAITING',
    answer: null,
    answeredAt: null,
    createDate: '2026-11-10T09:12:00',
    memberNickname: '달리는곰',
  },
]

let rows = seed()
let nextId = 4

export function resetMockInquiriesForTesting() {
  rows = seed()
  nextId = 4
}

const ok = (message: string, data: unknown, status = 200) =>
  HttpResponse.json({ status, message, data }, { status })
const notFound = () =>
  HttpResponse.json(
    { status: 404, message: '문의를 찾을 수 없습니다.', code: 'INQUIRY_NOT_FOUND', data: null },
    { status: 404 },
  )
const conflict = () =>
  HttpResponse.json(
    {
      status: 409,
      message: '이미 답변이 달린 문의입니다.',
      code: 'INQUIRY_ALREADY_ANSWERED',
      data: null,
    },
    { status: 409 },
  )
const invalid = (message: string) =>
  HttpResponse.json({ status: 400, message, data: null }, { status: 400 })

const visible = () => rows.filter((r) => !r.deleted)
const find = (id: string) => visible().find((r) => r.id === Number(id))

const toDto = (r: Row, manage = false, admin = false) => {
  const { memberNickname, deleted, ...rest } = r
  void deleted
  return {
    ...rest,
    memberNickname: manage ? memberNickname : null,
    order:
      manage && r.orderId
        ? {
            orderNo: 'OD-20261110-3F9A',
            paymentAmount: admin ? 50000 : null,
            status: 'PAID',
            items: [
              {
                raceId: r.raceId,
                raceName: '춘천 호수 하프마라톤',
                courseName: '5K',
                kitSummary: '티셔츠 M, 완주 메달',
                deliveryStatus: 'PREPARING',
              },
            ],
          }
        : null,
  }
}

const page = (items: Row[]) => ({
  inquiries: items,
  page: 0,
  size: 20,
  totalElements: items.length,
  totalPages: items.length ? 1 : 0,
})

// 답변 대기가 먼저, 그 안에서 최신순
const sorted = (items: Row[]) =>
  [...items].sort(
    (a, b) =>
      Number(a.status === 'ANSWERED') - Number(b.status === 'ANSWERED') ||
      b.createDate.localeCompare(a.createDate),
  )

const manageList = (scope: Row[], status: string | null, admin: boolean) =>
  ok('문의 목록을 조회했습니다.', {
    ...page(
      sorted(scope.filter((r) => !status || r.status === status)).map((r) => toDto(r, true, admin)),
    ),
    waitingCount: scope.filter((r) => r.status === 'WAITING').length,
    answeredCount: scope.filter((r) => r.status === 'ANSWERED').length,
  })

const answerWith = async (request: Request, id: string, assignee: 'SELLER' | 'ADMIN') => {
  const row = find(id)
  if (!row || row.assignee !== assignee) return notFound()
  if (row.status === 'ANSWERED') return conflict()
  const { content } = (await request.json()) as { content?: string }
  if (!content?.trim()) return invalid('답변 내용을 입력해주세요.')
  row.status = 'ANSWERED'
  row.answer = content
  row.answeredAt = new Date().toISOString().slice(0, 19)
  return ok('답변을 남겼습니다.', toDto(row, true, assignee === 'ADMIN'))
}

export const inquiryHandlers = [
  http.post('*/api/v1/inquiry/inquiries', async ({ request }) => {
    const body = (await request.json()) as Record<string, string | number | null>
    if (!body.title || !body.body) return invalid('제목과 문의 내용을 입력해주세요.')
    const type = String(body.type)
    const row: Row = {
      id: nextId++,
      raceId: (body.raceId as number | null) ?? null,
      orderId: (body.orderId as number | null) ?? null,
      type,
      assignee: ASSIGNEE[type] ?? 'ADMIN',
      title: String(body.title),
      body: String(body.body),
      status: 'WAITING',
      answer: null,
      answeredAt: null,
      createDate: new Date().toISOString().slice(0, 19),
      memberNickname: '달리는곰',
    }
    rows.unshift(row)
    return ok('문의를 남겼습니다.', toDto(row), 201)
  }),
  http.get('*/api/v1/inquiry/inquiries/me', () =>
    ok(
      '문의 목록을 조회했습니다.',
      page(
        sorted(visible())
          .sort((a, b) => b.createDate.localeCompare(a.createDate))
          .map((r) => toDto(r)),
      ),
    ),
  ),
  http.get('*/api/v1/inquiry/inquiries/:id', ({ params }) => {
    const row = find(String(params.id))
    return row ? ok('문의를 조회했습니다.', toDto(row)) : notFound()
  }),
  http.patch('*/api/v1/inquiry/inquiries/:id', async ({ params, request }) => {
    const row = find(String(params.id))
    if (!row) return notFound()
    if (row.status === 'ANSWERED') return conflict()
    const body = (await request.json()) as { title?: string; body?: string }
    if (!body.title?.trim() || !body.body?.trim())
      return invalid('제목과 문의 내용을 입력해주세요.')
    row.title = body.title
    row.body = body.body
    return ok('문의를 고쳤습니다.', toDto(row))
  }),
  http.delete('*/api/v1/inquiry/inquiries/:id', ({ params }) => {
    const row = find(String(params.id))
    if (!row) return notFound()
    if (row.status === 'ANSWERED') return conflict()
    row.deleted = true
    return ok('문의를 지웠습니다.', null)
  }),
  http.get('*/api/v1/inquiry/seller/inquiries', ({ request }) =>
    manageList(
      visible().filter((r) => r.assignee === 'SELLER'),
      new URL(request.url).searchParams.get('status'),
      false,
    ),
  ),
  http.get('*/api/v1/inquiry/seller/inquiries/:id', ({ params }) => {
    const row = find(String(params.id))
    return row?.assignee === 'SELLER' ? ok('문의를 조회했습니다.', toDto(row, true)) : notFound()
  }),
  http.post('*/api/v1/inquiry/seller/inquiries/:id/answer', ({ params, request }) =>
    answerWith(request, String(params.id), 'SELLER'),
  ),
  http.get('*/api/v1/inquiry/admin/inquiries', ({ request }) => {
    const query = new URL(request.url).searchParams
    const assignee = query.get('assignee')
    return manageList(
      visible().filter((r) => !assignee || r.assignee === assignee),
      query.get('status'),
      true,
    )
  }),
  http.get('*/api/v1/inquiry/admin/inquiries/:id', ({ params }) => {
    const row = find(String(params.id))
    return row ? ok('문의를 조회했습니다.', toDto(row, true, true)) : notFound()
  }),
  http.post('*/api/v1/inquiry/admin/inquiries/:id/answer', ({ params, request }) =>
    answerWith(request, String(params.id), 'ADMIN'),
  ),
]
