package com.bukang.order.in;

/**
 * ApiV1CartController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 */
final class CartApiExamples {
	static final String FIND_SUCCESS = """
		{
			"status": 200,
			"message": "장바구니를 조회했습니다.",
			"data": {
				"memberId": 1,
				"items": [
					{
						"id": 1,
						"createDate": "2026-10-12T10:15:30",
						"raceCourseId": 1,
						"participationId": 5,
						"raceId": 1,
						"participantName": "김러너"
					}
				]
			}
		}
		""";

	static final String FIND_EMPTY = """
		{ "status": 200, "message": "장바구니를 조회했습니다.", "data": { "memberId": 1, "items": [] } }
		""";

	static final String ADD_SUCCESS = """
		{
			"status": 201,
			"message": "장바구니에 담았습니다.",
			"data": {
				"id": 1,
				"createDate": "2026-10-12T10:15:30",
				"raceCourseId": 1,
				"participationId": 5,
				"raceId": 1,
				"participantName": "김러너"
			}
		}
		""";

	static final String ADD_ALREADY = """
		{
			"status": 200,
			"message": "이미 장바구니에 담긴 참가권입니다.",
			"data": {
				"id": 1,
				"createDate": "2026-10-12T10:15:30",
				"raceCourseId": 1,
				"participationId": 5,
				"raceId": 1,
				"participantName": "김러너"
			}
		}
		""";

	static final String REMOVE_SUCCESS = """
		{
			"status": 200,
			"message": "장바구니에서 뺐습니다.",
			"data": {
				"memberId": 1,
				"items": []
			}
		}
		""";

	static final String INVALID_INPUT = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "담을 종목을 선택해주세요.", "data": null }
		""";

	static final String INVALID_BODY = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "요청 본문 형식이 올바르지 않습니다.", "data": null }
		""";

	static final String INVALID_VALUE_TYPE = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "요청 값의 형식이 올바르지 않습니다.", "data": null }
		""";

	static final String INVALID_MEMBER_ID = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "회원 ID가 올바르지 않습니다.", "data": null }
		""";

	static final String LOGIN_REQUIRED = """
		{ "status": 401, "code": "LOGIN_REQUIRED", "message": "로그인이 필요합니다.", "data": null }
		""";

	static final String NOT_OWNER = """
		{ "status": 403, "code": "ORDER_CART_ITEM_FORBIDDEN", "message": "본인의 장바구니 항목만 뺄 수 있습니다.", "data": null }
		""";

	static final String COURSE_NOT_FOUND = """
		{ "status": 404, "code": "ORDER_COURSE_NOT_FOUND", "message": "존재하지 않는 종목입니다.", "data": null }
		""";

	static final String ITEM_NOT_FOUND = """
		{ "status": 404, "code": "ORDER_CART_ITEM_NOT_FOUND", "message": "장바구니에 없는 항목입니다.", "data": null }
		""";

	static final String APPLICATION_CLOSED = """
		{ "status": 409, "code": "ORDER_APPLICATION_CLOSED", "message": "접수가 마감된 종목은 담을 수 없습니다.", "data": null }
		""";

	static final String CONFLICT = """
		{ "status": 409, "code": "ORDER_CART_CONFLICT", "message": "장바구니에 담는 요청이 겹쳤습니다. 다시 시도해주세요.", "data": null }
		""";

	static final String CHECKOUT_SUCCESS = """
		{
			"status": 200,
			"message": "참가권 1건 중 1건의 자리를 확보했습니다. 결제 기한 안에 결제해주세요.",
			"data": {
				"orderedCount": 1,
				"results": [
					{
						"participationId": 11,
						"outcome": "CREATED",
						"message": "자리를 확보했습니다. 결제 기한 안에 결제해주세요.",
						"order": {
							"id": 1,
							"createDate": "2026-10-13T10:00:00",
							"orderNo": "OD-20261013-0001",
							"buyerId": 1,
							"raceId": 1,
							"status": "CREATED",
							"totalAmount": 50000,
							"canceledAmount": 0,
							"paymentDueAt": "2026-10-13T10:10:00",
							"paidAt": null,
							"shippingAddress": {
								"recipientName": "김러너",
								"recipientPhone": "010-1234-5678",
								"zipcode": "04700",
								"address": "서울 성동구 왕십리로 00",
								"addressDetail": "101동 1001호"
							},
							"items": [
								{
									"id": 1,
									"participationId": 11,
									"raceCourseId": 1,
									"sellerId": 1,
									"itemName": "2026 서울 하프 마라톤 10K",
									"unitPrice": 50000,
									"quantity": 1,
									"amount": 50000,
									"status": "ORDERED"
								}
							]
						}
					}
				]
			}
		}
		""";

	static final String CHECKOUT_NONE = """
		{
			"status": 200,
			"message": "주문한 참가권이 없습니다. 항목별 결과를 확인해주세요.",
			"data": {
				"orderedCount": 0,
				"results": [
					{
						"participationId": 12,
						"outcome": "SOLD_OUT",
						"code": "ORDER_SOLD_OUT",
						"message": "남은 자리가 없습니다. 참가 신청서를 다시 작성해 신청할 수 있습니다.",
						"order": null
					}
				]
			}
		}
		""";

	static final String CHECKOUT_EMPTY = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "결제할 참가 신청을 선택해주세요.", "data": null }
		""";

	static final String ONE_COURSE_ONLY = """
		{ "status": 409, "code": "ORDER_CART_ONE_COURSE_ONLY",
			"message": "장바구니에는 한 종목만 담을 수 있습니다. 담긴 종목을 뺀 뒤 담아주세요.", "data": null }
		""";

	static final String CHECKOUT_TOO_MANY = """
		{ "status": 400, "code": "INVALID_REQUEST",
			"message": "장바구니에는 한 종목만 담을 수 있어 참가 신청 1건만 결제할 수 있습니다.", "data": null }
		""";

	static final String CHECKOUT_INVALID_ID = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "참가 신청 ID가 올바르지 않습니다.", "data": null }
		""";

	static final String MISSING_IDEMPOTENCY_KEY = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "필수 요청 헤더(Idempotency-Key)가 없습니다.", "data": null }
		""";

	static final String INVALID_IDEMPOTENCY_KEY = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "Idempotency-Key는 1~64자로 보내주세요.", "data": null }
		""";

	static final String PARTICIPATION_NOT_OWNER = """
		{ "status": 403, "code": "ORDER_PARTICIPATION_FORBIDDEN", "message": "본인의 참가 신청만 결제할 수 있습니다.", "data": null }
		""";

	static final String PARTICIPATION_NOT_FOUND = """
		{ "status": 404, "code": "ORDER_PARTICIPATION_NOT_FOUND", "message": "존재하지 않는 참가 신청입니다.", "data": null }
		""";

	private CartApiExamples() {
	}
}
