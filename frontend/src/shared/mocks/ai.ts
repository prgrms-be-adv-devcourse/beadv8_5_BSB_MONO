import { http, HttpResponse } from 'msw'

/**
 * 크루런 AI 가짜 응답 (ai-service ApiV1AiChatController의 /api/v1/ai/{seller|admin}/chat 모양).
 * 브리핑 · 문의 초안 주소는 백엔드에 아직 없어 프론트가 정한 것이다.
 * 질문에 들어간 낱말로 시안(판매자 1388:65090, 관리자 1388:68427)의 답을 돌려주고, 「오류」가 들어가면 모델 오류(503)를 낸다.
 */
const ok = (data: unknown, message = '답변했습니다.') =>
  HttpResponse.json({ status: 200, message, data })

const fail = (status: number, code: string, message: string) =>
  HttpResponse.json({ status, code, message, data: null }, { status })

const SELLER_ANSWERS: { match: string; answer: unknown }[] = [
  {
    match: '정산',
    answer: {
      answer: '11월 10일에 12,480,000원이 지급될 예정이에요. 지급보류가 1건 있어요.',
      sources: [
        {
          screen: '정산 관리 · 정산 요약',
          asOf: '2026-11-03T14:03:00',
          buttonLabel: '정산 관리에서 보기',
          path: '/seller/settlements',
        },
      ],
      list: null,
    },
  },
  {
    match: '송장',
    answer: {
      answer: '4개예요. 환불 마감이 지났는데 송장을 아직 넣지 않은 키트예요.',
      sources: [
        {
          screen: '주문 관리 · 키트 배송 현황',
          asOf: '2026-11-03T14:03:00',
          buttonLabel: '송장 입력하러 가기',
          path: '/seller/kits',
        },
      ],
      list: {
        rows: [
          {
            orderNo: 'OD-20261028-0012',
            name: '김민지',
            detail: '춘천 호수 하프마라톤 · 하프 21K',
          },
          { orderNo: 'OD-20261027-0458', name: '이준호', detail: '서울 한강 나이트런 · 10K' },
          { orderNo: 'OD-20261025-0131', name: '박서연', detail: '제주 트레일 런 20K · 20K' },
          { orderNo: 'OD-20261024-0907', name: '최도윤', detail: '부산 바다 10K · 10K' },
        ],
        moreCount: 0,
      },
    },
  },
]

const ADMIN_ANSWERS: { match: string; answer: unknown }[] = [
  {
    match: '승인',
    answer: {
      answer: '3명이에요. 가장 오래된 신청은 박*민 님(10월 3일, 휴대폰 ***-****-1234)이에요.',
      sources: [
        {
          screen: '판매자 관리 · 승인 대기',
          asOf: '2026-11-03T14:03:00',
          buttonLabel: '판매자 관리에서 보기',
          path: '/admin/sellers',
        },
      ],
      list: null,
    },
  },
  {
    match: '지급보류',
    answer: {
      answer: '지급보류 중인 정산은 2건이고, 보류 금액은 모두 ₩3,260,000이에요.',
      sources: [
        {
          screen: '정산 관리 · 지급보류',
          asOf: '2026-11-03T14:03:00',
          buttonLabel: '정산 관리에서 보기',
          path: '/admin/settlements?tab=지급보류',
        },
      ],
      list: null,
    },
  },
]

function chatHandlers(screen: 'seller' | 'admin', answers: typeof SELLER_ANSWERS) {
  return [
    http.post(`*/api/v1/ai/${screen}/chat`, async ({ request }) => {
      const { message } = (await request.json()) as { message?: string }
      if (!message?.trim()) return fail(400, 'INVALID_REQUEST', '질문을 입력해주세요.')
      if (message.includes('오류')) {
        return fail(
          503,
          'AI_MODEL_UNAVAILABLE',
          '지금은 답변을 만들 수 없습니다. 잠시 뒤 다시 시도해주세요.',
        )
      }
      const hit = answers.find((item) => message.includes(item.match))
      return ok(
        hit?.answer ?? {
          answer: '이 화면에서 확인할 수 없는 내용이에요. 해당 관리 화면에서 확인해 주세요.',
          sources: [],
          list: null,
        },
      )
    }),
    http.delete(`*/api/v1/ai/${screen}/chat`, () => ok(null, '새 대화를 시작합니다.')),
  ]
}

// ai-service AiBriefingUseCase가 만드는 문장과 같은 모양 (모델을 부르지 않고 숫자로 만든다)
const SELLER_BRIEFING = {
  headline: '송장 입력 대기 키트가 4건 있어요. 지급보류 1건의 사유를 확인해 주세요.',
  basis: '오전 9:00 기준',
  todos: [
    {
      title: '송장 입력 대기 4건',
      description: '환불 기한이 지나면 2건을 더 보낼 수 있어요',
      linkLabel: '키트 배송 관리',
      href: '/seller/kits',
    },
    {
      title: '지급보류 1건',
      description: '보류가 풀려야 그 정산이 지급돼요',
      linkLabel: '지급보류 내역',
      href: '/seller/settlements?tab=holds',
    },
  ],
  metrics: [
    { label: '보낼 키트', value: '4건', note: '환불 기한 전 2건' },
    { label: '다음 정산 · 11/20 지급', value: '₩1,250,000', note: '보류 1건 빼고' },
  ],
}

const ADMIN_BRIEFING = {
  headline: '판매자 승인 대기가 3건 있어요. 관리자가 답할 문의가 2건 있어요.',
  basis: '오전 9:00 기준',
  todos: [
    {
      title: '판매자 승인 대기 3건',
      description: '11/12 신청부터 확인해 주세요',
      linkLabel: '판매자 승인',
      href: '/admin/sellers',
    },
    {
      title: '답변 대기 문의 · 관리자 담당 2건',
      description: '판매자 담당까지 전체 7건이 답변을 기다려요',
      linkLabel: '문의 관리',
      href: '/admin/inquiries',
    },
  ],
  metrics: [
    { label: '판매자 승인 대기', value: '3건', note: '가장 오래된 신청 5일째' },
    { label: '답변 대기 문의 · 전체', value: '7건', note: '관리자 담당 2건' },
  ],
}

const DRAFTS: Record<string, unknown> = {
  seller: {
    draft:
      '안녕하세요, 러너스컴퍼니예요. 배번과 기념 티셔츠는 환불 기한이 지난 뒤 신청 때 적어 주신 주소로 보내 드려요. 보내는 날짜는 [확인 필요: 발송 예정일]이에요.',
    references: ['주문 OD-20261030-0217', '키트 배송 상태(배송 준비)', '대회 키트 안내'],
    missing: [
      {
        title: '발송 날짜를 넣지 못했어요',
        reason: '발송 예정일이 대회 정보에 없어서 넣지 못했어요.',
      },
    ],
  },
  admin: {
    draft:
      '안녕하세요, 크루런이에요. 대회가 취소되면 결제하신 참가비 45,000원이 전액 크루런 예치금으로 돌아와요. 돌아오는 날짜는 [확인 필요: 환불 날짜]예요.',
    references: ['주문 OD-20261030-0217', '결제 수단(예치금)', '용어 「환불」 · 「환급」 뜻'],
    missing: [
      {
        title: '환불 날짜를 넣지 못했어요',
        reason: '환불 날짜를 확인할 정보가 없어서 넣지 못했어요.',
      },
    ],
  },
}

/** handlers.ts에 `...aiHandlers`로 등록한다 */
export const aiHandlers = [
  ...chatHandlers('seller', SELLER_ANSWERS),
  ...chatHandlers('admin', ADMIN_ANSWERS),
  http.get('*/api/v1/ai/seller/briefing', () => ok(SELLER_BRIEFING, 'ok')),
  http.get('*/api/v1/ai/admin/briefing', () => ok(ADMIN_BRIEFING, 'ok')),
  http.post('*/api/v1/ai/:screen/inquiries/:inquiryId/draft', ({ params }) => {
    const draft = DRAFTS[String(params.screen)]
    return draft ? ok(draft, 'ok') : fail(404, 'NOT_FOUND', '화면 종류가 올바르지 않습니다.')
  }),
]
