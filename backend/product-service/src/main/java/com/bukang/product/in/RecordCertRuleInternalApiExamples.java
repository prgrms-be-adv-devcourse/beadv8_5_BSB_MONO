package com.bukang.product.in;

/**
 * InternalV1RecordCertRuleController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 */
final class RecordCertRuleInternalApiExamples {
	static final String RULE_SUCCESS = """
		{
			"status": 200,
			"message": "종목 기록증 규칙을 조회했습니다.",
			"data": {
				"sellerMemberId": 42,
				"rule": {
					"recognizedSince": "2025-01-01",
					"maxNetSeconds": 7200,
					"allowedCourseTypes": ["HALF", "FULL"]
				}
			}
		}
		""";

	static final String NO_RULE_SUCCESS = """
		{
			"status": 200,
			"message": "종목 기록증 규칙을 조회했습니다.",
			"data": {
				"sellerMemberId": 42,
				"rule": null
			}
		}
		""";

	static final String INVALID_VALUE_TYPE = """
		{ "status": 400, "message": "요청 값의 형식이 올바르지 않습니다.", "data": null }
		""";

	static final String COURSE_NOT_FOUND = """
		{ "status": 404, "message": "존재하지 않는 종목입니다.", "data": null }
		""";

	private RecordCertRuleInternalApiExamples() {
	}
}
