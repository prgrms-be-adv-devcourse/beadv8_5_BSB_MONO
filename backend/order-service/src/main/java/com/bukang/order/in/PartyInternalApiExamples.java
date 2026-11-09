package com.bukang.order.in;

/**
 * InternalV1PartyController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 */
final class PartyInternalApiExamples {
	static final String PARTY_STATUS_FREE = """
		{ "status": 200, "message": "크루원의 파티 상태를 조회했습니다.",
			"data": { "canLeave": true, "canKick": true, "blockingParties": [] } }
		""";

	static final String PARTY_STATUS_RECRUITING = """
		{ "status": 200, "message": "크루원의 파티 상태를 조회했습니다.",
			"data": { "canLeave": false, "canKick": true,
				"blockingParties": [ { "partyId": 7, "status": "RECRUITING" } ] } }
		""";

	static final String PARTY_STATUS_PAYING = """
		{ "status": 200, "message": "크루원의 파티 상태를 조회했습니다.",
			"data": { "canLeave": false, "canKick": false,
				"blockingParties": [ { "partyId": 7, "status": "PAYING" } ] } }
		""";

	private PartyInternalApiExamples() {
	}
}
