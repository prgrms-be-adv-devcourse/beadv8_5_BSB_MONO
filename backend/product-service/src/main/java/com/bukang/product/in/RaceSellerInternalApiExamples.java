package com.bukang.product.in;

/**
 * InternalV1RaceSellerController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 */
final class RaceSellerInternalApiExamples {
	static final String SELLER_MEMBER_SUCCESS = """
		{
			"status": 200,
			"message": "대회 판매자를 조회했습니다.",
			"data": {
				"sellerMemberId": 42
			}
		}
		""";

	static final String INVALID_VALUE_TYPE = """
		{ "status": 400, "message": "요청 값의 형식이 올바르지 않습니다.", "data": null }
		""";

	static final String RACE_NOT_FOUND = """
		{ "status": 404, "code": "RACE_NOT_FOUND", "message": "존재하지 않는 대회입니다.", "data": null }
		""";

	private RaceSellerInternalApiExamples() {
	}
}
