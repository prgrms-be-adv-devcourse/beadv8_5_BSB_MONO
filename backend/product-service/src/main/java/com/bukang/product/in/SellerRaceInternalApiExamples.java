package com.bukang.product.in;

/**
 * InternalV1SellerRaceController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 */
final class SellerRaceInternalApiExamples {
	static final String ON_SALE_RACE_EXISTS = """
		{
			"status": 200,
			"message": "판매가 끝나지 않은 대회가 있는지 조회했습니다.",
			"data": {
				"hasOnSaleRace": true
			}
		}
		""";

	static final String NO_ON_SALE_RACE = """
		{
			"status": 200,
			"message": "판매가 끝나지 않은 대회가 있는지 조회했습니다.",
			"data": {
				"hasOnSaleRace": false
			}
		}
		""";

	static final String NOT_SELLER = """
		{
			"status": 200,
			"message": "판매자로 등록된 회원이 아닙니다.",
			"data": {
				"hasOnSaleRace": true
			}
		}
		""";

	static final String INVALID_VALUE_TYPE = """
		{ "status": 400, "message": "요청 값의 형식이 올바르지 않습니다.", "data": null }
		""";

	private SellerRaceInternalApiExamples() {
	}
}
