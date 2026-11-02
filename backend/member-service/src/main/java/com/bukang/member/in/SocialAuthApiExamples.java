package com.bukang.member.in;

/**
 * ApiV1SocialAuthController·ApiV1SocialAccountController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 */
final class SocialAuthApiExamples {
	static final String PROVIDER_NOT_FOUND = """
		{ "status": 404, "code": "AUTH_SOCIAL_PROVIDER_NOT_FOUND", "message": "지원하지 않는 소셜 로그인입니다.", "data": null }
		""";

	static final String SIGNUP_INFO_SUCCESS = """
		{
			"status": 200,
			"message": "소셜 가입 정보 조회 성공",
			"data": { "provider": "KAKAO", "email": "runner1@kakao.com", "emailLocked": true, "name": null }
		}
		""";

	static final String SIGNUP_EXPIRED = """
		{
			"status": 401,
			"code": "AUTH_SOCIAL_SIGNUP_EXPIRED",
			"message": "소셜 가입 시간이 지났습니다. 소셜 로그인부터 다시 해주세요.",
			"data": null
		}
		""";

	static final String JOIN_SUCCESS = """
		{
			"status": 201,
			"message": "소셜 가입 성공",
			"data": {
				"accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwidHlwZSI6IkFDQ0VTUyJ9.signature",
				"member": {
					"id": 1,
					"createDate": "2026-10-05T21:25:35",
					"modifyDate": "2026-10-05T21:25:35",
					"nickname": "러너1",
					"email": "runner1@kakao.com",
					"phone": "010-1234-5678",
					"name": "김러너",
					"birthDate": "1995-04-12",
					"socialProviders": ["KAKAO"],
					"passwordSet": false
				}
			}
		}
		""";

	static final String JOIN_EMAIL_REQUIRED = """
		{ "status": 400, "code": "AUTH_SOCIAL_EMAIL_REQUIRED", "message": "이메일을 입력해주세요.", "data": null }
		""";

	static final String PHONE_TAKEN = """
		{
			"status": 409,
			"code": "AUTH_SOCIAL_PHONE_TAKEN",
			"message": "이 휴대폰 번호로 가입한 계정이 있습니다. 원래 쓰던 방법으로 로그인한 뒤 계정 연결에서 연결해주세요.",
			"data": null
		}
		""";

	static final String ALREADY_LINKED = """
		{ "status": 409, "code": "AUTH_SOCIAL_ALREADY_LINKED", "message": "이미 다른 회원에 연결된 소셜 계정입니다.", "data": null }
		""";

	static final String LINK_TICKET_SUCCESS = """
		{ "status": 201, "message": "연결 표 발급 성공", "data": null }
		""";

	static final String PROVIDER_ALREADY_LINKED = """
		{
			"status": 409,
			"code": "MEMBER_SOCIAL_PROVIDER_ALREADY_LINKED",
			"message": "이미 연결된 소셜 로그인입니다.",
			"data": null
		}
		""";

	static final String UNLINK_SUCCESS = """
		{
			"status": 200,
			"message": "연결을 해제했습니다.",
			"data": {
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
		""";

	static final String MEMBER_NOT_FOUND = """
		{ "status": 404, "code": "MEMBER_NOT_FOUND", "message": "존재하지 않는 회원입니다.", "data": null }
		""";

	static final String ACCOUNT_NOT_FOUND = """
		{ "status": 404, "code": "MEMBER_SOCIAL_ACCOUNT_NOT_FOUND", "message": "연결되지 않은 소셜 로그인입니다.", "data": null }
		""";

	static final String LAST_LOGIN_METHOD = """
		{
			"status": 409,
			"code": "MEMBER_SOCIAL_LAST_LOGIN_METHOD",
			"message": "로그인할 방법이 하나는 남아 있어야 합니다.",
			"data": null
		}
		""";

	private SocialAuthApiExamples() {
	}
}
