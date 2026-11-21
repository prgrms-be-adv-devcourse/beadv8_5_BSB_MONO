import { http, HttpResponse } from 'msw'

import type { SellerOrderDto, SellerPartyDto } from '@/shared/api/generated/order/order.schemas'

// 판매 현황(판매자 센터)의 참가자 · 파티별 목록과 명단 내려받기.
// 어느 대회를 골라도 같은 명단을 그 대회 것으로 돌려준다(대회마다 다른 데이터를 만들지 않는다).
// 명단 내려받기 사유에 「오류 확인」을 넣으면 서버 오류(503)를 돌려 실패 알림을 볼 수 있다.

const NAMES = ['김러너', '박달림', '이페이스', '최스플릿', '정케이던스', '한마라톤', '오페이서']
const COURSES = [
  { raceCourseId: 1, courseName: '하프' },
  { raceCourseId: 2, courseName: '10K' },
] as const
const SIZES = ['S', 'M', 'L', 'XL']
const SOCKS = ['230-250', '255-280']
const STATUSES = ['PAID', 'PAID', 'ORDERED', 'CANCELED', 'PAID'] as const
const PARTY_IDS = [1023, 1019, 1011, 1008]

function createOrders(): SellerOrderDto[] {
  return Array.from({ length: 120 }, (_, i) => {
    const course = COURSES[i % 3 === 2 ? 1 : 0]
    const status = STATUSES[i % STATUSES.length]
    const day = String(12 + (i % 18)).padStart(2, '0')
    const partyId = i % 4 === 1 || i % 4 === 2 ? PARTY_IDS[i % PARTY_IDS.length] : null
    return {
      orderItemId: 5000 + i,
      orderId: 4000 + i,
      orderNo: `OD-202610${day}-${String(231 + i).padStart(4, '0')}`,
      orderedAt: `2026-10-${day}T10:15:00`,
      paidAt: status === 'ORDERED' ? null : `2026-10-${day}T10:17:30`,
      raceCourseId: course.raceCourseId,
      courseName: course.courseName,
      status,
      participantName: NAMES[i % NAMES.length],
      tshirtSize: null,
      kitOptions: [
        { kitOptionId: 10 + (i % 4), name: '티셔츠', value: SIZES[i % SIZES.length] },
        { kitOptionId: 20 + (i % 2), name: '양말', value: SOCKS[i % 2] },
      ],
      partyId,
    }
  })
}

const PARTIES: SellerPartyDto[] = [
  {
    partyId: 1023,
    crewId: 2,
    raceCourseId: 1,
    status: 'PAYING',
    appliedCount: 4,
    paidCount: 3,
    unpaidCount: 1,
    paymentDueAt: '2026-10-28T18:00:00',
  },
  {
    partyId: 1019,
    crewId: 5,
    raceCourseId: 1,
    status: 'PAID',
    appliedCount: 5,
    paidCount: 5,
    unpaidCount: 0,
    paymentDueAt: null,
  },
  {
    partyId: 1011,
    crewId: 8,
    raceCourseId: 2,
    status: 'PAID',
    appliedCount: 3,
    paidCount: 3,
    unpaidCount: 0,
    paymentDueAt: null,
  },
  {
    partyId: 1008,
    crewId: 9,
    raceCourseId: 1,
    status: 'CLOSED',
    appliedCount: 4,
    paidCount: 2,
    unpaidCount: 2,
    paymentDueAt: null,
  },
]

const orders = createOrders()

function page<T>(rows: T[], params: URLSearchParams) {
  const size = Number(params.get('size') ?? 20)
  const index = Number(params.get('page') ?? 0)
  return {
    content: rows.slice(index * size, (index + 1) * size),
    page: index,
    size,
    totalElements: rows.length,
    totalPages: Math.ceil(rows.length / size),
  }
}

export const sellerOrderHandlers = [
  http.get('*/api/v1/order/seller/orders', ({ request }) => {
    const params = new URL(request.url).searchParams
    const raceId = Number(params.get('raceId') ?? 1)
    const keyword = params.get('keyword')?.trim()
    const partyId = params.get('partyId')
    const found = orders.filter(
      (o) =>
        (!keyword || o.participantName?.includes(keyword) || o.orderNo?.includes(keyword)) &&
        (!partyId || String(o.partyId) === partyId),
    )
    const { content, ...rest } = page(found, params)
    return HttpResponse.json({
      status: 200,
      message: '주문 목록을 조회했습니다.',
      data: { orders: content.map((o) => ({ ...o, raceId })), ...rest },
    })
  }),
  http.get('*/api/v1/order/seller/parties', ({ request }) => {
    const params = new URL(request.url).searchParams
    const partyId = params.get('partyId')
    const found = PARTIES.filter((p) => !partyId || String(p.partyId) === partyId)
    const { content, ...rest } = page(found, params)
    return HttpResponse.json({
      status: 200,
      message: '파티별 내역을 조회했습니다.',
      data: { parties: content, ...rest },
    })
  }),
  // 결제 완료(참가 확정)만 담는다. 사유가 비거나 200자를 넘으면 서버처럼 400
  http.post('*/api/v1/order/seller/orders/roster-export', async ({ request }) => {
    const body = (await request.json()) as {
      raceId?: number
      raceCourseId?: number
      reason?: string
    }
    const reason = body.reason?.trim() ?? ''
    if (!reason || reason.length > 200) {
      return HttpResponse.json(
        {
          status: 400,
          code: 'INVALID_REQUEST',
          message: reason ? '사유는 200자 이하로 입력해주세요.' : '내려받는 사유를 입력해주세요.',
          data: null,
        },
        { status: 400 },
      )
    }
    if (reason === '오류 확인') {
      return HttpResponse.json(
        {
          status: 503,
          code: 'ORDER_EXTERNAL_SERVICE_UNAVAILABLE',
          message: '판매자 기능을 아직 쓸 수 없습니다. 잠시 뒤 다시 시도해주세요.',
          data: null,
        },
        { status: 503 },
      )
    }
    const lines = orders
      .filter(
        (o) => o.status === 'PAID' && (!body.raceCourseId || o.raceCourseId === body.raceCourseId),
      )
      .map((o) =>
        [
          o.orderNo,
          o.courseName,
          o.participantName,
          '010-0000-0000',
          o.kitOptions?.map((k) => `${k.name}: ${k.value}`).join(' / '),
          o.partyId ?? '',
        ].join(','),
      )
    const csv = ['﻿주문번호,종목,참가자 이름,연락처,키트 옵션,파티 번호', ...lines].join('\n')
    return new HttpResponse(csv + '\n', { headers: { 'Content-Type': 'text/csv;charset=UTF-8' } })
  }),
]
