package com.bukang.product.in;

/**
 * InternalV1CrewPaymentController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 */
final class CrewPaymentInternalApiExamples {
	static final String LOW_PAYMENT_SUCCESS = """
		{
			"status": 200,
			"message": "결제율 미달 크루를 조회했습니다.",
			"data": [
				{
					"crewId": 7,
					"lowPaymentCount": 3,
					"lowPaymentParties": [
						{ "partyId": 101, "memberCount": 4, "paidCount": 1 },
						{ "partyId": 105, "memberCount": 6, "paidCount": 0 },
						{ "partyId": 112, "memberCount": 8, "paidCount": 2 }
					]
				}
			]
		}
		""";

	static final String LOW_PAYMENT_EMPTY = """
		{
			"status": 200,
			"message": "결제율 미달 크루를 조회했습니다.",
			"data": []
		}
		""";

	static final String MISSING_MONTH = """
		{ "status": 400, "message": "필수 요청 값(month)이 없습니다.", "data": null }
		""";

	static final String INVALID_VALUE_TYPE = """
		{ "status": 400, "message": "요청 값의 형식이 올바르지 않습니다.", "data": null }
		""";

	private CrewPaymentInternalApiExamples() {
	}
}
