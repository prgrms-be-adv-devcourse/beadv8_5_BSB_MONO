package com.bukang.order.in;

/**
 * ApiV1ParticipationController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 */
final class ParticipationApiExamples {
	static final String APPLY_SUCCESS = """
		{
			"status": 201,
			"message": "참가 신청서를 저장했습니다.",
			"data": {
				"id": 1,
				"createDate": "2026-10-12T10:15:30",
				"modifyDate": "2026-10-12T10:15:30",
				"raceId": 1,
				"raceCourseId": 1,
				"memberId": 1,
				"participationType": "INDIVIDUAL",
				"status": "PENDING",
				"participant": {
					"name": "김러너",
					"birth": "1994-06-25",
					"gender": "MALE",
					"phone": "010-1234-5678",
					"emergencyContactName": "김가족",
					"emergencyContactPhone": "010-9876-5432",
					"tshirtSize": "L",
					"shippingAddress": {
						"recipientName": "김러너",
						"recipientPhone": "010-1234-5678",
						"zipcode": "04700",
						"address": "서울 성동구 왕십리로 00",
						"addressDetail": "101동 1001호"
					}
				},
				"thirdPartyConsent": true,
				"consentedAt": "2026-10-12T10:15:30",
				"paymentDueAt": null
			}
		}
		""";

	static final String FIND_SUCCESS = """
		{
			"status": 200,
			"message": "참가 신청을 조회했습니다.",
			"data": {
				"id": 1,
				"createDate": "2026-10-12T10:15:30",
				"modifyDate": "2026-10-12T10:15:30",
				"raceId": 1,
				"raceCourseId": 1,
				"memberId": 1,
				"participationType": "INDIVIDUAL",
				"status": "PENDING",
				"participant": {
					"name": "김러너",
					"birth": "1994-06-25",
					"gender": "MALE",
					"phone": "010-1234-5678",
					"emergencyContactName": "김가족",
					"emergencyContactPhone": "010-9876-5432",
					"tshirtSize": "L",
					"shippingAddress": {
						"recipientName": "김러너",
						"recipientPhone": "010-1234-5678",
						"zipcode": "04700",
						"address": "서울 성동구 왕십리로 00",
						"addressDetail": "101동 1001호"
					}
				},
				"thirdPartyConsent": true,
				"consentedAt": "2026-10-12T10:15:30",
				"paymentDueAt": null
			}
		}
		""";

	static final String CHANGE_SUCCESS = """
		{
			"status": 200,
			"message": "참가자 정보를 수정했습니다.",
			"data": {
				"id": 1,
				"createDate": "2026-10-12T10:15:30",
				"modifyDate": "2026-10-12T11:02:10",
				"raceId": 1,
				"raceCourseId": 1,
				"memberId": 1,
				"participationType": "INDIVIDUAL",
				"status": "PENDING",
				"participant": {
					"name": "김러너",
					"birth": "1994-06-25",
					"gender": "MALE",
					"phone": "010-1234-5678",
					"emergencyContactName": "김가족",
					"emergencyContactPhone": "010-9876-5432",
					"tshirtSize": "XL",
					"shippingAddress": {
						"recipientName": "김러너",
						"recipientPhone": "010-1234-5678",
						"zipcode": "04700",
						"address": "서울 성동구 왕십리로 00",
						"addressDetail": "101동 1001호"
					}
				},
				"thirdPartyConsent": true,
				"consentedAt": "2026-10-12T10:15:30",
				"paymentDueAt": null
			}
		}
		""";

	static final String LATEST_PARTICIPANT_SUCCESS = """
		{
			"status": 200,
			"message": "최근 참가자 정보를 조회했습니다.",
			"data": {
				"name": "김러너",
				"birth": "1994-06-25",
				"gender": "MALE",
				"phone": "010-1234-5678",
				"emergencyContactName": "김가족",
				"emergencyContactPhone": "010-9876-5432",
				"tshirtSize": "L",
				"shippingAddress": {
					"recipientName": "김러너",
					"recipientPhone": "010-1234-5678",
					"zipcode": "04700",
					"address": "서울 성동구 왕십리로 00",
					"addressDetail": "101동 1001호"
				}
			}
		}
		""";

	static final String LATEST_PARTICIPANT_EMPTY = """
		{ "status": 200, "message": "이전에 신청한 참가자 정보가 없습니다.", "data": null }
		""";

	static final String INVALID_INPUT = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "참가자 이름을 작성해주세요.", "data": null }
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

	static final String INVALID_MEMBER_ID = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "회원 ID가 올바르지 않습니다.", "data": null }
		""";

	static final String LOGIN_REQUIRED = """
		{ "status": 401, "code": "LOGIN_REQUIRED", "message": "로그인이 필요합니다.", "data": null }
		""";

	static final String NOT_OWNER = """
		{ "status": 403, "code": "ORDER_PARTICIPATION_FORBIDDEN", "message": "본인의 참가 신청만 보거나 고칠 수 있습니다.", "data": null }
		""";

	static final String COURSE_NOT_FOUND = """
		{ "status": 404, "code": "ORDER_COURSE_NOT_FOUND", "message": "존재하지 않는 종목입니다.", "data": null }
		""";

	static final String PARTICIPATION_NOT_FOUND = """
		{ "status": 404, "code": "ORDER_PARTICIPATION_NOT_FOUND", "message": "존재하지 않는 참가 신청입니다.", "data": null }
		""";

	static final String DUPLICATE_PARTICIPATION = """
		{ "status": 409, "code": "ORDER_DUPLICATE_PARTICIPATION", "message": "이미 이 대회에 신청했습니다.", "data": null }
		""";

	static final String NOT_ACCEPTING = """
		{ "status": 409, "code": "ORDER_APPLICATION_CLOSED", "message": "접수 기간이 아닙니다.", "data": null }
		""";

	static final String APPLICATION_CLOSED = """
		{ "status": 409, "code": "ORDER_APPLICATION_CLOSED", "message": "접수가 마감되어 참가자 정보를 고칠 수 없습니다.", "data": null }
		""";

	static final String NOT_EDITABLE = """
		{ "status": 409, "code": "ORDER_PARTICIPATION_NOT_EDITABLE",
			"message": "참가자 정보를 고칠 수 없는 신청입니다(현재 상태: 취소).", "data": null }
		""";

	static final String CANCEL_SUCCESS = """
		{
			"status": 200,
			"message": "참가 신청서를 취소했습니다.",
			"data": {
				"id": 1,
				"createDate": "2026-10-12T10:15:30",
				"modifyDate": "2026-10-12T10:20:05",
				"raceId": 1,
				"raceCourseId": 1,
				"memberId": 1,
				"participationType": "INDIVIDUAL",
				"status": "CANCELED",
				"participant": {
					"name": "김러너",
					"birth": "1994-06-25",
					"gender": "MALE",
					"phone": "010-1234-5678",
					"emergencyContactName": "김가족",
					"emergencyContactPhone": "010-9876-5432",
					"tshirtSize": "L",
					"shippingAddress": {
						"recipientName": "김러너",
						"recipientPhone": "010-1234-5678",
						"zipcode": "04700",
						"address": "서울 성동구 왕십리로 00",
						"addressDetail": "101동 1001호"
					}
				},
				"thirdPartyConsent": true,
				"consentedAt": "2026-10-12T10:15:30",
				"paymentDueAt": null
			}
		}
		""";

	static final String NOT_CANCELABLE_APPLIED = """
		{
			"status": 409, "code": "ORDER_PARTICIPATION_NOT_CANCELABLE",
			"message": "결제 대기 중인 신청은 취소할 수 없습니다. 결제하지 않으면 결제 기한이 지난 뒤 자동으로 풀립니다.",
			"data": null
		}
		""";

	static final String NOT_CANCELABLE_CONFIRMED = """
		{ "status": 409, "code": "ORDER_PARTICIPATION_NOT_CANCELABLE",
			"message": "결제한 참가권은 주문 상품 취소로 취소해주세요.", "data": null }
		""";

	static final String NOT_CANCELABLE_FINISHED = """
		{ "status": 409, "code": "ORDER_PARTICIPATION_NOT_CANCELABLE",
			"message": "이미 끝난 신청입니다(현재 상태: 취소).", "data": null }
		""";

	static final String SHIPPING_ADDRESS_REQUIRED = """
		{ "status": 400, "code": "ORDER_SHIPPING_ADDRESS_REQUIRED", "message": "키트를 받을 배송 주소를 작성해주세요.", "data": null }
		""";

	static final String TSHIRT_SIZE_REQUIRED = """
		{ "status": 400, "code": "ORDER_TSHIRT_SIZE_REQUIRED",
			"message": "티셔츠 사이즈를 골라주세요(고를 수 있는 사이즈: S, M, L).", "data": null }
		""";

	static final String TSHIRT_SIZE_NOT_AVAILABLE = """
		{ "status": 400, "code": "ORDER_TSHIRT_SIZE_NOT_AVAILABLE",
			"message": "이 종목에서 고를 수 없는 티셔츠 사이즈입니다(고를 수 있는 사이즈: S, M, L).", "data": null }
		""";

	static final String CHANGE_RETRY = """
		{ "status": 409, "code": "ORDER_PARTICIPATION_CHANGE_RETRY",
			"message": "방금 주문이 만들어졌습니다. 잠시 뒤 다시 시도해주세요.", "data": null }
		""";

	static final String APPLY_RETRY = """
		{ "status": 409, "code": "ORDER_PARTICIPATION_CHANGE_RETRY",
			"message": "이전 신청서를 처리하고 있습니다. 잠시 뒤 다시 시도해주세요.", "data": null }
		""";

	private ParticipationApiExamples() {
	}
}
