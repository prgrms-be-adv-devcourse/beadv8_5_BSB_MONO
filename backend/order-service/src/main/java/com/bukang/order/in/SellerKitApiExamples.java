package com.bukang.order.in;

// 판매자 키트 배송 API 응답 예시 (Swagger, PRO-197)
final class SellerKitApiExamples {
	static final String LIST_SUCCESS = """
		{
			"status": 200,
			"message": "키트 주문 목록을 조회했습니다.",
			"data": {
				"kits": [
					{
						"orderItemId": 12,
						"orderNo": "OD-20261016-000C",
						"orderItemStatus": "PAID",
						"raceId": 1,
						"raceCourseId": 1,
						"courseName": "10K",
						"participantName": "김러너",
						"tshirtSize": "L",
						"shippingAddress": {
							"recipientName": "김러너",
							"recipientPhone": "010-1234-5678",
							"zipcode": "04700",
							"address": "서울 성동구 왕십리로 00",
							"addressDetail": "101동 1001호"
						},
						"kitDeliveryStatus": "PREPARING",
						"kitCarrier": null,
						"kitTrackingNumber": null,
						"kitShippedAt": null,
						"kitDeliveredAt": null
					}
				],
				"page": 0,
				"size": 20,
				"totalElements": 1,
				"totalPages": 1
			}
		}
		""";

	static final String SUMMARY_SUCCESS = """
		{
			"status": 200,
			"message": "키트 배송 건수를 조회했습니다.",
			"data": { "preparing": 12, "shipping": 30, "delivered": 158, "beforeRefundDeadline": 41 }
		}
		""";

	static final String SHIP_SUCCESS = """
		{
			"status": 200,
			"message": "키트를 발송 처리했습니다.",
			"data": {
				"id": 12,
				"participationId": 30,
				"raceCourseId": 1,
				"sellerId": 7,
				"itemName": "10K",
				"unitPrice": 50000,
				"quantity": 1,
				"amount": 50000,
				"status": "PAID",
				"kitDeliveryStatus": "SHIPPING",
				"kitCarrier": "CJ대한통운",
				"kitTrackingNumber": "684912345678",
				"kitShippedAt": "2026-12-11T10:00:00",
				"kitDeliveredAt": null
			}
		}
		""";

	static final String DELIVER_SUCCESS = """
		{
			"status": 200,
			"message": "키트를 배송 완료로 바꿨습니다.",
			"data": {
				"id": 12,
				"participationId": 30,
				"raceCourseId": 1,
				"sellerId": 7,
				"itemName": "10K",
				"unitPrice": 50000,
				"quantity": 1,
				"amount": 50000,
				"status": "PAID",
				"kitDeliveryStatus": "DELIVERED",
				"kitCarrier": "CJ대한통운",
				"kitTrackingNumber": "684912345678",
				"kitShippedAt": "2026-12-11T10:00:00",
				"kitDeliveredAt": "2026-12-13T15:00:00"
			}
		}
		""";

	static final String INVALID_SHIPMENT = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "송장 번호를 입력해주세요.", "data": null }
		""";

	static final String INVALID_PAGE = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "한 번에 100개까지 조회할 수 있습니다.", "data": null }
		""";

	static final String INVALID_FORMAT = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "요청 값의 형식이 올바르지 않습니다.", "data": null }
		""";

	static final String INVALID_MEMBER_ID = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "회원 ID가 올바르지 않습니다.", "data": null }
		""";

	static final String LOGIN_REQUIRED = """
		{ "status": 401, "code": "LOGIN_REQUIRED", "message": "로그인이 필요합니다.", "data": null }
		""";

	static final String NOT_SELLER = """
		{ "status": 403, "code": "ORDER_SELLER_FORBIDDEN", "message": "승인된 판매자만 쓸 수 있습니다.", "data": null }
		""";

	static final String NOT_MY_RACE = """
		{ "status": 403, "code": "ORDER_FORBIDDEN", "message": "본인 대회의 참가권만 배송 처리할 수 있습니다.", "data": null }
		""";

	static final String ITEM_NOT_FOUND = """
		{ "status": 404, "code": "ORDER_ITEM_NOT_FOUND", "message": "주문에 없는 상품입니다.", "data": null }
		""";

	static final String BEFORE_REFUND_DEADLINE = """
		{ "status": 409, "code": "ORDER_KIT_NOT_SHIPPABLE",
			"message": "아직 환불할 수 있는 참가권이라 발송할 수 없습니다(환불 기한: 2026-12-10).", "data": null }
		""";

	static final String ALREADY_SHIPPED = """
		{ "status": 409, "code": "ORDER_KIT_NOT_SHIPPABLE", "message": "이미 발송한 키트입니다.", "data": null }
		""";

	static final String NOT_SHIPPED_YET = """
		{ "status": 409, "code": "ORDER_KIT_NOT_SHIPPABLE", "message": "아직 발송하지 않은 키트입니다.", "data": null }
		""";

	static final String SELLER_UNAVAILABLE = """
		{ "status": 503, "code": "ORDER_EXTERNAL_SERVICE_UNAVAILABLE",
			"message": "판매자 기능을 아직 쓸 수 없습니다. 잠시 뒤 다시 시도해주세요.", "data": null }
		""";

	private SellerKitApiExamples() {
	}
}
