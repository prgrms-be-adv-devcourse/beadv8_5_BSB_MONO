package com.bukang.member.recordcert.in;

/**
 * ApiV1SellerRecordCertController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 */
final class SellerRecordCertApiExamples {
	static final String LIST_SUCCESS = """
		{
			"status": 200,
			"message": "대회의 기록증 제출 목록을 조회했습니다.",
			"data": [
				{
					"submissionId": 31,
					"orderItemId": 700,
					"raceCourseId": 70,
					"status": "NEEDS_REVIEW",
					"reasons": ["TOO_FAST"],
					"serverCheck": {
						"NET_TIME": "MISMATCH",
						"NET_LABEL": "MATCH",
						"RACE_DATE": "MATCH",
						"COURSE": "MATCH"
					},
					"serverCheckMatched": false,
					"netTimeSeconds": 5405,
					"raceDate": "2026-04-12",
					"courseClass": "HALF",
					"denyReason": null,
					"denyMemo": null,
					"submittedAt": "2026-11-18T09:00:00",
					"reviewedAt": null
				}
			]
		}
		""";

	static final String DETAIL_SUCCESS = """
		{
			"status": 200,
			"message": "기록증 제출을 조회했습니다.",
			"data": {
				"submission": {
					"submissionId": 31,
					"orderItemId": 700,
					"raceCourseId": 70,
					"status": "NEEDS_REVIEW",
					"reasons": ["TOO_FAST"],
					"serverCheck": {
						"NET_TIME": "MISMATCH",
						"NET_LABEL": "MATCH",
						"RACE_DATE": "MATCH",
						"COURSE": "MATCH"
					},
					"serverCheckMatched": false,
					"netTimeSeconds": 5405,
					"raceDate": "2026-04-12",
					"courseClass": "HALF",
					"denyReason": null,
					"denyMemo": null,
					"submittedAt": "2026-11-18T09:00:00",
					"reviewedAt": null
				},
				"fileUrl": "https://bucket.s3.ap-northeast-2.amazonaws.com/prod/files/77.jpg?X-Amz-Expires=1800",
				"roi": { "x": 10, "y": 20, "width": 300, "height": 400 },
				"fieldBoxes": "{\\"NET_TIME\\":[[120,340],[380,340],[380,390],[120,390]]}",
				"bib": "M-8363"
			}
		}
		""";

	static final String APPROVE_SUCCESS = """
		{
			"status": 200,
			"message": "기록증 제출을 승인했습니다.",
			"data": {
				"submissionId": 31,
				"orderItemId": 700,
				"raceCourseId": 70,
				"status": "APPROVED",
				"reasons": ["TOO_FAST"],
				"serverCheck": {
					"NET_TIME": "MATCH",
					"NET_LABEL": "MATCH",
					"RACE_DATE": "MATCH",
					"COURSE": "MATCH"
				},
				"serverCheckMatched": true,
				"netTimeSeconds": 5405,
				"raceDate": "2026-04-12",
				"courseClass": "HALF",
				"denyReason": null,
				"denyMemo": null,
				"submittedAt": "2026-11-18T09:00:00",
				"reviewedAt": "2026-11-18T14:00:00"
			}
		}
		""";

	static final String DENY_SUCCESS = """
		{
			"status": 200,
			"message": "기록증 제출을 승인 불가로 정했습니다.",
			"data": {
				"submissionId": 31,
				"orderItemId": 700,
				"raceCourseId": 70,
				"status": "DENIED",
				"reasons": ["TOO_FAST"],
				"serverCheck": {
					"NET_TIME": "MISMATCH",
					"NET_LABEL": "MATCH",
					"RACE_DATE": "MATCH",
					"COURSE": "MATCH"
				},
				"serverCheckMatched": false,
				"netTimeSeconds": 5405,
				"raceDate": "2026-04-12",
				"courseClass": "HALF",
				"denyReason": "NAME_MISMATCH",
				"denyMemo": "사진 속 이름이 참가자와 달라요",
				"submittedAt": "2026-11-18T09:00:00",
				"reviewedAt": "2026-11-18T14:00:00"
			}
		}
		""";

	static final String COUNT_SUCCESS = """
		{
			"status": 200,
			"message": "심사 필요 기록증 제출 수를 조회했습니다.",
			"data": { "waitingCount": 12 }
		}
		""";

	static final String INVALID_VALUE_TYPE = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "요청 값의 형식이 올바르지 않습니다.", "data": null }
		""";

	static final String INVALID_INPUT = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "승인 불가 사유를 골라 주세요.", "data": null }
		""";

	static final String INVALID_MEMO = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "메모는 200자 이하로 써 주세요.", "data": null }
		""";

	static final String INVALID_BODY = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "요청 본문 형식이 올바르지 않습니다.", "data": null }
		""";

	static final String LOGIN_REQUIRED = """
		{ "status": 401, "code": "LOGIN_REQUIRED", "message": "로그인이 필요합니다.", "data": null }
		""";

	static final String NOT_SELLER = """
		{ "status": 403, "code": "RECORD_CERT_NOT_SELLER", "message": "승인된 판매자만 쓸 수 있습니다.", "data": null }
		""";

	static final String NOT_OWNER = """
		{ "status": 403, "code": "RECORD_CERT_NOT_OWNER", "message": "내 대회의 기록증 제출이 아닙니다.", "data": null }
		""";

	static final String NOT_FOUND = """
		{
			"status": 404,
			"code": "RECORD_CERT_SUBMISSION_NOT_FOUND",
			"message": "기록증 제출을 찾을 수 없습니다.",
			"data": null
		}
		""";

	static final String NOT_REVIEWABLE = """
		{
			"status": 409,
			"code": "RECORD_CERT_SUBMISSION_NOT_REVIEWABLE",
			"message": "심사 필요인 기록증 제출만 심사할 수 있습니다.",
			"data": null
		}
		""";

	static final String FILE_VIEW_UNAVAILABLE = """
		{
			"status": 503,
			"code": "RECORD_CERT_FILE_VIEW_UNAVAILABLE",
			"message": "기록증 사진을 불러오지 못했습니다. 잠시 뒤 다시 시도해주세요.",
			"data": null
		}
		""";

	private SellerRecordCertApiExamples() {
	}
}
