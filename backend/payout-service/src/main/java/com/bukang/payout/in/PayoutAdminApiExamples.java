package com.bukang.payout.in;

/**
 * ApiV1PayoutAdminController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 */
final class PayoutAdminApiExamples {
	static final String CREATE_FAILURES_SUCCESS = """
		{
			"status": 200,
			"message": "이상 감지 기록을 조회했습니다.",
			"data": {
				"mismatchCount": 2,
				"autoFilledCount": 1,
				"orderLookupFailureCount": 1,
				"records": {
					"content": [
						{
							"id": 3,
							"recordedAt": "2026-11-06T03:00:12",
							"runDate": "2026-11-06",
							"payoutStage": "최종_정산",
							"sellerId": 1,
							"raceId": 10,
							"raceCourseId": 4,
							"payoutTargetDate": "2026-11-05",
							"failureType": "대사_불일치",
							"actionTaken": "지급보류",
							"note": "더 들어감 2장 (코스 4: 2장)"
						}
					],
					"page": 0,
					"size": 50,
					"totalElements": 1,
					"totalPages": 1
				}
			}
		}
		""";

	static final String CREATE_FAILURES_EMPTY = """
		{
			"status": 200,
			"message": "이상 감지 기록을 조회했습니다.",
			"data": {
				"mismatchCount": 0,
				"autoFilledCount": 0,
				"orderLookupFailureCount": 0,
				"records": { "content": [], "page": 0, "size": 50, "totalElements": 0, "totalPages": 0 }
			}
		}
		""";

	static final String FAILURE_PERIOD_TOO_LONG = """
		{ "status": 400, "message": "이상 감지 기록은 최대 1개월까지 조회할 수 있습니다.", "data": null }
		""";

	static final String PERIOD_REVERSED = """
		{ "status": 400, "message": "조회 시작이 끝보다 늦습니다.", "data": null }
		""";

	static final String INVALID_PAGE = """
		{ "status": 400, "message": "페이지 번호는 0 이상이어야 합니다.", "data": null }
		""";

	static final String INVALID_PAGE_SIZE = """
		{ "status": 400, "message": "페이지 크기는 1 이상 100 이하여야 합니다.", "data": null }
		""";

	static final String MISSING_PARAMETER = """
		{ "status": 400, "message": "필수 요청 값이 없습니다.", "data": null }
		""";

	private PayoutAdminApiExamples() {
	}
}
