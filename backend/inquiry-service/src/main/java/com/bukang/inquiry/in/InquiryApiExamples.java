package com.bukang.inquiry.in;

/**
 * ApiV1InquiryController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 */
final class InquiryApiExamples {
	static final String CREATE_SUCCESS = """
		{
			"status": 201,
			"message": "문의를 남겼습니다.",
			"data": {
				"id": 12,
				"raceId": 1,
				"type": "RACE_VENUE",
				"assignee": "SELLER",
				"title": "주차는 어디에 하나요",
				"body": "대회장 근처 주차장이 있는지 알고 싶어요.",
				"status": "WAITING",
				"answer": null,
				"answeredAt": null,
				"createDate": "2026-11-10T10:00:00"
			}
		}
		""";

	static final String FIND_SUCCESS = """
		{
			"status": 200,
			"message": "문의를 조회했습니다.",
			"data": {
				"id": 12,
				"raceId": 1,
				"type": "RACE_VENUE",
				"assignee": "SELLER",
				"title": "주차는 어디에 하나요",
				"body": "대회장 근처 주차장이 있는지 알고 싶어요.",
				"status": "ANSWERED",
				"answer": "정문 앞 공영주차장을 이용해 주세요.",
				"answeredAt": "2026-11-10T15:30:00",
				"createDate": "2026-11-10T10:00:00"
			}
		}
		""";

	static final String LIST_SUCCESS = """
		{
			"status": 200,
			"message": "문의 목록을 조회했습니다.",
			"data": {
				"inquiries": [
					{
						"id": 12,
						"raceId": 1,
						"type": "RACE_VENUE",
						"assignee": "SELLER",
						"title": "주차는 어디에 하나요",
						"body": "대회장 근처 주차장이 있는지 알고 싶어요.",
						"status": "WAITING",
						"answer": null,
						"answeredAt": null,
						"createDate": "2026-11-10T10:00:00"
					}
				],
				"page": 0,
				"size": 20,
				"totalElements": 1,
				"totalPages": 1
			}
		}
		""";

	static final String ANSWER_SUCCESS = """
		{
			"status": 200,
			"message": "답변을 남겼습니다.",
			"data": {
				"id": 12,
				"raceId": 1,
				"type": "RACE_VENUE",
				"assignee": "SELLER",
				"title": "주차는 어디에 하나요",
				"body": "대회장 근처 주차장이 있는지 알고 싶어요.",
				"status": "ANSWERED",
				"answer": "정문 앞 공영주차장을 이용해 주세요.",
				"answeredAt": "2026-11-10T15:30:00",
				"createDate": "2026-11-10T10:00:00"
			}
		}
		""";

	static final String INVALID_BODY = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "제목은 100자 이하로 입력해주세요.", "data": null }
		""";

	static final String INVALID_PAGE = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "한 번에 100개까지 조회할 수 있습니다.", "data": null }
		""";

	static final String INVALID_VALUE_TYPE = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "요청 값의 형식이 올바르지 않습니다.", "data": null }
		""";

	static final String INVALID_MEMBER_ID = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "회원 ID가 올바르지 않습니다.", "data": null }
		""";

	static final String LOGIN_REQUIRED = """
		{ "status": 401, "code": "LOGIN_REQUIRED", "message": "로그인이 필요합니다.", "data": null }
		""";

	static final String NOT_ADMIN = """
		{ "status": 403, "code": "INQUIRY_ADMIN_FORBIDDEN", "message": "관리자만 이용할 수 있습니다.", "data": null }
		""";

	static final String NOT_FOUND = """
		{ "status": 404, "code": "INQUIRY_NOT_FOUND", "message": "문의를 찾을 수 없습니다.", "data": null }
		""";

	static final String RACE_NOT_FOUND = """
		{ "status": 404, "code": "INQUIRY_RACE_NOT_FOUND", "message": "대회를 찾을 수 없습니다.", "data": null }
		""";

	static final String ALREADY_ANSWERED = """
		{ "status": 409, "code": "INQUIRY_ALREADY_ANSWERED", "message": "이미 답변한 문의입니다.", "data": null }
		""";

	static final String PRODUCT_UNAVAILABLE = """
		{ "status": 503, "code": "INQUIRY_EXTERNAL_SERVICE_UNAVAILABLE", "message": "상품 서비스에 연결하지 못했습니다. 잠시 뒤 다시 시도해주세요.", "data": null }
		""";

	static final String MEMBER_UNAVAILABLE = """
		{ "status": 503, "code": "INQUIRY_EXTERNAL_SERVICE_UNAVAILABLE", "message": "회원 서비스에 연결하지 못했습니다. 잠시 뒤 다시 시도해주세요.", "data": null }
		""";

	private InquiryApiExamples() {
	}
}
