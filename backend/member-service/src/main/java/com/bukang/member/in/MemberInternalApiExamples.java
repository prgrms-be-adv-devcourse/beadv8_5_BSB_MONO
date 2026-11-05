package com.bukang.member.in;

/**
 * InternalV1MemberController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 */
final class MemberInternalApiExamples {
	static final String SELLER_APPROVED = """
		{
			"status": 200,
			"message": "회원의 판매자 신청을 조회했습니다.",
			"data": { "sellerId": 3, "status": "APPROVED" }
		}
		""";

	static final String SELLER_PENDING = """
		{
			"status": 200,
			"message": "회원의 판매자 신청을 조회했습니다.",
			"data": { "sellerId": 4, "status": "PENDING" }
		}
		""";

	static final String SELLER_NONE = """
		{
			"status": 200,
			"message": "회원의 판매자 신청을 조회했습니다.",
			"data": { "sellerId": null, "status": null }
		}
		""";

	static final String ADMIN = """
		{ "status": 200, "message": "관리자 여부를 조회했습니다.", "data": { "admin": true } }
		""";

	static final String NOT_ADMIN = """
		{ "status": 200, "message": "관리자 여부를 조회했습니다.", "data": { "admin": false } }
		""";

	static final String MEMBER_NOT_FOUND = """
		{ "status": 404, "code": "MEMBER_NOT_FOUND", "message": "존재하지 않는 회원입니다.", "data": null }
		""";

	static final String UNSUBSCRIBE_LINK = """
		{
			"status": 200,
			"message": "수신 거부 링크를 발급했습니다.",
			"data": {
				"url": "https://crewrun.example/unsubscribe?token=7.1793750400.q1b2c3",
				"expiresAt": "2027-11-04T14:30:00"
			}
		}
		""";

	private MemberInternalApiExamples() {
	}
}
