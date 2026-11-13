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

	static final String KIT_PRICE_OVER_PRICE = """
		{ "status": 400, "message": "키트 금액은 참가비보다 클 수 없습니다. (종목: 10K)", "data": null }
		""";

	static final String KIT_OPTION_DUPLICATED = """
		{ "status": 400, "message": "같은 키트 옵션을 두 번 작성할 수 없습니다. (옵션: T-shirt L)", "data": null }
		""";

	static final String KIT_OPTION_NAME_CASE = """
		{ "status": 400, "message": "같은 키트 옵션 이름은 대소문자까지 똑같이 작성해주세요. (T-shirt, t-shirt)", "data": null }
		""";

	static final String KIT_OPTION_REMOVED = """
		{
			"status": 409,
			"message": "판매가 시작된 대회는 키트 옵션을 빼거나 이름·값을 바꿀 수 없습니다. (옵션: T-shirt L)",
			"data": null
		}
		""";

	static final String KIT_OPTION_STOCK_TYPE_CHANGED = """
		{
			"status": 409,
			"message": "판매가 시작된 대회는 키트 옵션을 맞춤 제작과 준비 수량 사이에서 바꿀 수 없습니다. (옵션: T-shirt L)",
			"data": null
		}
		""";

	static final String KIT_OPTION_STOCK_DECREASED = """
		{ "status": 409, "message": "판매가 시작된 대회는 키트 옵션 준비 수량을 줄일 수 없습니다. (옵션: T-shirt L)", "data": null }
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
		{ "status": 404, "code": "RACE_NOT_FOUND", "message": "존재하지 않는 대회입니다.", "data": null }
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

	static final String STOP_SUCCESS = """
		{
			"status": 200,
			"message": "대회 판매를 멈췄습니다.",
			"data": {
				"raceId": 1,
				"saleStatus": "STOPPED",
				"saleOpenDate": "2026-10-20",
				"courseIds": [1]
			}
		}
		""";

	static final String RESUME_SUCCESS = """
		{
			"status": 200,
			"message": "대회 판매를 다시 열었습니다.",
			"data": {
				"raceId": 1,
				"saleStatus": "ON_SALE",
				"saleOpenDate": "2026-10-20",
				"courseIds": [1]
			}
		}
		""";

	static final String STOP_BEFORE_SALE = """
		{
			"status": 409,
			"message": "판매 중인 대회만 판매를 멈출 수 있습니다. 판매 시작 전에는 대회 수정으로 일정을 바꿔 주세요.",
			"data": null
		}
		""";

	static final String ALREADY_STOPPED = """
		{ "status": 409, "message": "이미 판매를 멈춘 대회입니다.", "data": null }
		""";

	static final String STOP_AFTER_APPLICATION_CLOSE = """
		{ "status": 409, "message": "접수 마감이 지난 대회는 판매를 멈출 수 없습니다.", "data": null }
		""";

	static final String RESUME_NOT_STOPPED = """
		{ "status": 409, "message": "판매를 멈춘 대회만 다시 열 수 있습니다.", "data": null }
		""";

	static final String RESUME_AFTER_APPLICATION_CLOSE = """
		{ "status": 409, "message": "접수 마감이 지나 판매를 다시 열 수 없습니다.", "data": null }
		""";

	static final String SALE_STATUS_CHANGED = """
		{ "status": 409, "message": "대회 상태가 바뀌어 처리하지 못했습니다. 다시 조회해 주세요.", "data": null }
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

	static final String KIT_OPTIONS_SUCCESS = """
		{
			"status": 200,
			"message": "키트 옵션 목록을 조회했습니다.",
			"data": {
				"raceId": 1,
				"kitOptions": [
					{
						"name": "T-shirt",
						"values": [
							{ "kitOptionId": 11, "value": "M", "stock": 100 },
							{ "kitOptionId": 12, "value": "L", "stock": 80 }
						]
					},
					{
						"name": "socks",
						"values": [
							{ "kitOptionId": 21, "value": "230-250", "stock": null },
							{ "kitOptionId": 22, "value": "255-280", "stock": null }
						]
					}
				]
			}
		}
		""";

	static final String KIT_OPTIONS_REGISTER_SUCCESS = """
		{
			"status": 201,
			"message": "키트 옵션을 등록했습니다.",
			"data": {
				"raceId": 1,
				"kitOptions": [
					{
						"name": "T-shirt",
						"values": [
							{ "kitOptionId": 11, "value": "M", "stock": 100 },
							{ "kitOptionId": 12, "value": "L", "stock": 80 }
						]
					}
				]
			}
		}
		""";

	static final String KIT_OPTIONS_UPDATE_SUCCESS = """
		{
			"status": 200,
			"message": "키트 옵션을 수정했습니다.",
			"data": {
				"raceId": 1,
				"kitOptions": [
					{
						"name": "T-shirt",
						"values": [
							{ "kitOptionId": 11, "value": "M", "stock": 120 },
							{ "kitOptionId": 12, "value": "L", "stock": 80 },
							{ "kitOptionId": 13, "value": "XL", "stock": 50 }
						]
					}
				]
			}
		}
		""";

	static final String KIT_OPTIONS_INVALID_INPUT = """
		{ "status": 400, "message": "키트 옵션 이름을 작성해주세요.", "data": null }
		""";

	static final String KIT_OPTIONS_EMPTY = """
		{ "status": 400, "message": "키트 옵션을 1개 이상 작성해주세요.", "data": null }
		""";

	static final String KIT_OPTIONS_ALREADY_REGISTERED = """
		{ "status": 409, "message": "이미 키트 옵션을 등록한 대회입니다. 키트 옵션 수정으로 바꿔 주세요.", "data": null }
		""";

	static final String KIT_OPTIONS_AFTER_SALE_START = """
		{ "status": 409, "message": "판매가 시작된 대회에는 키트 옵션을 등록할 수 없습니다.", "data": null }
		""";

	static final String KIT_OPTION_ADDED = """
		{ "status": 409, "message": "판매가 시작된 대회는 키트 옵션을 더할 수 없습니다. (옵션: T-shirt XL)", "data": null }
		""";

	static final String KIT_OPTIONS_RACE_CANCELED = """
		{ "status": 409, "message": "취소된 대회는 키트 옵션을 바꿀 수 없습니다.", "data": null }
		""";

	static final String KIT_OPTIONS_RACE_CLOSED = """
		{ "status": 409, "message": "판매가 끝난 대회는 키트 옵션을 바꿀 수 없습니다.", "data": null }
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

	static final String KEYWORD_TOO_LONG = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "검색어는 100자 이하로 작성해주세요.", "data": null }
		""";

	// AI 추천 (BUK-162). 비슷한 순서이고, 카드 모양은 공개 목록(SEARCH_SUCCESS)의 races와 같다
	static final String RECOMMEND_SUCCESS = """
		{
			"status": 200,
			"message": "AI 추천 대회를 조회했습니다.",
			"data": [
				{
					"raceId": 3,
					"title": "2026 한강 나이트런",
					"location": "여의도 한강공원",
					"regionSido": "서울특별시",
					"regionSigungu": "영등포구",
					"raceDate": "2026-12-05T19:00:00",
					"saleStatus": "ON_SALE",
					"saleOpenDate": "2026-11-10",
					"crewSaleStartAt": null,
					"crewSaleEndAt": null,
					"individualOpenAt": "2026-11-10T09:00:00",
					"applicationCloseAt": "2026-11-30T18:00:00",
					"minPrice": 40000,
					"courses": [
						{
							"raceCourseId": 7,
							"name": "10K",
							"courseType": "KM_10",
							"distanceKm": 10.00,
							"price": 40000,
							"status": "OPEN",
							"individualRemaining": 300
						}
					]
				}
			]
		}
		""";

	static final String RECOMMEND_EMPTY = """
		{ "status": 200, "message": "AI 추천 대회를 조회했습니다.", "data": [] }
		""";

	private RaceApiExamples() {
	}
}
