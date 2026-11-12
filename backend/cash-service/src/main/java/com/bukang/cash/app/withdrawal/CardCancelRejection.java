package com.bukang.cash.app.withdrawal;

import java.util.Set;

/**
 * 토스가 환급 결제취소를 확실히 거절했을 때(결과 모름이 아닐 때) 그 몫을 어떻게 처리할지.
 * 정책 5장 2차 1 「환급 방법」의 거절 처리 표: 정해 둔 코드 말고는 모두 계좌 송금으로 바꾼다.
 * 코드 출처: docs.tosspayments.com/reference/error-codes (결제 취소)
 */
enum CardCancelRejection {
	// 다시 신청하면 카드로 돌려받을 수 있다. 예치금에 되돌린다
	REVERT,
	// 우리가 모르는 취소로 이미 카드에 돌려줬을 수 있다. 되돌리면 돈이 두 번 나가 사람이 확인한다
	MANUAL_CHECK,
	// 다시 신청해도 카드로는 돌려줄 수 없다. PG가 취소하지 않은 것은 확실해 계좌로 보내도 안전하다
	SWITCH_TO_ACCOUNT;

	private static final Set<String> REVERT_CODES = Set.of(
		// 잠깐 생긴 문제
		"PROVIDER_ERROR", "FORBIDDEN_CONSECUTIVE_REQUEST", "EXCEED_CANCEL_LIMIT",
		// 우리 설정·요청 문제. 고치면 다시 신청해 카드로 돌려받을 수 있다
		"UNAUTHORIZED_KEY", "INCORRECT_BASIC_AUTH_FORMAT", "INVALID_REQUEST", "FORBIDDEN_REQUEST",
		"INVALID_CANCEL_REQUEST_ID");
	private static final Set<String> MANUAL_CHECK_CODES =
		Set.of("ALREADY_CANCELED_PAYMENT", "ALREADY_REFUND_PAYMENT", "NOT_CANCELABLE_AMOUNT");

	// 코드를 읽지 못한 4xx(pgCode null)도 모르는 코드처럼 계좌로 보낸다. Set.of는 null을 물으면 예외라 먼저 거른다
	static CardCancelRejection of(String pgCode) {
		if (pgCode == null) {
			return SWITCH_TO_ACCOUNT;
		}
		if (REVERT_CODES.contains(pgCode)) {
			return REVERT;
		}
		if (MANUAL_CHECK_CODES.contains(pgCode)) {
			return MANUAL_CHECK;
		}
		return SWITCH_TO_ACCOUNT;
	}
}
