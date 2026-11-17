package com.bukang.order.in;

/**
 * InternalV1OrderController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 */
final class OrderInternalApiExamples {
	static final String SOLD_ENTRIES_SUCCESS = """
		{
			"status": 200,
			"message": "판매 내역을 조회했습니다.",
			"data": {
				"raceId": 5, "raceDate": "2026-11-09", "sellerId": 3,
				"courses": [
					{ "raceCourseId": 11, "price": 50000, "paidIds": [1, 2], "blockingIds": [] },
					{ "raceCourseId": 12, "price": 70000, "paidIds": [3], "blockingIds": [4] }
				]
			}
		}
		""";

	static final String SOLD_ENTRIES_EMPTY = """
		{ "status": 200, "message": "판매 내역을 조회했습니다.",
			"data": { "raceId": 5, "raceDate": null, "sellerId": null, "courses": [] } }
		""";

	static final String INVALID_VALUE_TYPE = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "요청 값의 형식이 올바르지 않습니다.", "data": null }
		""";

	static final String SOLD_SUMMARY_SUCCESS = """
		{
			"status": 200,
			"message": "판매 집계를 조회했습니다.",
			"data": [
				{ "sellerId": 3, "raceId": 5, "raceCourseId": 11, "priceSum": 150000, "ticketCount": 3 },
				{ "sellerId": 3, "raceId": 5, "raceCourseId": 12, "priceSum": 70000, "ticketCount": 2 }
			]
		}
		""";

	static final String SOLD_SUMMARY_EMPTY = """
		{ "status": 200, "message": "판매 집계를 조회했습니다.", "data": [] }
		""";

	static final String MISSING_RACE_ID = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "필수 요청 값(raceId)이 없습니다.", "data": null }
		""";

	static final String ACTIVE_TICKETS_SUCCESS = """
		{ "status": 200, "message": "끝나지 않은 참가권 수를 조회했습니다.", "data": { "count": 2 } }
		""";

	static final String ACTIVE_TICKETS_NONE = """
		{ "status": 200, "message": "끝나지 않은 참가권 수를 조회했습니다.", "data": { "count": 0 } }
		""";

	private OrderInternalApiExamples() {
	}

	static final String ORDER_LOOKUP_SUCCESS = """
		{
			"status": 200,
			"message": "주문 요약을 조회했습니다.",
			"data": {
				"memberId": 3, "orderNo": "OD-20261110-3F9A", "paymentAmount": 50000, "status": "PAID",
				"items": [
					{ "raceId": 5, "raceName": "2026 서울 하프 마라톤", "courseName": "10K",
						"kitSummary": "T-shirt L", "deliveryStatus": "PREPARING" }
				]
			}
		}
		""";

	static final String ORDER_LOOKUP_NO_KIT = """
		{
			"status": 200,
			"message": "주문 요약을 조회했습니다.",
			"data": {
				"memberId": 3, "orderNo": "OD-20261110-3F9B", "paymentAmount": 30000, "status": "CANCELED",
				"items": [
					{ "raceId": 5, "raceName": "2026 서울 하프 마라톤", "courseName": "5K",
						"kitSummary": null, "deliveryStatus": null }
				]
			}
		}
		""";

	static final String ORDER_NOT_FOUND = """
		{ "status": 404, "code": "ORDER_NOT_FOUND", "message": "존재하지 않는 주문입니다.", "data": null }
		""";
}
