package com.bukang.member.in;

/**
 * ApiV1MemberController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 */
final class MemberApiExamples {
	static final String EMAIL_AVAILABILITY_SUCCESS = """
		{ "status": 200, "message": "이메일 사용 가능 여부를 확인했습니다.", "data": { "available": true } }
		""";

	static final String EMAIL_AVAILABILITY_INVALID_INPUT = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "이메일 형식에 맞게 작성해주세요.", "data": null }
		""";

	static final String FIND_ME_SUCCESS = """
		{
			"status": 200,
			"message": "내 정보를 조회했습니다.",
			"data": {
				"id": 1,
				"createDate": "2026-10-05T21:25:35",
				"modifyDate": "2026-10-05T21:25:35",
				"nickname": "러너1",
				"email": "runner1@crewrun.com",
				"phone": "010-1234-5678",
				"name": "김러너",
				"birthDate": "1995-04-12",
				"socialProviders": ["KAKAO"],
				"passwordSet": true
			}
		}
		""";

	static final String MODIFY_ME_SUCCESS = """
		{
			"status": 200,
			"message": "내 정보를 수정했습니다.",
			"data": {
				"id": 1,
				"createDate": "2026-10-05T21:25:35",
				"modifyDate": "2026-10-15T10:12:08",
				"nickname": "러너2",
				"email": "runner1@crewrun.com",
				"phone": "010-9876-5432",
				"name": "김러너",
				"birthDate": "1995-04-12",
				"socialProviders": ["KAKAO"],
				"passwordSet": true
			}
		}
		""";

	static final String MODIFY_ME_INVALID_INPUT = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "휴대폰 번호 형식에 맞게 작성해주세요.", "data": null }
		""";

	static final String INVALID_BODY = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "요청 본문 형식이 올바르지 않습니다.", "data": null }
		""";

	static final String MODIFY_ME_UNDER_AGE = """
		{ "status": 400, "code": "MEMBER_UNDER_AGE", "message": "만 14세 미만의 생년월일로는 수정할 수 없습니다.", "data": null }
		""";

	static final String DUPLICATE_NICKNAME = """
		{ "status": 409, "code": "MEMBER_DUPLICATE_NICKNAME", "message": "이미 사용 중인 닉네임입니다.", "data": null }
		""";

	static final String DUPLICATE_PHONE = """
		{ "status": 409, "code": "MEMBER_DUPLICATE_PHONE", "message": "이미 사용 중인 전화번호입니다.", "data": null }
		""";

	static final String LOGIN_REQUIRED = """
		{ "status": 401, "code": "LOGIN_REQUIRED", "message": "로그인이 필요합니다.", "data": null }
		""";

	static final String MEMBER_NOT_FOUND = """
		{ "status": 404, "code": "MEMBER_NOT_FOUND", "message": "존재하지 않는 회원입니다.", "data": null }
		""";

	static final String WITHDRAW_ME_SUCCESS = """
		{ "status": 200, "message": "탈퇴했습니다.", "data": null }
		""";

	static final String WITHDRAW_ME_NOT_FOUND = """
		{ "status": 404, "code": "MEMBER_NOT_FOUND", "message": "존재하지 않는 회원입니다.", "data": null }
		""";

	static final String WITHDRAW_CASH_LEFT = """
		{ "status": 409, "code": "MEMBER_WITHDRAW_CASH_LEFT",
			"message": "남은 예치금이 있어 탈퇴할 수 없습니다. 예치금을 모두 사용하거나 출금한 뒤 탈퇴해주세요.", "data": null }
		""";

	static final String WITHDRAW_TICKET_LEFT = """
		{ "status": 409, "code": "MEMBER_WITHDRAW_TICKET_LEFT",
			"message": "끝나지 않은 참가권이 있어 탈퇴할 수 없습니다. 대회가 끝나거나 참가권을 취소한 뒤 탈퇴해주세요.", "data": null }
		""";

	static final String WITHDRAW_CHECK_FAILED = """
		{ "status": 503, "code": "MEMBER_WITHDRAW_CHECK_FAILED",
			"message": "예치금을 확인하지 못했습니다. 잠시 뒤 다시 시도해주세요.", "data": null }
		""";

	private MemberApiExamples() {
	}
}
