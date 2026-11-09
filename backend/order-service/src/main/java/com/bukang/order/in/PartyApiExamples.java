package com.bukang.order.in;

/**
 * ApiV1PartyController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 */
final class PartyApiExamples {
	static final String CREATE_SUCCESS = """
		{
			"status": 201,
			"message": "파티를 만들었습니다.",
			"data": {
				"party": {
					"id": 1,
					"crewId": 7,
					"raceId": 100,
					"raceCourseId": 2,
					"leaderMemberId": 3,
					"status": "RECRUITING",
					"paymentDueAt": null,
					"joinToken": "k3Jx9dQ2mV7pL0sA8fB1nC4eR6tY5uI2oP9wZ3xH7gK",
					"joinTokenExpiresAt": "2026-11-06T10:15:30"
				},
				"participation": {
					"id": 11,
					"createDate": "2026-11-05T10:15:30",
					"modifyDate": "2026-11-05T10:15:30",
					"raceId": 100,
					"raceCourseId": 2,
					"memberId": 3,
					"participationType": "PARTY",
					"partyId": 1,
					"status": "PENDING",
					"participant": {
						"name": "김러너",
						"birth": "1994-06-25",
						"gender": "MALE",
						"phone": "010-1234-5678",
						"emergencyContactName": "김가족",
						"emergencyContactPhone": "010-9876-5432",
						"tshirtSize": "L",
						"shippingAddress": null
					},
					"thirdPartyConsent": true,
					"consentedAt": "2026-11-05T10:15:30",
					"paymentDueAt": null
				}
			}
		}
		""";

	static final String REGENERATE_SUCCESS = """
		{
			"status": 200,
			"message": "참가 링크를 다시 만들었습니다.",
			"data": {
				"id": 1,
				"crewId": 7,
				"raceId": 100,
				"raceCourseId": 2,
				"leaderMemberId": 3,
				"status": "RECRUITING",
				"paymentDueAt": null,
				"joinToken": "Zq8Wm1Xc5Vb2Nn7Ll4Kk9Jj3Hh6Gg0Ff2Dd5Ss8Aa1Q",
				"joinTokenExpiresAt": "2026-11-06T12:00:00"
			}
		}
		""";

	static final String JOIN_SUCCESS = """
		{
			"status": 201,
			"message": "파티에 합류했습니다.",
			"data": {
				"id": 12,
				"createDate": "2026-11-05T11:00:00",
				"modifyDate": "2026-11-05T11:00:00",
				"raceId": 100,
				"raceCourseId": 2,
				"memberId": 5,
				"participationType": "PARTY",
				"partyId": 1,
				"status": "PENDING",
				"participant": {
					"name": "이러너",
					"birth": "1996-03-14",
					"gender": "FEMALE",
					"phone": "010-2345-6789",
					"emergencyContactName": "이가족",
					"emergencyContactPhone": "010-8765-4321",
					"tshirtSize": "M",
					"shippingAddress": null
				},
				"thirdPartyConsent": true,
				"consentedAt": "2026-11-05T11:00:00",
				"paymentDueAt": null
			}
		}
		""";

	static final String LEAVE_SUCCESS = """
		{
			"status": 200,
			"message": "파티에서 나갔습니다.",
			"data": {
				"id": 12,
				"createDate": "2026-11-05T11:00:00",
				"modifyDate": "2026-11-05T12:30:00",
				"raceId": 100,
				"raceCourseId": 2,
				"memberId": 5,
				"participationType": "PARTY",
				"partyId": 1,
				"status": "CANCELED",
				"participant": {
					"name": "이러너",
					"birth": "1996-03-14",
					"gender": "FEMALE",
					"phone": "010-2345-6789",
					"emergencyContactName": "이가족",
					"emergencyContactPhone": "010-8765-4321",
					"tshirtSize": "M",
					"shippingAddress": null
				},
				"thirdPartyConsent": true,
				"consentedAt": "2026-11-05T11:00:00",
				"paymentDueAt": null
			}
		}
		""";

	static final String LEADER_RECRUITING = """
		{
			"status": 200,
			"message": "이 대회 내 파티 상태를 조회했습니다.",
			"data": { "role": "LEADER", "canApply": true, "partyId": 1, "raceCourseId": 2, "partyStatus": "RECRUITING",
				"memberCount": 4, "minCount": 4, "maxCount": 10, "paidCount": 0, "myPaid": false,
				"participationId": 10 }
		}
		""";

	static final String LEADER_TOO_FEW = """
		{
			"status": 200,
			"message": "이 대회 내 파티 상태를 조회했습니다.",
			"data": { "role": "LEADER", "canApply": false, "reason": "TOO_FEW_MEMBERS", "partyId": 1, "raceCourseId": 2,
				"partyStatus": "RECRUITING", "memberCount": 2, "minCount": 4, "maxCount": 10, "paidCount": 0,
				"myPaid": false, "participationId": 10 }
		}
		""";

	static final String MEMBER_RECRUITING = """
		{
			"status": 200,
			"message": "이 대회 내 파티 상태를 조회했습니다.",
			"data": { "role": "MEMBER", "canApply": false, "reason": "NOT_LEADER", "partyId": 1, "raceCourseId": 2,
				"partyStatus": "RECRUITING", "memberCount": 3, "minCount": 4, "maxCount": 10, "paidCount": 0,
				"myPaid": false, "participationId": 10 }
		}
		""";

	static final String MEMBER_PAYING = """
		{
			"status": 200,
			"message": "이 대회 내 파티 상태를 조회했습니다.",
			"data": { "role": "MEMBER", "canApply": false, "reason": "NOT_LEADER", "partyId": 1, "raceCourseId": 2,
				"partyStatus": "PAYING", "memberCount": 4, "minCount": 4, "maxCount": 10, "paidCount": 1,
				"paymentDueAt": "2026-11-07T10:00:00", "myPaid": false, "participationId": 10 }
		}
		""";

	static final String NO_PARTY = """
		{
			"status": 200,
			"message": "이 대회 내 파티 상태를 조회했습니다.",
			"data": { "role": "NONE", "canApply": false, "reason": "NO_PARTY" }
		}
		""";

	static final String RACE_ID_REQUIRED = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "필수 요청 값(raceId)이 없습니다.", "data": null }
		""";

	static final String INVALID_RACE_ID = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "대회 ID가 올바르지 않습니다.", "data": null }
		""";

	static final String INVALID_INPUT = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "참가자 정보를 작성해주세요.", "data": null }
		""";

	static final String CONSENT_REQUIRED = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "개인정보 제3자 제공에 동의해야 신청할 수 있습니다.", "data": null }
		""";

	static final String INVALID_BODY = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "요청 본문 형식이 올바르지 않습니다.", "data": null }
		""";

	static final String AGE_RESTRICTED = """
		{ "status": 400, "code": "ORDER_AGE_RESTRICTED",
			"message": "참가할 수 있는 나이가 아닙니다(대회 당일 만 14세 이상, 신청자 만 12세).", "data": null }
		""";

	static final String INVALID_VALUE_TYPE = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "요청 값의 형식이 올바르지 않습니다.", "data": null }
		""";

	static final String LOGIN_REQUIRED = """
		{ "status": 401, "code": "LOGIN_REQUIRED", "message": "로그인이 필요합니다.", "data": null }
		""";

	static final String CREW_MEMBER_REQUIRED = """
		{ "status": 403, "code": "ORDER_CREW_MEMBER_REQUIRED",
			"message": "이 크루의 크루원만 파티를 만들거나 합류할 수 있습니다.", "data": null }
		""";

	static final String LEADER_ONLY = """
		{ "status": 403, "code": "ORDER_PARTY_LEADER_ONLY", "message": "파티장만 할 수 있습니다.", "data": null }
		""";

	static final String NOT_MEMBER = """
		{ "status": 403, "code": "ORDER_PARTY_NOT_MEMBER", "message": "이 파티의 파티원이 아닙니다.", "data": null }
		""";

	static final String PARTY_NOT_FOUND = """
		{ "status": 404, "code": "ORDER_PARTY_NOT_FOUND", "message": "존재하지 않는 파티입니다.", "data": null }
		""";

	static final String JOIN_LINK_NOT_FOUND = """
		{ "status": 404, "code": "ORDER_PARTY_NOT_FOUND",
			"message": "참가 링크를 찾을 수 없습니다. 파티장에게 새 링크를 받아주세요.", "data": null }
		""";

	static final String COURSE_NOT_FOUND = """
		{ "status": 404, "code": "ORDER_COURSE_NOT_FOUND", "message": "존재하지 않는 종목입니다.", "data": null }
		""";

	static final String CREW_NOT_FOUND = """
		{ "status": 404, "code": "ORDER_CREW_NOT_FOUND", "message": "존재하지 않는 크루입니다.", "data": null }
		""";

	static final String NOT_RECRUITING = """
		{ "status": 409, "code": "ORDER_PARTY_NOT_RECRUITING",
			"message": "모집 중인 파티가 아니라 파티원을 바꿀 수 없습니다(현재 상태: 결제 진행 중).",
			"data": { "partyId": 1, "partyStatus": "PAYING" } }
		""";

	static final String LINK_EXPIRED = """
		{ "status": 409, "code": "ORDER_PARTY_LINK_EXPIRED",
			"message": "참가 링크가 만료됐습니다. 파티장에게 새 링크를 받아주세요.", "data": null }
		""";

	static final String ALREADY_JOINED = """
		{ "status": 409, "code": "ORDER_PARTY_ALREADY_JOINED", "message": "이미 이 파티에 합류했습니다.",
			"data": { "partyId": 1 } }
		""";

	static final String OTHER_PARTY_JOINED = """
		{ "status": 409, "code": "ORDER_PARTY_OTHER_PARTY_JOINED",
			"message": "다른 파티로 이 대회에 신청했습니다. 그 파티에서 나간 뒤 합류해 주세요.",
			"data": { "partyId": 4 } }
		""";

	static final String INDIVIDUAL_PARTICIPATION_EXISTS = """
		{ "status": 409, "code": "ORDER_INDIVIDUAL_PARTICIPATION_EXISTS",
			"message": "이 대회에 개인 신청이 있습니다. 개인 신청을 먼저 취소해 주세요.",
			"data": { "participationId": 9 } }
		""";

	static final String DUPLICATE_PARTICIPATION = """
		{ "status": 409, "code": "ORDER_DUPLICATE_PARTICIPATION", "message": "이미 이 대회에 신청했습니다.", "data": null }
		""";

	static final String RACE_CANCELED = """
		{ "status": 409, "code": "ORDER_RACE_CANCELED", "message": "취소된 대회입니다.", "data": null }
		""";

	static final String APPLICATION_CLOSED = """
		{ "status": 409, "code": "ORDER_APPLICATION_CLOSED", "message": "접수가 마감된 대회입니다.", "data": null }
		""";

	static final String MEMBER_SERVICE_UNAVAILABLE = """
		{ "status": 503, "code": "ORDER_EXTERNAL_SERVICE_UNAVAILABLE",
			"message": "회원 서비스에 연결하지 못했습니다. 잠시 뒤 다시 시도해주세요.", "data": null }
		""";

	static final String PRODUCT_SERVICE_UNAVAILABLE = """
		{ "status": 503, "code": "ORDER_EXTERNAL_SERVICE_UNAVAILABLE",
			"message": "상품 서비스에 연결하지 못했습니다. 잠시 뒤 다시 시도해주세요.", "data": null }
		""";

	static final String APPLY_SUCCESS = """
		{
			"status": 200,
			"message": "파티를 신청했습니다. 파티원에게 결제 알림을 보냈습니다.",
			"data": {
				"id": 1,
				"crewId": 7,
				"raceId": 100,
				"raceCourseId": 2,
				"leaderMemberId": 3,
				"status": "PAYING",
				"paymentDueAt": "2026-11-07T10:15:30",
				"joinToken": "k3Jx9dQ2mV7pL0sA8fB1nC4eR6tY5uI2oP9wZ3xH7gK",
				"joinTokenExpiresAt": "2026-11-06T10:15:30"
			}
		}
		""";

	static final String QUEUE_NOT_ADMITTED = """
		{ "status": 403, "code": "ORDER_QUEUE_NOT_ADMITTED", "message": "파티 대기열에서 입장한 뒤 파티를 신청할 수 있습니다.",
			"data": null }
		""";

	static final String FULL = """
		{ "status": 409, "code": "ORDER_PARTY_FULL", "message": "파티 인원이 다 찼습니다(최대 10명).",
			"data": { "partyId": 1 } }
		""";

	static final String APPLY_NOT_RECRUITING = """
		{ "status": 409, "code": "ORDER_PARTY_NOT_RECRUITING",
			"message": "이미 신청했거나 끝난 파티입니다(현재 상태: 결제 진행 중).",
			"data": { "partyId": 1, "partyStatus": "PAYING" } }
		""";

	static final String SALE_CLOSED = """
		{ "status": 409, "code": "ORDER_PARTY_SALE_CLOSED", "message": "파티 예매 기간이 아닙니다.",
			"data": { "partyId": 1 } }
		""";

	static final String SIZE_OUT_OF_RANGE = """
		{ "status": 409, "code": "ORDER_PARTY_SIZE_OUT_OF_RANGE",
			"message": "파티 인원이 4~10명이어야 신청할 수 있습니다(지금 3명).", "data": { "partyId": 1 } }
		""";

	static final String MEMBER_NOT_CREW = """
		{ "status": 409, "code": "ORDER_PARTY_MEMBER_NOT_CREW",
			"message": "크루를 떠난 파티원이 있어 신청할 수 없습니다.", "data": { "partyId": 1 } }
		""";

	static final String MEMBER_JOINED_LATE = """
		{ "status": 409, "code": "ORDER_PARTY_MEMBER_JOINED_LATE",
			"message": "파티 예매 시작 7일 전보다 늦게 크루에 가입한 파티원이 있어 신청할 수 없습니다.", "data": { "partyId": 1 } }
		""";

	static final String MEMBERS_CHANGED = """
		{ "status": 409, "code": "ORDER_PARTY_MEMBERS_CHANGED",
			"message": "신청하는 사이 파티원이 바뀌었습니다. 다시 신청해 주세요.", "data": { "partyId": 1 } }
		""";

	static final String CREW_SEAT_SHORTAGE = """
		{ "status": 409, "code": "ORDER_PARTY_CREW_SEAT_SHORTAGE",
			"message": "남은 크루 자리가 3석 모자랍니다(필요 4석, 남은 1석).",
			"data": { "partyId": 1, "raceCourseId": 2, "needed": 4, "remaining": 1 } }
		""";

	static final String PARTY_APPLY_UNAVAILABLE = """
		{ "status": 503, "code": "ORDER_EXTERNAL_SERVICE_UNAVAILABLE",
			"message": "파티 신청을 아직 쓸 수 없습니다. 잠시 뒤 다시 시도해주세요.", "data": null }
		""";

	private PartyApiExamples() {
	}
}
