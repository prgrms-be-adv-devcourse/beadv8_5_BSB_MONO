package com.bukang.product.in;

/**
 * ApiV1RaceController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 */
final class RaceApiExamples {
	static final String REGISTER_SUCCESS = """
		{
			"status": 201,
			"message": "대회를 등록했습니다. 10월 20일 0시부터 판매 중으로 표시되고, 개인 예매는 10월 20일 09:00에 열립니다.",
			"data": {
				"raceId": 1,
				"saleStatus": "SCHEDULED",
				"saleOpenDate": "2026-10-20",
				"courseIds": [1]
			}
		}
		""";

	static final String REGISTER_EXISTING = """
		{
			"status": 200,
			"message": "이미 처리한 대회 등록 요청입니다.",
			"data": {
				"raceId": 1,
				"saleStatus": "SCHEDULED",
				"saleOpenDate": "2026-10-20",
				"courseIds": [1]
			}
		}
		""";

	static final String UPDATE_SUCCESS = """
		{
			"status": 200,
			"message": "대회를 수정했습니다.",
			"data": {
				"raceId": 1,
				"saleStatus": "SCHEDULED",
				"saleOpenDate": "2026-10-20",
				"courseIds": [1, 2]
			}
		}
		""";

	static final String REGISTER_INVALID_INPUT = """
		{ "status": 400, "message": "참가비는 0원보다 커야 합니다.", "data": null }
		""";

	static final String INVALID_BODY = """
		{ "status": 400, "message": "요청 본문 형식이 올바르지 않습니다.", "data": null }
		""";

	static final String INVALID_SCHEDULE = """
		{ "status": 400, "message": "크루 예매 마감 일시는 시작 일시보다 늦어야 합니다.", "data": null }
		""";

	static final String SALE_OPEN_TOO_SOON = """
		{
			"status": 400,
			"message": "판매 시작일은 오늘로부터 이틀 뒤(10월 16일)부터 정할 수 있습니다. 크루 예매 시작·개인 예매 오픈 일시를 확인해주세요.",
			"data": null
		}
		""";

	static final String TSHIRT_SIZE_WITHOUT_KIT = """
		{ "status": 400, "message": "키트가 없는 종목은 티셔츠 사이즈를 작성할 수 없습니다. (종목: 10K)", "data": null }
		""";

	static final String KIT_PRICE_OVER_PRICE = """
		{ "status": 400, "message": "키트 금액은 참가비보다 클 수 없습니다. (종목: 10K)", "data": null }
		""";

	static final String THUMBNAIL_REQUIRED = """
		{ "status": 400, "message": "10K 종목의 대표 이미지를 1장 연결해주세요.", "data": null }
		""";

	static final String DUPLICATE_FILE = """
		{ "status": 400, "message": "같은 파일을 두 번 연결할 수 없습니다. (fileId: 3, fileType: COURSE)", "data": null }
		""";

	static final String COURSE_NOT_IN_RACE = """
		{ "status": 400, "message": "이 대회의 종목이 아니거나 같은 종목을 두 번 보냈습니다. (courseId: 5)", "data": null }
		""";

	static final String INVALID_VALUE_TYPE = """
		{ "status": 400, "message": "요청 값의 형식이 올바르지 않습니다.", "data": null }
		""";

	static final String INVALID_MEMBER_ID = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "회원 ID가 올바르지 않습니다.", "data": null }
		""";

	static final String MISSING_IDEMPOTENCY_KEY = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "필수 요청 헤더(Idempotency-Key)가 없습니다.", "data": null }
		""";

	static final String INVALID_IDEMPOTENCY_KEY = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "Idempotency-Key는 1~64자로 보내주세요.", "data": null }
		""";

	static final String LOGIN_REQUIRED = """
		{ "status": 401, "code": "LOGIN_REQUIRED", "message": "로그인이 필요합니다.", "data": null }
		""";

	static final String SELLER_NOT_APPROVED = """
		{ "status": 403, "message": "승인된 판매자만 대회를 등록·수정할 수 있습니다.", "data": null }
		""";

	static final String NOT_OWNER = """
		{ "status": 403, "message": "본인이 등록한 대회만 처리할 수 있습니다.", "data": null }
		""";

	static final String RACE_NOT_FOUND = """
		{ "status": 404, "message": "존재하지 않는 대회입니다.", "data": null }
		""";

	static final String PRICE_CHANGED = """
		{ "status": 409, "message": "판매가 시작된 종목은 참가비를 바꿀 수 없습니다. (종목: 10K)", "data": null }
		""";

	static final String KIT_PRICE_CHANGED = """
		{ "status": 409, "message": "판매가 시작된 종목은 키트 금액을 바꿀 수 없습니다. (종목: 10K)", "data": null }
		""";

	static final String REFUND_DEADLINE_ADVANCED = """
		{ "status": 409, "message": "판매가 시작된 대회는 환불 기준일을 앞당길 수 없습니다.", "data": null }
		""";

	static final String QUOTA_DECREASED = """
		{ "status": 409, "message": "판매가 시작된 종목은 정원을 줄일 수 없습니다. (종목: 10K)", "data": null }
		""";

	static final String COURSE_CONDITION_CHANGED = """
		{ "status": 409, "message": "판매가 시작된 종목은 참가 조건(거리)을 바꿀 수 없습니다. (종목: 10K)", "data": null }
		""";

	static final String TSHIRT_SIZE_CHANGED = """
		{ "status": 409, "message": "판매가 시작된 종목은 티셔츠 사이즈를 바꿀 수 없습니다. (종목: 10K)", "data": null }
		""";

	static final String SCHEDULE_PASSED = """
		{ "status": 409, "message": "이미 지난 크루 예매 시작 일시는 바꿀 수 없습니다.", "data": null }
		""";

	static final String SCHEDULE_TO_PAST = """
		{ "status": 409, "message": "개인 예매 오픈 일시를 지난 시각으로 바꿀 수 없습니다.", "data": null }
		""";

	static final String COURSE_REMOVED = """
		{ "status": 409, "message": "판매가 시작된 대회는 종목을 뺄 수 없습니다. (종목: 10K)", "data": null }
		""";

	static final String UPDATE_CANCELED = """
		{ "status": 409, "message": "취소된 대회는 수정할 수 없습니다.", "data": null }
		""";

	static final String UPDATE_CLOSED = """
		{ "status": 409, "message": "판매가 끝난 대회는 수정할 수 없습니다.", "data": null }
		""";

	static final String SALE_OPEN_SCHEDULE_CHANGED = """
		{ "status": 409, "message": "판매가 시작된 대회는 개인 예매 오픈 일시를 바꿀 수 없습니다.", "data": null }
		""";

	static final String CANCEL_SUCCESS = """
		{
			"status": 200,
			"message": "대회를 취소했습니다.",
			"data": {
				"raceId": 1,
				"saleStatus": "CANCELED",
				"saleOpenDate": "2026-10-20",
				"courseIds": [1]
			}
		}
		""";

	static final String ALREADY_CANCELED = """
		{ "status": 409, "message": "이미 취소된 대회입니다.", "data": null }
		""";

	static final String CANCEL_AFTER_CLOSED = """
		{ "status": 409, "message": "판매가 끝난 대회는 취소할 수 없습니다.", "data": null }
		""";

	static final String CANCEL_STATUS_CHANGED = """
		{ "status": 409, "message": "대회 상태가 바뀌어 취소하지 못했습니다. 다시 조회해 주세요.", "data": null }
		""";

	static final String SEARCH_SUCCESS = """
		{
			"status": 200,
			"message": "대회 목록을 조회했습니다.",
			"data": {
				"races": [
					{
						"raceId": 1,
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
						]
					}
				],
				"page": 0,
				"size": 20,
				"totalElements": 1,
				"totalPages": 1
			}
		}
		""";

	static final String SUGGEST_SUCCESS = """
		{
			"status": 200,
			"message": "연관검색어를 조회했습니다.",
			"data": {
				"races": [
					{ "raceId": 1, "title": "2026 용감한 쿠키RUN in 서울" }
				],
				"courses": [
					{ "raceCourseId": 1, "raceId": 1, "label": "2026 용감한 쿠키RUN in 서울 10K" }
				]
			}
		}
		""";

	static final String MINE_SUCCESS = """
		{
			"status": 200,
			"message": "내 대회 목록을 조회했습니다.",
			"data": [
				{
					"raceId": 1,
					"title": "2026 용감한 쿠키RUN in 서울",
					"raceDate": "2026-11-01T08:00:00",
					"saleStatus": "ON_SALE",
					"saleOpenDate": "2026-10-20",
					"applicationCloseAt": "2026-10-31T18:00:00",
					"courses": [
						{
							"raceCourseId": 1,
							"name": "10K",
							"price": 45000,
							"status": "OPEN",
							"quota": 600,
							"reserved": 5,
							"sold": 15
						}
					]
				}
			]
		}
		""";

	static final String DETAIL_SUCCESS = """
		{
			"status": 200,
			"message": "대회를 조회했습니다.",
			"data": {
				"raceId": 1,
				"title": "2026 용감한 쿠키RUN in 서울",
				"description": "서울숲에서 달리는 가을 러닝 대회",
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
				"refundDeadline": "2026-10-31",
				"refundPolicy": "환불 기한까지 취소하면 전액 환불됩니다.",
				"crewMinCount": 5,
				"crewMaxCount": null,
				"scheduleChangedAt": null,
				"seller": {
					"sellerId": 7001,
					"sellerType": "BUSINESS",
					"businessName": "(주)쿠키런",
					"businessNumber": "123-45-67890",
					"representativeName": "김대표"
				},
				"courses": [
					{
						"raceCourseId": 1,
						"name": "10K",
						"courseType": "KM_10",
						"surface": "ROAD",
						"distanceKm": 10.00,
						"timeLimitMinutes": 90,
						"minAge": 14,
						"maxAge": 70,
						"price": 45000,
						"kitIncluded": true,
						"kitPrice": 0,
						"tshirtSizeOption": ["S", "M", "L", "XL"],
						"status": "OPEN",
						"crewQuota": 0,
						"individualQuota": 600,
						"crewRemaining": 0,
						"individualRemaining": 580
					}
				]
			}
		}
		""";

	static final String PAGE_SIZE_EXCEEDED = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "한 번에 100개까지 조회할 수 있습니다.", "data": null }
		""";

	static final String PAGE_TOO_LARGE = """
		{ "status": 400, "message": "페이지 번호가 너무 큽니다.", "data": null }
		""";

	static final String PRICE_NEGATIVE = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "참가비 범위는 0원 이상이어야 합니다.", "data": null }
		""";

	static final String KEYWORD_BLANK = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "검색어를 작성해주세요.", "data": null }
		""";

	static final String KEYWORD_MISSING = """
		{ "status": 400, "message": "필수 요청 값(keyword)이 없습니다.", "data": null }
		""";

	private RaceApiExamples() {
	}
}
