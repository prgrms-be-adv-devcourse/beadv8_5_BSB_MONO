package com.bukang.member.in;

/**
 * ApiV1KakaoCrewInviteController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 */
final class KakaoInviteApiExamples {
	static final String CONSENT_TICKET_SUCCESS = """
		{ "status": 201, "message": "동의 표 발급 성공", "data": null }
		""";

	static final String FRIENDS_SUCCESS = """
		{
			"status": 200,
			"message": "카카오 친구를 조회했습니다.",
			"data": [
				{ "uuid": "abcdefg0001", "nickname": "이수민", "profileImageUrl": "https://k.kakaocdn.net/dn/a.jpg" }
			]
		}
		""";

	static final String SEND_SUCCESS = """
		{
			"status": 200,
			"message": "카카오톡으로 초대를 보냈습니다.",
			"data": {
				"sentUuids": ["abcdefg0001"],
				"failures": [ { "uuid": "abcdefg0002", "reason": "DAILY_LIMIT" } ]
			}
		}
		""";

	static final String INVALID_RECEIVERS = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "받는 사람이 중복되었습니다.", "data": null }
		""";

	static final String LOGIN_REQUIRED = """
		{ "status": 401, "code": "LOGIN_REQUIRED", "message": "로그인이 필요합니다.", "data": null }
		""";

	static final String CONSENT_REQUIRED = """
		{
			"status": 403,
			"code": "KAKAO_MESSAGE_CONSENT_REQUIRED",
			"message": "카카오톡으로 보내려면 친구 목록과 메시지 보내기 동의가 필요합니다.",
			"data": null
		}
		""";

	static final String CREW_NOT_MEMBER = """
		{ "status": 403, "code": "CREW_NOT_MEMBER", "message": "이 크루의 크루원만 이용할 수 있습니다.", "data": null }
		""";

	static final String CREW_INVITE_FORBIDDEN = """
		{ "status": 403, "code": "CREW_INVITE_FORBIDDEN", "message": "크루장과 운영진만 초대할 수 있습니다.", "data": null }
		""";

	static final String CREW_NOT_FOUND = """
		{ "status": 404, "code": "CREW_NOT_FOUND", "message": "존재하지 않는 크루입니다.", "data": null }
		""";

	static final String KAKAO_NOT_LINKED = """
		{ "status": 409, "code": "KAKAO_NOT_LINKED", "message": "카카오 계정을 먼저 연결해 주세요.", "data": null }
		""";

	static final String DAILY_LIMIT = """
		{ "status": 429, "code": "KAKAO_MESSAGE_DAILY_LIMIT", "message": "오늘은 카카오톡으로 더 보낼 수 없습니다.", "data": null }
		""";

	static final String KAKAO_UNAVAILABLE = """
		{
			"status": 502,
			"code": "KAKAO_UNAVAILABLE",
			"message": "카카오톡이 응답하지 않습니다. 잠시 뒤 다시 시도해 주세요.",
			"data": null
		}
		""";

	private KakaoInviteApiExamples() {
	}
}
