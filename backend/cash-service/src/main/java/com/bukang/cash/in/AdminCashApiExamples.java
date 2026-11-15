package com.bukang.cash.in;

/**
 * ApiV1AdminCashController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 * 로그인·형식 오류처럼 다른 캐시 API와 같은 응답은 CashApiExamples의 상수를 쓴다
 */
final class AdminCashApiExamples {
	static final String CHARGE_REVIEWS_SUCCESS = """
		{
			"status": 200,
			"message": "고액 충전 확인 목록을 조회했습니다.",
			"data": {
				"content": [
					{
						"memberId": 12,
						"nickname": "러너1",
						"joinedAt": "2026-11-10T09:00:00",
						"chargeAmount": 300000,
						"chargedAt": "2026-11-10T21:25:35",
						"joinWindowChargeTotal": 600000
					}
				],
				"page": 0,
				"size": 20,
				"totalElements": 1,
				"totalPages": 1
			}
		}
		""";

	static final String CHARGE_REVIEWS_EMPTY = """
		{
			"status": 200,
			"message": "고액 충전 확인 목록을 조회했습니다.",
			"data": {
				"content": [],
				"page": 0,
				"size": 20,
				"totalElements": 0,
				"totalPages": 0
			}
		}
		""";

	static final String NOT_ADMIN = """
		{ "status": 403, "code": "CASH_ADMIN_FORBIDDEN", "message": "관리자만 이용할 수 있습니다.", "data": null }
		""";

	static final String MEMBER_SERVICE_UNAVAILABLE = """
		{ "status": 503, "code": "CASH_MEMBER_SERVICE_UNAVAILABLE", \
		"message": "회원 서비스에 연결하지 못했습니다. 잠시 뒤 다시 시도해주세요.", "data": null }
		""";

	private AdminCashApiExamples() {
	}
}
