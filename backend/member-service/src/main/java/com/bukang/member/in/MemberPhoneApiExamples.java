package com.bukang.member.in;

/**
 * ApiV1MemberPhoneController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 * 로그인 · 회원 없음 · 본인인증 오류처럼 다른 API와 같은 응답은 MemberApiExamples, IdentityVerificationApiExamples를 쓴다
 */
final class MemberPhoneApiExamples {
	static final String COMPLETE_SUCCESS = """
		{
			"status": 200,
			"message": "본인인증을 확인했습니다.",
			"data": {
				"verificationToken": "crewrun-iv-6f1c2b9e-3a5d-4c7e-9b1a-2d8f0e4c6a71",
				"name": "김러너",
				"birthDate": "1995-04-12",
				"phone": "010-5555-6666"
			}
		}
		""";

	static final String CHANGE_SUCCESS = """
		{
			"status": 200,
			"message": "휴대폰 번호를 바꿨습니다.",
			"data": {
				"id": 1,
				"createDate": "2026-10-05T21:25:35",
				"modifyDate": "2026-11-04T14:30:12",
				"nickname": "러너1",
				"email": "runner1@crewrun.com",
				"phone": "010-5555-6666",
				"name": "김러너",
				"birthDate": "1995-04-12",
				"socialProviders": ["KAKAO"],
				"passwordSet": true
			}
		}
		""";

	static final String TOKEN_BLANK = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "본인인증을 다시 해주세요.", "data": null }
		""";

	static final String IDENTITY_MISMATCH = """
		{ "status": 403, "code": "MEMBER_IDENTITY_MISMATCH", "message": "가입한 본인의 인증이 아닙니다.", "data": null }
		""";

	static final String DUPLICATE_PHONE = """
		{ "status": 409, "code": "MEMBER_DUPLICATE_PHONE", "message": "이미 사용 중인 전화번호입니다.", "data": null }
		""";

	private MemberPhoneApiExamples() {
	}
}
