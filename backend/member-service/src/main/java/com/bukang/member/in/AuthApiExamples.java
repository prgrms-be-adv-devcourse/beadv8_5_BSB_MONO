package com.bukang.member.in;

/**
 * ApiV1AuthController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 */
final class AuthApiExamples {
	static final String JOIN_SUCCESS = """
		{
			"status": 201,
			"message": "Member Joined Successfully",
			"data": {
				"id": 1,
				"createDate": "2026-10-05T21:25:35",
				"modifyDate": "2026-10-05T21:25:35",
				"nickname": "러너1",
				"email": "runner1@crewrun.com"
			}
		}
		""";

	static final String LOGIN_SUCCESS = """
		{
			"status": 200,
			"message": "로그인 성공",
			"data": {
				"id": 1,
				"createDate": "2026-10-05T21:25:35",
				"modifyDate": "2026-10-05T21:25:35",
				"nickname": "러너1",
				"email": "runner1@crewrun.com"
			}
		}
		""";

	static final String JOIN_INVALID_INPUT = """
		{ "status": 400, "message": "비밀번호는 10자 이상 작성해야합니다.", "data": null }
		""";

	static final String LOGIN_INVALID_INPUT = """
		{ "status": 400, "message": "이메일을 입력해주세요.", "data": null }
		""";

	static final String INVALID_BODY = """
		{ "status": 400, "message": "요청 본문 형식이 올바르지 않습니다.", "data": null }
		""";

	static final String DUPLICATE_EMAIL = """
		{ "status": 409, "message": "이미 사용 중인 이메일입니다.", "data": null }
		""";

	static final String DUPLICATE_NICKNAME = """
		{ "status": 409, "message": "이미 사용 중인 닉네임입니다.", "data": null }
		""";

	static final String DUPLICATE_PHONE = """
		{ "status": 409, "message": "이미 사용 중인 전화번호입니다.", "data": null }
		""";

	static final String LOGIN_FAILED = """
		{ "status": 401, "message": "이메일 또는 비밀번호가 올바르지 않습니다.", "data": null }
		""";

	private AuthApiExamples() {
	}
}
