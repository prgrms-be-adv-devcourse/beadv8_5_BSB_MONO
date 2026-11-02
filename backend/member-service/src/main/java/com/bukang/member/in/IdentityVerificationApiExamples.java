package com.bukang.member.in;

/**
 * ApiV1IdentityVerificationController와 가입의 본인인증 오류 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 */
final class IdentityVerificationApiExamples {
	static final String START_SUCCESS = """
		{
			"status": 201,
			"message": "본인인증을 시작했습니다.",
			"data": {
				"identityVerificationId": "crewrun-iv-6f1c2b9e-3a5d-4c7e-9b1a-2d8f0e4c6a71"
			}
		}
		""";

	static final String COMPLETE_SUCCESS = """
		{
			"status": 200,
			"message": "본인인증을 확인했습니다.",
			"data": {
				"verificationToken": "crewrun-iv-6f1c2b9e-3a5d-4c7e-9b1a-2d8f0e4c6a71",
				"name": "김러너",
				"birthDate": "1995-04-12",
				"phone": "010-1234-5678"
			}
		}
		""";

	static final String VERIFICATION_REQUIRED = """
		{
			"status": 400,
			"code": "AUTH_VERIFICATION_REQUIRED",
			"message": "본인인증을 다시 해주세요.",
			"data": null
		}
		""";

	static final String VERIFICATION_FAILED = """
		{
			"status": 400,
			"code": "AUTH_VERIFICATION_FAILED",
			"message": "본인인증을 마치지 못했습니다. 다시 시도해주세요.",
			"data": null
		}
		""";

	static final String CI_MISSING = """
		{
			"status": 400,
			"code": "AUTH_VERIFICATION_CI_MISSING",
			"message": "이 인증 방법으로는 가입할 수 없습니다. 다른 인증 방법을 골라주세요.",
			"data": null
		}
		""";

	static final String UNAVAILABLE = """
		{
			"status": 503,
			"code": "AUTH_VERIFICATION_UNAVAILABLE",
			"message": "본인인증 결과를 확인하지 못했습니다. 잠시 후 다시 시도해주세요.",
			"data": null
		}
		""";

	static final String DUPLICATE_IDENTITY = """
		{
			"status": 409,
			"code": "MEMBER_DUPLICATE_IDENTITY",
			"message": "이미 가입한 사람입니다. 로그인하거나 계정 연결에서 연결해주세요.",
			"data": null
		}
		""";

	private IdentityVerificationApiExamples() {
	}
}
