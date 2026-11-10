package com.bukang.cash.in;

/**
 * ApiV1CashController, TossWebhookController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 */
final class CashApiExamples {
	static final String TOSS_DEPOSIT_RECEIVED = """
		{ "status": 200, "message": "입금 알림을 받았습니다.", "data": null }
		""";

	static final String TOSS_DEPOSIT_INVALID_BODY = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "요청 본문 형식이 올바르지 않습니다.", "data": null }
		""";

	static final String GET_BALANCE_SUCCESS = """
		{
			"status": 200,
			"message": "잔액 조회 성공",
			"data": {
				"holderId": 3,
				"balance": 50000
			}
		}
		""";

	static final String WALLET_NOT_FOUND = """
		{ "status": 404, "message": "존재하지 않는 지갑입니다.", "data": null }
		""";

	static final String LEDGER_LIST_SUCCESS = """
		{
			"status": 200,
			"message": "예치금 내역을 조회했습니다.",
			"data": {
				"content": [
					{
						"category": "PAYMENT",
						"ledgerType": "사용__주문결제",
						"amount": -5000,
						"balanceAfter": 5000,
						"createdAt": "2026-10-21T11:30:00.102938",
						"refId": 100,
						"charge": null
					},
					{
						"category": "CHARGE",
						"ledgerType": "충전__PG결제_토스페이먼츠",
						"amount": 10000,
						"balanceAfter": 10000,
						"createdAt": "2026-10-20T09:03:03.319058",
						"refId": 1,
						"charge": {
							"chargeId": 1,
							"chargedAt": "2026-10-20T09:03:01",
							"amount": 10000,
							"remainingAmount": 5000,
							"lastCancelableDate": "2027-10-19",
							"expired": false
						}
					}
				],
				"page": 0,
				"size": 20,
				"totalElements": 2,
				"totalPages": 1
			}
		}
		""";

	static final String REFUNDABLE_CHARGES_SUCCESS = """
		{
			"status": 200,
			"message": "환급 화면용 충전 내역을 조회했습니다.",
			"data": {
				"balance": 25000,
				"earliestLastCancelableDate": "2027-10-12",
				"charges": [
					{
						"chargeId": 3,
						"chargedAt": "2026-10-13T09:00:00",
						"amount": 20000,
						"remainingAmount": 20000,
						"lastCancelableDate": "2027-10-12",
						"expired": false
					},
					{
						"chargeId": 2,
						"chargedAt": "2026-10-10T09:00:00",
						"amount": 10000,
						"remainingAmount": 5000,
						"lastCancelableDate": "2027-10-09",
						"expired": true
					},
					{
						"chargeId": 1,
						"chargedAt": "2026-01-13T09:00:00",
						"amount": 15000,
						"remainingAmount": 0,
						"lastCancelableDate": "2027-01-12",
						"expired": true
					}
				]
			}
		}
		""";

	static final String INVALID_PAGE = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "한 번에 100개까지 조회할 수 있습니다.", "data": null }
		""";

	static final String CHARGE_CONFIRM_SUCCESS = """
		{
			"status": 200,
			"message": "충전했습니다.",
			"data": {
				"outcome": "COMPLETED",
				"balance": 60000
			}
		}
		""";

	static final String CHARGE_CONFIRM_FAILED = """
		{
			"status": 200,
			"message": "충전에 실패했습니다.",
			"data": {
				"outcome": "FAILED",
				"balance": 50000
			}
		}
		""";

	static final String CHARGE_AMOUNT_TOO_SMALL = """
		{ "status": 400, "code": "CHARGE_AMOUNT_TOO_SMALL", "message": "최소 충전 금액은 10,000원입니다.", "data": null }
		""";

	static final String CHARGE_LIMIT_PER_CHARGE = """
		{ "status": 400, "code": "CHARGE_LIMIT_PER_CHARGE", "message": "1회 충전 한도는 300,000원입니다.", "data": null }
		""";

	static final String CHARGE_LIMIT_PER_DAY = """
		{ "status": 400, "code": "CHARGE_LIMIT_PER_DAY", "message": "일일 충전 한도는 1,000,000원입니다.", "data": null }
		""";

	static final String CHARGE_LIMIT_BALANCE = """
		{ "status": 400, "code": "CHARGE_LIMIT_BALANCE", "message": "보유 한도는 10,000,000원입니다.", "data": null }
		""";

	static final String CHARGE_CONFIRM_INVALID_INPUT = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "paymentKey를 작성해주세요.", "data": null }
		""";

	static final String INVALID_BODY = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "요청 본문 형식이 올바르지 않습니다.", "data": null }
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

	static final String WITHDRAWAL_SUCCESS = """
		{
			"status": 200,
			"message": "환급 신청이 완료되었습니다.",
			"data": {
				"withdrawnAmount": 130000,
				"balance": 0,
				"cardAmount": 30000,
				"accountAmount": 100000,
				"processingAmount": 0,
				"revertedAmount": 0,
				"accountRequired": false
			}
		}
		""";

	static final String WITHDRAWAL_PARTIAL = """
		{
			"status": 200,
			"message": "환급 신청이 완료되었습니다.",
			"data": {
				"withdrawnAmount": 130000,
				"balance": 10000,
				"cardAmount": 30000,
				"accountAmount": 70000,
				"processingAmount": 20000,
				"revertedAmount": 10000,
				"accountRequired": false
			}
		}
		""";

	static final String WITHDRAWAL_ACCOUNT_NEEDED_AFTER_REJECT = """
		{
			"status": 200,
			"message": "환급 신청이 완료되었습니다.",
			"data": {
				"withdrawnAmount": 30000,
				"balance": 30000,
				"cardAmount": 0,
				"accountAmount": 0,
				"processingAmount": 0,
				"revertedAmount": 30000,
				"accountRequired": true
			}
		}
		""";

	static final String WALLET_BALANCE_EMPTY = """
		{ "status": 400, "message": "환급할 잔액이 없습니다.", "data": null }
		""";

	static final String WITHDRAWAL_INVALID_INPUT = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "은행 코드는 10자 이하여야 합니다.", "data": null }
		""";

	static final String MISSING_IDEMPOTENCY_KEY = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "필수 요청 헤더(Idempotency-Key)가 없습니다.", "data": null }
		""";

	static final String INVALID_IDEMPOTENCY_KEY = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "Idempotency-Key는 1~64자로 보내주세요.", "data": null }
		""";

	static final String WITHDRAWAL_ACCOUNT_REQUIRED = """
		{ "status": 400, "code": "WITHDRAWAL_ACCOUNT_REQUIRED",
			"message": "계좌로 돌려드릴 금액이 있어 계좌 정보가 필요합니다.", "data": null }
		""";

	static final String WITHDRAWAL_IN_PROGRESS = """
		{ "status": 409, "code": "WITHDRAWAL_IN_PROGRESS",
			"message": "처리 중인 환급이 있어 끝난 뒤에 다시 신청할 수 있습니다.", "data": null }
		""";

	static final String WITHDRAWAL_MEMBER_MISMATCH = """
		{ "status": 409, "message": "이 Idempotency-Key로 환급한 회원과 요청한 회원이 다릅니다.", "data": null }
		""";

	private CashApiExamples() {
	}
}
