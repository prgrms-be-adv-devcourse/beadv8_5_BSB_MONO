package com.bukang.order.in;

/**
 * ApiV1PartyQueryController의 Swagger 성공 응답 예시 (실제 응답 메시지와 같게 유지한다). 오류 예시는 PartyApiExamples를 쓴다
 */
final class PartyQueryApiExamples {
	static final String DETAIL_SUCCESS = """
		{
			"status": 200,
			"message": "파티를 조회했습니다.",
			"data": {
				"id": 1,
				"crewId": 7,
				"raceId": 100,
				"raceCourseId": 2,
				"leaderMemberId": 3,
				"status": "RECRUITING",
				"paymentDueAt": null,
				"joinToken": "k3Jx9dQ2mV7pL0sA8fB1nC4eR6tY5uI2oP9wZ3xH7gK",
				"joinTokenExpiresAt": "2026-11-06T10:15:30",
				"memberCount": 2,
				"paidCount": 0,
				"members": [
					{ "memberId": 3, "nickname": "러너A", "leader": true, "joinedAt": "2026-11-05T10:15:30",
						"participationStatus": "PENDING", "paid": false },
					{ "memberId": 8, "nickname": "탈퇴한 회원", "leader": false, "joinedAt": "2026-11-05T11:00:00",
						"participationStatus": "PENDING", "paid": false }
				]
			}
		}
		""";

	static final String MY_PARTIES_SUCCESS = """
		{
			"status": 200,
			"message": "내 파티를 조회했습니다.",
			"data": [
				{ "id": 1, "crewId": 7, "raceId": 100, "raceCourseId": 2, "status": "PAYING", "leaderMemberId": 3,
					"leaderNickname": "러너A", "memberCount": 4, "paidCount": 1,
					"paymentDueAt": "2026-11-07T10:15:30", "myParty": true }
			]
		}
		""";

	static final String CREW_ACTIVE_SUCCESS = """
		{
			"status": 200,
			"message": "크루의 진행 중인 파티를 조회했습니다.",
			"data": [
				{ "id": 2, "crewId": 7, "raceId": 101, "raceCourseId": 5, "status": "RECRUITING", "leaderMemberId": 9,
					"leaderNickname": "러너B", "memberCount": 2, "paidCount": 0, "paymentDueAt": null, "myParty": false },
				{ "id": 1, "crewId": 7, "raceId": 100, "raceCourseId": 2, "status": "PAYING", "leaderMemberId": 3,
					"leaderNickname": "러너A", "memberCount": 4, "paidCount": 1,
					"paymentDueAt": "2026-11-07T10:15:30", "myParty": true }
			]
		}
		""";

	static final String PREVIEW_SUCCESS = """
		{
			"status": 200,
			"message": "참가 링크의 파티를 조회했습니다.",
			"data": {
				"id": 1,
				"crewId": 7,
				"raceId": 100,
				"raceCourseId": 2,
				"status": "RECRUITING",
				"leaderNickname": "러너A",
				"memberCount": 2,
				"joinTokenExpiresAt": "2026-11-06T10:15:30",
				"joinLinkAlive": true
			}
		}
		""";

	static final String PREVIEW_INVALID_INPUT = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "참가 링크가 없습니다.", "data": null }
		""";

	private PartyQueryApiExamples() {
	}
}
