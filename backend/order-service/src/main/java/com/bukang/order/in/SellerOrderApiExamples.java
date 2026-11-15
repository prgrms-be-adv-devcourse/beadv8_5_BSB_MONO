package com.bukang.order.in;

// 판매자 주문 목록·파티별 내역 API 응답 예시 (Swagger, BUK-227)
final class SellerOrderApiExamples {
	static final String ORDERS_SUCCESS = """
		{
			"status": 200,
			"message": "주문 목록을 조회했습니다.",
			"data": {
				"orders": [
					{
						"orderItemId": 12,
						"orderId": 10,
						"orderNo": "OD-20261016-000C",
						"orderedAt": "2026-10-16T10:15:00",
						"paidAt": "2026-10-16T10:17:30",
						"raceId": 1,
						"raceCourseId": 1,
						"courseName": "10K",
						"status": "PAID",
						"participantName": "김러너",
						"tshirtSize": null,
						"kitOptions": [ { "kitOptionId": 12, "name": "T-shirt", "value": "L" } ],
						"partyId": 3
					}
				],
				"page": 0,
				"size": 20,
				"totalElements": 1,
				"totalPages": 1
			}
		}
		""";

	static final String PARTIES_SUCCESS = """
		{
			"status": 200,
			"message": "파티별 내역을 조회했습니다.",
			"data": {
				"parties": [
					{
						"partyId": 3,
						"crewId": 2,
						"raceCourseId": 1,
						"status": "PAYING",
						"appliedCount": 5,
						"paidCount": 4,
						"unpaidCount": 1,
						"paymentDueAt": "2026-10-28T18:00:00"
					}
				],
				"page": 0,
				"size": 20,
				"totalElements": 1,
				"totalPages": 1
			}
		}
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

	static final String PERIOD_REVERSED = """
		{ "status": 400, "code": "ORDER_INVALID_SEARCH_PERIOD", "message": "조회 시작일이 종료일보다 늦습니다.", "data": null }
		""";

	static final String PERIOD_TOO_LONG = """
		{ "status": 400, "code": "ORDER_INVALID_SEARCH_PERIOD", "message": "조회 기간은 한 번에 12개월까지입니다.", "data": null }
		""";

	static final String MISSING_RACE = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "필수 요청 값(raceId)이 없습니다.", "data": null }
		""";

	static final String LOGIN_REQUIRED = """
		{ "status": 401, "code": "LOGIN_REQUIRED", "message": "로그인이 필요합니다.", "data": null }
		""";

	static final String NOT_SELLER = """
		{ "status": 403, "code": "ORDER_SELLER_FORBIDDEN", "message": "승인된 판매자만 쓸 수 있습니다.", "data": null }
		""";

	static final String OTHER_SELLER_PARTY = """
		{ "status": 403, "code": "ORDER_SELLER_FORBIDDEN", "message": "본인 대회의 파티만 볼 수 있습니다.", "data": null }
		""";

	static final String COURSE_NOT_FOUND = """
		{ "status": 404, "code": "ORDER_COURSE_NOT_FOUND", "message": "존재하지 않는 종목입니다.", "data": null }
		""";

	static final String SELLER_UNAVAILABLE = """
		{ "status": 503, "code": "ORDER_EXTERNAL_SERVICE_UNAVAILABLE",
			"message": "판매자 기능을 아직 쓸 수 없습니다. 잠시 뒤 다시 시도해주세요.", "data": null }
		""";

	private SellerOrderApiExamples() {
	}
}
