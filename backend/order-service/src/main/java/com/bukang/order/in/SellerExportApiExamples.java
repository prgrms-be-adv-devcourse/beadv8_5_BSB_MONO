package com.bukang.order.in;

// 판매자 목록 내려받기 API 응답 예시 (Swagger, BUK-228)
final class SellerExportApiExamples {
	static final String ROSTER_CSV = """
		주문번호,종목,참가자 이름,연락처,키트 옵션,파티 번호
		OD-20261016-000C,10K,김러너,010-1234-5678,T-shirt: L,3
		""";

	static final String KIT_CSV = """
		주문번호,종목,참가권 상태,참가자 이름,받는 분,받는 분 연락처,우편번호,주소,상세 주소,키트 옵션,배송 상태,택배사,운송장 번호
		OD-20261016-000C,10K,결제 완료,김러너,김러너,010-1234-5678,04700,서울 성동구 왕십리로 00,101동 1001호,T-shirt: L,배송 준비,,
		""";

	static final String MISSING_REASON = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "내려받는 사유를 입력해주세요.", "data": null }
		""";

	static final String MISSING_RACE = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "대회를 골라주세요.", "data": null }
		""";

	static final String REASON_TOO_LONG = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "사유는 200자 이하로 입력해주세요.", "data": null }
		""";

	static final String INVALID_FORMAT = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "요청 값의 형식이 올바르지 않습니다.", "data": null }
		""";

	static final String INVALID_MEMBER_ID = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "회원 ID가 올바르지 않습니다.", "data": null }
		""";

	static final String LOGIN_REQUIRED = """
		{ "status": 401, "code": "LOGIN_REQUIRED", "message": "로그인이 필요합니다.", "data": null }
		""";

	static final String NOT_SELLER = """
		{ "status": 403, "code": "ORDER_SELLER_FORBIDDEN", "message": "승인된 판매자만 쓸 수 있습니다.", "data": null }
		""";

	static final String SELLER_UNAVAILABLE = """
		{ "status": 503, "code": "ORDER_EXTERNAL_SERVICE_UNAVAILABLE",
			"message": "판매자 기능을 아직 쓸 수 없습니다. 잠시 뒤 다시 시도해주세요.", "data": null }
		""";

	private SellerExportApiExamples() {
	}
}
