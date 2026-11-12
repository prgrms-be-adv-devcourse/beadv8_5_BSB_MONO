package com.bukang.ai.in;

/**
 * ApiV1AiChatController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 */
final class AiApiExamples {
	static final String CHAT_SUCCESS = """
		{
			"status": 200,
			"message": "답변했습니다.",
			"data": {
				"answer": "송장 입력 대기 키트는 주문 관리 화면에서 확인해 주세요."
			}
		}
		""";
	static final String CLEAR_SUCCESS = """
		{ "status": 200, "message": "새 대화를 시작합니다.", "data": null }
		""";
	static final String INVALID_BODY = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "질문을 입력해주세요.", "data": null }
		""";
	static final String INVALID_MEMBER_ID = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "회원 ID가 올바르지 않습니다.", "data": null }
		""";
	static final String LOGIN_REQUIRED = """
		{ "status": 401, "code": "LOGIN_REQUIRED", "message": "로그인이 필요합니다.", "data": null }
		""";
	static final String FORBIDDEN = """
		{ "status": 403, "code": "AI_FORBIDDEN", "message": "판매자 센터를 이용할 수 있는 회원이 아닙니다.", "data": null }
		""";
	static final String MEMBER_UNAVAILABLE = """
		{
			"status": 503,
			"code": "AI_EXTERNAL_SERVICE_UNAVAILABLE",
			"message": "회원 서비스에 연결하지 못했습니다. 잠시 뒤 다시 시도해주세요.",
			"data": null
		}
		""";
	static final String MODEL_UNAVAILABLE = """
		{ "status": 503, "code": "AI_MODEL_UNAVAILABLE", "message": "지금은 답변을 만들 수 없습니다. 잠시 뒤 다시 시도해주세요.", "data": null }
		""";

	private AiApiExamples() {
	}
}
