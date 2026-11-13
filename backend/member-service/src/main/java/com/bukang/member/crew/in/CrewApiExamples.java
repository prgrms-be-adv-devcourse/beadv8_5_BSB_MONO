package com.bukang.member.crew.in;

/**
 * ApiV1CrewController·ApiV1CrewManageController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
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

	static final String LINK_REMOVED_MEMBER = """
		{
			"status": 410,
			"code": "CREW_INVITE_REMOVED_MEMBER",
			"message": "크루에서 내보낸 회원은 이 초대 링크로 가입할 수 없습니다. 크루장에게 새 초대 링크를 받아주세요.",
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

	static final String LEAVE_SUCCESS = """
		{ "status": 200, "message": "크루를 탈퇴했습니다.", "data": null }
		""";

	static final String REMOVE_MEMBER_SUCCESS = """
		{ "status": 200, "message": "크루원을 내보냈습니다.", "data": null }
		""";

	static final String TRANSFER_LEADER_SUCCESS = """
		{
			"status": 200,
			"message": "크루장을 넘겼습니다.",
			"data": {
				"crewId": 1,
				"name": "새벽한강크루",
				"createdAt": "2026-11-02T20:10:00",
				"myRole": "MEMBER",
				"memberCount": 3,
				"members": [
					{ "memberId": 2, "nickname": "주말10K", "role": "LEADER" },
					{ "memberId": 4, "nickname": "한강페이서", "role": "MANAGER" },
					{ "memberId": 1, "nickname": "새벽러너", "role": "MEMBER" }
				]
			}
		}
		""";

	static final String DISBAND_SUCCESS = """
		{ "status": 200, "message": "크루를 없앴습니다.", "data": null }
		""";

	static final String RENAME_SUCCESS = """
		{
			"status": 200,
			"message": "크루 이름을 바꿨습니다.",
			"data": {
				"crewId": 1,
				"name": "새벽한강러닝크루",
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

	static final String TRANSFER_INVALID_INPUT = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "크루장을 넘겨받을 크루원을 선택해주세요.", "data": null }
		""";

	static final String CREW_LEADER_ONLY = """
		{ "status": 403, "code": "CREW_LEADER_ONLY", "message": "크루장만 할 수 있습니다.", "data": null }
		""";

	static final String CREW_MEMBER_NOT_FOUND = """
		{ "status": 404, "code": "CREW_MEMBER_NOT_FOUND", "message": "이 크루의 크루원이 아닙니다.", "data": null }
		""";

	static final String CREW_LEADER_CANNOT_LEAVE = """
		{
			"status": 409,
			"code": "CREW_LEADER_CANNOT_LEAVE",
			"message": "크루장은 크루장을 넘긴 뒤 탈퇴할 수 있습니다. 다른 크루원이 없으면 크루를 없애주세요.",
			"data": null
		}
		""";

	static final String CREW_LEAVE_PARTY_BLOCKED = """
		{
			"status": 409,
			"code": "CREW_LEAVE_PARTY_BLOCKED",
			"message": "참여 중인 파티가 있어 크루를 나갈 수 없습니다. 파티에서 나온 뒤 다시 시도해주세요.",
			"data": null
		}
		""";

	static final String CREW_KICK_PARTY_BLOCKED = """
		{
			"status": 409,
			"code": "CREW_KICK_PARTY_BLOCKED",
			"message": "결제가 진행 중이거나 끝난 파티에 들어 있는 크루원은 내보낼 수 없습니다. 파티가 끝난 뒤 다시 시도해주세요.",
			"data": null
		}
		""";

	static final String CREW_PARTY_CHECK_FAILED = """
		{
			"status": 503,
			"code": "CREW_PARTY_CHECK_FAILED",
			"message": "파티 상태를 확인하지 못했습니다. 잠시 뒤 다시 시도해주세요.",
			"data": null
		}
		""";

	static final String CREW_CANNOT_REMOVE_LEADER = """
		{ "status": 409, "code": "CREW_CANNOT_REMOVE_LEADER", "message": "크루장은 내보낼 수 없습니다.", "data": null }
		""";

	static final String CREW_ALREADY_LEADER = """
		{ "status": 409, "code": "CREW_ALREADY_LEADER", "message": "이미 크루장입니다.", "data": null }
		""";

	static final String CREW_HAS_OTHER_MEMBERS = """
		{
			"status": 409,
			"code": "CREW_HAS_OTHER_MEMBERS",
			"message": "다른 크루원이 있어 크루를 없앨 수 없습니다. 크루장을 넘긴 뒤 탈퇴해주세요.",
			"data": null
		}
		""";

	private CrewApiExamples() {
	}
}
