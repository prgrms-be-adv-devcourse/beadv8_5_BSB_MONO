package com.bukang.queue.in;

/**
 * ApiV1PartyQueueController·InternalV1PartyQueueController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 * 개인 대기열과 같은 응답(요청 값 오류, 회원 ID 헤더 누락, 없는 대회·종목, 이미 입장, 줄에 없음, Redis 연결 실패)은
 * QueueApiExamples의 상수를 함께 쓴다
 */
final class PartyQueueApiExamples {
	static final String REGISTER_SUCCESS = """
		{
			"status": 200,
			"message": "파티 대기열에 등록했습니다.",
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
			"message": "파티 대기열 상태를 조회했습니다.",
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
			"message": "파티 대기열 상태를 조회했습니다.",
			"data": {
				"myRank": 0,
				"ahead": 0,
				"total": 0,
				"canEnter": true
			}
		}
		""";

	static final String NOT_CREW_SALE_PERIOD = """
		{ "status": 403, "message": "크루 예매 기간이 아닙니다.", "data": null }
		""";

	static final String ADMITTED = """
		{
			"status": 200,
			"message": "파티 대기열에 입장 중입니다.",
			"data": {
				"admitted": true,
				"admissionEndsAt": "2026-10-16T14:15:00",
				"remainingMillis": 840000
			}
		}
		""";

	static final String NOT_ADMITTED = """
		{
			"status": 200,
			"message": "파티 대기열에 입장 중이 아닙니다.",
			"data": {
				"admitted": false,
				"admissionEndsAt": null,
				"remainingMillis": 0
			}
		}
		""";

	private PartyQueueApiExamples() {
	}
}
