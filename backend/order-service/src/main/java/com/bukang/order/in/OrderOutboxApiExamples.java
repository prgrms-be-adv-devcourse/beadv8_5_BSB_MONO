package com.bukang.order.in;

/**
 * InternalV1OrderOutboxController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 */
final class OrderOutboxApiExamples {
	static final String LIST_SUCCESS = """
		{
			"status": 200,
			"message": "발행 실패 메시지를 조회했습니다.",
			"data": [
				{
					"id": 42,
					"status": "SEND_FAILED",
					"messageId": "order-outbox-5d8a3f2e-1b4c-4e7a-9f0d-2c6b8e1a7d42",
					"eventType": "product.hold.restore",
					"refType": "Participation",
					"refId": 1001,
					"payload": "{\\"participationId\\":1001}",
					"retryCount": 3,
					"createDate": "2026-11-12T10:15:30"
				}
			]
		}
		""";

	static final String RETRY_SUCCESS = """
		{
			"status": 200,
			"message": "메시지를 다시 보내도록 되돌렸습니다.",
			"data": {
				"id": 42,
				"status": "PENDING",
				"messageId": "order-outbox-5d8a3f2e-1b4c-4e7a-9f0d-2c6b8e1a7d42",
				"eventType": "product.hold.restore",
				"refType": "Participation",
				"refId": 1001,
				"payload": "{\\"participationId\\":1001}",
				"retryCount": 0,
				"createDate": "2026-11-12T10:15:30"
			}
		}
		""";

	static final String NOT_FOUND = """
		{ "status": 404, "code": "ORDER_OUTBOX_NOT_FOUND", "message": "아웃박스 메시지 42번이 없습니다.", "data": null }
		""";

	static final String RETRY_INVALID_STATUS = """
		{
			"status": 409,
			"code": "ORDER_OUTBOX_INVALID_STATUS",
			"message": "아웃박스 메시지 42번은 발행 완료 상태라 다시 보낼 수 없습니다.",
			"data": null
		}
		""";

	static final String LIST_SIZE_OUT_OF_RANGE = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "한 번에 100건까지 조회할 수 있습니다.", "data": null }
		""";

	static final String INVALID_VALUE_TYPE = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "요청 값의 형식이 올바르지 않습니다.", "data": null }
		""";

	private OrderOutboxApiExamples() {
	}
}
