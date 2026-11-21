import { http, HttpResponse } from 'msw'

import type { SellerKitDto } from '@/shared/api/generated/order/order.schemas'

type MockKit = SellerKitDto & { orderItemId: number; beforeRefundDeadline?: boolean }

function createMockKits(): MockKit[] {
  const address = (text: string) => ({
    recipientName: '김러너',
    recipientPhone: '010-1234-5678',
    zipcode: '04700',
    address: text,
    addressDetail: '101동 1001호',
  })
  const base = { orderItemStatus: 'PAID', raceId: 1, raceCourseId: 1 } as const
  return [
    {
      ...base,
      orderItemId: 101,
      orderNo: 'OD-20261004-0101',
      courseName: '10K',
      participantName: '달리는곰',
      tshirtSize: 'M',
      shippingAddress: address('서울 마포구 월드컵로 00'),
      kitDeliveryStatus: 'PREPARING',
    },
    {
      ...base,
      orderItemId: 117,
      orderNo: 'OD-20261004-0117',
      courseName: '10K',
      participantName: '새벽6시',
      tshirtSize: 'L',
      shippingAddress: address('경기 성남시 분당구 00'),
      kitDeliveryStatus: 'PREPARING',
    },
    {
      ...base,
      orderItemId: 125,
      orderNo: 'OD-20261005-0125',
      courseName: '하프',
      participantName: '주말러너',
      tshirtSize: 'L',
      shippingAddress: address('인천 연수구 송도동 00'),
      kitDeliveryStatus: 'PREPARING',
      beforeRefundDeadline: true,
    },
    {
      ...base,
      orderItemId: 93,
      orderNo: 'OD-20261003-0093',
      courseName: '하프',
      participantName: '한강토끼',
      tshirtSize: 'M',
      shippingAddress: address('서울 송파구 올림픽로 00'),
      kitDeliveryStatus: 'SHIPPING',
      kitCarrier: 'CJ대한통운',
      kitTrackingNumber: '684912345678',
      kitShippedAt: '2026-10-14T10:00:00',
    },
    {
      ...base,
      orderItemId: 71,
      orderNo: 'OD-20261002-0071',
      courseName: '10K',
      participantName: '오르막왕',
      tshirtSize: 'S',
      shippingAddress: address('부산 해운대구 우동 00'),
      kitDeliveryStatus: 'DELIVERED',
      kitCarrier: 'CJ대한통운',
      kitTrackingNumber: '684900000071',
      kitShippedAt: '2026-10-10T10:00:00',
      kitDeliveredAt: '2026-10-12T15:00:00',
    },
    {
      // 보낸 뒤 취소된 참가권. 서버는 보낸 키트를 목록에 남기고 주문 상품 상태만 취소로 둔다
      ...base,
      orderItemStatus: 'CANCELED',
      orderItemId: 64,
      orderNo: 'OD-20261001-0064',
      courseName: '하프',
      participantName: '비오는날',
      tshirtSize: 'L',
      shippingAddress: address('대구 수성구 범어동 00'),
      kitDeliveryStatus: 'DELIVERED',
      kitCarrier: 'CJ대한통운',
      kitTrackingNumber: '684900000064',
      kitShippedAt: '2026-10-09T10:00:00',
      kitDeliveredAt: '2026-10-11T15:00:00',
    },
  ]
}

let kits = createMockKits()

/** 테스트 사이에 발송 · 배송 완료 상태가 넘어가지 않게 처음으로 돌린다. */
export function resetMockKitsForTesting() {
  kits = createMockKits()
}

const NOW = () => new Date(Date.now() + 9 * 60 * 60 * 1000).toISOString().slice(0, 19)

function toDto(kit: MockKit): SellerKitDto {
  const dto: Partial<MockKit> = { ...kit }
  delete dto.beforeRefundDeadline
  return dto
}

function fail(status: number, code: string, message: string) {
  return HttpResponse.json({ status, code, message, data: null }, { status })
}

function findKit(rawId: string | readonly string[] | undefined) {
  return kits.find((kit) => String(kit.orderItemId) === rawId)
}

/**
 * 판매자 키트 배송 목. 서버 규칙을 흉내 내 발송하면 배송 중으로, 배송 완료를 누르면 배송 완료로 바뀌고 건수도 따라 바뀐다.
 * - 환불 기한 전 주문(125)은 발송하면 409 ORDER_KIT_NOT_SHIPPABLE
 * - 이미 보낸 키트를 다시 보내거나, 보내기 전에 배송 완료로 바꾸면 409 ORDER_KIT_NOT_SHIPPABLE
 * - 송장 고치기는 배송 중일 때만 된다. 아니면 409 ORDER_KIT_NOT_SHIPPABLE
 * - 없는 주문 상품은 404 ORDER_ITEM_NOT_FOUND, 택배사 · 송장 번호가 비면 400 INVALID_REQUEST
 * 생성된 핸들러보다 앞에 둬서 먼저 걸리게 한다.
 */
export const sellerKitHandlers = [
  http.get('*/api/v1/order/seller/kits/summary', () => {
    const count = (status: string) => kits.filter((k) => k.kitDeliveryStatus === status).length
    return HttpResponse.json({
      status: 200,
      message: '키트 배송 건수를 조회했습니다.',
      data: {
        preparing: count('PREPARING'),
        shipping: count('SHIPPING'),
        delivered: count('DELIVERED'),
        beforeRefundDeadline: kits.filter(
          (k) => k.kitDeliveryStatus === 'PREPARING' && k.beforeRefundDeadline,
        ).length,
      },
    })
  }),
  // 내려받기: 목록과 같은 조건으로 거른 CSV(UTF-8 BOM). 사유가 비면 서버처럼 400
  http.post('*/api/v1/order/seller/kits/export', async ({ request }) => {
    const body = (await request.json()) as { raceId?: number; status?: string; reason?: string }
    if (!body.reason?.trim()) {
      return HttpResponse.json(
        { status: 400, message: '내려받는 사유를 입력해주세요.', data: null },
        { status: 400 },
      )
    }
    const found = kits.filter(
      (k) =>
        (!body.status || k.kitDeliveryStatus === body.status) &&
        (!body.raceId || k.raceId === body.raceId),
    )
    const lines = found.map((k) =>
      [
        k.orderNo,
        k.courseName,
        k.participantName,
        k.kitDeliveryStatus,
        k.kitTrackingNumber ?? '',
      ].join(','),
    )
    const csv = ['\uFEFF주문번호,종목,참가자 이름,배송 상태,운송장 번호', ...lines].join('\r\n')
    return new HttpResponse(csv + '\r\n', { headers: { 'Content-Type': 'text/csv;charset=UTF-8' } })
  }),
  http.get('*/api/v1/order/seller/kits', ({ request }) => {
    const params = new URL(request.url).searchParams
    const status = params.get('status')
    const raceId = params.get('raceId')
    const found = kits.filter(
      (k) =>
        (!status || k.kitDeliveryStatus === status) && (!raceId || String(k.raceId) === raceId),
    )
    return HttpResponse.json({
      status: 200,
      message: '키트 주문 목록을 조회했습니다.',
      data: {
        kits: found.map(toDto),
        page: 0,
        size: Number(params.get('size') ?? 20),
        totalElements: found.length,
        totalPages: found.length > 0 ? 1 : 0,
      },
    })
  }),
  http.post('*/api/v1/order/seller/kits/:orderItemId/shipment', async ({ params, request }) => {
    const kit = findKit(params.orderItemId)
    if (!kit) return fail(404, 'ORDER_ITEM_NOT_FOUND', '주문에 없는 상품입니다.')
    const body = (await request.json().catch(() => ({}))) as {
      carrier?: string
      trackingNumber?: string
    }
    if (!body.carrier?.trim()) return fail(400, 'INVALID_REQUEST', '택배사를 입력해주세요.')
    if (!body.trackingNumber?.trim()) {
      return fail(400, 'INVALID_REQUEST', '송장 번호를 입력해주세요.')
    }
    if (kit.kitDeliveryStatus !== 'PREPARING') {
      return fail(409, 'ORDER_KIT_NOT_SHIPPABLE', '이미 발송한 키트입니다.')
    }
    if (kit.beforeRefundDeadline) {
      return fail(
        409,
        'ORDER_KIT_NOT_SHIPPABLE',
        '아직 환불할 수 있는 참가권이라 발송할 수 없습니다(환불 기한: 2026-12-10).',
      )
    }
    kit.kitDeliveryStatus = 'SHIPPING'
    kit.kitCarrier = body.carrier
    kit.kitTrackingNumber = body.trackingNumber
    kit.kitShippedAt = NOW()
    return HttpResponse.json({
      status: 200,
      message: '키트를 발송 처리했습니다.',
      data: toItem(kit),
    })
  }),
  http.put('*/api/v1/order/seller/kits/:orderItemId/shipment', async ({ params, request }) => {
    const kit = findKit(params.orderItemId)
    if (!kit) return fail(404, 'ORDER_ITEM_NOT_FOUND', '주문에 없는 상품입니다.')
    const body = (await request.json().catch(() => ({}))) as {
      carrier?: string
      trackingNumber?: string
    }
    if (!body.carrier?.trim()) return fail(400, 'INVALID_REQUEST', '택배사를 입력해주세요.')
    if (!body.trackingNumber?.trim()) {
      return fail(400, 'INVALID_REQUEST', '송장 번호를 입력해주세요.')
    }
    if (kit.kitDeliveryStatus !== 'SHIPPING') {
      return fail(409, 'ORDER_KIT_NOT_SHIPPABLE', '배송 중인 키트만 송장을 고칠 수 있습니다.')
    }
    kit.kitCarrier = body.carrier
    kit.kitTrackingNumber = body.trackingNumber
    return HttpResponse.json({ status: 200, message: '송장 번호를 고쳤습니다.', data: toItem(kit) })
  }),
  http.post('*/api/v1/order/seller/kits/:orderItemId/delivery', ({ params }) => {
    const kit = findKit(params.orderItemId)
    if (!kit) return fail(404, 'ORDER_ITEM_NOT_FOUND', '주문에 없는 상품입니다.')
    if (kit.kitDeliveryStatus !== 'SHIPPING') {
      return fail(409, 'ORDER_KIT_NOT_SHIPPABLE', '아직 발송하지 않은 키트입니다.')
    }
    kit.kitDeliveryStatus = 'DELIVERED'
    kit.kitDeliveredAt = NOW()
    return HttpResponse.json({
      status: 200,
      message: '키트를 배송 완료로 바꿨습니다.',
      data: toItem(kit),
    })
  }),
]

/** 발송 · 배송 완료 응답의 주문 상품 */
function toItem(kit: MockKit) {
  return {
    id: kit.orderItemId,
    participationId: 30,
    raceCourseId: kit.raceCourseId,
    sellerId: 7,
    itemName: kit.courseName,
    unitPrice: 50000,
    quantity: 1,
    amount: 50000,
    status: kit.orderItemStatus,
    kitDeliveryStatus: kit.kitDeliveryStatus,
    kitCarrier: kit.kitCarrier ?? null,
    kitTrackingNumber: kit.kitTrackingNumber ?? null,
    kitShippedAt: kit.kitShippedAt ?? null,
    kitDeliveredAt: kit.kitDeliveredAt ?? null,
  }
}
