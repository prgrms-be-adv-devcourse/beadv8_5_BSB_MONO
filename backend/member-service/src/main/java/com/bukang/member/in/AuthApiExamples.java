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
				"email": "runner1@crewrun.com",
				"phone": "010-1234-5678",
				"name": "김러너",
				"birthDate": "1995-04-12"
			}
		}
		""";

	static final String LOGIN_SUCCESS = """
		{
			"status": 200,
			"message": "로그인 성공",
			"data": {
				"accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwidHlwZSI6IkFDQ0VTUyJ9.signature",
				"member": {
					"id": 1,
					"createDate": "2026-10-05T21:25:35",
					"modifyDate": "2026-10-05T21:25:35",
					"nickname": "러너1",
					"email": "runner1@crewrun.com",
					"phone": "010-1234-5678",
					"name": "김러너",
					"birthDate": "1995-04-12",
					"socialProviders": [],
					"passwordSet": true
				}
			}
		}
		""";

	static final String REISSUE_SUCCESS = """
		{
			"status": 200,
			"message": "토큰 재발급 성공",
			"data": {
				"accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwidHlwZSI6IkFDQ0VTUyJ9.signature"
			}
		}
		""";

	static final String LOGOUT_SUCCESS = """
		{
			"status": 200,
			"message": "로그아웃 성공",
			"data": null
		}
		""";

	static final String JOIN_INVALID_INPUT = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "비밀번호는 10자 이상 작성해야합니다.", "data": null }
		""";

	static final String LOGIN_INVALID_INPUT = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "이메일을 입력해주세요.", "data": null }
		""";

	static final String INVALID_BODY = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "요청 본문 형식이 올바르지 않습니다.", "data": null }
		""";

	static final String JOIN_UNDER_AGE = """
		{ "status": 400, "code": "MEMBER_UNDER_AGE", "message": "만 14세 미만은 가입할 수 없습니다.", "data": null }
		""";

	static final String JOIN_REQUIRED_TERMS_NOT_AGREED = """
		{
			"status": 400,
			"code": "MEMBER_REQUIRED_TERMS_NOT_AGREED",
			"message": "필수 약관에 동의해야 가입할 수 있습니다.",
			"data": null
		}
		""";

	static final String DUPLICATE_EMAIL = """
		{ "status": 409, "code": "MEMBER_DUPLICATE_EMAIL", "message": "이미 사용 중인 이메일입니다.", "data": null }
		""";

	static final String DUPLICATE_NICKNAME = """
		{ "status": 409, "code": "MEMBER_DUPLICATE_NICKNAME", "message": "이미 사용 중인 닉네임입니다.", "data": null }
		""";

	static final String DUPLICATE_PHONE = """
		{ "status": 409, "code": "MEMBER_DUPLICATE_PHONE", "message": "이미 사용 중인 전화번호입니다.", "data": null }
		""";

	static final String LOGIN_SOCIAL_ACCOUNT = """
		{ "status": 401, "code": "AUTH_SOCIAL_ACCOUNT", "message": "카카오로 가입한 계정입니다. 카카오로 로그인해주세요.", "data": null }
		""";

	static final String LOGIN_FAILED = """
		{ "status": 401, "code": "MEMBER_LOGIN_FAILED", "message": "이메일 또는 비밀번호가 올바르지 않습니다.", "data": null }
		""";

	static final String REISSUE_MISSING_TOKEN = """
		{
			"status": 401,
			"code": "MEMBER_INVALID_REFRESH_TOKEN",
			"message": "Refresh Token이 없습니다. 다시 로그인해주세요.",
			"data": null
		}
		""";

	static final String REISSUE_EXPIRED_TOKEN = """
		{
			"status": 401,
			"code": "MEMBER_INVALID_REFRESH_TOKEN",
			"message": "Refresh Token이 만료되었습니다. 다시 로그인해주세요.",
			"data": null
		}
		""";

	static final String REISSUE_INVALID_TOKEN = """
		{
			"status": 401,
			"code": "MEMBER_INVALID_REFRESH_TOKEN",
			"message": "유효하지 않은 Refresh Token입니다. 다시 로그인해주세요.",
			"data": null
		}
		""";

	private AuthApiExamples() {
	}
}
