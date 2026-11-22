package com.bukang.product.in;

/**
 * ApiV1AdminBannerController·ApiV1BannerController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 * 인증·관리자·대회 없음 응답은 관리자 대회 API와 같아 RaceApiExamples의 것을 쓴다
 */
final class BannerApiExamples {
	static final String ADMIN_BANNERS_SUCCESS = """
		{
			"status": 200,
			"message": "홈 배너 목록을 조회했습니다.",
			"data": [
				{
					"raceId": 3,
					"title": "2026 용감한 쿠키RUN in 서울",
					"saleStatus": "ON_SALE",
					"applicationCloseAt": "2026-10-31T18:00:00",
					"displayOrder": 0,
					"visible": true,
					"hiddenReason": null,
					"hiddenReasonLabel": null
				},
				{
					"raceId": 1,
					"title": "2026 한강 나이트 런",
					"saleStatus": "STOPPED",
					"applicationCloseAt": "2026-11-10T18:00:00",
					"displayOrder": 1,
					"visible": false,
					"hiddenReason": "STOPPED",
					"hiddenReasonLabel": "판매 중지"
				}
			]
		}
		""";

	static final String SAVE_SUCCESS = """
		{
			"status": 200,
			"message": "홈 배너를 저장했습니다.",
			"data": [
				{
					"raceId": 3,
					"title": "2026 용감한 쿠키RUN in 서울",
					"saleStatus": "ON_SALE",
					"applicationCloseAt": "2026-10-31T18:00:00",
					"displayOrder": 0,
					"visible": true,
					"hiddenReason": null,
					"hiddenReasonLabel": null
				}
			]
		}
		""";

	static final String TOO_MANY = """
		{ "status": 400, "message": "홈 배너는 최대 5개까지 고를 수 있습니다.", "data": null }
		""";

	static final String DUPLICATE_RACE = """
		{ "status": 400, "message": "같은 대회를 홈 배너에 두 번 넣을 수 없습니다.", "data": null }
		""";

	static final String MISSING_RACE_IDS = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "배너로 고른 대회 목록을 보내주세요.", "data": null }
		""";

	static final String INVALID_BODY = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "요청 본문 형식이 올바르지 않습니다.", "data": null }
		""";

	static final String CANCELED_RACE = """
		{ "status": 409, "message": "취소된 대회는 배너로 고를 수 없습니다.", "data": null }
		""";

	static final String CLOSED_RACE = """
		{ "status": 409, "message": "판매가 끝난 대회는 배너로 고를 수 없습니다.", "data": null }
		""";

	static final String BANNERS_SUCCESS = """
		{
			"status": 200,
			"message": "홈 배너를 조회했습니다.",
			"data": [
				{
					"raceId": 3,
					"title": "2026 용감한 쿠키RUN in 서울",
					"location": "서울숲 일대",
					"regionSido": "서울특별시",
					"regionSigungu": "성동구",
					"raceDate": "2026-11-01T08:00:00",
					"saleStatus": "ON_SALE",
					"saleOpenDate": "2026-10-20",
					"crewSaleStartAt": null,
					"crewSaleEndAt": null,
					"individualOpenAt": "2026-10-20T09:00:00",
					"applicationCloseAt": "2026-10-31T18:00:00",
					"minPrice": 45000,
					"courses": [
						{
							"raceCourseId": 1,
							"name": "10K",
							"courseType": "KM_10",
							"distanceKm": 10.00,
							"price": 45000,
							"status": "OPEN",
							"individualRemaining": 580
						}
					],
					"imageUrl": "https://crewrun-file-prod.s3.ap-northeast-2.amazonaws.com/prod/files/1a2b3c4d.jpg?X-Amz-Expires=1800&X-Amz-Signature=..."
				}
			]
		}
		""";

	private BannerApiExamples() {
	}
}
