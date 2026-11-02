package com.bukang.queue.in;

/**
 * ApiV1QueueController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 * 요청 값 오류·회원 ID 헤더 누락 예시는 InternalV1QueueController도 함께 쓴다
 */
final class QueueApiExamples {
	static final String REGISTER_SUCCESS = """
		{
			"status": 200,
			"message": "대기열에 등록했습니다.",
			"data": {
				"myRank": 3,
				"ahead": 2,
				"total": 10,
				"canEnter": false
			}
		}
		""";

	static final String STATUS_WAITING = """
		{
			"status": 200,
			"message": "대기열 상태를 조회했습니다.",
			"data": {
				"myRank": 3,
				"ahead": 2,
				"total": 10,
				"canEnter": false
			}
		}
		""";

	static final String STATUS_CAN_ENTER = """
		{
			"status": 200,
			"message": "대기열 상태를 조회했습니다.",
			"data": {
				"myRank": 0,
				"ahead": 0,
				"total": 0,
				"canEnter": true
			}
		}
		""";

	static final String INVALID_INPUT = """
		{ "status": 400, "message": "대회 ID를 작성해주세요.", "data": null }
		""";

	static final String INVALID_BODY = """
		{ "status": 400, "message": "요청 본문 형식이 올바르지 않습니다.", "data": null }
		""";

	static final String INVALID_VALUE_TYPE = """
		{ "status": 400, "message": "요청 값의 형식이 올바르지 않습니다.", "data": null }
		""";

	static final String INVALID_MEMBER_ID = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "회원 ID가 올바르지 않습니다.", "data": null }
		""";

	static final String INVALID_RACE_ID = """
		{ "status": 400, "message": "대회 ID는 0보다 커야 합니다.", "data": null }
		""";

	static final String LOGIN_REQUIRED = """
		{ "status": 401, "code": "LOGIN_REQUIRED", "message": "로그인이 필요합니다.", "data": null }
		""";

	static final String COURSE_NOT_IN_RACE = """
		{ "status": 403, "message": "이 대회의 종목이 아닙니다.", "data": null }
		""";

	static final String NOT_ENTERABLE = """
		{ "status": 403, "message": "접수 기간이 아닙니다.", "data": null }
		""";

	static final String RACE_NOT_FOUND = """
		{ "status": 404, "message": "존재하지 않는 대회입니다.", "data": null }
		""";

	static final String COURSE_NOT_FOUND = """
		{ "status": 404, "message": "존재하지 않는 종목입니다.", "data": null }
		""";

	static final String MEMBER_NOT_IN_QUEUE = """
		{ "status": 404, "message": "대기열에 없는 회원입니다.", "data": null }
		""";

	static final String ALREADY_ACTIVE = """
		{ "status": 409, "message": "이미 입장한 회원입니다.", "data": null }
		""";

	static final String QUEUE_UNAVAILABLE = """
		{ "status": 503, "message": "대기열에 연결하지 못했습니다. 잠시 뒤 다시 시도해주세요.", "data": null }
		""";

	private QueueApiExamples() {
	}
}
