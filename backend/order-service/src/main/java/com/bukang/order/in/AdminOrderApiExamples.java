package com.bukang.order.in;

/**
 * ApiV1AdminOrderController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 */
final class AdminOrderApiExamples {
	static final String LIST_SUCCESS = """
		{
			"status": 200,
			"message": "회원 주문을 조회했습니다.",
			"data": {
				"orders": [
					{
						"id": 18,
						"orderNo": "OD-20261004-0012",
						"buyerId": 4,
						"createDate": "2026-10-04T10:00:00",
						"itemName": "쿠키RUN 10K",
						"itemCount": 1,
						"totalAmount": 45000,
						"canceledAmount": 0,
						"status": "PAID",
						"statusLabel": "결제 완료",
						"paidAt": "2026-10-04T10:03:00"
					},
					{
						"id": 11,
						"orderNo": "OD-20260920-000B",
						"buyerId": 4,
						"createDate": "2026-09-20T09:00:00",
						"itemName": "춘천 나이트런 10K",
						"itemCount": 1,
						"totalAmount": 45000,
						"canceledAmount": 0,
						"status": "EXPIRED",
						"statusLabel": "기한 만료",
						"paidAt": null
					}
				],
				"page": 0,
				"size": 20,
				"totalElements": 2,
				"totalPages": 1
			}
		}
		""";

	static final String INVALID_SEARCH = """
		{ "status": 400, "code": "ORDER_INVALID_ADMIN_SEARCH",
			"message": "회원 닉네임과 주문번호 중 하나만 넣어 주세요.", "data": null }
		""";

	static final String INVALID_PAGE = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "한 번에 100개까지 조회할 수 있습니다.", "data": null }
		""";

	static final String LOGIN_REQUIRED = """
		{ "status": 401, "code": "LOGIN_REQUIRED", "message": "로그인이 필요합니다.", "data": null }
		""";

	static final String NOT_ADMIN = """
		{ "status": 403, "code": "ORDER_ADMIN_FORBIDDEN", "message": "관리자만 이용할 수 있습니다.", "data": null }
		""";

	static final String MEMBER_NOT_FOUND = """
		{ "status": 404, "code": "ORDER_MEMBER_NOT_FOUND", "message": "해당 닉네임의 회원이 없습니다.", "data": null }
		""";

	static final String MEMBER_UNAVAILABLE = """
		{ "status": 503, "code": "ORDER_EXTERNAL_SERVICE_UNAVAILABLE",
			"message": "회원 서비스에 연결하지 못했습니다. 잠시 뒤 다시 시도해주세요.", "data": null }
		""";

	static final String NICKNAME_DISABLED = """
		{ "status": 503, "code": "ORDER_EXTERNAL_SERVICE_UNAVAILABLE",
			"message": "닉네임 검색은 아직 쓸 수 없습니다. 주문번호로 찾아 주세요.", "data": null }
		""";

	private AdminOrderApiExamples() {
	}
}
