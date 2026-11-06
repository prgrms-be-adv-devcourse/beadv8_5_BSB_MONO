package com.bukang.notification.in;

/**
 * ApiV1NotificationController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 */
final class NotificationApiExamples {
	static final String FIND_SUCCESS = """
		{
			"status": 200,
			"message": "알림을 조회했습니다.",
			"data": [
				{
					"id": "12",
					"icon": "CircleCheck",
					"title": "환불이 끝났어요",
					"body": "가을 트레일 21K ·\\n₩60,000이 예치금으로 들어왔어요.",
					"at": "5시간 전",
					"group": "today",
					"href": "/me/deposit",
					"unread": true
				},
				{
					"id": "9",
					"icon": "CircleAlert",
					"title": "대회가 취소됐어요",
					"body": "10K · 참가비 전액\\n₩45,000을 예치금으로 돌려드렸어요.",
					"at": "11/2",
					"group": "earlier",
					"href": "/tickets",
					"unread": false
				}
			]
		}
		""";

	static final String READ_SUCCESS = """
		{ "status": 200, "message": "알림을 읽음으로 바꿨습니다.", "data": null }
		""";

	static final String READ_ALL_SUCCESS = """
		{ "status": 200, "message": "알림을 모두 읽음으로 바꿨습니다.", "data": null }
		""";

	static final String NOT_FOUND = """
		{ "status": 404, "code": "NOTIFICATION_NOT_FOUND", "message": "알림을 찾을 수 없습니다.", "data": null }
		""";

	static final String LOGIN_REQUIRED = """
		{ "status": 401, "code": "LOGIN_REQUIRED", "message": "로그인이 필요합니다.", "data": null }
		""";

	static final String INVALID_MEMBER_ID = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "회원 ID가 올바르지 않습니다.", "data": null }
		""";

	static final String INVALID_VALUE_TYPE = """
		{ "status": 400, "message": "요청 값의 형식이 올바르지 않습니다.", "data": null }
		""";

	private NotificationApiExamples() {
	}
}
