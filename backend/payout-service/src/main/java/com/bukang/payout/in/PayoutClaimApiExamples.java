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
				"claimCode": "RC-20261215-0001",
				"sellerId": 1,
				"raceId": 7,
				"amount": 105500,
				"startDate": "2026-12-14",
				"dueDate": "2027-01-13",
				"status": "입금_확인",
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

	static final String CLAIMS_SUCCESS = """
		{
			"status": 200,
			"message": "청구 목록을 조회했습니다.",
			"data": {
				"summary": { "claimingCount": 1, "claimingAmount": 105500, "confirmedThisMonthCount": 1 },
				"claims": {
					"content": [
						{
							"id": 2,
							"claimCode": "RC-20261215-0002",
							"sellerId": 1,
							"raceId": 7,
							"amount": 105500,
							"startDate": "2026-12-14",
							"dueDate": "2027-01-13",
							"status": "청구_중",
							"confirmedBy": null,
							"confirmedAt": null
						},
						{
							"id": 1,
							"claimCode": "RC-20261215-0001",
							"sellerId": 3,
							"raceId": 7,
							"amount": 50000,
							"startDate": "2026-12-14",
							"dueDate": "2027-01-13",
							"status": "입금_확인",
							"confirmedBy": 99,
							"confirmedAt": "2026-12-20T10:00:00"
						}
					],
					"page": 0,
					"size": 50,
					"totalElements": 2,
					"totalPages": 1
				}
			}
		}
		""";

	static final String CLAIMS_EMPTY = """
		{
			"status": 200,
			"message": "청구 목록을 조회했습니다.",
			"data": {
				"summary": { "claimingCount": 0, "claimingAmount": 0, "confirmedThisMonthCount": 0 },
				"claims": { "content": [], "page": 0, "size": 50, "totalElements": 0, "totalPages": 0 }
			}
		}
		""";

	static final String INVALID_PAGE_SIZE = """
		{ "status": 400, "message": "페이지 크기는 1 이상 100 이하여야 합니다.", "data": null }
		""";

	private PayoutClaimApiExamples() {
	}
}
