package com.bukang.product.in;

/**
 * InternalV1RaceScheduleController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 */
final class RaceScheduleInternalApiExamples {
	static final String SCHEDULES_SUCCESS = """
		{
			"status": 200,
			"message": "정산 대상 대회를 조회했습니다.",
			"data": [
				{
					"raceId": 1,
					"raceName": "2026 용감한 쿠키RUN in 서울",
					"raceDate": "2026-11-01T08:00:00",
					"refundDeadline": "2026-10-31"
				}
			]
		}
		""";

	static final String SCHEDULES_EMPTY = """
		{
			"status": 200,
			"message": "정산 대상 대회를 조회했습니다.",
			"data": []
		}
		""";

	static final String MISSING_FROM = """
		{ "status": 400, "message": "필수 요청 값(from)이 없습니다.", "data": null }
		""";

	static final String INVALID_VALUE_TYPE = """
		{ "status": 400, "message": "요청 값의 형식이 올바르지 않습니다.", "data": null }
		""";

	static final String FROM_AFTER_TO = """
		{ "status": 400, "message": "조회 시작일이 종료일보다 늦습니다.", "data": null }
		""";

	static final String PERIOD_TOO_LONG = """
		{ "status": 400, "message": "대회는 한 번에 31일까지 조회할 수 있습니다.", "data": null }
		""";

	private RaceScheduleInternalApiExamples() {
	}
}
