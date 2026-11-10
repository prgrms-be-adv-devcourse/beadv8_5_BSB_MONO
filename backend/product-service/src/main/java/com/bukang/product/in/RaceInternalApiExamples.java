package com.bukang.product.in;

/**
 * InternalV1RaceCourseController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 */
final class RaceInternalApiExamples {
	static final String COURSE_INFO_SUCCESS = """
		{
			"status": 200,
			"message": "종목 정보를 조회했습니다.",
			"data": {
				"raceCourseId": 1,
				"raceId": 1,
				"title": "2026 용감한 쿠키RUN in 서울",
				"raceDate": "2026-11-01T08:00:00",
				"applicationOpenAt": "2026-10-13T10:00:00",
				"applicationCloseAt": "2026-10-31T18:00:00",
				"minAge": 14,
				"maxAge": 70,
				"sellerId": 7001,
				"course": "10K",
				"courseName": "2026 용감한 쿠키RUN in 서울 10K",
				"price": 45000,
				"kitPrice": 0,
				"refundDeadline": "2026-10-31",
				"scheduleChangedAt": null,
				"saleStatus": "ON_SALE",
				"courseStatus": "OPEN",
				"individualRemaining": 580,
				"crewRemaining": 0,
				"kitIncluded": true,
				"crewSaleStartAt": "2026-10-10T10:00:00",
				"crewSaleEndAt": "2026-10-12T18:00:00",
				"crewMinCount": 5,
				"crewMaxCount": 10
			}
		}
		""";

	static final String INVALID_VALUE_TYPE = """
		{ "status": 400, "message": "요청 값의 형식이 올바르지 않습니다.", "data": null }
		""";

	static final String COURSE_NOT_FOUND = """
		{ "status": 404, "message": "존재하지 않는 종목입니다.", "data": null }
		""";

	private RaceInternalApiExamples() {
	}
}
