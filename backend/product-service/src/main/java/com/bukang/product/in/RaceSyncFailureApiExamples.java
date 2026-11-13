package com.bukang.product.in;

/**
 * InternalV1RaceSyncFailureController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 */
final class RaceSyncFailureApiExamples {
	static final String LIST_SUCCESS = """
		{
			"status": 200,
			"message": "처리 실패한 동기화 행을 조회했습니다.",
			"data": [
				{
					"id": 42,
					"raceId": 7,
					"status": "FAILED",
					"retryCount": 10,
					"failedDate": "2026-11-11T10:20:30",
					"createDate": "2026-11-11T10:00:00",
					"completeDate": null,
					"completeMemo": null
				}
			]
		}
		""";

	static final String RETRY_SUCCESS = """
		{
			"status": 200,
			"message": "재처리를 요청했습니다.",
			"data": {
				"id": 42,
				"raceId": 7,
				"status": "PENDING",
				"retryCount": 0,
				"failedDate": "2026-11-11T10:20:30",
				"createDate": "2026-11-11T10:00:00",
				"completeDate": null,
				"completeMemo": null
			}
		}
		""";

	static final String COMPLETE_SUCCESS = """
		{
			"status": 200,
			"message": "처리 완료로 바꿨습니다.",
			"data": {
				"id": 42,
				"raceId": 7,
				"status": "COMPLETE",
				"retryCount": 10,
				"failedDate": "2026-11-11T10:20:30",
				"createDate": "2026-11-11T10:00:00",
				"completeDate": "2026-11-11T10:40:00",
				"completeMemo": "Kibana에서 대회 7의 종목 문서 2개를 직접 고침"
			}
		}
		""";

	static final String NOT_FOUND = """
		{ "status": 404, "code": "RACE_SYNC_EVENT_NOT_FOUND", "message": "동기화 행 42번이 없습니다.", "data": null }
		""";

	// 아래 두 응답은 메시지가 길어 한 줄 120자를 넘으므로 여러 줄로 쓴다
	static final String RETRY_INVALID_STATUS = """
		{
			"status": 409,
			"code": "RACE_SYNC_EVENT_INVALID_STATUS",
			"message": "동기화 행 42번은 처리 완료 상태라 재처리할 수 없습니다. 처리 실패 상태에서만 할 수 있습니다.",
			"data": null
		}
		""";

	static final String COMPLETE_INVALID_STATUS = """
		{
			"status": 409,
			"code": "RACE_SYNC_EVENT_INVALID_STATUS",
			"message": "동기화 행 42번은 처리 완료 상태라 처리 완료로 닫을 수 없습니다. 처리 실패 상태에서만 할 수 있습니다.",
			"data": null
		}
		""";

	static final String COMPLETE_MEMO_BLANK = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "처리 완료 메모를 작성해주세요.", "data": null }
		""";

	static final String COMPLETE_MEMO_TOO_LONG = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "처리 완료 메모는 500자 이하로 작성해주세요.", "data": null }
		""";

	static final String INVALID_BODY = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "요청 본문 형식이 올바르지 않습니다.", "data": null }
		""";

	static final String LIST_SIZE_OUT_OF_RANGE = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "한 번에 100건까지 조회할 수 있습니다.", "data": null }
		""";

	static final String INVALID_VALUE_TYPE = """
		{ "status": 400, "message": "요청 값의 형식이 올바르지 않습니다.", "data": null }
		""";

	private RaceSyncFailureApiExamples() {
	}
}
