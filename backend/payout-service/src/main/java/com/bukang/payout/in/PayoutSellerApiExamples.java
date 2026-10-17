package com.bukang.payout.in;

/**
 * ApiV1PayoutSellerController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 */
final class PayoutSellerApiExamples {
	static final String SUMMARY_SUCCESS = """
		{
			"status": 200,
			"message": "정산 요약을 조회했습니다.",
			"data": {
				"nextPayDate": "2026-10-20",
				"scheduledPayoutAmount": 94500,
				"heldCount": 1
			}
		}
		""";

	static final String PAYOUTS_SUCCESS = """
		{
			"status": 200,
			"message": "정산 내역을 조회했습니다.",
			"data": [
				{
					"id": 2,
					"payoutCode": "ST-20260926-0002",
					"raceId": 10,
					"raceName": "2026 한강 나이트런",
					"payoutStage": "FINAL",
					"payoutMonth": "2026-09",
					"salesAmount": 100000,
					"refundAmount": 0,
					"feeAmount": 5500,
					"advanceDeductionAmount": 40000,
					"payoutAmount": 54500,
					"status": "CALCULATED",
					"settledAt": "2026-09-26T03:00:00",
					"scheduledPayDate": "2026-10-20",
					"paidAt": null
				},
				{
					"id": 1,
					"payoutCode": "ST-20260916-0001",
					"raceId": 10,
					"raceName": "2026 한강 나이트런",
					"payoutStage": "FIRST",
					"payoutMonth": "2026-09",
					"salesAmount": 100000,
					"refundAmount": 0,
					"feeAmount": 0,
					"advanceDeductionAmount": 0,
					"payoutAmount": 40000,
					"status": "CALCULATED",
					"settledAt": "2026-09-16T03:00:00",
					"scheduledPayDate": "2026-10-20",
					"paidAt": null
				}
			]
		}
		""";

	static final String ITEMS_SUCCESS = """
		{
			"status": 200,
			"message": "건별 정산을 조회했습니다.",
			"data": {
				"content": [
					{
						"id": 1,
						"orderItemId": 100,
						"recordType": "SALE",
						"amount": 50000,
						"feeRate": 0.0550,
						"feeAmount": 2750,
						"targetDate": "2026-09-26",
						"payoutId": 1,
						"payoutCode": "ST-20260926-0001",
						"raceId": 10,
						"raceName": "2026 한강 나이트런",
						"payoutStatus": "CALCULATED",
						"scheduledPayDate": "2026-10-20"
					}
				],
				"page": 0,
				"size": 50,
				"totalElements": 1,
				"totalPages": 1
			}
		}
		""";

	static final String INVALID_PAGE = """
		{ "status": 400, "message": "페이지 번호는 0 이상이어야 합니다.", "data": null }
		""";

	static final String INVALID_PAGE_SIZE = """
		{ "status": 400, "message": "페이지 크기는 1 이상 100 이하여야 합니다.", "data": null }
		""";

	static final String HOLDS_SUCCESS = """
		{
			"status": 200,
			"message": "지급보류 내역을 조회했습니다.",
			"data": [
				{
					"id": 1,
					"holdCode": "HD-20261012-0001",
					"payoutId": 1,
					"payoutCode": "ST-20260926-0001",
					"raceId": 10,
					"raceName": "2026 한강 나이트런",
					"payoutAmount": 94500,
					"reasonType": "ACCOUNT_NOT_REGISTERED",
					"reason": "정산 계좌 등록 요청 중",
					"status": "HELD",
					"heldAt": "2026-10-12T10:00:00",
					"releasedAt": null
				}
			]
		}
		""";

	static final String PAYOUT_PERIOD_TOO_LONG = """
		{ "status": 400, "message": "정산 내역은 최대 12개월까지 조회할 수 있습니다.", "data": null }
		""";

	static final String DETAIL_PERIOD_TOO_LONG = """
		{ "status": 400, "message": "건별 정산·지급보류 내역은 최대 1개월까지 조회할 수 있습니다.", "data": null }
		""";

	static final String PERIOD_REVERSED = """
		{ "status": 400, "message": "조회 시작이 끝보다 늦습니다.", "data": null }
		""";

	static final String MISSING_PARAMETER = """
		{ "status": 400, "message": "필수 요청 값이 없습니다.", "data": null }
		""";

	static final String INVALID_VALUE_TYPE = """
		{ "status": 400, "message": "요청 값의 형식이 올바르지 않습니다.", "data": null }
		""";

	static final String LOGIN_REQUIRED = """
		{ "status": 401, "message": "로그인이 필요합니다.", "data": null }
		""";

	private PayoutSellerApiExamples() {
	}
}
