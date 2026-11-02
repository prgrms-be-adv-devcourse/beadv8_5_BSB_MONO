package com.bukang.payout.in;

/**
 * ApiV1PayoutHoldController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 */
final class PayoutApiExamples {
	static final String HOLD_SUCCESS = """
		{
			"status": 201,
			"message": "지급보류를 걸었습니다.",
			"data": {
				"id": 1,
				"holdCode": "HD-20261012-0001",
				"payoutId": 1,
				"reasonType": "ACCOUNT_NOT_REGISTERED",
				"reason": "정산 계좌 등록 요청 중",
				"status": "HELD",
				"heldBy": 1,
				"heldAt": "2026-10-12T10:00:00",
				"releasedBy": null,
				"releasedAt": null
			}
		}
		""";

	static final String RELEASE_SUCCESS = """
		{
			"status": 200,
			"message": "지급보류를 풀었습니다.",
			"data": {
				"id": 1,
				"holdCode": "HD-20261012-0001",
				"payoutId": 1,
				"reasonType": "ACCOUNT_NOT_REGISTERED",
				"reason": "정산 계좌 등록 요청 중",
				"status": "RELEASED",
				"heldBy": 1,
				"heldAt": "2026-10-12T10:00:00",
				"releasedBy": 1,
				"releasedAt": "2026-10-15T09:00:00"
			}
		}
		""";

	static final String REASON_TYPE_REQUIRED = """
		{ "status": 400, "message": "보류 사유를 선택해주세요.", "data": null }
		""";

	static final String REASON_TOO_LONG = """
		{ "status": 400, "message": "사유 상세는 500자 이하로 작성해주세요.", "data": null }
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

	static final String LOGIN_REQUIRED = """
		{ "status": 401, "code": "LOGIN_REQUIRED", "message": "로그인이 필요합니다.", "data": null }
		""";

	// 관리자 API(지급보류 걸기·풀기, 회수 완료) 공통 (PRO-265)
	static final String NOT_ADMIN = """
		{ "status": 403, "code": "PAYOUT_NOT_ADMIN", "message": "관리자만 이용할 수 있습니다.", "data": null }
		""";

	static final String MEMBER_NOT_FOUND = """
		{ "status": 404, "code": "PAYOUT_MEMBER_NOT_FOUND", "message": "존재하지 않는 회원입니다.", "data": null }
		""";

	static final String MEMBER_UNAVAILABLE = """
		{ "status": 503, "code": "PAYOUT_MEMBER_UNAVAILABLE",
			"message": "회원 서비스에 연결하지 못해 관리자인지 확인하지 못했습니다. 잠시 뒤 다시 시도해주세요.", "data": null }
		""";

	static final String PAYOUT_NOT_FOUND = """
		{ "status": 404, "message": "정산 내역을 찾을 수 없습니다.", "data": null }
		""";

	static final String HOLD_NOT_FOUND = """
		{ "status": 404, "message": "지급보류를 찾을 수 없습니다.", "data": null }
		""";

	static final String ALREADY_HELD = """
		{ "status": 409, "message": "이미 지급보류 중인 정산 내역입니다.", "data": null }
		""";

	static final String ALREADY_PAID = """
		{ "status": 409, "message": "이미 지급 완료된 정산 내역입니다.", "data": null }
		""";

	static final String HOLD_CANCELED_PAYOUT = """
		{ "status": 409, "message": "취소된 정산 내역입니다.", "data": null }
		""";

	static final String RELEASE_CANCELED_PAYOUT = """
		{ "status": 409, "message": "취소된 정산 내역의 지급보류는 풀 수 없습니다.", "data": null }
		""";

	static final String ALREADY_RELEASED = """
		{ "status": 409, "message": "이미 해제된 지급보류입니다.", "data": null }
		""";

	static final String CONCURRENT_UPDATE = """
		{ "status": 409, "message": "다른 요청이 먼저 정산 내역을 바꿨습니다. 다시 시도해주세요.", "data": null }
		""";

	private PayoutApiExamples() {
	}
}
