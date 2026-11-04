package com.bukang.member.in;

/**
 * ApiV1PasswordResetController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 */
final class PasswordResetApiExamples {
	static final String REQUEST_SUCCESS = """
		{ "status": 200, "message": "가입된 이메일이면 재설정 메일을 보냈습니다.", "data": null }
		""";

	static final String REQUEST_INVALID_INPUT = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "이메일 형식에 맞게 작성해주세요.", "data": null }
		""";

	static final String TOO_MANY = """
		{
			"status": 429,
			"code": "AUTH_RESET_TOO_MANY",
			"message": "재설정 메일을 너무 자주 요청했습니다. 잠시 뒤 다시 요청해주세요.",
			"data": null
		}
		""";

	static final String VERIFY_SUCCESS = """
		{ "status": 200, "message": "쓸 수 있는 재설정 링크입니다.", "data": null }
		""";

	static final String CONFIRM_SUCCESS = """
		{ "status": 200, "message": "비밀번호를 바꿨습니다.", "data": null }
		""";

	static final String CONFIRM_INVALID_INPUT = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "비밀번호는 10자 이상 작성해야합니다.", "data": null }
		""";

	static final String TOKEN_INVALID = """
		{
			"status": 400,
			"code": "AUTH_RESET_TOKEN_INVALID",
			"message": "재설정 링크가 만료됐거나 이미 사용됐습니다. 다시 요청해주세요.",
			"data": null
		}
		""";

	static final String INVALID_BODY = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "요청 본문 형식이 올바르지 않습니다.", "data": null }
		""";

	private PasswordResetApiExamples() {
	}
}
