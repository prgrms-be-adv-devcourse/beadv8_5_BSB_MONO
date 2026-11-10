package com.bukang.cash.in;

/**
 * InternalV1CashController의 Swagger 응답 예시 (실제 응답 메시지와 같게 유지한다)
 */
final class CashInternalApiExamples {
	static final String PAY_SUCCESS_PAID = """
		{
			"status": 200,
			"message": "결제했습니다.",
			"data": {
				"outcome": "PAID",
				"shortage": 0
			}
		}
		""";

	static final String PAY_SUCCESS_INSUFFICIENT = """
		{
			"status": 200,
			"message": "예치금이 부족합니다.",
			"data": {
				"outcome": "INSUFFICIENT_BALANCE",
				"shortage": 5000
			}
		}
		""";

	static final String BALANCE_SUCCESS = """
		{
			"status": 200,
			"message": "잔액을 조회했습니다.",
			"data": {
				"balance": 10000
			}
		}
		""";

	static final String BALANCE_NO_WALLET = """
		{
			"status": 200,
			"message": "잔액을 조회했습니다.",
			"data": {
				"balance": 0
			}
		}
		""";

	static final String CANCEL_SUCCESS = """
		{
			"status": 200,
			"message": "결제를 취소했습니다.",
			"data": null
		}
		""";

	static final String REFUND_SUCCESS = """
		{
			"status": 200,
			"message": "환불했습니다.",
			"data": {
				"amount": 10000
			}
		}
		""";

	static final String PAYOUT_SUCCESS_PAID = """
		{
			"status": 200,
			"message": "정산금을 지급했습니다.",
			"data": {
				"outcome": "PAID",
				"settlementAmount": 90000,
				"feeAmount": 5500
			}
		}
		""";

	static final String PAYOUT_ALREADY_PAID = """
		{
			"status": 200,
			"message": "이미 지급된 정산입니다.",
			"data": {
				"outcome": "ALREADY_PAID",
				"settlementAmount": 90000,
				"feeAmount": 5500
			}
		}
		""";

	static final String CLAIM_VIRTUAL_ACCOUNT_SUCCESS = """
		{
			"status": 200,
			"message": "가상계좌를 발급했습니다.",
			"data": {
				"bankName": "국민은행",
				"accountNumber": "X6000123456789"
			}
		}
		""";

	static final String CLAIM_AMOUNT_MISSING = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "금액을 작성해주세요.", "data": null }
		""";

	static final String CLAIM_AMOUNT_NOT_POSITIVE = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "금액은 0보다 커야 합니다.", "data": null }
		""";

	static final String INVALID_CLAIM_CODE = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "청구 ID는 영문·숫자·-·_로 6~64자여야 합니다.", "data": null }
		""";

	static final String VIRTUAL_ACCOUNT_REJECTED = """
		{ "status": 503, "message": "PG사가 가상계좌 발급을 거절했습니다.", "data": null }
		""";

	static final String VIRTUAL_ACCOUNT_LOOKUP_REJECTED = """
		{ "status": 503, "message": "PG사에서 가상계좌를 확인하지 못했습니다.", "data": null }
		""";

	static final String VIRTUAL_ACCOUNT_PG_UNREACHABLE = """
		{ "status": 503, "message": "PG 서버에 연결하지 못했습니다. 잠시 후 다시 시도해주세요.", "data": null }
		""";

	static final String VIRTUAL_ACCOUNT_INVALID_RESPONSE = """
		{ "status": 503, "message": "PG 서버 응답이 올바르지 않습니다.", "data": null }
		""";

	static final String VIRTUAL_ACCOUNT_AMOUNT_MISMATCH = """
		{ "status": 503, "message": "PG에 같은 주문번호로 금액이 다른 가상계좌가 있습니다.", "data": null }
		""";

	static final String INVALID_MEMBER_ID = """
		{ "status": 400, "message": "회원 ID를 작성해주세요.", "data": null }
		""";

	static final String INVALID_SELLER_ID = """
		{ "status": 400, "message": "판매자 ID를 작성해주세요.", "data": null }
		""";

	static final String INVALID_BODY = """
		{ "status": 400, "message": "요청 본문 형식이 올바르지 않습니다.", "data": null }
		""";

	static final String INVALID_VALUE_TYPE = """
		{ "status": 400, "code": "INVALID_REQUEST", "message": "요청 값의 형식이 올바르지 않습니다.", "data": null }
		""";

	static final String WALLET_NOT_FOUND = """
		{ "status": 404, "message": "존재하지 않는 지갑입니다.", "data": null }
		""";

	static final String SELLER_NOT_FOUND = """
		{ "status": 404, "message": "존재하지 않는 판매자입니다. sellerId=10", "data": null }
		""";

	static final String PAYOUT_MISMATCH = """
		{
			"status": 409,
			"message": "같은 정산 ID로 이전과 다른 값이 요청됐습니다. payoutId=500",
			"data": {
				"outcome": "ALREADY_PAID",
				"settlementAmount": 90000,
				"feeAmount": 5500
			}
		}
		""";

	static final String PAYMENT_ALREADY_FINALIZED = """
		{ "status": 409, "message": "이미 취소·환불된 결제입니다.", "data": null }
		""";

	static final String PAYMENT_MEMBER_MISMATCH = """
		{ "status": 409, "message": "결제한 회원과 요청한 회원이 다릅니다.", "data": null }
		""";

	static final String REFUND_NOT_FOUND = """
		{ "status": 409, "message": "환불할 결제가 없습니다.", "data": null }
		""";

	static final String REFUND_NOT_COMPLETED = """
		{ "status": 409, "message": "결제가 완료되지 않아 환불할 수 없습니다.", "data": null }
		""";

	private CashInternalApiExamples() {
	}
}
