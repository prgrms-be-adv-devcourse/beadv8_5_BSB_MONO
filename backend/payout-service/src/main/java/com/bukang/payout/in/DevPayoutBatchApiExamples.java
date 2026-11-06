package com.bukang.payout.in;

/**
 * DevV1PayoutBatchController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 */
final class DevPayoutBatchApiExamples {
	static final String CREATE_SUCCESS = """
		{
			"status": 200,
			"message": "정산 생성일 배치를 실행했습니다.",
			"data": {
				"fromRaceDate": "2026-10-06", "toRaceDate": "2026-10-12", "created": 2, "skipped": 0, "failed": 0
			}
		}
		""";

	static final String PAY_SUCCESS = """
		{ "status": 200, "message": "지급 배치를 실행했습니다.",
			"data": { "today": "2026-11-20", "paid": 2, "failed": 0, "unknown": 0, "mismatch": 0 } }
		""";

	static final String INVALID_VALUE_TYPE = """
		{ "status": 400, "message": "요청 값의 형식이 올바르지 않습니다.", "data": null }
		""";

	private DevPayoutBatchApiExamples() {
	}
}
