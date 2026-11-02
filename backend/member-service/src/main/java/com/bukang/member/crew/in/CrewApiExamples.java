package com.bukang.member.crew.in;

/**
 * ApiV1CrewController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 */
final class CrewApiExamples {
	static final String CREATE_SUCCESS = """
		{
			"status": 201,
			"message": "크루를 만들었습니다.",
			"data": {
				"crewId": 1,
				"name": "새벽한강크루",
				"createdAt": "2026-11-02T20:10:00",
				"myRole": "LEADER",
				"memberCount": 1,
				"members": [
					{ "memberId": 1, "nickname": "새벽러너", "role": "LEADER" }
				]
			}
		}
		""";

	static final String FIND_MY_CREWS_SUCCESS = """
		{
			"status": 200,
			"message": "내 크루 목록을 조회했습니다.",
			"data": [
				{ "crewId": 1, "name": "새벽한강크루", "myRole": "LEADER", "memberCount": 3 },
				{ "crewId": 5, "name": "판교점심런", "myRole": "MEMBER", "memberCount": 12 }
			]
		}
		""";

	static final String FIND_CREW_SUCCESS = """
		{
			"status": 200,
			"message": "크루를 조회했습니다.",
			"data": {
				"crewId": 1,
				"name": "새벽한강크루",
				"createdAt": "2026-11-02T20:10:00",
				"myRole": "LEADER",
				"memberCount": 3,
				"members": [
					{ "memberId": 1, "nickname": "새벽러너", "role": "LEADER" },
					{ "memberId": 4, "nickname": "한강페이서", "role": "MANAGER" },
					{ "memberId": 2, "nickname": "주말10K", "role": "MEMBER" }
				]
			}
		}
		""";

	static final String CREATE_INVITE_SUCCESS = """
		{
			"status": 201,
			"message": "초대 링크를 만들었습니다.",
			"data": {
				"inviteId": 3,
				"inviteToken": "q7Xk2LmZ9vBn4RtW8yCp1A",
				"createdAt": "2026-11-02T20:30:00",
				"expiresAt": "2026-11-03T20:30:00"
			}
		}
		""";

	static final String FIND_INVITES_SUCCESS = """
		{
			"status": 200,
			"message": "초대 목록을 조회했습니다.",
			"data": [
				{
					"inviteId": 3,
					"createdBy": { "memberId": 1, "nickname": "새벽러너" },
					"createdAt": "2026-11-02T20:30:00",
					"expiresAt": "2026-11-03T20:30:00"
				}
			]
		}
		""";

	static final String CANCEL_INVITE_SUCCESS = """
		{ "status": 200, "message": "초대를 취소했습니다.", "data": null }
		""";

	static final String FIND_INVITE_SUCCESS = """
		{
			"status": 200,
			"message": "초대 링크를 조회했습니다.",
			"data": {
				"crewId": 1,
				"crewName": "새벽한강크루",
				"memberCount": 3,
				"expiresAt": "2026-11-03T20:30:00",
				"alreadyMember": false
			}
		}
		""";

	static final String JOIN_SUCCESS = """
		{
			"status": 201,
			"message": "크루에 가입했습니다.",
			"data": {
				"crewId": 1,
				"crewName": "새벽한강크루",
				"role": "MEMBER",
				"joinedAt": "2026-11-02T21:05:12"
			}
		}
		""";

	static final String CREATE_INVALID_INPUT = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "크루 이름은 1자 이상 20자 이하로 입력해주세요.", "data": null }
		""";

	static final String INVALID_BODY = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "요청 본문 형식이 올바르지 않습니다.", "data": null }
		""";

	static final String LOGIN_REQUIRED = """
		{ "status": 401, "code": "LOGIN_REQUIRED", "message": "로그인이 필요합니다.", "data": null }
		""";

	static final String CREW_NOT_MEMBER = """
		{ "status": 403, "code": "CREW_NOT_MEMBER", "message": "이 크루의 크루원만 이용할 수 있습니다.", "data": null }
		""";

	static final String CREW_INVITE_FORBIDDEN = """
		{ "status": 403, "code": "CREW_INVITE_FORBIDDEN", "message": "크루장과 운영진만 초대할 수 있습니다.", "data": null }
		""";

	static final String CREW_INVITE_CANCEL_FORBIDDEN = """
		{
			"status": 403,
			"code": "CREW_INVITE_CANCEL_FORBIDDEN",
			"message": "초대를 보낸 사람이나 크루장만 취소할 수 있습니다.",
			"data": null
		}
		""";

	static final String CREW_NOT_FOUND = """
		{ "status": 404, "code": "CREW_NOT_FOUND", "message": "존재하지 않는 크루입니다.", "data": null }
		""";

	static final String CREW_INVITE_NOT_FOUND = """
		{ "status": 404, "code": "CREW_INVITE_NOT_FOUND", "message": "존재하지 않는 초대 링크입니다.", "data": null }
		""";

	static final String CANCEL_ALREADY_USED = """
		{
			"status": 409,
			"code": "CREW_INVITE_ALREADY_USED",
			"message": "이미 가입에 쓰인 초대는 취소할 수 없습니다.",
			"data": null
		}
		""";

	static final String CANCEL_ALREADY_CANCELED = """
		{ "status": 409, "code": "CREW_INVITE_CANCELED", "message": "이미 취소한 초대입니다.", "data": null }
		""";

	static final String CANCEL_EXPIRED = """
		{ "status": 409, "code": "CREW_INVITE_EXPIRED", "message": "유효 시간이 지난 초대입니다.", "data": null }
		""";

	static final String CREW_ALREADY_MEMBER = """
		{ "status": 409, "code": "CREW_ALREADY_MEMBER", "message": "이미 가입한 크루입니다.", "data": null }
		""";

	static final String LINK_EXPIRED = """
		{
			"status": 410,
			"code": "CREW_INVITE_EXPIRED",
			"message": "초대 링크의 유효 시간(24시간)이 지났습니다. 새 초대 링크를 받아주세요.",
			"data": null
		}
		""";

	static final String LINK_CANCELED = """
		{
			"status": 410,
			"code": "CREW_INVITE_CANCELED",
			"message": "취소된 초대 링크입니다. 새 초대 링크를 받아주세요.",
			"data": null
		}
		""";

	static final String LINK_ALREADY_USED = """
		{
			"status": 410,
			"code": "CREW_INVITE_ALREADY_USED",
			"message": "이미 가입에 쓰인 초대 링크입니다. 새 초대 링크를 받아주세요.",
			"data": null
		}
		""";

	private CrewApiExamples() {
	}
}
