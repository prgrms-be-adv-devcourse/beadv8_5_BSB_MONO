package com.bukang.member.crew.in;

/**
 * InternalV1CrewController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 */
final class CrewInternalApiExamples {
	static final String CREW_MEMBER = """
		{
			"status": 200,
			"message": "크루원 여부를 조회했습니다.",
			"data": {
				"crewId": 1,
				"memberId": 2,
				"crewMember": true,
				"role": "MEMBER",
				"joinedAt": "2026-11-02T21:05:12"
			}
		}
		""";

	static final String NOT_CREW_MEMBER = """
		{
			"status": 200,
			"message": "크루원 여부를 조회했습니다.",
			"data": { "crewId": 1, "memberId": 9, "crewMember": false, "role": null, "joinedAt": null }
		}
		""";

	static final String MEMBER_COUNT = """
		{
			"status": 200,
			"message": "크루원 수를 조회했습니다.",
			"data": { "crewId": 1, "memberCount": 5 }
		}
		""";

	static final String CREW_NOT_FOUND = """
		{ "status": 404, "code": "CREW_NOT_FOUND", "message": "존재하지 않는 크루입니다.", "data": null }
		""";

	private CrewInternalApiExamples() {
	}
}
