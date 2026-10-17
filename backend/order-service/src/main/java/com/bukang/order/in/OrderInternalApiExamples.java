package com.bukang.order.in;

/**
 * InternalV1OrderController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 */
final class OrderInternalApiExamples {
	static final String SOLD_ENTRIES_SUCCESS = """
		{
			"status": 200,
			"message": "판매 내역을 조회했습니다.",
			"data": [
				{ "orderItemId": 1, "sellerId": 3, "raceId": 5, "raceCourseId": 11, "raceDate": "2026-11-09",
					"price": 50000 },
				{ "orderItemId": 4, "sellerId": 3, "raceId": 7, "raceCourseId": 14, "raceDate": "2026-11-15",
					"price": 50000 }
			]
		}
		""";

	static final String SOLD_ENTRIES_EMPTY = """
		{ "status": 200, "message": "판매 내역을 조회했습니다.", "data": [] }
		""";

	static final String MISSING_FROM = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "필수 요청 값(from)이 없습니다.", "data": null }
		""";

	static final String INVALID_VALUE_TYPE = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "요청 값의 형식이 올바르지 않습니다.", "data": null }
		""";

	static final String FROM_AFTER_TO = """
		{ "status": 400, "code": "ORDER_INVALID_SOLD_ENTRY_PERIOD", "message": "조회 시작일이 종료일보다 늦습니다.", "data": null }
		""";

	static final String PERIOD_TOO_LONG = """
		{ "status": 400, "code": "ORDER_INVALID_SOLD_ENTRY_PERIOD",
			"message": "판매 내역은 한 번에 31일까지 조회할 수 있습니다.", "data": null }
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
}
