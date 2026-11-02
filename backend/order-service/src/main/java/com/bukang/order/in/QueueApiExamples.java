package com.bukang.order.in;

// 대기열 입장 상태 API 응답 예시 (Swagger)
final class QueueApiExamples {
	static final String ADMITTED = """
		{
			"status": 200,
			"message": "대기열에 입장 중입니다.",
			"data": { "admitted": true, "admissionEndsAt": "2026-10-15T11:40:00", "remainingSeconds": 583 }
		}
		""";

	static final String NOT_ADMITTED = """
		{
			"status": 200,
			"message": "대기열에 입장 중이 아닙니다.",
			"data": { "admitted": false, "admissionEndsAt": null, "remainingSeconds": 0 }
		}
		""";

	static final String INVALID_MEMBER_ID = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "회원 ID가 올바르지 않습니다.", "data": null }
		""";

	static final String INVALID_RACE_ID = """
		{ "status": 400, "message": "대회 ID가 올바르지 않습니다.", "data": null }
		""";

	static final String LOGIN_REQUIRED = """
		{ "status": 401, "code": "LOGIN_REQUIRED", "message": "로그인이 필요합니다.", "data": null }
		""";

	private QueueApiExamples() {
	}
}
