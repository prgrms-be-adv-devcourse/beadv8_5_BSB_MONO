package com.bukang.payout.in;

/**
 * ApiV1PayoutClaimController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 */
final class PayoutClaimApiExamples {
	static final String CONFIRM_SUCCESS = """
		{
			"status": 200,
			"message": "청구 입금을 확인했습니다.",
			"data": {
				"id": 1,
				"sellerId": 1,
				"raceId": 7,
				"amount": 105500,
				"startDate": "2026-12-14",
				"status": "CONFIRMED",
				"confirmedBy": 99,
				"confirmedAt": "2026-12-20T10:00:00"
			}
		}
		""";

	static final String CLAIM_NOT_FOUND = """
		{ "status": 404, "message": "청구를 찾을 수 없습니다.", "data": null }
		""";

	static final String ALREADY_CONFIRMED = """
		{ "status": 409, "message": "이미 입금을 확인한 청구입니다.", "data": null }
		""";

	private PayoutClaimApiExamples() {
	}
}
