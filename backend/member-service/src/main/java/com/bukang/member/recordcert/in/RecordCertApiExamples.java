package com.bukang.member.recordcert.in;

// ApiV1RecordCertController의 Swagger 응답 예시
final class RecordCertApiExamples {
	static final String SUBMIT_SUCCESS = """
		{
			"status": 201,
			"message": "기록증을 제출했습니다.",
			"data": { "submissionId": 1, "status": "CHECKING" }
		}
		""";

	static final String FIND_MINE_SUCCESS = """
		{
			"status": 200,
			"message": "내 기록증 제출 목록을 조회했습니다.",
			"data": [
				{
					"submissionId": 2,
					"orderItemId": 42,
					"status": "CHECKING",
					"reasons": [],
					"denyReason": null,
					"submittedAt": "2026-11-18T10:00:00"
				},
				{
					"submissionId": 1,
					"orderItemId": 42,
					"status": "DENIED",
					"reasons": ["TOO_FAST"],
					"denyReason": "NAME_MISMATCH",
					"submittedAt": "2026-11-17T10:00:00"
				}
			]
		}
		""";

	static final String INVALID_INPUT = """
		{
			"status": 400,
			"code": "INVALID_REQUEST",
			"message": "netTimeSeconds: 널이어서는 안됩니다",
			"data": null
		}
		""";

	static final String INVALID_BODY = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "요청 본문 형식이 올바르지 않습니다.", "data": null }
		""";

	static final String LOGIN_REQUIRED = """
		{ "status": 401, "code": "LOGIN_REQUIRED", "message": "로그인이 필요합니다.", "data": null }
		""";

	static final String TICKET_FORBIDDEN = """
		{
			"status": 403,
			"code": "RECORD_CERT_TICKET_FORBIDDEN",
			"message": "내 참가권으로만 기록증을 제출할 수 있습니다.",
			"data": null
		}
		""";

	static final String FILE_FORBIDDEN = """
		{
			"status": 403,
			"code": "RECORD_CERT_FILE_FORBIDDEN",
			"message": "내가 올린 기록증 사진으로만 제출할 수 있습니다.",
			"data": null
		}
		""";

	static final String TICKET_NOT_FOUND = """
		{ "status": 404, "code": "RECORD_CERT_TICKET_NOT_FOUND", "message": "참가권을 찾을 수 없습니다.", "data": null }
		""";

	static final String FILE_NOT_FOUND = """
		{
			"status": 404,
			"code": "RECORD_CERT_FILE_NOT_FOUND",
			"message": "기록증 사진을 찾을 수 없습니다. 사진을 다시 올려 주세요.",
			"data": null
		}
		""";

	static final String TICKET_NOT_PAID = """
		{
			"status": 409,
			"code": "RECORD_CERT_TICKET_NOT_PAID",
			"message": "결제를 마친 참가권으로만 기록증을 제출할 수 있습니다. 결제 대기 · 취소 중 · 취소된 참가권은 낼 수 없습니다.",
			"data": null
		}
		""";

	static final String RULE_NOT_FOUND = """
		{ "status": 409, "code": "RECORD_CERT_RULE_NOT_FOUND", "message": "기록증이 필요 없는 종목입니다.", "data": null }
		""";

	static final String SUBMISSION_IN_PROGRESS = """
		{
			"status": 409,
			"code": "RECORD_CERT_SUBMISSION_IN_PROGRESS",
			"message": "이 참가권으로 낸 기록증이 이미 확인 중이거나 승인됐습니다.",
			"data": null
		}
		""";

	static final String SUBMIT_LOCKED = """
		{
			"status": 409,
			"code": "RECORD_CERT_SUBMIT_LOCKED",
			"message": "같은 참가권의 제출을 처리하고 있습니다. 잠시 뒤 다시 시도해주세요.",
			"data": null
		}
		""";

	static final String CHECK_UNAVAILABLE = """
		{
			"status": 503,
			"code": "RECORD_CERT_CHECK_UNAVAILABLE",
			"message": "기록증 제출을 확인하지 못했습니다. 잠시 뒤 다시 시도해주세요.",
			"data": null
		}
		""";

	private RecordCertApiExamples() {
	}
}
