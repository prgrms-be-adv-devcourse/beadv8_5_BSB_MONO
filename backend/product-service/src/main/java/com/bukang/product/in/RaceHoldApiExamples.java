package com.bukang.product.in;

/**
 * InternalV1HoldController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 */
final class RaceHoldApiExamples {
	static final String HOLD_SUCCESS = """
		{
			"status": 200,
			"message": "자리를 확보했습니다.",
			"data": {
				"held": true,
				"expiresAt": "2026-10-13T10:10:00"
			}
		}
		""";

	static final String HOLD_SOLD_OUT = """
		{
			"status": 200,
			"message": "남은 자리가 없습니다.",
			"data": {
				"held": false,
				"expiresAt": null
			}
		}
		""";

	static final String HOLD_INVALID_INPUT = """
		{ "status": 400, "message": "임시 확보 끝 시각을 작성해주세요.", "data": null }
		""";

	static final String HOLD_EXPIRES_PASSED = """
		{ "status": 400, "message": "임시 확보 끝 시각이 이미 지났습니다.", "data": null }
		""";

	static final String NOT_ACCEPTING = """
		{ "status": 409, "message": "접수 기간이 아닙니다.", "data": null }
		""";

	static final String HOLD_FINISHED = """
		{ "status": 409, "message": "이미 끝난 참가 신청이라 자리를 다시 확보할 수 없습니다. (자리 상태: 해제)", "data": null }
		""";

	static final String SELL_SOLD = """
		{
			"status": 200,
			"message": "자리를 판매했습니다.",
			"data": {
				"result": "SOLD"
			}
		}
		""";

	static final String SELL_EXPIRED = """
		{
			"status": 200,
			"message": "결제 기한이 지난 자리입니다.",
			"data": {
				"result": "EXPIRED"
			}
		}
		""";

	// 아래는 InternalV1CrewHoldController (크루 자리 확보)

	static final String CREW_HOLD_SUCCESS = """
		{
			"status": 200,
			"message": "크루 자리를 확보했습니다.",
			"data": {
				"held": true,
				"expiresAt": "2026-11-21T06:00:00",
				"shortages": []
			}
		}
		""";

	static final String CREW_HOLD_SHORTAGE = """
		{
			"status": 200,
			"message": "남은 크루 자리가 모자랍니다.",
			"data": {
				"held": false,
				"expiresAt": null,
				"shortages": [
					{ "raceCourseId": 10, "needed": 3, "remaining": 1 }
				]
			}
		}
		""";

	static final String CREW_HOLD_INVALID_INPUT = """
		{ "status": 400, "message": "잡을 자리를 하나 이상 작성해주세요.", "data": null }
		""";

	static final String CREW_HOLD_EXPIRES_PASSED = """
		{ "status": 400, "message": "자리 끝 시각이 이미 지났습니다.", "data": null }
		""";

	static final String CREW_HOLD_DUPLICATED_PARTICIPATION = """
		{ "status": 400, "message": "같은 참가 신청이 두 번 들어 있습니다. participationId=1001", "data": null }
		""";

	static final String SALE_STOPPED = """
		{ "status": 409, "message": "판매를 잠시 멈춘 대회입니다.", "data": null }
		""";

	static final String CREW_NOT_ACCEPTING = """
		{ "status": 409, "message": "크루 예매 기간이 아닙니다.", "data": null }
		""";

	static final String CREW_HOLD_MISMATCH = """
		{ "status": 409, "message": "이미 다른 자리 목록으로 확보한 파티입니다.", "data": null }
		""";

	static final String CREW_HOLD_COURSE_NOT_IN_RACE = """
		{ "status": 409, "message": "이 대회의 종목이 아닙니다.", "data": null }
		""";

	static final String CREW_HOLD_PARTICIPATION_TAKEN = """
		{ "status": 409, "message": "이미 자리를 확보한 참가 신청이 들어 있습니다.", "data": null }
		""";

	private RaceHoldApiExamples() {
	}
}
