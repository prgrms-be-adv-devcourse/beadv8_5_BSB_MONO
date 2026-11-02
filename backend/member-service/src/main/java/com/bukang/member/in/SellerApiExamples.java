package com.bukang.member.in;

/**
 * ApiV1SellerController, ApiV1AdminSellerController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 */
final class SellerApiExamples {
	static final String APPLY_SUCCESS = """
		{
			"status": 201,
			"message": "판매자 등록을 신청했습니다.",
			"data": {
				"id": 1,
				"memberId": 1,
				"sellerType": "BUSINESS",
				"businessName": "크루런 스포츠",
				"businessNumber": "123-45-67890",
				"representativeName": "김러너",
				"status": "PENDING",
				"requestedAt": "2026-10-05T21:25:35",
				"reviewedAt": null,
				"rejectReason": null,
				"resultCheckedAt": null
			}
		}
		""";

	static final String FIND_MINE_SUCCESS = """
		{
			"status": 200,
			"message": "판매자 신청 내역을 조회했습니다.",
			"data": {
				"id": 1,
				"memberId": 1,
				"sellerType": "BUSINESS",
				"businessName": "크루런 스포츠",
				"businessNumber": "123-45-67890",
				"representativeName": "김러너",
				"status": "REJECTED",
				"requestedAt": "2026-10-05T21:25:35",
				"reviewedAt": "2026-10-06T10:12:08",
				"rejectReason": "사업자등록번호를 확인할 수 없습니다.",
				"resultCheckedAt": null
			}
		}
		""";

	static final String FIND_ALL_SUCCESS = """
		{
			"status": 200,
			"message": "판매자 신청 목록을 조회했습니다.",
			"data": [
				{
					"id": 1,
					"memberId": 1,
					"sellerType": "BUSINESS",
					"businessName": "크루런 스포츠",
					"businessNumber": "123-45-67890",
					"representativeName": "김러너",
					"status": "PENDING",
					"requestedAt": "2026-10-05T21:25:35",
					"reviewedAt": null,
					"rejectReason": null,
					"resultCheckedAt": null
				}
			]
		}
		""";

	static final String APPROVE_SUCCESS = """
		{
			"status": 200,
			"message": "판매자 신청을 승인했습니다.",
			"data": {
				"id": 1,
				"memberId": 1,
				"sellerType": "BUSINESS",
				"businessName": "크루런 스포츠",
				"businessNumber": "123-45-67890",
				"representativeName": "김러너",
				"status": "APPROVED",
				"requestedAt": "2026-10-05T21:25:35",
				"reviewedAt": "2026-10-06T10:12:08",
				"rejectReason": null,
				"resultCheckedAt": null
			}
		}
		""";

	static final String REJECT_SUCCESS = """
		{
			"status": 200,
			"message": "판매자 신청을 반려했습니다.",
			"data": {
				"id": 1,
				"memberId": 1,
				"sellerType": "BUSINESS",
				"businessName": "크루런 스포츠",
				"businessNumber": "123-45-67890",
				"representativeName": "김러너",
				"status": "REJECTED",
				"requestedAt": "2026-10-05T21:25:35",
				"reviewedAt": "2026-10-06T10:12:08",
				"rejectReason": "사업자등록번호를 확인할 수 없습니다.",
				"resultCheckedAt": null
			}
		}
		""";

	static final String CHECK_RESULT_SUCCESS = """
		{
			"status": 200,
			"message": "판매자 신청 결과를 확인했습니다.",
			"data": {
				"id": 1,
				"memberId": 1,
				"sellerType": "BUSINESS",
				"businessName": "크루런 스포츠",
				"businessNumber": "123-45-67890",
				"representativeName": "김러너",
				"status": "APPROVED",
				"requestedAt": "2026-10-05T21:25:35",
				"reviewedAt": "2026-10-06T10:12:08",
				"rejectReason": null,
				"resultCheckedAt": "2026-10-06T12:30:41"
			}
		}
		""";

	static final String APPLY_INVALID_INPUT = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "사업자등록번호는 숫자 10자리로 작성해주세요.", "data": null }
		""";

	static final String REJECT_INVALID_INPUT = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "반려 사유를 작성해주세요.", "data": null }
		""";

	static final String INVALID_BODY = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "요청 본문 형식이 올바르지 않습니다.", "data": null }
		""";

	static final String LOGIN_REQUIRED = """
		{ "status": 401, "code": "LOGIN_REQUIRED", "message": "로그인이 필요합니다.", "data": null }
		""";

	static final String NOT_ADMIN = """
		{ "status": 403, "code": "MEMBER_NOT_ADMIN", "message": "관리자만 이용할 수 있습니다.", "data": null }
		""";

	static final String MEMBER_NOT_FOUND = """
		{ "status": 404, "code": "MEMBER_NOT_FOUND", "message": "존재하지 않는 회원입니다.", "data": null }
		""";

	static final String MY_SELLER_NOT_FOUND = """
		{ "status": 404, "code": "SELLER_NOT_FOUND", "message": "판매자 신청 내역이 없습니다.", "data": null }
		""";

	static final String SELLER_NOT_FOUND = """
		{ "status": 404, "code": "SELLER_NOT_FOUND", "message": "존재하지 않는 판매자 신청입니다.", "data": null }
		""";

	static final String ALREADY_APPLIED = """
		{ "status": 409, "code": "SELLER_ALREADY_APPLIED", "message": "이미 심사 중이거나 승인된 판매자 신청이 있습니다.", "data": null }
		""";

	static final String ALREADY_REVIEWED = """
		{ "status": 409, "code": "SELLER_ALREADY_REVIEWED", "message": "이미 심사한 판매자 신청입니다.", "data": null }
		""";

	static final String NOT_REVIEWED = """
		{ "status": 409, "code": "SELLER_NOT_REVIEWED", "message": "아직 심사 중인 판매자 신청입니다.", "data": null }
		""";

	private SellerApiExamples() {
	}
}
