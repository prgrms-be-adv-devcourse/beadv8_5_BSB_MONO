package com.bukang.file.in;

/**
 * InternalV1FileDltController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 */
final class FileDltApiExamples {
	static final String LIST_SUCCESS = """
		{
			"status": 200,
			"message": "처리 실패 메시지를 조회했습니다.",
			"data": [
				{
					"id": 15,
					"status": "FAILED",
					"originalTopic": "race.filelink.requested",
					"dltTopic": "race.filelink.requested.dlt",
					"dltPartition": 0,
					"dltOffset": 7,
					"messageKey": "1",
					"messageId": "product-outbox-42",
					"payload": "{\\"ownerId\\":1,\\"refs\\":[{\\"refType\\":\\"Race\\",\\"refId\\":1,\\"files\\":[]}]}",
					"exceptionClass": "org.springframework.dao.DataIntegrityViolationException",
					"exceptionMessage": "could not execute statement",
					"createDate": "2026-11-04T10:15:30",
					"resentDate": null,
					"completedDate": null,
					"completeMemo": null
				}
			]
		}
		""";

	static final String RESEND_SUCCESS = """
		{
			"status": 200,
			"message": "메시지를 다시 보냈습니다.",
			"data": {
				"id": 15,
				"status": "RESENT",
				"originalTopic": "race.filelink.requested",
				"dltTopic": "race.filelink.requested.dlt",
				"dltPartition": 0,
				"dltOffset": 7,
				"messageKey": "1",
				"messageId": "product-outbox-42",
				"payload": "{\\"ownerId\\":1,\\"refs\\":[{\\"refType\\":\\"Race\\",\\"refId\\":1,\\"files\\":[]}]}",
				"exceptionClass": "org.springframework.dao.DataIntegrityViolationException",
				"exceptionMessage": "could not execute statement",
				"createDate": "2026-11-04T10:15:30",
				"resentDate": "2026-11-04T11:02:10",
				"completedDate": null,
				"completeMemo": null
			}
		}
		""";

	static final String COMPLETE_SUCCESS = """
		{
			"status": 200,
			"message": "처리 완료로 바꿨습니다.",
			"data": {
				"id": 15,
				"status": "COMPLETED",
				"originalTopic": "race.filelink.requested",
				"dltTopic": "race.filelink.requested.dlt",
				"dltPartition": 0,
				"dltOffset": 7,
				"messageKey": "1",
				"messageId": "product-outbox-42",
				"payload": "{\\"ownerId\\":1,\\"refs\\":[{\\"refType\\":\\"Race\\",\\"refId\\":1,\\"files\\":[]}]}",
				"exceptionClass": "org.springframework.dao.DataIntegrityViolationException",
				"exceptionMessage": "could not execute statement",
				"createDate": "2026-11-04T10:15:30",
				"resentDate": null,
				"completedDate": "2026-11-04T11:05:00",
				"completeMemo": "파일을 다시 올려 대회에 직접 연결하고 다시 보내지 않고 닫음"
			}
		}
		""";

	static final String NOT_FOUND = """
		{ "status": 404, "code": "KAFKA_DLT_NOT_FOUND", "message": "[file] DLT 메시지 15번이 없습니다.", "data": null }
		""";

	// 아래 세 응답은 메시지가 길어 한 줄 120자를 넘으므로 여러 줄로 쓴다
	static final String RESEND_INVALID_STATUS = """
		{
			"status": 409,
			"code": "KAFKA_DLT_INVALID_STATUS",
			"message": "[file] DLT 메시지 15번은 다시 보냄 상태라 다시 보낼 수 없습니다.",
			"data": null
		}
		""";

	static final String RESEND_FAILED = """
		{
			"status": 503,
			"code": "KAFKA_DLT_RESEND_FAILED",
			"message": "[file] DLT 메시지 15번을 Kafka로 다시 보내지 못했습니다. 잠시 뒤 다시 시도하세요.",
			"data": null
		}
		""";

	static final String COMPLETE_INVALID_STATUS = """
		{
			"status": 409,
			"code": "KAFKA_DLT_INVALID_STATUS",
			"message": "[file] DLT 메시지 15번은 처리 완료 상태라 처리 완료로 바꿀 수 없습니다.",
			"data": null
		}
		""";

	static final String COMPLETE_MEMO_BLANK = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "처리 완료 메모를 작성해주세요.", "data": null }
		""";

	static final String COMPLETE_MEMO_TOO_LONG = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "처리 완료 메모는 500자 이하로 작성해주세요.", "data": null }
		""";

	static final String LIST_SIZE_OUT_OF_RANGE = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "한 번에 100건까지 조회할 수 있습니다.", "data": null }
		""";

	static final String INVALID_VALUE_TYPE = """
		{ "status": 400, "message": "요청 값의 형식이 올바르지 않습니다.", "data": null }
		""";

	static final String INVALID_BODY = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "요청 본문 형식이 올바르지 않습니다.", "data": null }
		""";

	private FileDltApiExamples() {
	}
}
